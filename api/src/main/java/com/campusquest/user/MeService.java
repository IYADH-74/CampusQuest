package com.campusquest.user;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.auth.token.RefreshTokenService;
import com.campusquest.cohort.CohortRepository;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.Refs.CohortRef;
import com.campusquest.progression.LevelCurve;
import com.campusquest.quest.ParticipationRepository;
import com.campusquest.quest.ParticipationStatus;
import com.campusquest.security.AuthenticatedUser;
import com.campusquest.security.PasswordPolicy;
import com.campusquest.security.ratelimit.RateLimitPolicy;
import com.campusquest.security.ratelimit.RateLimiter;
import com.campusquest.user.UserApi.ChangePasswordRequest;
import com.campusquest.user.UserApi.MeView;
import com.campusquest.user.UserApi.Progression;
import com.campusquest.user.UserApi.UpdateProfileRequest;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {

    private final UserRepository users;
    private final CohortRepository cohorts;
    private final ParticipationRepository participations;
    private final LevelCurve curve;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final RateLimiter rateLimiter;
    private final AuditService audit;

    public MeService(UserRepository users, CohortRepository cohorts, ParticipationRepository participations,
            LevelCurve curve, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
            RateLimiter rateLimiter, AuditService audit) {
        this.users = users;
        this.cohorts = cohorts;
        this.participations = participations;
        this.curve = curve;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MeView me(AuthenticatedUser me) {
        return view(load(me));
    }

    /** Builds the profile view. Must run inside a transaction (lazy cohort). */
    @Transactional(readOnly = true)
    public MeView view(User user) {
        List<CohortRef> taught = user.getRole() == Role.TEACHER
                ? cohorts.findTaughtBy(user.getId()).stream().map(CohortRef::of).toList()
                : null;
        Progression progression = null;
        if (user.isStudent()) {
            int used = (int) participations.countChallengesForStudent(user.getId(), ParticipationStatus.IN_FLIGHT);
            long rank = user.isActive() ? users.countActiveStudentsWithMoreXp(user.getXp()) + 1 : 0;
            progression = new Progression(user.getXp(), user.getLevel(), curve.thresholdFor(user.getLevel()),
                    curve.nextThresholdAfter(user.getLevel()), used, curve.challengeSlotsFor(user.getLevel()), rank);
        }
        return new MeView(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole(),
                user.getStatus(), user.getDepartment(), user.getSubject(), CohortRef.of(user.getCohort()), taught,
                progression);
    }

    @Transactional
    public MeView updateProfile(AuthenticatedUser me, UpdateProfileRequest request) {
        User user = load(me);
        user.rename(request.firstName() != null ? request.firstName().strip() : user.getFirstName(),
                request.lastName() != null ? request.lastName().strip() : user.getLastName());
        return view(user);
    }

    /** Changing the password ends every session, including this one: the client must sign in again. */
    @Transactional
    public void changePassword(AuthenticatedUser me, ChangePasswordRequest request) {
        rateLimiter.consume(RateLimitPolicy.CHANGE_PASSWORD_PER_USER, me.id().toString());
        User user = load(me);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        PasswordPolicy.validate(request.newPassword(), user.getEmail());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "Choose a password you are not currently using");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAllSessions(user.getId());
        audit.record(AuditAction.PASSWORD_CHANGED, user.getId(), "user", user.getId());
    }

    private User load(AuthenticatedUser me) {
        return users.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
    }
}

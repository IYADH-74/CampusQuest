package com.campusquest.auth;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.auth.AuthApi.LoginRequest;
import com.campusquest.auth.AuthApi.RegisterRequest;
import com.campusquest.auth.AuthApi.ResetPasswordRequest;
import com.campusquest.auth.AuthApi.SessionResponse;
import com.campusquest.auth.AuthApi.VerifyEmailRequest;
import com.campusquest.auth.token.OneTimeToken;
import com.campusquest.auth.token.OneTimeTokenService;
import com.campusquest.auth.token.RefreshTokenService;
import com.campusquest.auth.token.TokenPurpose;
import com.campusquest.cohort.Cohort;
import com.campusquest.cohort.CohortRepository;
import com.campusquest.common.Text;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.RequestMetadata;
import com.campusquest.config.SecuritySettings;
import com.campusquest.mail.Mailer;
import com.campusquest.quest.EnrollmentService;
import com.campusquest.security.AccessTokenService;
import com.campusquest.security.PasswordPolicy;
import com.campusquest.security.ratelimit.RateLimitPolicy;
import com.campusquest.security.ratelimit.RateLimiter;
import com.campusquest.user.AccountStatus;
import com.campusquest.user.MeService;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle and sessions. Every public flow is written to give the same answer whether or not the
 * email exists, and every guessable secret sits behind a rate limit.
 */
@Service
public class AuthService {

    /** A session plus the refresh token the controller puts in an HttpOnly cookie (never in the body). */
    public record IssuedSession(SessionResponse body, String refreshToken) {}

    private final UserRepository users;
    private final CohortRepository cohorts;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final OneTimeTokenService oneTimeTokens;
    private final EnrollmentService enrollment;
    private final MeService meService;
    private final Mailer mailer;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final SecuritySettings settings;
    private final String dummyHash;

    public AuthService(UserRepository users, CohortRepository cohorts, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens, RefreshTokenService refreshTokens, OneTimeTokenService oneTimeTokens,
            EnrollmentService enrollment, MeService meService, Mailer mailer, RateLimiter rateLimiter,
            AuditService audit, SecuritySettings settings) {
        this.users = users;
        this.cohorts = cohorts;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.oneTimeTokens = oneTimeTokens;
        this.enrollment = enrollment;
        this.meService = meService;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.settings = settings;
        // Verified against when the email is unknown, so "no such user" costs as much time as "wrong password".
        this.dummyHash = passwordEncoder.encode("campusquest-timing-equaliser-" + System.nanoTime());
    }

    // --- registration & verification ---------------------------------------------------------------

    @Transactional
    public void register(RegisterRequest request, RequestMetadata client) {
        rateLimiter.consume(RateLimitPolicy.REGISTER_PER_IP, client.ip());
        String email = Text.normalizeEmail(request.email());
        if (!settings.isEmailDomainAllowed(email)) {
            throw ApiException.badRequest("EMAIL_DOMAIN_NOT_ALLOWED", "Use your university email address");
        }
        PasswordPolicy.validate(request.password(), email);
        Cohort cohort = cohorts.findById(request.cohortId())
                .filter(c -> !c.isArchived())
                .orElseThrow(() -> ApiException.badRequest("INVALID_COHORT", "Unknown or archived cohort"));
        // Hash before the existence check so both branches cost the same.
        String passwordHash = passwordEncoder.encode(request.password());

        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
                mailer.sendVerificationCode(user, oneTimeTokens.issueVerificationCode(user));
            } else if (rateLimiter.tryConsume(RateLimitPolicy.ACCOUNT_NOTICE_PER_EMAIL, email)) {
                mailer.sendAccountAlreadyExists(user);
            }
            return;
        }
        User user = users.save(User.selfRegisteredStudent(email, passwordHash, request.firstName().strip(),
                request.lastName().strip(), cohort));
        mailer.sendVerificationCode(user, oneTimeTokens.issueVerificationCode(user));
        audit.record(AuditAction.USER_REGISTERED, user.getId(), "user", user.getId());
    }

    /** Wrong guesses must be persisted even though we answer 400, hence {@code noRollbackFor}. */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(VerifyEmailRequest request, RequestMetadata client) {
        String email = Text.normalizeEmail(request.email());
        rateLimiter.consume(RateLimitPolicy.VERIFY_PER_IP, client.ip());
        rateLimiter.consume(RateLimitPolicy.VERIFY_PER_EMAIL, email);
        User user = users.findByEmail(email)
                .filter(u -> u.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .orElse(null);
        if (user == null || !oneTimeTokens.redeemVerificationCode(user, request.code())) {
            throw ApiException.badRequest("INVALID_CODE", "The code is invalid or has expired");
        }
        user.activate();
        enrollment.enrollInOpenAssignments(user);
        audit.record(AuditAction.EMAIL_VERIFIED, user.getId(), "user", user.getId());
    }

    @Transactional
    public void resendVerification(String rawEmail, RequestMetadata client) {
        String email = Text.normalizeEmail(rawEmail);
        rateLimiter.consume(RateLimitPolicy.VERIFY_PER_IP, client.ip());
        rateLimiter.consume(RateLimitPolicy.VERIFY_PER_EMAIL, email);
        users.findByEmail(email)
                .filter(u -> u.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(u -> mailer.sendVerificationCode(u, oneTimeTokens.issueVerificationCode(u)));
    }

    // --- sessions ---------------------------------------------------------------------------------

    @Transactional
    public IssuedSession login(LoginRequest request, RequestMetadata client) {
        String email = Text.normalizeEmail(request.email());
        rateLimiter.consume(RateLimitPolicy.LOGIN_PER_IP, client.ip());
        rateLimiter.requireBudget(RateLimitPolicy.LOGIN_FAILURES_PER_EMAIL, email);

        User user = users.findByEmail(email).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash) && user != null
                && user.getPasswordHash() != null;
        if (!passwordMatches) {
            rateLimiter.tryConsume(RateLimitPolicy.LOGIN_FAILURES_PER_EMAIL, email);
            audit.record(AuditAction.LOGIN_FAILED, user == null ? null : user.getId(), "user",
                    user == null ? null : user.getId(), Map.of("email", email));
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        rateLimiter.reset(RateLimitPolicy.LOGIN_FAILURES_PER_EMAIL, email);
        // Only reveal account state to someone who proved they know the password.
        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> throw ApiException.forbidden("EMAIL_NOT_VERIFIED",
                    "Verify your email address before signing in");
            case DISABLED -> throw ApiException.forbidden("ACCOUNT_DISABLED", "This account has been disabled");
            case INVITED -> throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
            case ACTIVE -> { }
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.upgradePasswordHash(passwordEncoder.encode(request.password()));
        }
        String refreshToken = refreshTokens.startSession(user, client);
        audit.record(AuditAction.LOGIN_SUCCEEDED, user.getId(), "user", user.getId());
        return new IssuedSession(session(user), refreshToken);
    }

    /** Reuse detection revokes the token family and then answers 401; that revocation must commit. */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession refresh(String refreshToken, RequestMetadata client) {
        rateLimiter.consume(RateLimitPolicy.REFRESH_PER_IP, client.ip());
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken, client);
        return new IssuedSession(session(rotation.user()), rotation.newToken());
    }

    public void logout(String refreshToken) {
        refreshTokens.endSession(refreshToken);
    }

    // --- password recovery ------------------------------------------------------------------------

    @Transactional
    public void forgotPassword(String rawEmail, RequestMetadata client) {
        String email = Text.normalizeEmail(rawEmail);
        rateLimiter.consume(RateLimitPolicy.FORGOT_PASSWORD_PER_IP, client.ip());
        if (!rateLimiter.tryConsume(RateLimitPolicy.FORGOT_PASSWORD_PER_EMAIL, email)) {
            return; // Silently drop: answering 429 here would confirm the address is being targeted.
        }
        users.findByEmail(email).filter(User::isActive).ifPresent(user -> {
            mailer.sendPasswordReset(user, oneTimeTokens.issueLinkToken(user, TokenPurpose.PASSWORD_RESET));
            audit.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), "user", user.getId());
        });
    }

    /** Completes a password reset or an invite. Either way, every existing session is revoked. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, RequestMetadata client) {
        rateLimiter.consume(RateLimitPolicy.RESET_PASSWORD_PER_IP, client.ip());
        OneTimeToken token = oneTimeTokens.redeemLinkToken(request.token(),
                        EnumSet.of(TokenPurpose.PASSWORD_RESET, TokenPurpose.ACCOUNT_INVITE))
                .orElseThrow(AuthService::invalidLink);
        User user = users.findByIdForUpdate(token.getUserId()).orElseThrow(AuthService::invalidLink);
        if (user.getStatus() == AccountStatus.DISABLED || user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw invalidLink();
        }
        PasswordPolicy.validate(request.newPassword(), user.getEmail());
        boolean firstActivation = user.getStatus() == AccountStatus.INVITED;
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        if (firstActivation) {
            user.activate();
            enrollment.enrollInOpenAssignments(user);
        }
        oneTimeTokens.invalidateAll(user, EnumSet.of(TokenPurpose.PASSWORD_RESET, TokenPurpose.ACCOUNT_INVITE));
        refreshTokens.revokeAllSessions(user.getId());
        audit.record(AuditAction.PASSWORD_RESET_COMPLETED, user.getId(), "user", user.getId(),
                Map.of("invite", firstActivation));
    }

    private SessionResponse session(User user) {
        AccessTokenService.IssuedAccessToken token = accessTokens.issue(user);
        return new SessionResponse(token.value(), "Bearer", token.expiresInSeconds(), meService.view(user));
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired");
    }
}

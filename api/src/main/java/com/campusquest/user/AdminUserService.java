package com.campusquest.user;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.auth.token.OneTimeTokenService;
import com.campusquest.auth.token.RefreshTokenService;
import com.campusquest.auth.token.TokenPurpose;
import com.campusquest.cohort.Cohort;
import com.campusquest.cohort.CohortRepository;
import com.campusquest.common.Text;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.PageResponse;
import com.campusquest.mail.Mailer;
import com.campusquest.quest.EnrollmentService;
import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.UserApi.AdminUserView;
import com.campusquest.user.UserApi.InviteUserRequest;
import com.campusquest.user.UserApi.UpdateUserRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account administration. Admins invite people. They never set or see passwords and cannot change roles. */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final CohortRepository cohorts;
    private final OneTimeTokenService oneTimeTokens;
    private final RefreshTokenService refreshTokens;
    private final EnrollmentService enrollment;
    private final Mailer mailer;
    private final AuditService audit;

    public AdminUserService(UserRepository users, CohortRepository cohorts, OneTimeTokenService oneTimeTokens,
            RefreshTokenService refreshTokens, EnrollmentService enrollment, Mailer mailer, AuditService audit) {
        this.users = users;
        this.cohorts = cohorts;
        this.oneTimeTokens = oneTimeTokens;
        this.refreshTokens = refreshTokens;
        this.enrollment = enrollment;
        this.mailer = mailer;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> search(Role role, AccountStatus status, String query, int page, int size) {
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            String q = Text.trimToNull(query);
            if (q != null) {
                String pattern = Text.likeContains(q);
                predicates.add(cb.or(
                        cb.like(root.get("email"), pattern, '\\'),
                        cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("lastName")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page, size, Sort.by("lastName", "firstName", "id"));
        return PageResponse.of(users.findAll(spec, pageable), AdminUserView::of);
    }

    @Transactional(readOnly = true)
    public AdminUserView get(UUID id) {
        return AdminUserView.of(load(id));
    }

    @Transactional
    public AdminUserView invite(AuthenticatedUser admin, InviteUserRequest request) {
        String email = Text.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = User.invited(email, request.firstName().strip(), request.lastName().strip(), request.role());
        applyRoleAttributes(user, request.department(), request.subject(), request.cohortId(), true);
        users.save(user);
        mailer.sendInvite(user, oneTimeTokens.issueLinkToken(user, TokenPurpose.ACCOUNT_INVITE));
        audit.record(AuditAction.USER_INVITED, admin.id(), "user", user.getId(), Map.of("role", user.getRole().name()));
        return AdminUserView.of(user);
    }

    @Transactional
    public AdminUserView update(AuthenticatedUser admin, UUID id, UpdateUserRequest request) {
        User user = load(id);
        if (request.firstName() != null || request.lastName() != null) {
            user.rename(request.firstName() != null ? request.firstName().strip() : user.getFirstName(),
                    request.lastName() != null ? request.lastName().strip() : user.getLastName());
        }
        applyRoleAttributes(user, request.department(), request.subject(), request.cohortId(), false);
        audit.record(AuditAction.USER_UPDATED, admin.id(), "user", user.getId());
        return AdminUserView.of(user);
    }

    @Transactional
    public AdminUserView disable(AuthenticatedUser admin, UUID id) {
        if (admin.id().equals(id)) {
            throw ApiException.conflict("CANNOT_DISABLE_SELF", "You cannot disable your own account");
        }
        User user = users.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getStatus() == AccountStatus.DISABLED) {
            return AdminUserView.of(user);
        }
        if (user.getRole() == Role.ADMIN && user.isActive()
                && users.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "The last active admin cannot be disabled");
        }
        user.disable();
        refreshTokens.revokeAllSessions(user.getId());
        audit.record(AuditAction.USER_DISABLED, admin.id(), "user", user.getId());
        return AdminUserView.of(user);
    }

    @Transactional
    public AdminUserView enable(AuthenticatedUser admin, UUID id) {
        User user = load(id);
        if (user.getStatus() != AccountStatus.DISABLED) {
            throw ApiException.conflict("NOT_DISABLED", "Only disabled accounts can be enabled");
        }
        user.enable();
        enrollment.enrollInOpenAssignments(user);
        audit.record(AuditAction.USER_ENABLED, admin.id(), "user", user.getId());
        return AdminUserView.of(user);
    }

    @Transactional
    public void resendInvite(AuthenticatedUser admin, UUID id) {
        User user = load(id);
        if (user.getStatus() != AccountStatus.INVITED) {
            throw ApiException.conflict("NOT_INVITED", "This account has already been set up");
        }
        mailer.sendInvite(user, oneTimeTokens.issueLinkToken(user, TokenPurpose.ACCOUNT_INVITE));
        audit.record(AuditAction.USER_INVITED, admin.id(), "user", user.getId(), Map.of("resent", true));
    }

    private void applyRoleAttributes(User user, String department, String subject, UUID cohortId, boolean creating) {
        String dept = Text.trimToNull(department);
        String subj = Text.trimToNull(subject);
        if (dept != null) {
            if (user.getRole() != Role.STAFF) {
                throw ApiException.badRequest("ATTRIBUTE_NOT_APPLICABLE", "Only staff have a department");
            }
            user.assignDepartment(dept);
        }
        if (subj != null) {
            if (user.getRole() != Role.TEACHER) {
                throw ApiException.badRequest("ATTRIBUTE_NOT_APPLICABLE", "Only teachers have a subject");
            }
            user.assignSubject(subj);
        }
        if (cohortId != null) {
            if (!user.isStudent()) {
                throw ApiException.badRequest("ATTRIBUTE_NOT_APPLICABLE", "Only students belong to a cohort");
            }
            Cohort cohort = cohorts.findById(cohortId)
                    .filter(c -> !c.isArchived())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_COHORT", "Unknown or archived cohort"));
            if (creating) {
                user.assignCohort(cohort);
            } else {
                enrollment.moveStudent(user, cohort);
            }
        }
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }
}

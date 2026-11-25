package com.campusquest.cohort;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.cohort.CohortApi.CohortDetail;
import com.campusquest.cohort.CohortApi.CohortStudent;
import com.campusquest.cohort.CohortApi.CohortSummary;
import com.campusquest.cohort.CohortApi.CreateCohortRequest;
import com.campusquest.cohort.CohortApi.PublicCohort;
import com.campusquest.common.Text;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.PageResponse;
import com.campusquest.common.web.Refs.UserRef;
import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CohortService {

    private final CohortRepository cohorts;
    private final UserRepository users;
    private final AuditService audit;

    public CohortService(CohortRepository cohorts, UserRepository users, AuditService audit) {
        this.cohorts = cohorts;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PublicCohort> openForRegistration() {
        return cohorts.findAllByArchivedFalseOrderByCode().stream()
                .map(c -> new PublicCohort(c.getId(), c.getCode(), c.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CohortSummary> list(boolean includeArchived) {
        Map<UUID, Long> counts = studentCounts();
        List<Cohort> all = includeArchived ? cohorts.findAllByOrderByCode() : cohorts.findAllByArchivedFalseOrderByCode();
        return all.stream()
                .map(c -> new CohortSummary(c.getId(), c.getCode(), c.getName(), c.isArchived(),
                        counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CohortDetail get(UUID id) {
        return detail(load(id));
    }

    @Transactional
    public CohortDetail create(AuthenticatedUser me, CreateCohortRequest request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (cohorts.existsByCode(code)) {
            throw ApiException.conflict("COHORT_CODE_TAKEN", "A cohort with this code already exists");
        }
        Cohort cohort = cohorts.saveAndFlush(new Cohort(code, request.name().strip()));
        audit.record(AuditAction.COHORT_CREATED, me.id(), "cohort", cohort.getId(), Map.of("code", code));
        return detail(cohort);
    }

    @Transactional
    public CohortDetail rename(AuthenticatedUser me, UUID id, String name) {
        Cohort cohort = load(id);
        cohort.rename(name.strip());
        audit.record(AuditAction.COHORT_UPDATED, me.id(), "cohort", cohort.getId());
        return detail(cohort);
    }

    @Transactional
    public CohortDetail archive(AuthenticatedUser me, UUID id) {
        Cohort cohort = load(id);
        cohort.archive();
        audit.record(AuditAction.COHORT_ARCHIVED, me.id(), "cohort", cohort.getId());
        return detail(cohort);
    }

    @Transactional
    public CohortDetail restore(AuthenticatedUser me, UUID id) {
        Cohort cohort = load(id);
        cohort.restore();
        audit.record(AuditAction.COHORT_RESTORED, me.id(), "cohort", cohort.getId());
        return detail(cohort);
    }

    @Transactional
    public CohortDetail replaceTeachers(AuthenticatedUser me, UUID id, Set<UUID> teacherIds) {
        Cohort cohort = load(id);
        List<User> teachers = users.findAllById(teacherIds);
        if (teachers.size() != teacherIds.size() || teachers.stream().anyMatch(t -> t.getRole() != Role.TEACHER)) {
            throw ApiException.badRequest("INVALID_TEACHER", "Every id must belong to an existing teacher");
        }
        cohort.replaceTeachers(teachers);
        audit.record(AuditAction.COHORT_TEACHERS_CHANGED, me.id(), "cohort", cohort.getId(),
                Map.of("teacherIds", teacherIds.stream().map(UUID::toString).sorted().toList()));
        return detail(cohort);
    }

    /** Staff and admins see any roster; a teacher only sees cohorts they teach. */
    @Transactional(readOnly = true)
    public PageResponse<CohortStudent> students(AuthenticatedUser me, UUID cohortId, int page, int size) {
        load(cohortId);
        if (me.is(Role.TEACHER) && !cohorts.isTaughtBy(me.id(), cohortId)) {
            throw ApiException.notFound("Cohort");
        }
        var pageable = PageRequest.of(page, size, Sort.by("lastName", "firstName", "id"));
        return PageResponse.of(users.findStudentsInCohort(cohortId, pageable),
                u -> new CohortStudent(u.getId(), Text.displayName(u.getFirstName(), u.getLastName()), u.getLevel(), u.getXp()));
    }

    private CohortDetail detail(Cohort cohort) {
        List<UserRef> teachers = cohort.getTeachers().stream()
                .map(UserRef::of)
                .sorted(Comparator.comparing(UserRef::displayName))
                .toList();
        long students = studentCounts().getOrDefault(cohort.getId(), 0L);
        return new CohortDetail(cohort.getId(), cohort.getCode(), cohort.getName(), cohort.isArchived(), students,
                teachers, cohort.getCreatedAt(), cohort.getUpdatedAt());
    }

    private Map<UUID, Long> studentCounts() {
        return cohorts.countStudentsPerCohort().stream()
                .collect(Collectors.toMap(CohortRepository.StudentCount::getCohortId, CohortRepository.StudentCount::getStudents));
    }

    private Cohort load(UUID id) {
        return cohorts.findById(id).orElseThrow(() -> ApiException.notFound("Cohort"));
    }
}

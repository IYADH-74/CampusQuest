package com.campusquest.cohort;

import com.campusquest.cohort.CohortApi.CohortDetail;
import com.campusquest.cohort.CohortApi.CohortStudent;
import com.campusquest.cohort.CohortApi.CohortSummary;
import com.campusquest.cohort.CohortApi.CreateCohortRequest;
import com.campusquest.cohort.CohortApi.PublicCohort;
import com.campusquest.cohort.CohortApi.RenameCohortRequest;
import com.campusquest.cohort.CohortApi.ReplaceTeachersRequest;
import com.campusquest.common.web.PageResponse;
import com.campusquest.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CohortController {

    private final CohortService cohorts;

    public CohortController(CohortService cohorts) {
        this.cohorts = cohorts;
    }

    /** Unauthenticated: the registration form needs to offer cohorts. Codes and names only. */
    @GetMapping("/api/v1/public/cohorts")
    public List<PublicCohort> openForRegistration() {
        return cohorts.openForRegistration();
    }

    @GetMapping("/api/v1/cohorts")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'TEACHER')")
    public List<CohortSummary> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return cohorts.list(includeArchived);
    }

    @GetMapping("/api/v1/cohorts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'TEACHER')")
    public CohortDetail get(@PathVariable UUID id) {
        return cohorts.get(id);
    }

    @PostMapping("/api/v1/cohorts")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<CohortDetail> create(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CreateCohortRequest request) {
        CohortDetail created = cohorts.create(me, request);
        return ResponseEntity.created(URI.create("/api/v1/cohorts/" + created.id())).body(created);
    }

    @PatchMapping("/api/v1/cohorts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public CohortDetail rename(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody RenameCohortRequest request) {
        return cohorts.rename(me, id, request.name());
    }

    @PostMapping("/api/v1/cohorts/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public CohortDetail archive(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return cohorts.archive(me, id);
    }

    @PostMapping("/api/v1/cohorts/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public CohortDetail restore(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return cohorts.restore(me, id);
    }

    @PutMapping("/api/v1/cohorts/{id}/teachers")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public CohortDetail replaceTeachers(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody ReplaceTeachersRequest request) {
        return cohorts.replaceTeachers(me, id, request.teacherIds());
    }

    @GetMapping("/api/v1/cohorts/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'TEACHER')")
    public PageResponse<CohortStudent> students(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return cohorts.students(me, id, page, size);
    }
}

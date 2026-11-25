package com.campusquest.cohort;

import com.campusquest.common.web.Refs.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CohortApi {

    private CohortApi() {}

    public record CreateCohortRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$",
                    message = "1-32 letters, digits, '-' or '_'") String code,
            @NotBlank @Size(max = 120) String name) {}

    public record RenameCohortRequest(@NotBlank @Size(max = 120) String name) {}

    public record ReplaceTeachersRequest(@NotNull @Size(max = 100) Set<@NotNull UUID> teacherIds) {}

    public record CohortSummary(UUID id, String code, String name, boolean archived, long studentCount) {}

    public record CohortDetail(UUID id, String code, String name, boolean archived, long studentCount,
            List<UserRef> teachers, Instant createdAt, Instant updatedAt) {}

    public record PublicCohort(UUID id, String code, String name) {}

    public record CohortStudent(UUID id, String displayName, int level, long xp) {}
}

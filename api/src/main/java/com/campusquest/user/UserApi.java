package com.campusquest.user;

import com.campusquest.common.Text;
import com.campusquest.common.web.Refs.CohortRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response contracts for accounts. No view here ever includes a password hash or token. */
public final class UserApi {

    private UserApi() {}

    // --- me ---------------------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeView(
            UUID id,
            String email,
            String firstName,
            String lastName,
            Role role,
            AccountStatus status,
            String department,
            String subject,
            CohortRef cohort,
            List<CohortRef> taughtCohorts,
            Progression progression) {}

    public record Progression(
            long xp,
            int level,
            long currentLevelXp,
            Long nextLevelXp,
            int challengeSlotsUsed,
            int challengeSlotsMax,
            long rank) {}

    public record UpdateProfileRequest(
            @Size(min = 1, max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String firstName,
            @Size(min = 1, max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String lastName) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {}

    // --- admin ------------------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminUserView(
            UUID id,
            String email,
            String firstName,
            String lastName,
            Role role,
            AccountStatus status,
            String department,
            String subject,
            CohortRef cohort,
            Long xp,
            Integer level,
            Instant createdAt,
            Instant updatedAt) {

        public static AdminUserView of(User user) {
            boolean student = user.isStudent();
            return new AdminUserView(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.getRole(), user.getStatus(), user.getDepartment(), user.getSubject(),
                    CohortRef.of(user.getCohort()), student ? user.getXp() : null, student ? user.getLevel() : null,
                    user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    public record InviteUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String firstName,
            @NotBlank @Size(max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String lastName,
            @NotNull Role role,
            @Size(max = 120) String department,
            @Size(max = 120) String subject,
            UUID cohortId) {}

    /** Partial update. Role and email are deliberately immutable. */
    public record UpdateUserRequest(
            @Size(min = 1, max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String firstName,
            @Size(min = 1, max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String lastName,
            @Size(max = 120) String department,
            @Size(max = 120) String subject,
            UUID cohortId) {}
}

package com.campusquest.quest;

import com.campusquest.common.web.Refs.CohortRef;
import com.campusquest.common.web.Refs.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response contracts for quests and participations. */
public final class QuestApi {

    private QuestApi() {}

    // --- requests ---------------------------------------------------------------------------------

    public record CreateQuestRequest(
            @NotNull QuestKind kind,
            QuestCategory category,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull @Min(1) @Max(5) Integer tier,
            Instant dueAt,
            @Min(1) @Max(100_000) Integer maxParticipants,
            @Min(0) @Max(20) Integer minLevel,
            @Size(max = 50) Set<@NotNull UUID> cohortIds) {}

    /** Partial update: {@code null} means "leave unchanged". */
    public record UpdateQuestRequest(
            @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
            @Size(min = 1, max = 5000) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String description,
            QuestCategory category,
            @Min(1) @Max(5) Integer tier,
            Instant dueAt,
            @Min(1) @Max(100_000) Integer maxParticipants,
            @Min(0) @Max(20) Integer minLevel,
            @Size(min = 1, max = 50) Set<@NotNull UUID> cohortIds) {}

    public record SubmitRequest(
            @NotBlank @Size(max = 5000) String text,
            @Size(max = 2048) @Pattern(regexp = "^https://\\S+$", message = "must be an https:// link") String url) {}

    public record ReviewRequest(@Size(max = 2000) String feedback) {}

    public record FeedbackRequiredReviewRequest(@NotBlank @Size(max = 2000) String feedback) {}

    // --- responses --------------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestView(
            UUID id,
            QuestKind kind,
            QuestCategory category,
            String title,
            String description,
            int tier,
            int xpReward,
            QuestStatus status,
            UserRef author,
            Instant dueAt,
            Integer maxParticipants,
            int minLevel,
            long participantCount,
            List<CohortRef> cohorts,
            ParticipationRef myParticipation,
            Instant publishedAt,
            Instant closedAt,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt) {

        static QuestView of(Quest quest, int xpReward, long participantCount, ParticipationRef mine) {
            List<CohortRef> cohorts = quest.getTargetCohorts().stream()
                    .map(CohortRef::of)
                    .sorted(Comparator.comparing(CohortRef::code))
                    .toList();
            return new QuestView(quest.getId(), quest.getKind(), quest.getCategory(), quest.getTitle(),
                    quest.getDescription(), quest.getTier(), xpReward, quest.getStatus(), UserRef.of(quest.getAuthor()),
                    quest.getDueAt(), quest.getMaxParticipants(), quest.getMinLevel(), participantCount, cohorts, mine,
                    quest.getPublishedAt(), quest.getClosedAt(), quest.getCancelledAt(), quest.getCreatedAt(),
                    quest.getUpdatedAt());
        }
    }

    public record ParticipationRef(UUID id, ParticipationStatus status) {}

    public record QuestRef(UUID id, String title, QuestKind kind, QuestCategory category, int tier, QuestStatus status,
            Instant dueAt) {
        static QuestRef of(Quest quest) {
            return new QuestRef(quest.getId(), quest.getTitle(), quest.getKind(), quest.getCategory(), quest.getTier(),
                    quest.getStatus(), quest.getDueAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParticipationView(
            UUID id,
            QuestRef quest,
            UserRef student,
            ParticipationStatus status,
            Instant joinedAt,
            Instant submittedAt,
            String submissionText,
            String submissionUrl,
            UserRef reviewer,
            Instant reviewedAt,
            String feedback,
            Integer xpAwarded,
            int revisionCount) {

        public static ParticipationView of(Participation p) {
            return new ParticipationView(p.getId(), QuestRef.of(p.getQuest()), UserRef.of(p.getStudent()), p.getStatus(),
                    p.getJoinedAt(), p.getSubmittedAt(), p.getSubmissionText(), p.getSubmissionUrl(),
                    UserRef.of(p.getReviewer()), p.getReviewedAt(), p.getFeedback(), p.getXpAwarded(),
                    p.getRevisionCount());
        }
    }
}

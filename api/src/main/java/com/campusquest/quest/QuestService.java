package com.campusquest.quest;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.cohort.Cohort;
import com.campusquest.cohort.CohortRepository;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.PageResponse;
import com.campusquest.progression.LevelCurve;
import com.campusquest.quest.QuestApi.CreateQuestRequest;
import com.campusquest.quest.QuestApi.ParticipationRef;
import com.campusquest.quest.QuestApi.QuestView;
import com.campusquest.quest.QuestApi.UpdateQuestRequest;
import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.Role;
import com.campusquest.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quest authoring and discovery. */
@Service
public class QuestService {

    static final Duration MIN_LEAD_TIME = Duration.ofMinutes(5);
    static final Duration MAX_HORIZON = Duration.ofDays(366);

    private final QuestRepository quests;
    private final ParticipationRepository participations;
    private final CohortRepository cohorts;
    private final UserRepository users;
    private final EnrollmentService enrollment;
    private final LevelCurve curve;
    private final AuditService audit;
    private final Clock clock;

    public QuestService(QuestRepository quests, ParticipationRepository participations, CohortRepository cohorts,
            UserRepository users, EnrollmentService enrollment, LevelCurve curve, AuditService audit, Clock clock) {
        this.quests = quests;
        this.participations = participations;
        this.cohorts = cohorts;
        this.users = users;
        this.enrollment = enrollment;
        this.curve = curve;
        this.audit = audit;
        this.clock = clock;
    }

    // --- authoring --------------------------------------------------------------------------------

    @Transactional
    public QuestView create(AuthenticatedUser me, CreateQuestRequest request) {
        requireAuthorRole(me);
        QuestKind kind = request.kind();
        QuestCategory category = request.category() != null ? request.category()
                : kind == QuestKind.ASSIGNMENT ? QuestCategory.COURSEWORK : null;
        if (category == null) {
            throw ApiException.badRequest("CATEGORY_REQUIRED", "Challenges need a category");
        }
        if (!category.allowedFor(kind)) {
            throw ApiException.badRequest("INVALID_CATEGORY", "Category " + category + " is not valid for " + kind);
        }
        Quest quest = new Quest(kind, category, request.title().strip(), request.description().strip(), request.tier(),
                users.getReferenceById(me.id()));
        Instant now = clock.instant();
        if (kind == QuestKind.ASSIGNMENT) {
            if (!me.is(Role.TEACHER)) {
                throw ApiException.forbidden("ONLY_TEACHERS_ASSIGN", "Only teachers can set assignments");
            }
            if (request.dueAt() == null) {
                throw ApiException.badRequest("DUE_DATE_REQUIRED", "Assignments need a due date");
            }
            if (request.maxParticipants() != null || (request.minLevel() != null && request.minLevel() != 0)) {
                throw ApiException.badRequest("NOT_APPLICABLE_TO_ASSIGNMENTS",
                        "Capacity and minimum level only apply to challenges");
            }
            quest.retarget(resolveTeachableCohorts(me, request.cohortIds()));
        } else {
            if (request.cohortIds() != null && !request.cohortIds().isEmpty()) {
                throw ApiException.badRequest("CHALLENGES_ARE_CAMPUS_WIDE", "Challenges cannot target cohorts");
            }
            quest.changeMaxParticipants(request.maxParticipants());
            quest.changeMinLevel(validMinLevel(request.minLevel() == null ? 0 : request.minLevel()));
        }
        if (request.dueAt() != null) {
            quest.changeDueAt(validDueAt(request.dueAt(), now));
        }
        quests.save(quest);
        return view(quest, me);
    }

    @Transactional
    public QuestView update(AuthenticatedUser me, UUID questId, UpdateQuestRequest request) {
        Quest quest = managedQuest(me, questId);
        Instant now = clock.instant();
        switch (quest.getStatus()) {
            case DRAFT -> applyDraftEdits(me, quest, request, now);
            case PUBLISHED, CLOSED -> applyLiveEdits(quest, request, now);
            case CANCELLED -> throw ApiException.conflict("INVALID_QUEST_STATE", "Cancelled quests cannot be edited");
        }
        return view(quest, me);
    }

    @Transactional
    public QuestView publish(AuthenticatedUser me, UUID questId) {
        Quest quest = managedQuest(me, questId);
        Instant now = clock.instant();
        if (quest.getDueAt() != null) {
            validDueAt(quest.getDueAt(), now);
        }
        if (quest.isAssignment()) {
            // Re-check: the roster may have changed since the draft was written.
            resolveTeachableCohorts(me, quest.getTargetCohorts().stream().map(Cohort::getId).toList());
        }
        quest.publish(now);
        int enrolled = quest.isAssignment() ? enrollment.enrollTargetCohorts(quest) : 0;
        audit.record(AuditAction.QUEST_PUBLISHED, me.id(), "quest", quest.getId(),
                Map.of("kind", quest.getKind().name(), "enrolled", enrolled));
        return view(quest, me);
    }

    @Transactional
    public QuestView close(AuthenticatedUser me, UUID questId) {
        Quest quest = managedQuest(me, questId);
        quest.close(clock.instant());
        return view(quest, me);
    }

    @Transactional
    public QuestView cancel(AuthenticatedUser me, UUID questId) {
        Quest quest = managedQuest(me, questId);
        quest.cancel(clock.instant());
        int dropped = 0;
        for (Participation p : participations.findByQuestAndStatuses(quest.getId(), ParticipationStatus.IN_FLIGHT)) {
            if (p.cancelIfOpen()) {
                dropped++;
            }
        }
        audit.record(AuditAction.QUEST_CANCELLED, me.id(), "quest", quest.getId(), Map.of("cancelledParticipations", dropped));
        return view(quest, me);
    }

    @Transactional
    public void deleteDraft(AuthenticatedUser me, UUID questId) {
        Quest quest = managedQuest(me, questId);
        quest.requireDraft();
        quests.delete(quest);
    }

    // --- reading ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public QuestView get(AuthenticatedUser me, UUID questId) {
        Quest quest = quests.findById(questId).orElseThrow(() -> ApiException.notFound("Quest"));
        boolean participates = me.is(Role.STUDENT)
                && participations.findByQuestIdAndStudentId(questId, me.id()).isPresent();
        if (!QuestAccess.canView(quest, me, participates)) {
            throw ApiException.notFound("Quest");
        }
        return view(quest, me);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestView> openChallenges(AuthenticatedUser me, QuestCategory category, int page, int size) {
        return views(quests.findOpenChallenges(clock.instant(), category, PageRequest.of(page, size)), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestView> authoredBy(AuthenticatedUser me, QuestStatus status, int page, int size) {
        return views(quests.findAuthoredBy(me.id(), status, PageRequest.of(page, size)), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestView> search(AuthenticatedUser me, QuestStatus status, QuestKind kind, int page, int size) {
        return views(quests.search(status, kind, PageRequest.of(page, size)), me);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void applyDraftEdits(AuthenticatedUser me, Quest quest, UpdateQuestRequest request, Instant now) {
        if (request.title() != null) {
            quest.retitle(request.title().strip());
        }
        if (request.description() != null) {
            quest.describe(request.description().strip());
        }
        if (request.category() != null) {
            if (!request.category().allowedFor(quest.getKind())) {
                throw ApiException.badRequest("INVALID_CATEGORY", "Category not valid for " + quest.getKind());
            }
            quest.changeCategory(request.category());
        }
        if (request.tier() != null) {
            quest.changeTier(request.tier());
        }
        if (request.dueAt() != null) {
            quest.changeDueAt(validDueAt(request.dueAt(), now));
        }
        if (quest.isAssignment()) {
            if (request.maxParticipants() != null || request.minLevel() != null) {
                throw ApiException.badRequest("NOT_APPLICABLE_TO_ASSIGNMENTS",
                        "Capacity and minimum level only apply to challenges");
            }
            if (request.cohortIds() != null) {
                quest.retarget(resolveTeachableCohorts(me, request.cohortIds()));
            }
        } else {
            if (request.cohortIds() != null) {
                throw ApiException.badRequest("CHALLENGES_ARE_CAMPUS_WIDE", "Challenges cannot target cohorts");
            }
            if (request.maxParticipants() != null) {
                quest.changeMaxParticipants(request.maxParticipants());
            }
            if (request.minLevel() != null) {
                quest.changeMinLevel(validMinLevel(request.minLevel()));
            }
        }
    }

    /** Once students can see a quest, only cosmetic edits and changes in their favour are allowed. */
    private void applyLiveEdits(Quest quest, UpdateQuestRequest request, Instant now) {
        if (request.category() != null || request.tier() != null || request.minLevel() != null
                || request.cohortIds() != null) {
            throw ApiException.conflict("FIELD_LOCKED_AFTER_PUBLISH",
                    "Category, tier, minimum level and cohorts are locked once published");
        }
        if (request.title() != null) {
            quest.retitle(request.title().strip());
        }
        if (request.description() != null) {
            quest.describe(request.description().strip());
        }
        if (request.dueAt() != null) {
            if (quest.getDueAt() == null || !request.dueAt().isAfter(quest.getDueAt())) {
                throw ApiException.conflict("DUE_DATE_CAN_ONLY_EXTEND", "A published due date can only move later");
            }
            quest.changeDueAt(validDueAt(request.dueAt(), now));
        }
        if (request.maxParticipants() != null) {
            if (quest.getMaxParticipants() == null || request.maxParticipants() < quest.getMaxParticipants()) {
                throw ApiException.conflict("CAPACITY_CAN_ONLY_GROW", "A published capacity can only grow");
            }
            quest.changeMaxParticipants(request.maxParticipants());
        }
    }

    private Quest managedQuest(AuthenticatedUser me, UUID questId) {
        Quest quest = quests.findByIdForUpdate(questId).orElseThrow(() -> ApiException.notFound("Quest"));
        if (!QuestAccess.canManage(quest, me)) {
            throw ApiException.notFound("Quest");
        }
        return quest;
    }

    private List<Cohort> resolveTeachableCohorts(AuthenticatedUser me, Collection<UUID> cohortIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            throw ApiException.badRequest("COHORTS_REQUIRED", "Assignments must target at least one cohort");
        }
        Set<UUID> ids = Set.copyOf(cohortIds);
        List<Cohort> found = cohorts.findAllById(ids);
        if (found.size() != ids.size()) {
            throw ApiException.badRequest("UNKNOWN_COHORT", "One or more cohorts do not exist");
        }
        if (found.stream().anyMatch(Cohort::isArchived)) {
            throw ApiException.badRequest("COHORT_ARCHIVED", "Archived cohorts cannot receive assignments");
        }
        if (cohorts.countTaughtBy(me.id(), ids) != ids.size()) {
            throw ApiException.forbidden("NOT_TEACHING_COHORT", "You can only set assignments for cohorts you teach");
        }
        return found;
    }

    private Instant validDueAt(Instant dueAt, Instant now) {
        if (dueAt.isBefore(now.plus(MIN_LEAD_TIME)) || dueAt.isAfter(now.plus(MAX_HORIZON))) {
            throw ApiException.badRequest("INVALID_DUE_DATE",
                    "Due date must be at least 5 minutes and at most one year in the future");
        }
        return dueAt;
    }

    private int validMinLevel(int minLevel) {
        if (minLevel > curve.maxLevel()) {
            throw ApiException.badRequest("INVALID_MIN_LEVEL", "Minimum level cannot exceed " + curve.maxLevel());
        }
        return minLevel;
    }

    private static void requireAuthorRole(AuthenticatedUser me) {
        if (!me.is(Role.TEACHER) && !me.is(Role.STAFF)) {
            throw ApiException.forbidden("FORBIDDEN", "Only teachers and staff can author quests");
        }
    }

    private QuestView view(Quest quest, AuthenticatedUser me) {
        long count = participations.countByQuestIdAndStatusNotIn(quest.getId(), ParticipationStatus.NOT_HOLDING_A_PLACE);
        ParticipationRef mine = me.is(Role.STUDENT)
                ? participations.findByQuestIdAndStudentId(quest.getId(), me.id())
                        .map(p -> new ParticipationRef(p.getId(), p.getStatus())).orElse(null)
                : null;
        return QuestView.of(quest, curve.rewardFor(quest.getTier()), count, mine);
    }

    private PageResponse<QuestView> views(Page<Quest> page, AuthenticatedUser me) {
        List<UUID> ids = page.getContent().stream().map(Quest::getId).toList();
        if (ids.isEmpty()) {
            return PageResponse.of(page, q -> null);
        }
        Map<UUID, Long> counts = participations.countParticipants(ids, ParticipationStatus.NOT_HOLDING_A_PLACE).stream()
                .collect(Collectors.toMap(ParticipationRepository.QuestCount::getQuestId,
                        ParticipationRepository.QuestCount::getParticipants));
        Map<UUID, ParticipationRef> mine = me.is(Role.STUDENT)
                ? participations.findForStudentAndQuests(me.id(), ids).stream()
                        .collect(Collectors.toMap(p -> p.getQuest().getId(),
                                p -> new ParticipationRef(p.getId(), p.getStatus())))
                : Map.of();
        Function<Quest, QuestView> mapper = q -> QuestView.of(q, curve.rewardFor(q.getTier()),
                counts.getOrDefault(q.getId(), 0L), mine.get(q.getId()));
        return PageResponse.of(page, mapper);
    }
}

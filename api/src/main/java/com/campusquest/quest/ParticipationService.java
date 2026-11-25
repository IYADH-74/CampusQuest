package com.campusquest.quest;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.common.Text;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.PageResponse;
import com.campusquest.progression.LevelCurve;
import com.campusquest.progression.XpBoost;
import com.campusquest.progression.XpBoostRepository;
import com.campusquest.progression.XpLedger;
import com.campusquest.progression.XpReason;
import com.campusquest.progression.XpTransaction;
import com.campusquest.quest.QuestApi.ParticipationView;
import com.campusquest.quest.QuestApi.SubmitRequest;
import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The game loop: join → submit → review → XP. Joining locks student → quest (slot budget, then capacity).
 * Every other mutation locks the participation row first, then the student row via {@link XpLedger}.
 * XP only ever moves through the ledger.
 */
@Service
public class ParticipationService {

    private final ParticipationRepository participations;
    private final QuestRepository quests;
    private final UserRepository users;
    private final XpLedger ledger;
    private final XpBoostRepository boosts;
    private final LevelCurve curve;
    private final AuditService audit;
    private final Clock clock;

    public ParticipationService(ParticipationRepository participations, QuestRepository quests, UserRepository users,
            XpLedger ledger, XpBoostRepository boosts, LevelCurve curve, AuditService audit, Clock clock) {
        this.participations = participations;
        this.quests = quests;
        this.users = users;
        this.ledger = ledger;
        this.boosts = boosts;
        this.curve = curve;
        this.audit = audit;
        this.clock = clock;
    }

    // --- student actions --------------------------------------------------------------------------

    @Transactional
    public ParticipationView join(AuthenticatedUser me, UUID questId) {
        requireStudent(me);
        Instant now = clock.instant();
        // Lock the student first: serialises this student's concurrent joins (slot budget).
        User student = users.findByIdForUpdate(me.id()).orElseThrow(() -> ApiException.notFound("Student"));
        // Then the quest: serialises everyone's joins for this quest (capacity).
        Quest quest = quests.findByIdForUpdate(questId).orElseThrow(() -> ApiException.notFound("Quest"));
        if (!quest.isVisibleToStudents() && quest.getStatus() != QuestStatus.CANCELLED) {
            throw ApiException.notFound("Quest");
        }
        if (quest.isAssignment()) {
            throw ApiException.conflict("NOT_JOINABLE", "Assignments are given to your cohort automatically");
        }
        if (!quest.isAcceptingParticipants(now)) {
            throw ApiException.conflict("QUEST_CLOSED", "This challenge is not accepting participants");
        }
        if (student.getLevel() < quest.getMinLevel()) {
            throw ApiException.conflict("LEVEL_TOO_LOW", "This challenge requires level " + quest.getMinLevel());
        }
        Optional<Participation> existing = participations.findByQuestIdAndStudentId(questId, student.getId());
        if (existing.isPresent() && existing.get().getStatus() != ParticipationStatus.WITHDRAWN) {
            throw ApiException.conflict("ALREADY_JOINED", "You are already taking part in this challenge");
        }
        long inFlight = participations.countChallengesForStudent(student.getId(), ParticipationStatus.IN_FLIGHT);
        int slots = curve.challengeSlotsFor(student.getLevel());
        if (inFlight >= slots) {
            throw ApiException.conflict("NO_FREE_SLOT",
                    "All " + slots + " of your challenge slots are in use. Finish one or level up to unlock more");
        }
        if (quest.getMaxParticipants() != null && participations.countByQuestIdAndStatusNotIn(quest.getId(),
                ParticipationStatus.NOT_HOLDING_A_PLACE) >= quest.getMaxParticipants()) {
            throw ApiException.conflict("QUEST_FULL", "This challenge is full");
        }
        Participation participation;
        if (existing.isPresent()) {
            participation = existing.get();
            participation.rejoin(now);
        } else {
            participation = participations.save(new Participation(quest, student, now));
        }
        return ParticipationView.of(participation);
    }

    @Transactional
    public ParticipationView withdraw(AuthenticatedUser me, UUID participationId) {
        Participation p = ownParticipation(me, participationId);
        p.withdraw();
        return ParticipationView.of(p);
    }

    @Transactional
    public ParticipationView submit(AuthenticatedUser me, UUID participationId, SubmitRequest request) {
        Participation p = ownParticipation(me, participationId);
        p.submit(request.text().strip(), Text.trimToNull(request.url()), clock.instant());
        return ParticipationView.of(p);
    }

    // --- reviewer actions -------------------------------------------------------------------------

    @Transactional
    public ParticipationView approve(AuthenticatedUser me, UUID participationId, String feedback) {
        Participation p = reviewableParticipation(me, participationId);
        Quest quest = p.getQuest();
        XpBoost boost = quest.isChallenge() && p.getSubmittedAt() != null
                ? boosts.findActiveAt(p.getSubmittedAt()).orElse(null)
                : null;
        int reward = curve.boostedReward(quest.getTier(), boost == null ? null : boost.getMultiplier());
        XpTransaction tx = ledger.post(new XpLedger.Entry(p.getStudent().getId(), reward, XpReason.QUEST_REWARD,
                p.getId(), boost == null ? null : boost.getId(), me.id(), null));
        p.approve(users.getReferenceById(me.id()), Text.trimToNull(feedback), tx.getAmount(), clock.instant());
        Map<String, Object> details = new HashMap<>();
        details.put("xp", tx.getAmount());
        details.put("studentId", p.getStudent().getId().toString());
        if (boost != null) {
            details.put("boostId", boost.getId().toString());
        }
        audit.record(AuditAction.SUBMISSION_APPROVED, me.id(), "participation", p.getId(), details);
        return ParticipationView.of(p);
    }

    @Transactional
    public ParticipationView requestChanges(AuthenticatedUser me, UUID participationId, String feedback) {
        Participation p = reviewableParticipation(me, participationId);
        p.requestChanges(users.getReferenceById(me.id()), feedback.strip(), clock.instant());
        return ParticipationView.of(p);
    }

    @Transactional
    public ParticipationView reject(AuthenticatedUser me, UUID participationId, String feedback) {
        Participation p = reviewableParticipation(me, participationId);
        int delta = 0;
        if (p.getQuest().isAssignment() && curve.assignmentPenalty() > 0) {
            delta = ledger.post(new XpLedger.Entry(p.getStudent().getId(), -curve.assignmentPenalty(),
                    XpReason.QUEST_PENALTY, p.getId(), null, me.id(), null)).getAmount();
        }
        p.reject(users.getReferenceById(me.id()), feedback.strip(), delta, clock.instant());
        audit.record(AuditAction.SUBMISSION_REJECTED, me.id(), "participation", p.getId(),
                Map.of("xp", delta, "studentId", p.getStudent().getId().toString()));
        return ParticipationView.of(p);
    }

    // --- reading ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ParticipationView get(AuthenticatedUser me, UUID participationId) {
        Participation p = participations.findDetailed(participationId)
                .orElseThrow(() -> ApiException.notFound("Participation"));
        if (!QuestAccess.canView(p, me)) {
            throw ApiException.notFound("Participation");
        }
        return ParticipationView.of(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<ParticipationView> mine(AuthenticatedUser me, ParticipationStatus status, QuestKind kind,
            int page, int size) {
        requireStudent(me);
        return PageResponse.of(participations.findForStudent(me.id(), status, kind, PageRequest.of(page, size)),
                ParticipationView::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<ParticipationView> forQuest(AuthenticatedUser me, UUID questId, ParticipationStatus status,
            int page, int size) {
        Quest quest = quests.findById(questId).orElseThrow(() -> ApiException.notFound("Quest"));
        if (!QuestAccess.canListParticipants(quest, me)) {
            throw ApiException.notFound("Quest");
        }
        return PageResponse.of(participations.findForQuest(questId, status, PageRequest.of(page, size)),
                ParticipationView::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<ParticipationView> reviewQueue(AuthenticatedUser me, int page, int size) {
        if (!me.is(Role.TEACHER) && !me.is(Role.STAFF)) {
            throw ApiException.forbidden("FORBIDDEN", "Only reviewers have a review queue");
        }
        return PageResponse.of(participations.findReviewQueue(me.id(), me.is(Role.STAFF), PageRequest.of(page, size)),
                ParticipationView::of);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private Participation ownParticipation(AuthenticatedUser me, UUID participationId) {
        requireStudent(me);
        Participation p = participations.findByIdForUpdate(participationId)
                .orElseThrow(() -> ApiException.notFound("Participation"));
        if (!p.belongsTo(me.id())) {
            throw ApiException.notFound("Participation");
        }
        return p;
    }

    private Participation reviewableParticipation(AuthenticatedUser me, UUID participationId) {
        Participation p = participations.findByIdForUpdate(participationId)
                .orElseThrow(() -> ApiException.notFound("Participation"));
        if (!QuestAccess.canReview(p.getQuest(), me)) {
            throw ApiException.notFound("Participation");
        }
        return p;
    }

    private static void requireStudent(AuthenticatedUser me) {
        if (!me.is(Role.STUDENT)) {
            throw ApiException.forbidden("FORBIDDEN", "Only students can do this");
        }
    }
}

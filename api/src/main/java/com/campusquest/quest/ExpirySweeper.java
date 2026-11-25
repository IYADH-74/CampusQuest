package com.campusquest.quest;

import com.campusquest.progression.LevelCurve;
import com.campusquest.progression.XpLedger;
import com.campusquest.progression.XpReason;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expires work that missed its due date and applies the assignment penalty. Each batch claims its rows with
 * {@code FOR UPDATE SKIP LOCKED}, so several instances can run the sweep at once without double-penalising
 * anyone. The ledger's unique (participation, reason) constraint backs that up.
 */
@Component
public class ExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);
    private static final int BATCH_SIZE = 200;

    private final JdbcClient jdbc;
    private final ParticipationRepository participations;
    private final XpLedger ledger;
    private final LevelCurve curve;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ExpirySweeper(JdbcClient jdbc, ParticipationRepository participations, XpLedger ledger, LevelCurve curve,
            TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.participations = participations;
        this.ledger = ledger;
        this.curve = curve;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${campusquest.game.expiry-sweep-interval}", initialDelayString = "PT1M")
    public void scheduledSweep() {
        int expired = sweep();
        if (expired > 0) {
            log.info("Expired {} overdue participations", expired);
        }
    }

    /** Runs batches until nothing overdue is left. Returns the number of participations expired. */
    public int sweep() {
        int total = 0;
        int batch;
        do {
            Integer processed = transactions.execute(status -> expireBatch());
            batch = processed == null ? 0 : processed;
            total += batch;
        } while (batch == BATCH_SIZE);
        return total;
    }

    private int expireBatch() {
        List<UUID> ids = jdbc.sql("""
                select p.id from quest_participations p
                join quests q on q.id = p.quest_id
                where p.status = 'ACTIVE' and q.due_at <= :now and q.status in ('PUBLISHED', 'CLOSED')
                order by q.due_at, p.id
                limit :limit
                for update of p skip locked
                """)
                .param("now", Timestamp.from(clock.instant()))
                .param("limit", BATCH_SIZE)
                .query(UUID.class)
                .list();
        for (UUID id : ids) {
            Participation p = participations.findById(id).orElseThrow();
            int delta = 0;
            if (p.getQuest().isAssignment() && curve.assignmentPenalty() > 0 && p.getStudent().isActive()) {
                delta = ledger.post(new XpLedger.Entry(p.getStudent().getId(), -curve.assignmentPenalty(),
                        XpReason.QUEST_PENALTY, p.getId(), null, null, null)).getAmount();
            }
            p.expire(delta);
        }
        return ids.size();
    }
}

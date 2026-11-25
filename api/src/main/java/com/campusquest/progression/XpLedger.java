package com.campusquest.progression;

import com.campusquest.common.error.ApiException;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that changes XP. Locks the student row, clamps penalties so the balance never goes negative,
 * appends the ledger row and updates the cached balance and level, all in the caller's transaction.
 */
@Service
public class XpLedger {

    public record Entry(UUID studentId, int amount, XpReason reason, UUID participationId, UUID boostId,
            UUID actorId, String note) {}

    private final UserRepository users;
    private final XpTransactionRepository transactions;
    private final LevelCurve curve;
    private final Clock clock;

    public XpLedger(UserRepository users, XpTransactionRepository transactions, LevelCurve curve, Clock clock) {
        this.users = users;
        this.transactions = transactions;
        this.curve = curve;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public XpTransaction post(Entry entry) {
        User student = users.findByIdForUpdate(entry.studentId()).orElseThrow(() -> ApiException.notFound("Student"));
        if (!student.isStudent()) {
            throw ApiException.badRequest("NOT_A_STUDENT", "XP can only be granted to students");
        }
        long balance = student.getXp();
        long effective = entry.amount() < 0 ? Math.max(entry.amount(), -balance) : entry.amount();
        long newBalance = balance + effective;
        student.applyXpBalance(newBalance, curve.levelFor(newBalance));
        return transactions.save(new XpTransaction(student.getId(), (int) effective, entry.reason(),
                entry.participationId(), entry.boostId(), entry.actorId(), entry.note(), newBalance, clock.instant()));
    }
}

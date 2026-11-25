package com.campusquest.progression;

import com.campusquest.common.InsertOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** One immutable row of the XP ledger. A student's balance is the sum of their rows. */
@Entity
@Immutable
@Table(name = "xp_transactions")
public class XpTransaction extends InsertOnlyEntity {

    @Column(nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private XpReason reason;

    private UUID participationId;

    private UUID boostId;

    private UUID actorId;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false)
    private Instant createdAt;

    protected XpTransaction() {}

    XpTransaction(UUID studentId, int amount, XpReason reason, UUID participationId, UUID boostId, UUID actorId,
            String note, long balanceAfter, Instant createdAt) {
        this.studentId = studentId;
        this.amount = amount;
        this.reason = reason;
        this.participationId = participationId;
        this.boostId = boostId;
        this.actorId = actorId;
        this.note = note;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public int getAmount() {
        return amount;
    }

    public XpReason getReason() {
        return reason;
    }

    public UUID getParticipationId() {
        return participationId;
    }

    public UUID getBoostId() {
        return boostId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getNote() {
        return note;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

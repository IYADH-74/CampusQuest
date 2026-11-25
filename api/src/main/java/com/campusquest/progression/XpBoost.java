package com.campusquest.progression;

import com.campusquest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A scheduled XP multiplier window for challenges. Windows never overlap (DB exclusion constraint). */
@Entity
@Table(name = "xp_boosts")
public class XpBoost extends BaseEntity {

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    private Instant cancelledAt;

    protected XpBoost() {}

    public XpBoost(BigDecimal multiplier, Instant startsAt, Instant endsAt, String reason, UUID createdBy) {
        this.multiplier = multiplier;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    /**
     * Stops the boost. A boost that has not started is cancelled outright. A running one is cut short at
     * {@code now}, so work submitted while it was live keeps its multiplier.
     */
    public void stop(Instant now) {
        if (now.isBefore(startsAt)) {
            cancelledAt = now;
        } else {
            endsAt = now;
        }
    }

    public boolean isOver(Instant now) {
        return cancelledAt != null || !now.isBefore(endsAt);
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}

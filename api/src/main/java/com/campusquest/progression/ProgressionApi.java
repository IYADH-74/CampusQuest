package com.campusquest.progression;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProgressionApi {

    private ProgressionApi() {}

    public record CreateBoostRequest(
            @NotNull @DecimalMin("1.05") @DecimalMax("3.00") @Digits(integer = 1, fraction = 2) BigDecimal multiplier,
            Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 200) String reason) {}

    public record BoostView(UUID id, BigDecimal multiplier, Instant startsAt, Instant endsAt, String reason,
            boolean active, Instant cancelledAt) {
        static BoostView of(XpBoost boost, Instant now) {
            boolean active = boost.getCancelledAt() == null && !now.isBefore(boost.getStartsAt())
                    && now.isBefore(boost.getEndsAt());
            return new BoostView(boost.getId(), boost.getMultiplier(), boost.getStartsAt(), boost.getEndsAt(),
                    boost.getReason(), active, boost.getCancelledAt());
        }
    }

    public record XpAdjustmentRequest(
            @NotNull @Min(-100_000) @Max(100_000) Integer amount,
            @NotBlank @Size(min = 5, max = 500) String note) {}

    public record XpTransactionView(UUID id, int amount, XpReason reason, UUID participationId, UUID boostId,
            String note, long balanceAfter, Instant createdAt) {
        public static XpTransactionView of(XpTransaction tx) {
            return new XpTransactionView(tx.getId(), tx.getAmount(), tx.getReason(), tx.getParticipationId(),
                    tx.getBoostId(), tx.getNote(), tx.getBalanceAfter(), tx.getCreatedAt());
        }
    }

    public record LeaderboardEntry(long rank, String displayName, String cohortCode, int level, long xp, boolean me) {}
}

package com.campusquest.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable game rules. See {@code docs/domain.md}. Validated eagerly so a bad config fails at startup.
 */
@Validated
@ConfigurationProperties("campusquest.game")
public record GameProperties(
        @NotNull List<Integer> tierXp,
        @NotNull List<Long> levelThresholds,
        @NotNull List<SlotRule> challengeSlots,
        int assignmentPenaltyXp,
        @NotNull Duration expirySweepInterval) {

    public record SlotRule(int minLevel, int slots) {}

    public GameProperties {
        if (tierXp.size() != 5 || tierXp.stream().anyMatch(xp -> xp <= 0)) {
            throw new IllegalArgumentException("campusquest.game.tier-xp must list 5 positive values");
        }
        if (levelThresholds.isEmpty() || levelThresholds.getFirst() != 0L) {
            throw new IllegalArgumentException("campusquest.game.level-thresholds must start at 0");
        }
        for (int i = 1; i < levelThresholds.size(); i++) {
            if (levelThresholds.get(i) <= levelThresholds.get(i - 1)) {
                throw new IllegalArgumentException("campusquest.game.level-thresholds must be strictly increasing");
            }
        }
        if (challengeSlots.isEmpty() || challengeSlots.getFirst().minLevel() != 0) {
            throw new IllegalArgumentException("campusquest.game.challenge-slots must start at min-level 0");
        }
        for (int i = 1; i < challengeSlots.size(); i++) {
            if (challengeSlots.get(i).minLevel() <= challengeSlots.get(i - 1).minLevel()
                    || challengeSlots.get(i).slots() < challengeSlots.get(i - 1).slots()) {
                throw new IllegalArgumentException("campusquest.game.challenge-slots must be increasing");
            }
        }
        if (assignmentPenaltyXp < 0) {
            throw new IllegalArgumentException("campusquest.game.assignment-penalty-xp must be >= 0");
        }
        tierXp = List.copyOf(tierXp);
        levelThresholds = List.copyOf(levelThresholds);
        challengeSlots = List.copyOf(challengeSlots);
    }
}

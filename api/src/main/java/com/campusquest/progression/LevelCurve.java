package com.campusquest.progression;

import com.campusquest.config.GameProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Pure game math: tier rewards, the level curve and challenge slots. */
@Component
public class LevelCurve {

    private final GameProperties game;

    public LevelCurve(GameProperties game) {
        this.game = game;
    }

    public int maxLevel() {
        return game.levelThresholds().size() - 1;
    }

    public int levelFor(long xp) {
        int level = 0;
        for (int i = 0; i < game.levelThresholds().size(); i++) {
            if (xp >= game.levelThresholds().get(i)) {
                level = i;
            }
        }
        return level;
    }

    public long thresholdFor(int level) {
        return game.levelThresholds().get(Math.clamp(level, 0, maxLevel()));
    }

    /** XP needed for the next level, or {@code null} at the top of the curve. */
    public Long nextThresholdAfter(int level) {
        return level >= maxLevel() ? null : game.levelThresholds().get(level + 1);
    }

    public int challengeSlotsFor(int level) {
        int slots = 0;
        for (GameProperties.SlotRule rule : game.challengeSlots()) {
            if (level >= rule.minLevel()) {
                slots = rule.slots();
            }
        }
        return slots;
    }

    public int rewardFor(int tier) {
        if (tier < 1 || tier > game.tierXp().size()) {
            throw new IllegalArgumentException("Tier out of range: " + tier);
        }
        return game.tierXp().get(tier - 1);
    }

    public int boostedReward(int tier, BigDecimal multiplier) {
        int base = rewardFor(tier);
        if (multiplier == null) {
            return base;
        }
        return BigDecimal.valueOf(base).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public int assignmentPenalty() {
        return game.assignmentPenaltyXp();
    }
}

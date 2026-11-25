package com.campusquest.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusquest.config.GameProperties;
import com.campusquest.config.GameProperties.SlotRule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LevelCurveTest {

    private final LevelCurve curve = new LevelCurve(new GameProperties(
            List.of(2000, 3000, 4000, 5000, 6000),
            List.of(0L, 5000L, 10500L, 16500L, 23000L, 30000L, 37500L, 45500L, 54500L, 64500L, 75500L,
                    87500L, 101000L, 116000L, 132500L, 150500L, 170000L, 191000L, 213500L, 237500L, 263500L),
            List.of(new SlotRule(0, 1), new SlotRule(5, 2), new SlotRule(8, 3), new SlotRule(10, 4),
                    new SlotRule(12, 5), new SlotRule(14, 6), new SlotRule(16, 7), new SlotRule(18, 8),
                    new SlotRule(20, 10)),
            2000,
            Duration.ofMinutes(5)));

    @ParameterizedTest
    @CsvSource({"0,0", "4999,0", "5000,1", "10499,1", "10500,2", "263499,19", "263500,20", "999999999,20"})
    void levelForXp(long xp, int level) {
        assertThat(curve.levelFor(xp)).isEqualTo(level);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "4,1", "5,2", "9,3", "10,4", "17,7", "19,8", "20,10"})
    void slotsForLevel(int level, int slots) {
        assertThat(curve.challengeSlotsFor(level)).isEqualTo(slots);
    }

    @Test
    void nextThresholdStopsAtTheTop() {
        assertThat(curve.nextThresholdAfter(0)).isEqualTo(5000L);
        assertThat(curve.nextThresholdAfter(20)).isNull();
    }

    @Test
    void boostedRewardsRoundHalfUp() {
        assertThat(curve.boostedReward(2, new BigDecimal("1.50"))).isEqualTo(4500);
        assertThat(curve.boostedReward(1, new BigDecimal("1.05"))).isEqualTo(2100);
        assertThat(curve.boostedReward(3, null)).isEqualTo(4000);
    }

    @Test
    void tiersOutsideOneToFiveAreRejected() {
        assertThatThrownBy(() -> curve.rewardFor(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> curve.rewardFor(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> new GameProperties(List.of(1, 2), List.of(0L), List.of(new SlotRule(0, 1)), 0,
                Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameProperties(List.of(1, 2, 3, 4, 5), List.of(0L, 10L, 5L),
                List.of(new SlotRule(0, 1)), 0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
    }
}

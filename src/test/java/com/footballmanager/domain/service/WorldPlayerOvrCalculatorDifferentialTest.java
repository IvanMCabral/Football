package com.footballmanager.domain.service;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.view.WorldPlayerOvrProjection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorldPlayerOvrCalculatorDifferentialTest {

    private static final List<String> SUPPORTED_POSITIONS = List.of(
            "GK", "LB", "CB", "RB", "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM",
            "LW", "RW", "CF", "ST", "DEF", "MID", "WINGER", "ATT", "UNKNOWN");

    @Test
    void exactStCounterexampleIs57OnBothCanonicalAndProjectionPaths() {
        WorldPlayer player = player("ST", 90, 50, 50, 50, 50, 50);
        WorldPlayerOvrProjection projection = projection("ST", 90, 50, 50, 50, 50, 50);

        assertThat(player.calculateOverall()).isEqualTo(57);
        assertThat(projection.calculateOverall()).isEqualTo(57);
    }

    @Test
    void positionMatrixHasExactEqualityForEverySupportedPositionAndScenario() {
        int[][] scenarios = {
                {1, 1, 1, 1, 1, 1},
                {50, 50, 50, 50, 50, 50},
                {99, 50, 50, 50, 50, 50},
                {50, 99, 50, 50, 50, 50},
                {0, 99, 99, 99, 99, 99}
        };

        for (String position : SUPPORTED_POSITIONS) {
            for (int[] stats : scenarios) {
                assertExact(position, stats);
            }
        }
    }

    @Test
    void deterministicRawStatDifferentialHasZeroMismatchesAcrossSeveralHundredCases() {
        int cases = 0;
        int mismatches = 0;
        long state = 0x1234ABCDL;
        for (int i = 0; i < 1000; i++) {
            String position = SUPPORTED_POSITIONS.get(i % SUPPORTED_POSITIONS.size());
            int[] stats = new int[6];
            for (int j = 0; j < stats.length; j++) {
                state = state * 1103515245L + 12345L;
                stats[j] = (int) ((state >>> 16) % 100);
            }
            WorldPlayer player = player(position, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
            WorldPlayerOvrProjection projection = projection(position, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
            cases++;
            if (player.calculateOverall() != projection.calculateOverall()) {
                mismatches++;
            }
        }

        assertThat(cases).isEqualTo(1000);
        assertThat(mismatches).isZero();
    }

    @Test
    void missingBaseStatsKeepWorldPlayerDefaultOf50() {
        WorldPlayer player = player("ST", null, 50, 50, 50, 50, 50);
        WorldPlayerOvrProjection projection = projection("ST", null, 50, 50, 50, 50, 50);

        assertThat(player.calculateOverall()).isEqualTo(50);
        assertThat(projection.calculateOverall()).isEqualTo(50);
    }

    private void assertExact(String position, int[] stats) {
        assertThat(projection(position, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]).calculateOverall())
                .isEqualTo(player(position, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]).calculateOverall());
    }

    private WorldPlayer player(String position, Integer attack, Integer defense, Integer technique,
                               Integer speed, Integer stamina, Integer mentality) {
        return WorldPlayer.fromCanonicalPlayer(UUID.randomUUID(), UUID.randomUUID(), "team", "Player", 22,
                position, attack, defense, technique, speed, stamina, mentality, BigDecimal.ONE);
    }

    private WorldPlayerOvrProjection projection(String position, Integer attack, Integer defense, Integer technique,
                                                Integer speed, Integer stamina, Integer mentality) {
        return new WorldPlayerOvrProjection(UUID.randomUUID(), position, attack, defense, technique,
                speed, stamina, mentality);
    }
}

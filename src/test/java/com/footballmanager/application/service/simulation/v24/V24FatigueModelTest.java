package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24C1: Tests for V24FatigueModel.
 * Validates base drain, action drain, stamina clamping, fatigue factor bands,
 * and fatigue application to quality scores.
 */
class V24FatigueModelTest {

    private final V24FatigueModel calc = new V24FatigueModel();

    // ========== baseDrainPerMinute tests ==========

    @Test
    void baseDrainMatchesTeamStyle() {
        assertEquals(6, calc.baseDrainPerMinute(TeamStyle.ATTACKING));
        assertEquals(5, calc.baseDrainPerMinute(TeamStyle.POSSESSION));
        assertEquals(5, calc.baseDrainPerMinute(TeamStyle.COUNTER));
        assertEquals(4, calc.baseDrainPerMinute(TeamStyle.DEFENSIVE));
        assertEquals(5, calc.baseDrainPerMinute(TeamStyle.BALANCED));
        assertEquals(5, calc.baseDrainPerMinute(null)); // null → BALANCED default
    }

    // ========== actionDrain tests ==========

    @Test
    void actionDrainIsAdditive() {
        assertEquals(0, calc.actionDrain(false, false, false));
        assertEquals(8, calc.actionDrain(true, false, false));   // shot only
        assertEquals(5, calc.actionDrain(false, true, false));    // foul only
        assertEquals(3, calc.actionDrain(false, false, true));    // chance only
        assertEquals(11, calc.actionDrain(true, false, true));    // shot + chance = 8+3
        assertEquals(13, calc.actionDrain(true, true, false));    // shot + foul = 8+5
        assertEquals(8, calc.actionDrain(false, true, true));     // foul + chance = 5+3
        assertEquals(16, calc.actionDrain(true, true, true));     // all
    }

    // ========== stamina clamping tests ==========

    @Test
    void staminaNeverBelowZero() {
        V24PlayerMatchState player = makePlayer("stam-zero", 50, 100);
        // Apply huge drain
        calc.applyDrain(player, 999);
        assertEquals(0, player.currentStamina());
    }

    @Test
    void staminaNeverAboveStartingValue() {
        // Note: applyDrain clamps to [0, 100] via drainStamina which uses Math.max(0, ...)
        // Starting stamina of 80 shouldn't be exceeded
        V24PlayerMatchState player = makePlayer("stam-over", 80, 100);
        calc.applyDrain(player, -50); // negative amount does nothing
        assertEquals(100, player.currentStamina()); // still at starting value
    }

    @Test
    void zeroDrainDoesNothing() {
        V24PlayerMatchState player = makePlayer("zero-drain", 50, 100);
        calc.applyDrain(player, 0);
        assertEquals(100, player.currentStamina()); // unchanged
    }

    @Test
    void negativeDrainDoesNothing() {
        V24PlayerMatchState player = makePlayer("neg-drain", 50, 100);
        calc.applyDrain(player, -10);
        assertEquals(100, player.currentStamina()); // unchanged
    }

    // ========== fatigueFactor tests ==========

    @Test
    void fatigueFactorMatchesBands() {
        // Fresh: >= 80
        assertEquals(1.00, calc.fatigueFactor(makePlayerWithStamina("p100", 100)));
        assertEquals(1.00, calc.fatigueFactor(makePlayerWithStamina("p80", 80)));

        // 60-79
        assertEquals(0.95, calc.fatigueFactor(makePlayerWithStamina("p75", 75)));
        assertEquals(0.95, calc.fatigueFactor(makePlayerWithStamina("p60", 60)));

        // 40-59
        assertEquals(0.85, calc.fatigueFactor(makePlayerWithStamina("p50", 50)));
        assertEquals(0.85, calc.fatigueFactor(makePlayerWithStamina("p40", 40)));

        // 20-39
        assertEquals(0.70, calc.fatigueFactor(makePlayerWithStamina("p30", 30)));
        assertEquals(0.70, calc.fatigueFactor(makePlayerWithStamina("p20", 20)));

        // < 20
        assertEquals(0.50, calc.fatigueFactor(makePlayerWithStamina("p19", 19)));
        assertEquals(0.50, calc.fatigueFactor(makePlayerWithStamina("p0", 0)));
    }

    @Test
    void fatigueFactorRequiresNonNullPlayer() {
        assertThrows(IllegalArgumentException.class, () -> calc.fatigueFactor(null));
    }

    // ========== applyFatigueToQuality tests ==========

    @Test
    void fatigueReducesQuality() {
        // Fresh player: no penalty
        V24PlayerMatchState fresh = makePlayerWithStamina("fresh", 100);
        assertEquals(1.00, calc.applyFatigueToQuality(1.0, fresh), 0.001);

        // Exhausted player: 50% penalty
        V24PlayerMatchState exhausted = makePlayerWithStamina("exhausted", 10);
        double result = calc.applyFatigueToQuality(1.0, exhausted);
        assertEquals(0.50, result, 0.001);

        // Medium stamina: 85%
        V24PlayerMatchState medium = makePlayerWithStamina("medium", 50);
        double medResult = calc.applyFatigueToQuality(1.0, medium);
        assertEquals(0.85, medResult, 0.001);
    }

    @Test
    void applyFatigueToQualityClampsToUnitRange() {
        // Quality > 1.0 with fresh player → clamped to 1.0
        V24PlayerMatchState fresh = makePlayerWithStamina("fresh", 100);
        assertEquals(1.00, calc.applyFatigueToQuality(1.5, fresh), 0.001);

        // Very low quality with exhausted player → closer to 0
        double result = calc.applyFatigueToQuality(0.2, makePlayerWithStamina("low", 10));
        assertTrue(result >= 0.0 && result <= 1.0);
    }

    @Test
    void applyFatigueToQualityHandlesNonFinite() {
        V24PlayerMatchState p = makePlayerWithStamina("finite", 75);
        assertEquals(0.0, calc.applyFatigueToQuality(Double.NaN, p));
        assertEquals(0.0, calc.applyFatigueToQuality(Double.POSITIVE_INFINITY, p));
        assertEquals(0.0, calc.applyFatigueToQuality(Double.NEGATIVE_INFINITY, p));
    }

    @Test
    void applyFatigueToQualityRequiresNonNullPlayer() {
        assertThrows(IllegalArgumentException.class, () -> calc.applyFatigueToQuality(0.5, null));
    }

    // ========== totalDrain convenience method tests ==========

    @Test
    void totalDrainCombinesBaseAndAction() {
        // ATTACKING base 6 + shot 8 = 14
        assertEquals(14, calc.totalDrain(TeamStyle.ATTACKING, true, false, false));
        // BALANCED base 5 + foul 5 = 10
        assertEquals(10, calc.totalDrain(TeamStyle.BALANCED, false, true, false));
        // DEFENSIVE base 4 + chance 3 = 7
        assertEquals(7, calc.totalDrain(TeamStyle.DEFENSIVE, false, false, true));
        // no action: just base
        assertEquals(5, calc.totalDrain(TeamStyle.BALANCED, false, false, false));
        // all actions: base 5 + 16 = 21
        assertEquals(21, calc.totalDrain(TeamStyle.BALANCED, true, true, true));
        // null style defaults to BALANCED base 5
        assertEquals(5, calc.totalDrain(null, false, false, false));
    }

    // ========== Fixture helpers ==========

    private V24PlayerMatchState makePlayer(String id, int ovr, int energy) {
        SessionPlayer sp = SessionPlayer.custom(
                id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
        sp.setEnergy(energy);
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-" + id);
    }

    private V24PlayerMatchState makePlayerWithStamina(String id, int startingStamina) {
        SessionPlayer sp = SessionPlayer.custom(
                id, 25, "MID",
                70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(70000));
        sp.setEnergy(startingStamina);
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-" + id);
    }
}
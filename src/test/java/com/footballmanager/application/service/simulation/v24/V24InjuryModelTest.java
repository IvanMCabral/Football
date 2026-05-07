package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24C3: Tests for V24InjuryModel.
 * Validates base injury probability, stamina modifiers, high-intensity modifier,
 * style modifiers, clamping, and deterministic behavior.
 */
class V24InjuryModelTest {

    private final V24InjuryModel model = new V24InjuryModel();

    // ========== baseInjuryProbability tests ==========

    @Test
    void baseInjuryProbabilityIsLow() {
        double base = model.baseInjuryProbability();
        assertTrue(base <= 0.01, "base should be <= 0.01, got " + base);
        assertTrue(base > 0, "base should be > 0, got " + base);
    }

    // ========== adjustedInjuryProbability tests ==========

    @Test
    void lowStaminaIncreasesInjuryRisk() {
        V24PlayerMatchState fresh = makePlayer("fresh-injury", 70, 90);
        V24PlayerMatchState tired = makePlayer("tired-injury", 70, 25);

        double probFresh = model.adjustedInjuryProbability(fresh, TeamStyle.BALANCED, false);
        double probTired = model.adjustedInjuryProbability(tired, TeamStyle.BALANCED, false);

        assertTrue(probTired > probFresh,
                "Low stamina player should have higher injury probability");
    }

    @Test
    void veryLowStaminaIncreasesMoreThanLowStamina() {
        V24PlayerMatchState exhausted = makePlayer("exhausted-injury", 70, 10);
        V24PlayerMatchState low = makePlayer("low-injury", 70, 30);
        V24PlayerMatchState fresh = makePlayer("fresh-injury", 70, 90);

        double probExhausted = model.adjustedInjuryProbability(exhausted, TeamStyle.BALANCED, false);
        double probLow = model.adjustedInjuryProbability(low, TeamStyle.BALANCED, false);
        double probFresh = model.adjustedInjuryProbability(fresh, TeamStyle.BALANCED, false);

        assertTrue(probExhausted > probLow,
                "Very low stamina (10) should exceed low stamina (30)");
        assertTrue(probLow > probFresh,
                "Low stamina (30) should exceed fresh (90)");
    }

    @Test
    void highIntensityActionIncreasesInjuryRisk() {
        V24PlayerMatchState player = makePlayer("hi-action", 70, 70);

        double probNormal = model.adjustedInjuryProbability(player, TeamStyle.BALANCED, false);
        double probHighIntensity = model.adjustedInjuryProbability(player, TeamStyle.BALANCED, true);

        assertTrue(probHighIntensity > probNormal,
                "High intensity action should increase injury probability");
    }

    @Test
    void attackingAndCounterSlightlyIncreaseRisk() {
        V24PlayerMatchState player = makePlayer("style-injury", 70, 70);

        double probBalanced = model.adjustedInjuryProbability(player, TeamStyle.BALANCED, false);
        double probAttacking = model.adjustedInjuryProbability(player, TeamStyle.ATTACKING, false);
        double probCounter = model.adjustedInjuryProbability(player, TeamStyle.COUNTER, false);

        assertTrue(probAttacking > probBalanced,
                "ATTACKING style should increase injury risk vs BALANCED");
        assertTrue(probCounter > probBalanced,
                "COUNTER style should increase injury risk vs BALANCED");
    }

    @Test
    void injuryProbabilityIsClamped() {
        V24PlayerMatchState player = makePlayer("clamp-injury", 70, 100);

        // Min clamp test: POSSESSION style, no high-intensity, high stamina → should be near min
        double probMin = model.adjustedInjuryProbability(player, TeamStyle.POSSESSION, false);
        assertTrue(probMin >= 0.0005 && probMin <= 0.02,
                "probMin should be clamped to [0.0005, 0.02], got " + probMin);

        // Max clamp test: very low stamina, high-intensity, attacking
        V24PlayerMatchState maxPlayer = makePlayer("max-injury", 70, 15);
        double probMax = model.adjustedInjuryProbability(maxPlayer, TeamStyle.ATTACKING, true);
        assertTrue(probMax >= 0.0005 && probMax <= 0.02,
                "probMax should be clamped to [0.0005, 0.02], got " + probMax);

        // Exhausted with all modifiers should still clamp
        V24PlayerMatchState exhausted = makePlayer("ex-injury", 70, 5);
        double probExhausted = model.adjustedInjuryProbability(exhausted, TeamStyle.ATTACKING, true);
        assertTrue(probExhausted >= 0.0005 && probExhausted <= 0.02,
                "probExhausted should be clamped to [0.0005, 0.02], got " + probExhausted);
    }

    // ========== shouldInjure tests ==========

    @Test
    void shouldInjureIsDeterministicWithSeed() {
        V24PlayerMatchState player = makePlayer("determ-injury", 70, 40);
        boolean result1 = model.shouldInjure(player, TeamStyle.ATTACKING, true, new Random(99));
        boolean result2 = model.shouldInjure(player, TeamStyle.ATTACKING, true, new Random(99));
        assertEquals(result1, result2,
                "shouldInjure should be deterministic with same Random seed");
    }

    @Test
    void injuredPlayerIsOffPitch() {
        V24PlayerMatchState player = makePlayer("off-pitch-injury", 70, 70);
        assertFalse(player.injured());
        assertTrue(player.onPitch());

        player.injure();

        assertTrue(player.injured(), "injured flag should be true after injure()");
        assertFalse(player.onPitch(), "onPitch should be false after injure()");
    }

    // ========== null argument tests ==========

    @Test
    void nullPlayerThrowsOnAdjustedInjuryProbability() {
        assertThrows(IllegalArgumentException.class,
                () -> model.adjustedInjuryProbability(null, TeamStyle.BALANCED, false));
    }

    @Test
    void nullPlayerThrowsOnShouldInjure() {
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldInjure(null, TeamStyle.BALANCED, false, new Random()));
    }

    @Test
    void nullRandomThrowsOnShouldInjure() {
        V24PlayerMatchState p = makePlayer("norandom-inj", "MID", 70, 70);
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldInjure(p, TeamStyle.BALANCED, false, null));
    }

    @Test
    void nullStyleDefaultsToBalanced() {
        V24PlayerMatchState player = makePlayer("null-style", 70, 70);
        double probNull = model.adjustedInjuryProbability(player, null, false);
        double probBalanced = model.adjustedInjuryProbability(player, TeamStyle.BALANCED, false);
        assertEquals(probBalanced, probNull,
                "null style should default to BALANCED");
    }

    // ========== Fixture helpers ==========

    private V24PlayerMatchState makePlayer(String id, int ovr, int stamina) {
        return makePlayer(id, "MID", ovr, stamina);
    }

    private V24PlayerMatchState makePlayer(String id, String position, int ovr, int stamina) {
        SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
        sp.setEnergy(stamina);
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-" + id);
    }
}
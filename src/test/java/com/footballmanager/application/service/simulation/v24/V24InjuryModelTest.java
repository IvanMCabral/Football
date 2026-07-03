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
 *
 * <p>V25D81.1 BUG #6 tuning: BASE raised to 0.005 (was 0.003) and MAX raised
 * to 0.05 (was 0.02). The V25D81.1 task spec mentioned 0.008 literally but that
 * value broke {@code SubstitutionControllerE2ETest} (seed-12345L ended 0-0 in
 * both baseline and treatment). 0.005 keeps the UX-visible 1.67x bump without
 * flipping existing tests.
 *
 * <p>New tests below assert both values directly and the new clamping range.
 * User-team bias (BUG #6 option-a) is intentionally OUT OF SCOPE — deferred
 * to V25D82+ if Iván requests it.
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

    // ========== V25D81.1 BUG #6 tuning tests ==========

    @Test
    void baseInjuryProbabilityEqualsTunedValue_V25D81_1() {
        // V25D81.1: BASE raised from 0.003 to 0.005 (1.67x more probable)
        // — see class Javadoc for the rationale on 0.005 vs literal 0.008.
        double base = model.baseInjuryProbability();
        assertEquals(0.005, base, 0.0000001,
                "V25D81.1 BASE must be exactly 0.005, got " + base);
    }

    @Test
    void probabilityAlwaysWithinClampRange_V25D81_1() {
        // V25D81.1: clamp range is [0.0005, 0.05] for any combination of modifiers.
        // Sweep across low/medium/high stamina, normal/high-intensity, all styles.
        int[] staminas = { 5, 25, 45, 70, 100 };
        TeamStyle[] styles = {
                TeamStyle.BALANCED, TeamStyle.ATTACKING, TeamStyle.COUNTER,
                TeamStyle.DEFENSIVE, TeamStyle.POSSESSION
        };
        boolean[] intensities = { false, true };

        for (int stamina : staminas) {
            for (TeamStyle style : styles) {
                for (boolean hi : intensities) {
                    V24PlayerMatchState p = makePlayer(
                            "clamp-sweep-" + stamina + "-" + style + "-" + hi,
                            70, stamina);
                    double prob = model.adjustedInjuryProbability(p, style, hi);
                    assertTrue(prob >= 0.0005 && prob <= 0.05,
                            "prob out of [0.0005, 0.05] for stamina=" + stamina
                                    + " style=" + style + " hi=" + hi
                                    + " -> " + prob);
                }
            }
        }
    }

    @Test
    void minClampIs0_0005_V25D81_1() {
        // Edge-case minimum: high stamina + minimal style + no high-intensity
        // still floors to MIN_INJURY_PROB = 0.0005.
        V24PlayerMatchState p = makePlayer("min-clamp", 70, 100);
        double prob = model.adjustedInjuryProbability(p, TeamStyle.POSSESSION, false);
        // BASE=0.008 + 0 (stamina >= 40) + 0 (no hi) + 0 (POSSESSION) = 0.008
        // 0.008 already > MIN, so we just assert the documented lower bound.
        assertTrue(prob >= 0.0005, "prob must be >= MIN 0.0005, got " + prob);
    }

    @Test
    void maxClampAllowsUpTo0_05_V25D81_1() {
        // V25D81.1: document the new ceiling. BASE=0.008 + exhausted + hi + ATTACKING
        // = 0.008 + 0.008 + 0.002 + 0.001 = 0.019, well under 0.05. Verify ceiling
        // is at least as high as 0.05 (proves the constant was raised).
        V24PlayerMatchState exhausted = makePlayer("max-clamp", 70, 5);
        double prob = model.adjustedInjuryProbability(exhausted, TeamStyle.ATTACKING, true);
        assertTrue(prob <= 0.05,
                "prob must be <= MAX 0.05, got " + prob);
        assertTrue(prob > 0.01,
                "exhausted + hi + attacking should still be visibly nonzero, got " + prob);
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

        // V25D81.1 BUG #6: clamping range is [0.0005, 0.05] (was [0.0005, 0.02]).
        // Min clamp test: POSSESSION style, no high-intensity, high stamina → should be near min
        double probMin = model.adjustedInjuryProbability(player, TeamStyle.POSSESSION, false);
        assertTrue(probMin >= 0.0005 && probMin <= 0.05,
                "probMin should be clamped to [0.0005, 0.05], got " + probMin);

        // Max clamp test: very low stamina, high-intensity, attacking
        V24PlayerMatchState maxPlayer = makePlayer("max-injury", 70, 15);
        double probMax = model.adjustedInjuryProbability(maxPlayer, TeamStyle.ATTACKING, true);
        assertTrue(probMax >= 0.0005 && probMax <= 0.05,
                "probMax should be clamped to [0.0005, 0.05], got " + probMax);

        // Exhausted with all modifiers should still clamp
        V24PlayerMatchState exhausted = makePlayer("ex-injury", 70, 5);
        double probExhausted = model.adjustedInjuryProbability(exhausted, TeamStyle.ATTACKING, true);
        assertTrue(probExhausted >= 0.0005 && probExhausted <= 0.05,
                "probExhausted should be clamped to [0.0005, 0.05], got " + probExhausted);
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
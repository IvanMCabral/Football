package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24C2: Tests for V24DisciplineModel.
 * Validates foul probability modulation, yellow card probability,
 * second-yellow-red-card enforcement, and deterministic behavior.
 */
class V24DisciplineModelTest {

    private final V24DisciplineModel model = new V24DisciplineModel();
    private final Random fixedRandom = new Random(42);

    // ========== foulProbability tests ==========

    @Test
    void foulProbabilityVariesWithStyle() {
        V24PlayerMatchState defender = makePlayer("def-style", "DEF", 70, 80);

        double foulDef = model.foulProbability(defender, TeamStyle.DEFENSIVE, true);
        double foulBal = model.foulProbability(defender, TeamStyle.BALANCED, true);
        double foulPoss = model.foulProbability(defender, TeamStyle.POSSESSION, true);

        assertTrue(foulDef > foulBal,
                "DEFENSIVE foul probability should exceed BALANCED");
        assertTrue(foulBal > foulPoss,
                "BALANCED foul probability should exceed POSSESSION");
    }

    @Test
    void lowStaminaIncreasesFoulProbability() {
        V24PlayerMatchState fresh = makePlayer("fresh-foul", "DEF", 70, 90);
        V24PlayerMatchState tired = makePlayer("tired-foul", "DEF", 70, 25);

        double foulFresh = model.foulProbability(fresh, TeamStyle.BALANCED, true);
        double foulTired = model.foulProbability(tired, TeamStyle.BALANCED, true);

        assertTrue(foulTired > foulFresh,
                "Low stamina player should have higher foul probability");
    }

    @Test
    void defenderPositionIncreasesFoulProbability() {
        V24PlayerMatchState defender = makePlayer("def-pos", "DEF", 70, 70);
        V24PlayerMatchState midfielder = makePlayer("mid-pos", "MID", 70, 70);

        double foulDef = model.foulProbability(defender, TeamStyle.BALANCED, true);
        double foulMid = model.foulProbability(midfielder, TeamStyle.BALANCED, true);

        assertTrue(foulDef > foulMid,
                "Defender should have higher foul probability than midfielder");
    }

    @Test
    void foulProbabilityIsClampedToValidRange() {
        V24PlayerMatchState p = makePlayer("clamp-foul", "DEF", 70, 100);
        // Style and stamina extremes should still clamp
        double foulMin = model.foulProbability(p, TeamStyle.POSSESSION, false);
        double foulMax = model.foulProbability(p, TeamStyle.DEFENSIVE, true);
        // Exhausted defender in defensive style
        V24PlayerMatchState exhaustedDefender = makePlayer("ex-def", "DEF", 70, 20);
        double foulExtreme = model.foulProbability(exhaustedDefender, TeamStyle.DEFENSIVE, true);

        assertTrue(foulMin >= 0.005 && foulMin <= 0.12,
                "foulMin should be clamped to [0.005, 0.12], got " + foulMin);
        assertTrue(foulMax >= 0.005 && foulMax <= 0.12,
                "foulMax should be clamped to [0.005, 0.12], got " + foulMax);
        assertTrue(foulExtreme >= 0.005 && foulExtreme <= 0.12,
                "foulExtreme should be clamped to [0.005, 0.12], got " + foulExtreme);
    }

    @Test
    void defendingStateIncreasesFoulProbability() {
        V24PlayerMatchState player = makePlayer("def-state", "DEF", 70, 60);
        double foulAttacking = model.foulProbability(player, TeamStyle.BALANCED, false);
        double foulDefending = model.foulProbability(player, TeamStyle.BALANCED, true);
        assertTrue(foulDefending > foulAttacking,
                "Defending state should increase foul probability");
    }

    // ========== yellowCardProbability tests ==========

    @Test
    void yellowProbabilityIncreasesForExistingYellow() {
        V24PlayerMatchState clean = makePlayerWithYellows("clean-card", "MID", 70, 70, 0);
        V24PlayerMatchState warned = makePlayerWithYellows("warned-card", "MID", 70, 70, 1);

        double yellowClean = model.yellowCardProbability(clean, TeamStyle.BALANCED);
        double yellowWarned = model.yellowCardProbability(warned, TeamStyle.BALANCED);

        assertTrue(yellowWarned > yellowClean,
                "Player with existing yellow should have higher yellow probability");
    }

    @Test
    void yellowProbabilityIsClampedToValidRange() {
        V24PlayerMatchState p = makePlayerWithYellows("clamp-yel", "DEF", 70, 80, 0);
        // Max yellow prob (defender, 1 yellow, defensive, low stamina)
        V24PlayerMatchState maxYellow = makePlayerWithYellows("max-yel", "DEF", 70, 20, 1);
        double yellowMin = model.yellowCardProbability(p, TeamStyle.POSSESSION);
        double yellowMax = model.yellowCardProbability(maxYellow, TeamStyle.DEFENSIVE);

        assertTrue(yellowMin >= 0.10 && yellowMin <= 0.80,
                "yellowMin should be clamped to [0.10, 0.80], got " + yellowMin);
        assertTrue(yellowMax >= 0.10 && yellowMax <= 0.80,
                "yellowMax should be clamped to [0.10, 0.80], got " + yellowMax);
    }

    // ========== shouldProduceRedCard tests ==========

    @Test
    void shouldProduceRedCardOnlyWhenSecondYellowReached() {
        V24PlayerMatchState clean = makePlayerWithYellows("clean-red", "MID", 70, 70, 0);
        V24PlayerMatchState warned = makePlayerWithYellows("warned-red", "MID", 70, 70, 1);

        assertFalse(model.shouldProduceRedCard(clean),
                "Clean player should not produce red card");
        // A player with 1 yellow should not yet produce red card
        // (red card only when yellowCards >= 2)
        assertFalse(model.shouldProduceRedCard(warned),
                "Player with 1 yellow should not produce red card yet");
    }

    @Test
    void secondYellowProducesRedCardAndPlayerOffPitch() {
        V24PlayerMatchState player = makePlayerWithYellows("second-red", "MID", 70, 70, 1);

        // Simulate adding second yellow via addYellowCard
        int yellowsBefore = player.yellowCards();
        assertEquals(1, yellowsBefore);

        player.addYellowCard();

        assertTrue(player.yellowCards() <= 2,
                "yellowCards should not exceed 2");
        // V24PlayerMatchState.addYellowCard() gives red card when yellowCards >= 2
        assertTrue(player.redCard(),
                "Second yellow should produce red card");
        assertFalse(player.onPitch(),
                "Red-carded player should be off pitch");
    }

    @Test
    void redCardedPlayerCannotContinueOnPitch() {
        V24PlayerMatchState player = makePlayerWithYellows("cant-continue", "DEF", 70, 60, 1);
        player.addYellowCard(); // second yellow

        assertTrue(player.redCard());
        assertFalse(player.onPitch(),
                "Red-carded player must not be on pitch");
    }

    // ========== shouldCommitFoul / shouldReceiveYellow tests ==========

    @Test
    void shouldCommitFoulUsesRandom() {
        V24PlayerMatchState p = makePlayer("foul-decide", "DEF", 70, 70);
        // With high foul probability (defensive, defending, tired, defender)
        V24PlayerMatchState highFoul = makePlayer("high-foul", "DEF", 70, 25);
        boolean foulHappened = model.shouldCommitFoul(highFoul, TeamStyle.DEFENSIVE, true, fixedRandom);
        // Should be deterministic with same random
        boolean foulAgain = model.shouldCommitFoul(highFoul, TeamStyle.DEFENSIVE, true, new Random(42));
        assertEquals(foulHappened, foulAgain,
                "shouldCommitFoul should be deterministic with same Random");
    }

    @Test
    void shouldReceiveYellowUsesRandom() {
        V24PlayerMatchState p = makePlayer("yellow-decide", "MID", 70, 70);
        boolean yellow1 = model.shouldReceiveYellow(p, TeamStyle.DEFENSIVE, fixedRandom);
        boolean yellow2 = model.shouldReceiveYellow(p, TeamStyle.DEFENSIVE, new Random(42));
        assertEquals(yellow1, yellow2,
                "shouldReceiveYellow should be deterministic with same Random");
    }

    @Test
    void nullPlayerThrowsOnFoulProbability() {
        assertThrows(IllegalArgumentException.class,
                () -> model.foulProbability(null, TeamStyle.BALANCED, true));
    }

    @Test
    void nullPlayerThrowsOnYellowCardProbability() {
        assertThrows(IllegalArgumentException.class,
                () -> model.yellowCardProbability(null, TeamStyle.BALANCED));
    }

    @Test
    void nullPlayerThrowsOnShouldProduceRedCard() {
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldProduceRedCard(null));
    }

    @Test
    void nullPlayerThrowsOnShouldCommitFoul() {
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldCommitFoul(null, TeamStyle.BALANCED, true, new Random()));
    }

    @Test
    void nullPlayerThrowsOnShouldReceiveYellow() {
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldReceiveYellow(null, TeamStyle.BALANCED, new Random()));
    }

    @Test
    void nullRandomThrowsOnShouldCommitFoul() {
        V24PlayerMatchState p = makePlayer("norandom", "MID", 70, 70);
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldCommitFoul(p, TeamStyle.BALANCED, true, null));
    }

    @Test
    void nullRandomThrowsOnShouldReceiveYellow() {
        V24PlayerMatchState p = makePlayer("norand2", "MID", 70, 70);
        assertThrows(IllegalArgumentException.class,
                () -> model.shouldReceiveYellow(p, TeamStyle.BALANCED, null));
    }

    // ========== Fixture helpers ==========

    private V24PlayerMatchState makePlayer(String id, String position, int ovr, int stamina) {
        SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
        sp.setEnergy(stamina);
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-" + id);
    }

    private V24PlayerMatchState makePlayerWithYellows(String id, String position, int ovr, int stamina, int yellows) {
        SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
        sp.setEnergy(stamina);
        V24PlayerMatchState p = V24PlayerMatchState.fromSessionPlayer(sp, "team-" + id);
        // Manually set yellows for testing via reflection-free approach
        // Use addYellowCard to reach target count
        for (int i = 0; i < yellows; i++) {
            p.addYellowCard();
        }
        return p;
    }
}
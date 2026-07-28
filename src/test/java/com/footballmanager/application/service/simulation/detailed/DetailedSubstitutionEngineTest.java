package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  Tests for SubstitutionEngine.
 * Validates priority order, bench selection, max substitutions,
 * duplicate prevention, and red-card handling.
 */
class SubstitutionEngineTest {

    private final int MAX_SUBS = 5;

    // ========== injuredPlayerIsSubstitutedWhenBenchAvailable ==========

    @Test
    void injuredPlayerIsSubstitutedWhenBenchAvailable() {
        TeamMatchState team = makeTeam();

        // Make one starting player injured
        PlayerMatchState injured = team.startingPlayers().get(0);
        injured.injure();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 70);

        assertTrue(event.isPresent(), "Should generate substitution for injured player");
        assertEquals(DetailedMatchEventType.SUBSTITUTION, event.get().type());
        assertEquals(injured.sessionPlayerId(), event.get().playerId());
        assertNotNull(event.get().relatedPlayerId(), "Should have related player (coming on)");
        assertFalse(injured.onPitch(), "Injured player should be off pitch");
    }

    // ========== veryTiredPlayerCanBeSubstituted ==========

    @Test
    void veryTiredPlayerCanBeSubstituted() {
        TeamMatchState team = makeTeam();

        // Make one starting player very tired (stamina < 30)
        PlayerMatchState tired = team.startingPlayers().get(1);
        tired.drainStamina(100); // set to 0 stamina via drain

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isPresent(), "Should generate substitution for very tired player");
        assertEquals(DetailedMatchEventType.SUBSTITUTION, event.get().type());
        assertEquals(tired.sessionPlayerId(), event.get().playerId());
        assertFalse(tired.onPitch(), "Very tired player should be off pitch");
    }

    // ========== tiredYellowCardedPlayerCanBeSubstituted ==========

    @Test
    void tiredYellowCardedPlayerCanBeSubstituted() {
        TeamMatchState team = makeTeam();

        // Make one starting player tired (< 50 stamina) and yellow-carded
        PlayerMatchState tiredYellow = team.startingPlayers().get(2);
        tiredYellow.drainStamina(60); // stamina = 40
        tiredYellow.addYellowCard();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 70);

        assertTrue(event.isPresent(), "Should generate substitution for tired+yellow player");
        assertEquals(DetailedMatchEventType.SUBSTITUTION, event.get().type());
        assertEquals(tiredYellow.sessionPlayerId(), event.get().playerId());
    }

    // ========== injuredHasPriorityOverVeryTired ==========

    @Test
    void injuredHasPriorityOverVeryTired() {
        TeamMatchState team = makeTeam();

        PlayerMatchState injured = team.startingPlayers().get(0);
        injured.injure();

        PlayerMatchState veryTired = team.startingPlayers().get(1);
        veryTired.drainStamina(85); // stamina = 15

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isPresent());
        assertEquals(injured.sessionPlayerId(), event.get().playerId(),
                "Injured should be selected over very tired");
    }

    // ========== veryTiredHasPriorityOverTiredYellow ==========

    @Test
    void veryTiredHasPriorityOverTiredYellow() {
        TeamMatchState team = makeTeam();

        PlayerMatchState veryTired = team.startingPlayers().get(1);
        veryTired.drainStamina(85); // stamina = 15

        PlayerMatchState tiredYellow = team.startingPlayers().get(1);
        tiredYellow.drainStamina(60); // stamina = 40
        tiredYellow.addYellowCard();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isPresent());
        assertEquals(veryTired.sessionPlayerId(), event.get().playerId(),
                "Very tired should be selected over tired+yellow");
    }

    // ========== noMoreThanFiveSubstitutions ==========

    @Test
    void noMoreThanFiveSubstitutions() {
        TeamMatchState team = makeTeam();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);

        // Make all 11 starters eligible for substitution (stamina < 30)
        for (PlayerMatchState p : team.startingPlayers()) {
            p.drainStamina(100); // set stamina to 0 (very tired)
        }

        int count = 0;
        // Try 7 substitutions — only 5 should succeed
        for (int i = 0; i < 7; i++) {
            var event = engine.attemptSubstitution(team, 60 + (i * 8));
            if (event.isPresent()) count++;
        }

        assertEquals(4, count, "Should not exceed available outfield bench substitutions");
        assertEquals(1, engine.substitutionsRemaining(team.teamId()),
                "One substitution remains because backup GK is not an outfield fallback");
    }

    // ========== replacementPrefersSamePosition ==========

    @Test
    void replacementPrefersSamePosition() {
        TeamMatchState team = makeTeam();

        // Find a MID who hasn't been substituted yet
        PlayerMatchState mid = team.startingPlayers().stream()
                .filter(p -> "MID".equals(p.position()))
                .findFirst()
                .orElseThrow();

        // Make MID very tired so they become a substitution candidate
        mid.drainStamina(100); // stamina = 0

        // Find bench MID first (before draining)
        PlayerMatchState benchMid = team.benchPlayers().stream()
                .filter(p -> "MID".equals(p.position()))
                .findFirst()
                .orElse(null);

        if (benchMid == null) return; // skip if no bench MID

        // Drain all OTHER bench players (not benchMid) to make only benchMid eligible
        for (PlayerMatchState b : team.benchPlayers()) {
            if (b != benchMid) {
                b.drainStamina(100); // exhaust
            }
        }

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);
        assertTrue(event.isPresent(), "Should generate substitution");
        assertEquals(benchMid.sessionPlayerId(), event.get().relatedPlayerId(),
                "Should prefer same position MID from bench");
    }

    // ========== noDuplicateSubstitutions ==========

    @Test
    void noDuplicateSubstitutions() {
        TeamMatchState team = makeTeam();

        PlayerMatchState tired = team.startingPlayers().get(1);
        tired.drainStamina(90);

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);

        var event1 = engine.attemptSubstitution(team, 65);
        assertTrue(event1.isPresent());

        // Second attempt on same team — no more subs or bench empty
        // The first sub used the bench player, so bench may be empty
        // But at least the same starting player shouldn't be subbed twice
        int subsUsedAfterFirst = engine.substitutionsUsed(team.teamId());
        assertEquals(1, subsUsedAfterFirst);
    }

    // ========== redCardedPlayerCannotBeSubstituted ==========

    @Test
    void redCardedPlayerCannotBeSubstituted() {
        TeamMatchState team = makeTeam();

        // Red card a player
        PlayerMatchState redCarded = team.startingPlayers().get(0);
        redCarded.giveRedCard(); // redCard=true, onPitch=false

        // Also have a tired player
        PlayerMatchState tired = team.startingPlayers().get(1);
        tired.drainStamina(90);

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isPresent());
        assertEquals(tired.sessionPlayerId(), event.get().playerId(),
                "Red-carded player should not be substituted; tired player should be");
        assertEquals(1, engine.substitutionsUsed(team.teamId()),
                "Tired player substitution should count as 1 sub");
    }

    // ========== noEligibleBenchMeansNoSubstitution ==========

    @Test
    void noEligibleBenchMeansNoSubstitution() {
        TeamMatchState team = makeTeam();

        // Injured candidate exists
        PlayerMatchState injured = team.startingPlayers().get(0);
        injured.injure();

        // But all bench are injured/redCarded/offPitch already
        for (PlayerMatchState b : team.benchPlayers()) {
            b.injure();
        }

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isEmpty(), "No substitution when no eligible bench");
    }

    // ========== substitutionEventHasRealPlayerIdsAndNames ==========

    @Test
    void substitutionEventHasRealPlayerIdsAndNames() {
        TeamMatchState team = makeTeam();

        PlayerMatchState tired = team.startingPlayers().get(1);
        tired.drainStamina(90);

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isPresent());
        DetailedMatchEvent e = event.get();
        assertNotNull(e.playerId());
        assertNotNull(e.playerName());
        assertNotNull(e.relatedPlayerId());
        assertNotNull(e.relatedPlayerName());
        assertFalse(e.playerId().isBlank());
        assertFalse(e.playerName().isBlank());
        assertFalse(e.relatedPlayerId().isBlank());
        assertFalse(e.relatedPlayerName().isBlank());
        assertEquals(0.0, e.xg());
        assertEquals(DetailedMatchEventType.SUBSTITUTION, e.type());
    }

    // ========== null argument tests ==========

    @Test
    void nullTeamThrowsOnAttemptSubstitution() {
        SubstitutionEngine engine = new SubstitutionEngine();
        assertThrows(IllegalArgumentException.class,
                () -> engine.attemptSubstitution(null, 65));
    }

    @Test
    void nullTeamIdThrowsOnSubstitutionsUsed() {
        SubstitutionEngine engine = new SubstitutionEngine();
        assertThrows(IllegalArgumentException.class,
                () -> engine.substitutionsUsed(null));
    }

    @Test
    void invalidMaxSubstitutionsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubstitutionEngine(-1));
    }

    @Test
    void tiredGoalkeeperIsNotAutoSubstituted() {
        TeamMatchState team = makeTeam();

        PlayerMatchState goalkeeper = team.startingPlayers().get(0);
        goalkeeper.drainStamina(100);

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isEmpty(), "GK must not be auto-subbed for tiredness");
        assertTrue(goalkeeper.onPitch(), "GK should remain on pitch when only tired");
    }

    @Test
    void injuredGoalkeeperCanBeAutoSubstituted() {
        TeamMatchState team = makeTeam();

        PlayerMatchState goalkeeper = team.startingPlayers().get(0);
        goalkeeper.injure();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        assertTrue(event.isPresent(), "Injured GK can be replaced by backup GK");
        assertEquals(goalkeeper.sessionPlayerId(), event.get().playerId());
        assertFalse(goalkeeper.onPitch(), "Injured GK should leave the pitch");
    }

    @Test
    void nonInjuryAutoSubstitutionsAreSpacedByAtLeastEightMinutes() {
        TeamMatchState team = makeTeam();

        PlayerMatchState firstTired = team.startingPlayers().get(1);
        PlayerMatchState secondTired = team.startingPlayers().get(2);
        firstTired.drainStamina(100);
        secondTired.drainStamina(100);

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);

        var first = engine.attemptSubstitution(team, 60);
        var tooSoon = engine.attemptSubstitution(team, 61);
        var later = engine.attemptSubstitution(team, 68);

        assertTrue(first.isPresent(), "First tired outfield player should be replaced");
        assertTrue(tooSoon.isEmpty(), "Second non-injury auto-sub should not happen immediately");
        assertTrue(later.isPresent(), "Second non-injury auto-sub can happen after spacing window");
    }

    // ========== Fixture helpers ==========

    private TeamMatchState makeTeam() {
        // Start with stamina=100 for all players (not tired)
        List<PlayerMatchState> starters = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> makePlayer("starter-" + i, positionForIndex(i), 70, 100))
                .toList();

        List<PlayerMatchState> bench = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> makePlayer("bench-" + i, benchPositionForIndex(i), 70, 100))
                .toList();

        // Bench players start off pitch
        for (PlayerMatchState b : bench) {
            b.substituteOff();
        }

        return new TeamMatchState("team-1", "Test Team", "4-3-3",
                TeamStyle.BALANCED, starters, bench);
    }

    private PlayerMatchState makePlayer(String id, String position, int ovr, int stamina) {
        SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
        sp.setEnergy(stamina);
        return PlayerMatchState.fromSessionPlayer(sp, "team-1");
    }

    private String positionForIndex(int i) {
        return switch (i) {
            case 0 -> "GK";
            case 1, 2, 3, 4 -> "DEF";
            case 5, 6, 7 -> "MID";
            case 8, 9 -> "WINGER";
            default -> "ATT";
        };
    }

    private String benchPositionForIndex(int i) {
        return switch (i) {
            case 0 -> "GK";
            case 1 -> "DEF";
            case 2 -> "MID";
            case 3 -> "WINGER";
            default -> "ATT";
        };
    }

    @Test
    void manualSubstitute_allowsOutOfRoleDtDecision() {
        TeamMatchState team = makeTeam();
        PlayerMatchState attackerOff = team.startingPlayers().stream()
            .filter(p -> "ATT".equals(p.position()))
            .findFirst()
            .orElseThrow();
        PlayerMatchState defenderOn = team.benchPlayers().stream()
            .filter(p -> "DEF".equals(p.position()))
            .findFirst()
            .orElseThrow();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        DetailedMatchEvent event = engine.manualSubstitute(
            team,
            attackerOff.sessionPlayerId(),
            defenderOn.sessionPlayerId(),
            70);

        assertEquals(DetailedMatchEventType.SUBSTITUTION, event.type());
        assertEquals(attackerOff.sessionPlayerId(), event.playerId());
        assertEquals(defenderOn.sessionPlayerId(), event.relatedPlayerId());
        assertFalse(attackerOff.onPitch(), "Player leaving should be off pitch");
        assertTrue(defenderOn.onPitch(), "Out-of-role DT choice should enter the pitch");
        assertEquals(1, engine.substitutionsUsed(team.teamId()));
    }

    @Test
    void manualSubstitute_validPair_returnsEventAndMutatesState() {
        TeamMatchState team = makeTeam();
        PlayerMatchState subOff = team.startingPlayers().get(0);
        PlayerMatchState subOn = team.benchPlayers().get(0); // GK, same position as subOff[0]

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        DetailedMatchEvent event = engine.manualSubstitute(
            team, subOff.sessionPlayerId(), subOn.sessionPlayerId(), 70);

        assertNotNull(event);
        assertEquals(DetailedMatchEventType.SUBSTITUTION, event.type());
        assertEquals(subOff.sessionPlayerId(), event.playerId());
        assertEquals(subOn.sessionPlayerId(), event.relatedPlayerId());
        assertEquals(70, event.minute());
        // Player state mutated
        assertFalse(subOff.onPitch(), "Substituted-off player should be off pitch");
        assertTrue(subOn.onPitch(), "Substituted-on player should be on pitch");
        // Counter incremented
        assertEquals(1, engine.substitutionsUsed("team-1"));
        assertEquals(MAX_SUBS - 1, engine.substitutionsRemaining("team-1"));
    }

    @Test
    void manualSubstitute_offPlayerInjured_canBeSubstitutedOff() {
        TeamMatchState team = makeTeam();
        PlayerMatchState injured = team.startingPlayers().get(0);
        injured.injure();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        DetailedMatchEvent event = engine.manualSubstitute(team, injured.sessionPlayerId(),
                team.benchPlayers().get(0).sessionPlayerId(), 70);

        assertEquals(DetailedMatchEventType.SUBSTITUTION, event.type());
        assertFalse(injured.onPitch(), "Injured off-player should be allowed to leave the pitch");
    }

    @Test
    void manualSubstitute_onPlayerInjured_throwsIllegalState() {
        TeamMatchState team = makeTeam();
        PlayerMatchState subOff = team.startingPlayers().get(0);
        PlayerMatchState injuredBench = team.benchPlayers().get(0);
        injuredBench.injure();

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> engine.manualSubstitute(team, subOff.sessionPlayerId(),
                injuredBench.sessionPlayerId(), 70));
        assertTrue(ex.getMessage().contains("cannot come on"));
    }

    @Test
    void manualSubstitute_offPlayerAlreadySubstitutedOff_throwsIllegalState() {
        TeamMatchState team = makeTeam();
        PlayerMatchState subOff = team.startingPlayers().get(0);
        PlayerMatchState subOn = team.benchPlayers().get(0);

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        // First substitution succeeds
        engine.manualSubstitute(team, subOff.sessionPlayerId(), subOn.sessionPlayerId(), 70);
        // Second attempt with the same off player should fail
        assertThrows(IllegalStateException.class,
            () -> engine.manualSubstitute(team, subOff.sessionPlayerId(),
                team.benchPlayers().get(1).sessionPlayerId(), 75));
    }

    @Test
    void manualSubstitute_maxSubsReached_throwsIllegalState() {
        TeamMatchState team = makeTeam();
        SubstitutionEngine engine = new SubstitutionEngine(2); // tighter limit

        // First substitution
        engine.manualSubstitute(team,
            team.startingPlayers().get(0).sessionPlayerId(),
            team.benchPlayers().get(0).sessionPlayerId(), 30);

        // Second substitution (different players)
        engine.manualSubstitute(team,
            team.startingPlayers().get(1).sessionPlayerId(),
            team.benchPlayers().get(1).sessionPlayerId(), 50);

        // Third attempt should fail (max 2 reached)
        assertThrows(IllegalStateException.class,
            () -> engine.manualSubstitute(team,
                team.startingPlayers().get(2).sessionPlayerId(),
                team.benchPlayers().get(2).sessionPlayerId(), 70));
    }

    @Test
    void manualSubstitute_nullOrBlankIds_throwsIllegalArgument() {
        TeamMatchState team = makeTeam();
        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);

        assertThrows(IllegalArgumentException.class,
            () -> engine.manualSubstitute(team, null, "bench-0", 70));
        assertThrows(IllegalArgumentException.class,
            () -> engine.manualSubstitute(team, "", "bench-0", 70));
        assertThrows(IllegalArgumentException.class,
            () -> engine.manualSubstitute(team, "starter-0", null, 70));
        assertThrows(IllegalArgumentException.class,
            () -> engine.manualSubstitute(team, "starter-0", "  ", 70));
        assertThrows(IllegalArgumentException.class,
            () -> engine.manualSubstitute(team, "starter-0", "bench-0", 0));
        assertThrows(IllegalArgumentException.class,
            () -> engine.manualSubstitute(team, "starter-0", "bench-0", 131));
    }

    @Test
    void isSubstitutedOffPublic_returnsCorrectValue() {
        TeamMatchState team = makeTeam();
        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        // SessionPlayer.custom generates a random sessionPlayerId, so we
        // capture the actual id of starter-0 rather than hardcoding the name.
        String starterId = team.startingPlayers().get(0).sessionPlayerId();

        // Initially false
        assertFalse(engine.isSubstitutedOffPublic(starterId));
        // After substitution, true
        engine.manualSubstitute(team,
            starterId,
            team.benchPlayers().get(0).sessionPlayerId(), 70);
        assertTrue(engine.isSubstitutedOffPublic(starterId));
    }

    /**
     * formation has been tactically changed mid-match to a layout that
     * does NOT have a slot for the OFF player's position, the engine
     * must skip the substitution (not produce a SUBSTITUTION event that
     * would reference a non-existent slot).
     *
     * <p>Scenario: start with a 4-3-3 team (has GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT).
     * Tactically change formation to 3-5-2 (GK + 3 DEF + 5 MID + 2 ATT — no WINGER slot).
     * A WINGER becomes "very tired" → attemptSubstitution should NOT produce
     * a substitution event because 3-5-2 has no WINGER slot to fill.
     */
    @Test
    void attemptSubstitution_respectsCurrentFormation_skipsWhenNoSlot() {
        TeamMatchState team = makeTeam(); // starts at 4-3-3

        // Mid-match tactical change: switch to 3-5-2 (no WINGER slot).
        team.setFormation("3-5-2");

        // Make a WINGER very tired so they become a substitution candidate.
        PlayerMatchState winger = team.startingPlayers().stream()
            .filter(p -> "WINGER".equals(p.position()))
            .findFirst()
            .orElseThrow();
        winger.drainStamina(100); // stamina = 0 → very tired

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        // Bug colateral fix: with no WINGER slot in 3-5-2, the engine must skip.
        assertTrue(event.isEmpty(),
            "Substitution must be skipped when current formation has no slot for the OFF position");
        // Sub counter is NOT incremented (no actual sub happened).
        assertEquals(0, engine.substitutionsUsed(team.teamId()),
            "Substitution counter must not increment on a no-op skip");
    }

    /**
     * to a formation that DOES have a slot for the OFF player's position,
     * the substitution proceeds normally.
     */
    @Test
    void attemptSubstitution_respectsCurrentFormation_proceedsWhenSlotExists() {
        TeamMatchState team = makeTeam(); // starts at 4-3-3

        // Mid-match tactical change: stay in 4-3-3 (or any DEF-supporting layout).
        // Make a DEF very tired — 4-3-3 has 4 DEF slots, so the substitution should proceed.
        team.setFormation("4-3-3");

        PlayerMatchState def = team.startingPlayers().stream()
            .filter(p -> "DEF".equals(p.position()))
            .findFirst()
            .orElseThrow();
        def.drainStamina(100); // very tired

        SubstitutionEngine engine = new SubstitutionEngine(MAX_SUBS);
        var event = engine.attemptSubstitution(team, 65);

        // Sanity: 4-3-3 has DEF slots, so the substitution should fire.
        assertTrue(event.isPresent(),
            "Substitution must proceed when current formation has a slot for the OFF position");
    }
}

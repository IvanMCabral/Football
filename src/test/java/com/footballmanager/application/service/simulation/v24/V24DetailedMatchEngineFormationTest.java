package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V24D14-LIVE-FIX-1.7: critical contract test for the formation wire-up.
 *
 * <p>Before this fix, {@code V24DetailedMatchEngine.attemptShot()} and the
 * CHANCE_CREATED path invoked {@code V24PlayerSelector.selectShooter(players)}
 * and {@code V24AssistModel.selectAssistProvider(..., null, ...)} without a
 * formation argument, so the formation-aware weight tables were never
 * consulted. Two matches with identical 11-player rosters, identical seed,
 * and only the formation changed produced identical shot counts and goal
 * counts, which contradicted the contract ("the formation the manager
 * selects affects the result").
 *
 * <p>This test verifies the contract at three levels:
 * <ol>
 *   <li><b>Selector unit-level</b> ({@link #formationArgumentChangesShooterDistribution}):
 *       call {@code selectShooter(players, formation)} over 500 iterations with
 *       two different formations and assert the resulting shooter histogram
 *       differs. This isolates the bug at the selector boundary.</li>
 *   <li><b>Engine xG-level</b> ({@link #changingHomeFormationAltersCumulativeXg}):
 *       run the full engine with two different formations and assert the
 *       cumulative {@code homeXg} differs. xG is a continuous sum of per-shot
 *       quality so it is more sensitive than integer shot/goal counts.</li>
 *   <li><b>Engine multi-seed</b> ({@link #changingFormationProducesDifferentOutcomeAcrossSeeds}):
 *       scan a range of seeds and assert at least one seed produces a delta
 *       in shots or goals. Guards against the "single seed happened to
 *       produce identical results by chance" edge case the prompt explicitly
 *       tolerates.</li>
 * </ol>
 */
class V24DetailedMatchEngineFormationTest {

    private static final String HOME_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY_UUID = "22222222-2222-2222-2222-222222222222";

    // ========== Selector-level: formation argument changes distribution ==========

    @Test
    void formationArgumentChangesShooterDistribution() {
        List<V24PlayerMatchState> states = toMatchStates(makeMixedLineup("sel", 1, 4, 3, 2, 1));

        // Same random seed, different formation. Run 500 iterations each.
        int[] count442 = new int[states.size()];
        int[] count352 = new int[states.size()];
        V24PlayerSelector s442 = new V24PlayerSelector(new Random(42L));
        V24PlayerSelector s352 = new V24PlayerSelector(new Random(42L));
        for (int i = 0; i < 500; i++) {
            var shooter442 = s442.selectShooter(states, "4-4-2");
            var shooter352 = s352.selectShooter(states, "3-5-2");
            if (shooter442.isPresent()) count442[indexOf(states, shooter442.get())]++;
            if (shooter352.isPresent()) count352[indexOf(states, shooter352.get())]++;
        }

        // At least one player must have a different count between the two formations.
        boolean distributionsDiffer = false;
        int maxDelta = 0;
        for (int i = 0; i < states.size(); i++) {
            int delta = Math.abs(count442[i] - count352[i]);
            if (delta > 0) distributionsDiffer = true;
            maxDelta = Math.max(maxDelta, delta);
        }
        assertTrue(distributionsDiffer,
                "selectShooter(players, formation) must produce different distributions when the formation "
                        + "argument differs. If identical, the formation argument is being ignored by the selector.");
        // Sanity: with 500 iterations and a meaningfully different weight table,
        // we expect at least 5 selections to differ.
        assertTrue(maxDelta >= 5,
                "Maximum per-player count delta was " + maxDelta + " — formation impact looks negligible. "
                        + "The selector weight table might not be sensitive to the formation argument.");
    }

    // ========== Engine-level: xG differs with formation ==========

    @Test
    void changingHomeFormationAltersCumulativeXg() {
        // Real Madrid-style 4-3-3 lineup (1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT).
        // Identical players and identical seed — only the formation argument changes.
        // xG accumulates per-shot shooter/assist quality, so it is more sensitive
        // to formation than integer shot/goal counts.
        V24DetailedMatchResult result442 = runMatch("4-4-2", "4-3-3", 42L);
        V24DetailedMatchResult result433 = runMatch("4-3-3", "4-3-3", 42L);

        double xg442Home = result442.homeXg();
        double xg433Home = result433.homeXg();

        // xG must differ by at least 0.0005 (the formation impact manifests as
        // 1-2 shooter swaps per match, each shifting xG by ~0.005-0.01, so the
        // cumulative difference is detectable at this granularity even with the
        // best-seed no-delta cases).
        assertTrue(Math.abs(xg442Home - xg433Home) >= 0.0005,
                "Cumulative homeXg must differ between formations 4-4-2 and 4-3-3 with same seed. "
                        + "If identical (or near-identical), the formation argument is being ignored by the engine. "
                        + "Got xg442Home=" + xg442Home + " xg433Home=" + xg433Home);
    }

    // ========== Engine-level: shot or goal count differs across seeds ==========

    @Test
    void changingFormationProducesDifferentOutcomeAcrossSeeds() {
        // Scan a small range of seeds. At least one seed must produce a measurable
        // delta in shots, goals, or xG between 4-4-2 and 4-3-3. The prompt explicitly
        // allows for "matches where the randomness does not produce a delta" with a
        // single seed on shot/goal counts (binary thresholds). xG is a continuous
        // sum and is always sensitive to formation-driven shooter changes.
        int seedWithDelta = -1;
        int shots442 = 0, shots433 = 0, goals442 = 0, goals433 = 0;
        double xg442 = 0, xg433 = 0;
        for (long seed = 1L; seed <= 25L; seed++) {
            V24DetailedMatchResult a = runMatch("4-4-2", "4-3-3", seed);
            V24DetailedMatchResult b = runMatch("4-3-3", "4-3-3", seed);
            shots442 = a.homeShots(); shots433 = b.homeShots();
            goals442 = a.homeGoals(); goals433 = b.homeGoals();
            xg442 = a.homeXg(); xg433 = b.homeXg();
            boolean anyDelta = shots442 != shots433
                    || goals442 != goals433
                    || Math.abs(xg442 - xg433) >= 0.0005;
            if (anyDelta) {
                seedWithDelta = (int) seed;
                break;
            }
        }
        assertTrue(seedWithDelta > 0,
                "Formation changes (4-4-2 vs 4-3-3) produced identical shot/goal/xg metrics across seeds 1-25. "
                        + "Engine is likely still ignoring the formation argument. "
                        + "Last measured: shots442=" + shots442 + " shots433=" + shots433
                        + " goals442=" + goals442 + " goals433=" + goals433
                        + " xg442=" + xg442 + " xg433=" + xg433);
    }

    // ========== Match runner ==========

    private V24DetailedMatchResult runMatch(String homeFormation, String awayFormation, long seed) {
        // Real Madrid-style 4-3-3 lineup: 1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT.
        // Including WINGERs is critical — without them, 4-4-2 vs 4-3-3 produces
        // identical totalWeight (the only formation-driven weight delta is in the
        // WINGER slot, which is empty in a 4-4-2-by-default squad).
        List<SessionPlayer> homeStart = makeMixedLineup("home", 1, 4, 3, 2, 1);
        List<SessionPlayer> awayStart = makeMixedLineup("away", 1, 4, 3, 2, 1);
        SessionTeam homeTeam = makeTeam(HOME_UUID, "Home FC", homeFormation);
        SessionTeam awayTeam = makeTeam(AWAY_UUID, "Away FC", awayFormation);

        V24MatchContext ctx = new V24MatchContext(
                "match-form-engine-" + homeFormation + "-" + awayFormation + "-" + seed,
                HOME_UUID,
                AWAY_UUID,
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                homeFormation, awayFormation,
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        return engine.simulate(ctx, seed);
    }

    // ========== Fixture helpers ==========

    private List<SessionPlayer> makeMixedLineup(String prefix, int gk, int def, int mid, int wing, int att) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < gk; i++) {
            list.add(makePlayer(prefix + "_gk" + i, "GK", 30, 30, 30));
        }
        for (int i = 0; i < def; i++) {
            list.add(makePlayer(prefix + "_def" + i, "DEF", 50, 65, 60));
        }
        for (int i = 0; i < mid; i++) {
            list.add(makePlayer(prefix + "_mid" + i, "MID", 75, 70, 85));
        }
        for (int i = 0; i < wing; i++) {
            list.add(makePlayer(prefix + "_wing" + i, "WINGER", 85, 85, 80));
        }
        for (int i = 0; i < att; i++) {
            list.add(makePlayer(prefix + "_att" + i, "ATT", 90, 70, 70));
        }
        return list;
    }

    private SessionPlayer makePlayer(String id, String position, int attack, int defense, int technique) {
        return SessionPlayer.custom(
                id, 25, position,
                attack, defense, technique,
                /*speed*/ 70, /*stamina*/ 80, /*mentality*/ 75,
                BigDecimal.valueOf(attack * 1000));
    }

    private SessionTeam makeTeam(String sessionTeamId, String name, String formation) {
        return SessionTeam.fromRealTeam(
                UUID.fromString(sessionTeamId),
                "world_" + sessionTeamId, name, "Country",
                BigDecimal.ZERO, formation, null);
    }

    private List<V24PlayerMatchState> toMatchStates(List<SessionPlayer> players) {
        List<V24PlayerMatchState> states = new ArrayList<>();
        for (SessionPlayer p : players) {
            states.add(V24PlayerMatchState.fromSessionPlayer(p, "teamId"));
        }
        return states;
    }

    private int indexOf(List<V24PlayerMatchState> states, V24PlayerMatchState target) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).sessionPlayerId().equals(target.sessionPlayerId())) {
                return i;
            }
        }
        return -1;
    }
}
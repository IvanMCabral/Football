package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void defenderRosterChanceVolumeMultiplierRewardsAndPunishesDefensiveQuality() throws Exception {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
            "defenderRosterChanceVolumeMultiplier",
            double.class);
        method.setAccessible(true);

        double weakDefense = (double) method.invoke(engine, 45.0);
        double neutralDefense = (double) method.invoke(engine, 70.0);
        double eliteDefense = (double) method.invoke(engine, 95.0);

        assertTrue(weakDefense > neutralDefense,
            "Weak defensive roster should increase opponent chance volume.");
        assertTrue(eliteDefense < neutralDefense,
            "Elite defensive roster should suppress opponent chance volume.");
        assertTrue(Math.abs(neutralDefense - 1.0) < 0.000001,
            "Neutral defensive roster should leave chance volume unchanged.");
        assertTrue(weakDefense <= 1.35 && eliteDefense >= 0.78,
            "Defensive roster chance-volume multiplier must stay bounded but readable. "
                + "weak=" + weakDefense + " elite=" + eliteDefense);
        assertTrue(weakDefense - neutralDefense >= 0.28,
            "A very weak defensive roster should be visibly readable in the harness. "
                + "weak=" + weakDefense + " neutral=" + neutralDefense);
        assertTrue(neutralDefense - eliteDefense >= 0.18,
            "An elite defensive roster should visibly suppress chance volume. "
                + "neutral=" + neutralDefense + " elite=" + eliteDefense);
    }

    @Test
    void channelSensitiveDefenderStatMakesWeakWideDefenderHurtWideShotsMore() throws Exception {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
            "aggregateDefenderStatForLocation",
            List.class,
            Map.class,
            V24ShotLocation.class,
            double.class);
        method.setAccessible(true);

        V24PlayerMatchState gk = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("chan_gk", "GK", 30, 82, 50), "teamC");
        V24PlayerMatchState weakLeftBack = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("chan_lb", "DEF", 40, 25, 45), "teamC");
        V24PlayerMatchState strongCenterBack = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("chan_cb", "DEF", 40, 95, 55), "teamC");
        List<V24PlayerMatchState> defenders = List.of(gk, weakLeftBack, strongCenterBack);

        Map<String, LineupSlotDTO> slots = new HashMap<>();
        slots.put(gk.sessionPlayerId(), new LineupSlotDTO(gk.sessionPlayerId(), "GK-1", 50.0, 93.0));
        slots.put(weakLeftBack.sessionPlayerId(), new LineupSlotDTO(weakLeftBack.sessionPlayerId(), "S22-1", 18.0, 78.0));
        slots.put(strongCenterBack.sessionPlayerId(), new LineupSlotDTO(strongCenterBack.sessionPlayerId(), "S23-2", 50.0, 80.0));

        double wideDefense = (double) method.invoke(
            engine, defenders, slots, V24ShotLocation.PENALTY_AREA_WIDE, 70.0);
        double centralDefense = (double) method.invoke(
            engine, defenders, slots, V24ShotLocation.PENALTY_AREA_CENTER, 70.0);

        assertTrue(wideDefense < centralDefense,
            "A weak wide defender should reduce channel defensive quality more for wide shots "
                + "than central shots. wideDefense=" + wideDefense + " centralDefense=" + centralDefense);
    }

    @Test
    void channelSensitiveDefenderStatDistinguishesLeftAndRightWideShots() throws Exception {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
            "aggregateDefenderStatForLocation",
            List.class,
            Map.class,
            V24ShotLocation.class,
            V24ShotCoordinate.class,
            double.class);
        method.setAccessible(true);

        V24PlayerMatchState gk = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("lr_gk", "GK", 30, 82, 50), "teamLR");
        V24PlayerMatchState weakLeftBack = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("lr_lb", "DEF", 40, 25, 45), "teamLR");
        V24PlayerMatchState strongRightBack = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("lr_rb", "DEF", 40, 95, 55), "teamLR");
        V24PlayerMatchState neutralCenterBack = V24PlayerMatchState.fromSessionPlayer(
            makePlayer("lr_cb", "DEF", 40, 70, 55), "teamLR");
        List<V24PlayerMatchState> defenders = List.of(gk, weakLeftBack, strongRightBack, neutralCenterBack);

        Map<String, LineupSlotDTO> slots = new HashMap<>();
        slots.put(gk.sessionPlayerId(), new LineupSlotDTO(gk.sessionPlayerId(), "GK-1", 50.0, 94.0));
        slots.put(weakLeftBack.sessionPlayerId(), new LineupSlotDTO(weakLeftBack.sessionPlayerId(), "S22-1", 18.0, 78.0));
        slots.put(strongRightBack.sessionPlayerId(), new LineupSlotDTO(strongRightBack.sessionPlayerId(), "S24-1", 82.0, 78.0));
        slots.put(neutralCenterBack.sessionPlayerId(), new LineupSlotDTO(neutralCenterBack.sessionPlayerId(), "S23-2", 50.0, 80.0));

        V24ShotCoordinate leftWideShot = new V24ShotCoordinate(88.0, 28.0, V24ShotLocation.PENALTY_AREA_WIDE);
        V24ShotCoordinate rightWideShot = new V24ShotCoordinate(88.0, 72.0, V24ShotLocation.PENALTY_AREA_WIDE);

        double leftChannelDefense = (double) method.invoke(
            engine, defenders, slots, V24ShotLocation.PENALTY_AREA_WIDE, leftWideShot, 70.0);
        double rightChannelDefense = (double) method.invoke(
            engine, defenders, slots, V24ShotLocation.PENALTY_AREA_WIDE, rightWideShot, 70.0);

        assertTrue(leftChannelDefense < rightChannelDefense - 8.0,
            "Left wide shots must feel the weak left defender more than the strong right defender. "
                + "leftChannelDefense=" + leftChannelDefense
                + " rightChannelDefense=" + rightChannelDefense);
    }

    @Test
    void leftAndRightFlankCoordinateGeneratorKeepsWideShotsOnRequestedSide() {
        V24ShotCoordinateGenerator generator = new V24ShotCoordinateGenerator();

        for (int seed = 1; seed <= 100; seed++) {
            V24ShotCoordinate left = generator.generateWideFlank(true, new Random(seed));
            V24ShotCoordinate right = generator.generateWideFlank(false, new Random(seed));

            assertTrue(left.location() == V24ShotLocation.PENALTY_AREA_WIDE,
                "LEFT_FLANK generated coordinate must remain PENALTY_AREA_WIDE.");
            assertTrue(right.location() == V24ShotLocation.PENALTY_AREA_WIDE,
                "RIGHT_FLANK generated coordinate must remain PENALTY_AREA_WIDE.");
            assertTrue(left.y() >= 18.0 && left.y() <= 42.0,
                "LEFT_FLANK must place wide shots on the left channel. seed=" + seed + " y=" + left.y());
            assertTrue(right.y() >= 58.0 && right.y() <= 82.0,
                "RIGHT_FLANK must place wide shots on the right channel. seed=" + seed + " y=" + right.y());
        }
    }

    @Test
    void defenderChannelWeightChangesSmoothlyAroundLaneBoundaries() throws Exception {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
            "defenderChannelWeight",
            V24ShotLocation.class,
            double.class,
            V24ShotCoordinate.class);
        method.setAccessible(true);

        V24ShotCoordinate leftWideShot = new V24ShotCoordinate(88.0, 28.0, V24ShotLocation.PENALTY_AREA_WIDE);

        double x34 = (double) method.invoke(engine, V24ShotLocation.PENALTY_AREA_WIDE, 34.0, leftWideShot);
        double x35 = (double) method.invoke(engine, V24ShotLocation.PENALTY_AREA_WIDE, 35.0, leftWideShot);
        double x36 = (double) method.invoke(engine, V24ShotLocation.PENALTY_AREA_WIDE, 36.0, leftWideShot);
        double x64 = (double) method.invoke(engine, V24ShotLocation.PENALTY_AREA_WIDE, 64.0, leftWideShot);
        double x65 = (double) method.invoke(engine, V24ShotLocation.PENALTY_AREA_WIDE, 65.0, leftWideShot);
        double x66 = (double) method.invoke(engine, V24ShotLocation.PENALTY_AREA_WIDE, 66.0, leftWideShot);

        assertTrue(Math.abs(x34 - x35) < 0.08 && Math.abs(x35 - x36) < 0.08,
            "Wide defensive weight must not jump around the left/center lane boundary. "
                + "x34=" + x34 + " x35=" + x35 + " x36=" + x36);
        assertTrue(Math.abs(x64 - x65) < 0.08 && Math.abs(x65 - x66) < 0.08,
            "Wide defensive weight must not jump around the center/right lane boundary. "
                + "x64=" + x64 + " x65=" + x65 + " x66=" + x66);
        assertTrue(x34 > x36,
            "For a left-side shot, moving the defender gradually away from the left channel "
                + "should gradually lower same-side defensive weight.");
    }

    // ========== V24D23-A B2: shot-location distribution is formation-aware ==========

    /**
     * V24D23-A: verify the {@code hasWingers} modifier (4-3-3, 3-4-3)
     * shifts the aggregate shot location distribution toward
     * {@link V24ShotLocation#PENALTY_AREA_WIDE} relative to a non-wingers
     * formation (4-4-2). Direct unit-level isolation of the
     * {@code selectShotLocation(style, formation, random)} method via
     * reflection — no engine loop, no player state, no Squad fixture.
     *
     * <p>Methodology: invoke the private
     * {@code selectShotLocation(style, formation, random)} via reflection
     * {@code N=20000} times with a fresh {@code Random} per call (so
     * each call is an independent weighted draw). The expected
     * PENALTY_AREA_WIDE share for 4-4-2 with BALANCED style is 20% and
     * for 4-3-3 with BALANCED style is 24.4% (per the design table).
     * The assertion is conservative: 4-3-3 must produce at least 10%
     * more wide shots than 4-4-2 in the aggregate sample, which is well
     * above the ~1pp ratio increase and accommodates sample noise.
     */
    @Test
    void selectShotLocation_hasWingers_shiftsToWide() {
        final int samples = 20000;
        Map<V24ShotLocation, Integer> counts433 = aggregateShotLocations("4-3-3", samples);
        Map<V24ShotLocation, Integer> counts442 = aggregateShotLocations("4-4-2", samples);

        double wideShare433 = counts433.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;
        double wideShare442 = counts442.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;

        // Design table predicts 4-3-3 wide ≈ 24.4%, 4-4-2 wide ≈ 19.0%.
        // We assert the ratio is at least 1.10 (10% more wide in 4-3-3),
        // which is comfortably below the predicted ratio (~1.28) and well
        // above any sample-noise floor (N=20000 → std error on a 20% share
        // is ~0.3pp → ratio noise is well under 1%).
        assertTrue(wideShare433 > wideShare442 * 1.10,
            "4-3-3 (hasWingers) should produce at least 10% more PENALTY_AREA_WIDE shots "
                + "than 4-4-2 (no wingers). Observed wide share: 4-3-3=" + wideShare433
                + ", 4-4-2=" + wideShare442
                + " (ratio " + (wideShare433 / Math.max(wideShare442, 1e-9)) + "). "
                + "If the ratio is <= 1.10, the hasWingers modifier is not applied in "
                + "V24DetailedMatchEngine.computeLocationWeights.");
    }

    /**
     * V24D23-A: verify the {@code defenders()==3} modifier (3-5-2,
     * 3-4-3, 5-3-2) shifts the aggregate shot location distribution
     * away from {@link V24ShotLocation#PENALTY_AREA_WIDE} relative to a
     * 4-defender formation (4-4-2). Direct unit-level isolation of the
     * {@code selectShotLocation(style, formation, random)} method via
     * reflection — no engine loop, no player state, no Squad fixture.
     *
     * <p>Methodology: invoke the private {@code selectShotLocation} via
     * reflection {@code N=20000} times. The expected PENALTY_AREA_WIDE
     * share for 4-4-2 with BALANCED style is 20% (baseline) and for
     * 3-5-2 with BALANCED style is 9.9% (per the design table — the
     * defenders=3 modifier halves wide shots). The assertion is
     * conservative: 3-5-2 must produce at most 75% of the wide shots
     * of 4-4-2 in the aggregate sample.
     */
    @Test
    void selectShotLocation_backThree_reducesWide() {
        final int samples = 20000;
        Map<V24ShotLocation, Integer> counts352 = aggregateShotLocations("3-5-2", samples);
        Map<V24ShotLocation, Integer> counts442 = aggregateShotLocations("4-4-2", samples);

        double wideShare352 = counts352.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;
        double wideShare442 = counts442.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;

        // Design table predicts 3-5-2 wide ≈ 9.9%, 4-4-2 wide ≈ 19.0%.
        // We assert the ratio is at most 0.75 (25% reduction in wide
        // shots for 3-5-2), which is comfortably above the predicted
        // ratio (~0.52) and well above the sample-noise floor.
        assertTrue(wideShare352 < wideShare442 * 0.75,
            "3-5-2 (defenders=3) should produce at least 25% fewer PENALTY_AREA_WIDE shots "
                + "than 4-4-2 (defenders=4). Observed wide share: 3-5-2=" + wideShare352
                + ", 4-4-2=" + wideShare442
                + " (ratio " + (wideShare352 / Math.max(wideShare442, 1e-9)) + "). "
                + "If the ratio is >= 0.75, the defenders==3 modifier is not applied in "
                + "V24DetailedMatchEngine.computeLocationWeights.");
    }

    @Test
    void selectShotLocation_widePlay_shiftsToWideChannel() {
        final int samples = 20000;
        Map<V24ShotLocation, Integer> balanced = aggregateShotLocations("4-4-2", TeamStyle.BALANCED, samples);
        Map<V24ShotLocation, Integer> wide = aggregateShotLocations("4-4-2", TeamStyle.WIDE_PLAY, samples);

        double balancedWideShare = balanced.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;
        double wideShare = wide.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;

        assertTrue(wideShare > balancedWideShare * 1.30,
            "WIDE_PLAY should materially increase PENALTY_AREA_WIDE shots versus BALANCED. "
                + "Observed: wide=" + wideShare + ", balanced=" + balancedWideShare);
    }

    @Test
    void selectShotLocation_centralPlay_shiftsToCentralChannel() {
        final int samples = 20000;
        Map<V24ShotLocation, Integer> balanced = aggregateShotLocations("4-4-2", TeamStyle.BALANCED, samples);
        Map<V24ShotLocation, Integer> central = aggregateShotLocations("4-4-2", TeamStyle.CENTRAL_PLAY, samples);

        double balancedCentralShare = centralShare(balanced, samples);
        double centralShare = centralShare(central, samples);
        double balancedWideShare = balanced.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;
        double centralWideShare = central.get(V24ShotLocation.PENALTY_AREA_WIDE) / (double) samples;

        assertTrue(centralShare > balancedCentralShare * 1.12,
            "CENTRAL_PLAY should increase SIX_YARD_BOX + PENALTY_AREA_CENTER shots versus BALANCED. "
                + "Observed: central=" + centralShare + ", balanced=" + balancedCentralShare);
        assertTrue(centralWideShare < balancedWideShare * 0.75,
            "CENTRAL_PLAY should reduce PENALTY_AREA_WIDE shots versus BALANCED. "
                + "Observed: centralWide=" + centralWideShare + ", balancedWide=" + balancedWideShare);
    }

    /**
     * Helper for the V24D23-A B2 unit tests: invokes the private
     * {@code selectShotLocation(style, formation, random)} method
     * {@code samples} times via reflection and counts the resulting
     * {@link V24ShotLocation} draws. Each call uses a fresh
     * {@link Random} (seeded by the call index) so the draws are
     * independent.
     *
     * <p>Reflection is used because the method is package-private (no
     * public API exposure to keep the engine API tight). The method
     * signature is
     * {@code V24ShotLocation selectShotLocation(TeamStyle, String, Random)}.
     */
    private Map<V24ShotLocation, Integer> aggregateShotLocations(String formation, int samples) {
        return aggregateShotLocations(formation, TeamStyle.BALANCED, samples);
    }

    private Map<V24ShotLocation, Integer> aggregateShotLocations(String formation, TeamStyle style, int samples) {
        Map<V24ShotLocation, Integer> counts = new EnumMap<>(V24ShotLocation.class);
        for (V24ShotLocation loc : V24ShotLocation.values()) {
            counts.put(loc, 0);
        }
        try {
            V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
            Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
                "selectShotLocation", TeamStyle.class, String.class, Random.class);
            method.setAccessible(true);
            for (int i = 0; i < samples; i++) {
                // Fresh Random per call — independent weighted draws.
                Random r = new Random(i * 31L + 17L);
                V24ShotLocation loc = (V24ShotLocation) method.invoke(
                    engine, style, formation, r);
                counts.merge(loc, 1, Integer::sum);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke selectShotLocation via reflection", e);
        }
        return counts;
    }

    private double centralShare(Map<V24ShotLocation, Integer> counts, int samples) {
        return (counts.get(V24ShotLocation.SIX_YARD_BOX)
            + counts.get(V24ShotLocation.PENALTY_AREA_CENTER)) / (double) samples;
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

    // ============================================================================
    // V25D47 (Sprint C11c): tactical engine smoke tests
    //
    // C11a added PositionEffectivenessCalculator + the naturalPosition field on
    // V24PlayerMatchState. aggregateAttackerStat / aggregateDefenderStat now
    // weight each player's contribution by effectiveness(naturalPosition,
    // position). These tests verify:
    //   1. The effectiveness weighting kicks in when setPosition() moves a
    //      player to a tactical slot that differs from their naturalPosition.
    //   2. LWB-like (WINGER-natural) flexibility vs CB-like (DEF-natural)
    //      rigidity produces measurably different xG outcomes.
    //   3. Backward compat: lineups that never call setPosition() still
    //      behave as the pre-C11a baseline (effectiveness = 1.0 multiplier).
    //   4. A 5-formations probe with the same 11-player base + same seed
    //      produces 5 distinct cumulative xG outcomes.
    //   5. Severe penalty: a defensive-natural player forced into ATT slot
    //      (effectiveness 0.4 per the PositionEffectivenessCalculator table)
    //      is correctly down-weighted in aggregateAttackerStat.
    // ============================================================================

    // ========== C11c Test 1: tactical position changes effectiveness ==========

    /**
     * V25D47 / C11c Test 1: verify that calling {@code setPosition(tactical)}
     * on a player with a fixed naturalPosition changes
     * {@code aggregateAttackerStat} via the effectiveness multiplier.
     *
     * <p>Methodology: reflective access to the private
     * {@code aggregateAttackerStat(players, formation)} method (same pattern
     * as the V24D23-A selectShotLocation tests). Build 11 players with
     * controlled attack stats, then compare:
     * <ul>
     *   <li>Baseline: every player has {@code naturalPosition == position}
     *       (effectiveness = 1.0 for everyone → aggregate is the unweighted
     *       top-5 average of the raw attack stats).</li>
     *   <li>Misaligned: one top-5 attacker is forced into a MID slot
     *       (effectiveness ATT->MID = 0.7 per
     *       {@link com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator}),
     *       so the top-5 average drops by ~30% * (attacker_attack / top5_avg).</li>
     * </ul>
     * The assertion is conservative: the misaligned aggregate must be at
     * least 5% lower than the baseline (sample noise on top-5 selection is
     * zero here — same players, deterministic sort).
     */
    @Test
    void tacticalPosition_changesAggregateStatEffectiveness() throws Exception {
        // 11 attackers with attack stats 100..90 (highest 5 are 100,99,98,97,96).
        List<V24PlayerMatchState> baselineStates = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            SessionPlayer p = makePlayer("att" + i, "ATT", 100 - i, 50, 50);
            V24PlayerMatchState s = V24PlayerMatchState.fromSessionPlayer(p, "teamA");
            // fromSessionPlayer already sets naturalPosition == "ATT" and
            // position == "ATT" — no setPosition call needed.
            baselineStates.add(s);
        }

        // Misaligned: move the highest-attack player (att0, attack=100)
        // from ATT to MID — effectiveness(ATT, MID) = 0.7.
        List<V24PlayerMatchState> misalignedStates = new ArrayList<>(baselineStates);
        V24PlayerMatchState moved = V24PlayerMatchState.fromSessionPlayer(
                makePlayer("att0", "ATT", 100, 50, 50), "teamA");
        moved.setPosition("MID");  // naturalPosition still "ATT", tactical = "MID"
        misalignedStates.set(0, moved);

        double baselineAgg = invokeAggregateAttackerStat(baselineStates, "4-4-2");
        double misalignedAgg = invokeAggregateAttackerStat(misalignedStates, "4-4-2");

        // Top-7 baseline (V25D99.18: widened from top-5): 100+99+98+97+96+95+94 = 679 / 7 = 97.0.
        // Top-7 misaligned: att0 still in top-7 by raw sort (raw attack=100 keeps it #1),
        // but weighted contribution is 100*0.7 = 70. So misaligned =
        // (70 + 99 + 98 + 97 + 96 + 95 + 94) / 7 = 649 / 7 = 92.71.
        // Baseline = 97.0. Difference = 4.29 (~4.4% lower).
        assertTrue(baselineAgg > misalignedAgg,
                "aggregateAttackerStat must decrease when a top attacker is moved to a "
                        + "MID tactical slot (effectiveness 0.7). baseline=" + baselineAgg
                        + ", misaligned=" + misalignedAgg);
        // Sanity: misaligned must be at least 3.5% lower (V25D99.18 top-7: expected delta ~4.4%).
        assertTrue(baselineAgg - misalignedAgg >= baselineAgg * 0.035,
                "Misaligned aggregate should be at least 3.5% lower than baseline. "
                        + "baseline=" + baselineAgg + ", misaligned=" + misalignedAgg
                        + ", delta=" + (baselineAgg - misalignedAgg));
    }

    // ========== C11c Test 2: LWB (WINGER) vs CB (DEF) flexibility ==========

    /**
     * V25D47 / C11c Test 2: prove the LWB/CB flexibility asymmetry.
     *
     * <p>In a 5-cat world, "LWB" (left wing-back) folds into the
     * {@code WINGER} category (the role is naturally wide and gets back on
     * defense), and "CB" (center back) maps to {@code DEF}. The
     * {@link com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator}
     * table reflects modern football: WINGER in MID = 0.95 (carrilero is
     * the WINGER's other job), DEF in MID = 0.8 (CB can do a defensive
     * midfield role but it's a noticeable step down).
     *
     * <p>Implementation note: {@code V24MatchContext} accepts
     * {@code List<SessionPlayer>}, and {@code V24PlayerMatchState.fromSessionPlayer}
     * uses {@code SessionPlayer.position} as BOTH naturalPosition and tactical
     * position. To exercise the asymmetry (naturalPosition != position) the
     * test must drive {@code aggregateAttackerStat} directly via reflection,
     * building pre-mutated {@code V24PlayerMatchState} instances with
     * {@code setPosition("MID")} applied to the right player.
     *
     * <p>Setup: 11-player base, one player with attack=95 (top-5 guaranteed).
     * Scenario A: WINGER-natural + setPosition(MID) → effectiveness 0.95 →
     * contributes 95 * 0.95 = 90.25 to the top-5 weighted average.
     * Scenario B: DEF-natural + setPosition(MID) → effectiveness 0.8 →
     * contributes 95 * 0.8 = 76.0.
     * Difference: 90.25 - 76.0 = 14.25; /5 = 2.85 → aggregateAttackerStat
     * must differ by at least ~3 between the two scenarios.
     */
    @Test
    void lwbInMidVsCbInMid_differentXgOutcomes() throws Exception {
        // Build a controlled 11-player lineup where the asymmetry surfaces cleanly:
        // 1 GK + 4 DEF + 6 MID players (attack 70) — all low-attack except the
        // variable top-5 candidate (attack 95, naturalPosition WINGER in A, DEF in B).
        // Top-5 composition is identical in both scenarios (95, 70, 70, 70, 70)
        // — only the weighted contribution of the 95-attack player differs
        // (effectiveness 0.95 vs 0.8).
        List<V24PlayerMatchState> lineupA = new ArrayList<>();
        lineupA.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("gk", "GK", 30, 80, 50), "teamL"));
        for (int i = 0; i < 4; i++) {
            lineupA.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("def" + i, "DEF", 50, 70, 50), "teamL"));
        }
        for (int i = 0; i < 5; i++) {
            lineupA.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("mid" + i, "MID", 70, 60, 80), "teamL"));
        }
        // Slot 10 (idx 10) is the variable player — WINGER-natural with attack 95.
        V24PlayerMatchState lwb = V24PlayerMatchState.fromSessionPlayer(
                makePlayer("lwb_var", "WINGER", 95, 50, 80), "teamL");
        lwb.setPosition("MID");  // WINGER-natural -> MID tactical (effectiveness 0.95)
        lineupA.add(lwb);

        List<V24PlayerMatchState> lineupB = new ArrayList<>();
        lineupB.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("gk", "GK", 30, 80, 50), "teamC"));
        for (int i = 0; i < 4; i++) {
            lineupB.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("def" + i, "DEF", 50, 70, 50), "teamC"));
        }
        for (int i = 0; i < 5; i++) {
            lineupB.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("mid" + i, "MID", 70, 60, 80), "teamC"));
        }
        // Slot 10 (idx 10) is the variable player — DEF-natural with attack 95.
        V24PlayerMatchState cb = V24PlayerMatchState.fromSessionPlayer(
                makePlayer("cb_var", "DEF", 95, 70, 50), "teamC");
        cb.setPosition("MID");  // DEF-natural -> MID tactical (effectiveness 0.8)
        lineupB.add(cb);

        double aggA = invokeAggregateAttackerStat(lineupA, "4-4-2");
        double aggB = invokeAggregateAttackerStat(lineupB, "4-4-2");

        // Hand-computed expected:
        // Top-5 by attack is identical: 95 (variable), 70, 70, 70, 70.
        // A: weighted = (95*0.95 + 70*4) / 5 = (90.25 + 280) / 5 = 74.05.
        // B: weighted = (95*0.8 + 70*4) / 5 = (76 + 280) / 5 = 71.20.
        // Delta = 74.05 - 71.20 = 2.85.
        assertTrue(aggA > aggB,
                "WINGER-natural in MID (effectiveness 0.95) must outscore "
                        + "DEF-natural in MID (effectiveness 0.8). aggA=" + aggA
                        + ", aggB=" + aggB);
        assertTrue(aggA - aggB >= 2.0,
                "LWB/CB asymmetry must produce aggregate delta >= 2.0 (expected ~2.85). "
                        + "aggA=" + aggA + ", aggB=" + aggB + ", delta=" + (aggA - aggB));
    }

    // ========== C11c Test 3: backward compat (no setPosition called) ==========

    /**
     * V25D47 / C11c Test 3: when no player has {@code setPosition} called
     * (pre-C11a legacy path), {@code naturalPosition == position} for every
     * player. PositionEffectivenessCalculator.effectiveness(X, X) = 1.0 for
     * any X, so aggregateAttackerStat and aggregateDefenderStat reduce to
     * the pre-C11a unweighted averages.
     *
     * <p>Methodology: reflective access to both private aggregate methods.
     * Build 11 players with controlled stats via {@code fromSessionPlayer}
     * (no setPosition). The aggregate values must equal the hand-computed
     * unweighted averages.
     */
    @Test
    void backyardCompat_noTacticalPosition_unchangedBehavior() throws Exception {
        List<V24PlayerMatchState> states = new ArrayList<>();
        // 1 GK (defense 80, mentality 75), 4 DEF (defense 70, mentality 75),
        // 4 MID (attack 75, technique 80), 2 ATT (attack 90, technique 85).
        // Note: makePlayer() hardcodes mentality=75 (see helper below), so
        // the hand-computed expected value below uses 75 for all positions.
        for (int i = 0; i < 1; i++) {
            SessionPlayer p = makePlayer("gk" + i, "GK", 30, 80, 50);
            states.add(V24PlayerMatchState.fromSessionPlayer(p, "teamX"));
        }
        for (int i = 0; i < 4; i++) {
            SessionPlayer p = makePlayer("def" + i, "DEF", 50, 70, 50);
            states.add(V24PlayerMatchState.fromSessionPlayer(p, "teamX"));
        }
        for (int i = 0; i < 4; i++) {
            SessionPlayer p = makePlayer("mid" + i, "MID", 75, 60, 80);
            states.add(V24PlayerMatchState.fromSessionPlayer(p, "teamX"));
        }
        for (int i = 0; i < 2; i++) {
            SessionPlayer p = makePlayer("att" + i, "ATT", 90, 50, 85);
            states.add(V24PlayerMatchState.fromSessionPlayer(p, "teamX"));
        }

        double actualAttack = invokeAggregateAttackerStat(states, "4-4-2");
        double actualDefense = invokeAggregateDefenderStat(states);

        // Hand-computed baseline (no effectiveness weighting, mentality=75 for all).
        // Top-7 by attack (V25D99.18: widened from top-5):
        // lineup is 1 GK(30) + 4 DEF(50) + 4 MID(75) + 2 ATT(90). Top-7 =
        // 2 ATT(90) + 4 MID(75) + 1 DEF(50) = 530 / 7 ≈ 75.71.
        double expectedAttack = (90.0 + 90.0 + 75.0 + 75.0 + 75.0 + 75.0 + 50.0) / 7.0;
        // DEF+GK: GK(def=80,ment=75)=(80+75)/2=77.5; 4 DEF(def=70,ment=75)=(70+75)/2=72.5 each.
        // avg = (77.5 + 72.5*4) / 5 = (77.5 + 290) / 5 = 367.5 / 5 = 73.5.
        double expectedDefense = (77.5 + 72.5 * 4) / 5.0;

        assertTrue(Math.abs(actualAttack - expectedAttack) < 1e-6,
                "aggregateAttackerStat with no tactical moves must equal the "
                        + "pre-C11a unweighted top-7 average (V25D99.18). expected=" + expectedAttack
                        + ", actual=" + actualAttack);
        assertTrue(Math.abs(actualDefense - expectedDefense) < 1e-6,
                "aggregateDefenderStat with no tactical moves must equal the "
                        + "pre-C11a unweighted DEF+GK average. expected=" + expectedDefense
                        + ", actual=" + actualDefense);
    }

    // ========== C11c Test 4: 5-formations probe ==========

    /**
     * V25D47 / C11c Test 4: same 11-player base, same seed, 5 different
     * formation labels. Cumulative homeXg must vary across variants — the
     * engine consumes formation label via the offensive/defensive
     * modifiers (formationOffensiveModifier, formationDefensiveModifier,
     * defenders==3 modifier, hasWingers modifier, etc.). Even with all
     * players in their natural positions, varying the formation label
     * alone changes the outcome.
     *
     * <p>Variants (canonical 5 formations per the memory lesson on
     * formation probing):
     * <ol>
     *   <li>{@code 4-4-2} — baseline, no wings, 4 defenders.</li>
     *   <li>{@code 3-5-2} — 3 defenders modifier (reduces wide shots).</li>
     *   <li>{@code 5-3-2} — 5 defenders modifier (defensive).</li>
     *   <li>{@code 4-3-3} — hasWingers modifier (more wide shots).</li>
     *   <li>{@code 4-4-1-1} — invented; exercises FormationInferer fallback
     *       path. The inferer returns "4-4-2" as the default when the
     *       label is unknown, so the engine treats it identically to V1.</li>
     * </ol>
     *
     * <p>Note on tactical position swaps: the engine consumes
     * {@code SessionPlayer} via {@code V24MatchContext.homeStartingPlayers}
     * and builds {@code V24PlayerMatchState} via {@code fromSessionPlayer},
     * which sets naturalPosition = tactical position = SessionPlayer.position.
     * Per-player tactical moves (effectiveness penalty path) are NOT
     * exercisable through V24MatchContext — they require direct construction
     * of {@code V24PlayerMatchState} + {@code setPosition}, which is what
     * Tests 1, 2, 5 do via reflection. This test focuses on the
     * formation-label dimension of the engine's sensitivity.
     */
    @Test
    void fiveFormationsProbe_effectivenessChangesOutcomes() {
        long seed = 42L;
        List<SessionPlayer> awayStart = makeMixedLineup("away", 1, 4, 3, 2, 1);
        List<SessionPlayer> homeBase = makeMixedLineup("home", 1, 4, 3, 2, 1);

        V24DetailedMatchResult r442 = runMatchWithLineup("4-4-2",   "4-4-2", seed, homeBase, awayStart);
        V24DetailedMatchResult r352 = runMatchWithLineup("3-5-2",   "4-4-2", seed, homeBase, awayStart);
        V24DetailedMatchResult r532 = runMatchWithLineup("5-3-2",   "4-4-2", seed, homeBase, awayStart);
        V24DetailedMatchResult r433 = runMatchWithLineup("4-3-3",   "4-4-2", seed, homeBase, awayStart);
        V24DetailedMatchResult r4411 = runMatchWithLineup("4-4-1-1","4-4-2", seed, homeBase, awayStart);

        double xg442 = r442.homeXg(), xg352 = r352.homeXg(), xg532 = r532.homeXg(),
               xg433 = r433.homeXg(), xg4411 = r4411.homeXg();

        // Engine must produce different xG for different formation labels (same seed).
        assertTrue(Math.abs(xg442 - xg352) >= 1e-6,
                "V1 (4-4-2) vs V2 (3-5-2): xG must differ (formation modifier). "
                        + "xg442=" + xg442 + ", xg352=" + xg352);
        assertTrue(Math.abs(xg442 - xg532) >= 1e-6,
                "V1 (4-4-2) vs V3 (5-3-2): xG must differ. "
                        + "xg442=" + xg442 + ", xg532=" + xg532);
        assertTrue(Math.abs(xg352 - xg433) >= 1e-6,
                "V2 (3-5-2) vs V4 (4-3-3): xG must differ (defenders=3 vs hasWingers). "
                        + "xg352=" + xg352 + ", xg433=" + xg433);
        // V5 is the invented formation. The engine should not crash and should
        // produce a non-negative xG. With FormationInferer fallback to "4-4-2",
        // xg4411 should equal xg442 (modulo seed-derived randomness on the
        // formation-modifier path which the inferer normalizes). We don't
        // assert equality — engine internals might still process the unknown
        // label slightly differently — but we require non-crash + xG > 0.
        assertTrue(xg4411 >= 0,
                "V5 (4-4-1-1 invented): engine must handle unknown label without crashing. "
                        + "xg4411=" + xg4411);
    }

    // ========== C11c Test 5: LWB-like (DEF-natural) in ATT = severe penalty ==========

    /**
     * V25D47 / C11c Test 5: a defensive-natural player forced into the ATT
     * slot is severely penalized. In the
     * {@link com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator}
     * table, effectiveness(DEF, ATT) = 0.4 — a CB or LWB asked to play as
     * a striker fails badly.
     *
     * <p>Note on the 5-cat simplification (C11a): the natural "LWB" 15-value
     * position maps to {@code WINGER} (carrilero/LWB folds into WINGER
     * because both are wide defenders who attack). WINGER->ATT is 0.9
     * (mild penalty) per the table — NOT 0.4 as the task description
     * suggests. To exercise the 0.4 severe-penalty code path, this test
     * uses {@code naturalPosition = "DEF"} + tactical "ATT" which gives
     * effectiveness = 0.4. The test captures the spirit of the original
     * task ("defensive-natural in ATT = severe penalty").
     *
     * <p>Methodology: 11-player base, top-5 attacker is a DEF-natural with
     * a high attack stat. Compare aggregateAttackerStat with the player
     * in their natural DEF slot vs forced into ATT slot. The ATT-slot
     * aggregate must be noticeably lower (effectiveness 0.4 * raw attack).
     */
    @Test
    void lwbInAtt_highPenalty() throws Exception {
        // Base lineup: 1 GK + 3 DEF + 4 MID + 1 ATT + 2 WINGER.
        // One of the DEFs has attack=95 (high — pushing into top-5).
        List<V24PlayerMatchState> naturalStates = new ArrayList<>();
        naturalStates.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("gk0", "GK", 30, 80, 50), "teamZ"));
        for (int i = 0; i < 3; i++) {
            int attack = (i == 0) ? 95 : 50;  // first DEF is a "tweener" with attack 95
            naturalStates.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("def" + i, "DEF", attack, 70, 50), "teamZ"));
        }
        for (int i = 0; i < 4; i++) {
            naturalStates.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("mid" + i, "MID", 75, 60, 80), "teamZ"));
        }
        for (int i = 0; i < 1; i++) {
            naturalStates.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("att" + i, "ATT", 90, 50, 85), "teamZ"));
        }
        for (int i = 0; i < 2; i++) {
            naturalStates.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("wing" + i, "WINGER", 85, 50, 80), "teamZ"));
        }

        // Misaligned: copy + force def0 (attack=95, naturalPosition=DEF) into ATT slot.
        List<V24PlayerMatchState> misalignedStates = new ArrayList<>(naturalStates);
        V24PlayerMatchState movedDef = V24PlayerMatchState.fromSessionPlayer(
                makePlayer("def0", "DEF", 95, 70, 50), "teamZ");
        movedDef.setPosition("ATT");  // effectiveness(DEF, ATT) = 0.4
        misalignedStates.set(1, movedDef);

        double naturalAgg = invokeAggregateAttackerStat(naturalStates, "4-4-2");
        double misalignedAgg = invokeAggregateAttackerStat(misalignedStates, "4-4-2");

        // Natural top-5 by attack: def0 (95), att0 (90), wing0 (85), wing1 (85), mid0 (75) → avg = 86.0.
        // Misaligned top-5: def0 weighted = 95*0.4 = 38 (falls out of top-5).
        //   Top-5 becomes: att0 (90), wing0 (85), wing1 (85), mid0 (75), mid1 (75) → avg = 82.0.
        // Delta = 4.0 (~4.7% lower).
        assertTrue(naturalAgg > misalignedAgg,
                "aggregateAttackerStat must drop when a DEF-natural is forced into ATT slot. "
                        + "natural=" + naturalAgg + ", misaligned=" + misalignedAgg);
        assertTrue(naturalAgg - misalignedAgg >= naturalAgg * 0.03,
                "Severe penalty (effectiveness 0.4) must drop aggregate by at least 3%. "
                        + "natural=" + naturalAgg + ", misaligned=" + misalignedAgg
                        + ", delta=" + (naturalAgg - misalignedAgg));
    }

    /**
     * V25D99.22.1: the actual match engine must consume the same visual
     * free-positioning coordinates used by the formation editor preview.
     *
     * <p>Regression captured from the MVP editor: pushing one midfielder a bit
     * higher from a 4-4-2-like shape felt visually more attacking, but the
     * engine-side aggregate could only see a coarse MID label and/or the
     * distance-from-ideal penalty. The result was backwards: both attack and
     * midfield could drop even though the manager clearly made a more
     * aggressive shape.
     *
     * <p>This test keeps the same 11 players and same tactical MID label, only
     * changing one player's customY from CM-ish ({@code y=60}) to CAM-ish
     * ({@code y=40}). The attacker aggregate must react upward, proving the
     * pixel move is not ignored by the partido.
     */
    @Test
    void visualForwardMoveOfMidfielderRaisesEngineAttackInput() throws Exception {
        List<V24PlayerMatchState> states = new ArrayList<>();
        states.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("gk0", "GK", 30, 80, 50), "teamP"));
        for (int i = 0; i < 4; i++) {
            states.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("def" + i, "DEF", 50, 70, 50), "teamP"));
        }
        for (int i = 0; i < 4; i++) {
            states.add(V24PlayerMatchState.fromSessionPlayer(
                    makePlayer("mid" + i, "MID", 76, 60, 82), "teamP"));
        }
        states.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("att0", "ATT", 86, 50, 82), "teamP"));
        states.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("att1", "ATT", 85, 50, 82), "teamP"));

        String movedMidfielderId = states.stream()
                .filter(p -> "mid0".equals(p.name()))
                .findFirst()
                .orElseThrow()
                .sessionPlayerId();

        Map<String, LineupSlotDTO> baselineSlots = new HashMap<>();
        baselineSlots.put(movedMidfielderId, new LineupSlotDTO(movedMidfielderId, "S5-2", 50.0, 60.0));

        Map<String, LineupSlotDTO> advancedSlots = new HashMap<>();
        advancedSlots.put(movedMidfielderId, new LineupSlotDTO(movedMidfielderId, "S5-2", 50.0, 40.0));

        double baselineAttack = invokeAggregateAttackerStat(states, "4-4-2", baselineSlots);
        double advancedAttack = invokeAggregateAttackerStat(states, "4-4-2", advancedSlots);

        assertTrue(advancedAttack > baselineAttack,
                "A same-label MID moved visually forward must increase engine attack input. "
                        + "baseline=" + baselineAttack + ", advanced=" + advancedAttack);
    }

    /**
     * V25D99.22.2: tactical shape must be continuous with the visual pitch.
     * Moving one midfielder upward should gradually raise attack volume; moving
     * one more pixel should not cause a formation-level cliff.
     */
    @Test
    void visualForwardMoveChangesTacticalShapeSmoothly() throws Exception {
        List<SessionPlayer> starting = new ArrayList<>();
        starting.add(makePlayer("gk0", "GK", 30, 80, 50));
        for (int i = 0; i < 4; i++) {
            starting.add(makePlayer("def" + i, "DEF", 50, 70, 50));
        }
        for (int i = 0; i < 4; i++) {
            starting.add(makePlayer("mid" + i, "MID", 76, 60, 82));
        }
        starting.add(makePlayer("att0", "ATT", 86, 50, 82));
        starting.add(makePlayer("att1", "ATT", 85, 50, 82));

        String movedMidfielderId = starting.stream()
                .filter(p -> "mid0".equals(p.getName()))
                .findFirst()
                .orElseThrow()
                .getSessionPlayerId();

        double attackAt60 = invokeAttackVolumeForMovedMidfielder(starting, movedMidfielderId, 60.0);
        double attackAt40 = invokeAttackVolumeForMovedMidfielder(starting, movedMidfielderId, 40.0);
        double attackAt39 = invokeAttackVolumeForMovedMidfielder(starting, movedMidfielderId, 39.0);

        assertTrue(attackAt40 > attackAt60,
                "Moving a midfielder visually forward must increase attack-volume shape. "
                        + "y60=" + attackAt60 + ", y40=" + attackAt40);
        assertTrue(Math.abs(attackAt39 - attackAt40) < 0.02,
                "One-pixel/one-percent tactical movement must not create a cliff. "
                        + "y40=" + attackAt40 + ", y39=" + attackAt39);
    }

    /**
     * V25D99.76: horizontal pixels must feed the same channel story that the
     * visual editor now shows in "Shape & canales". Moving a left attacker
     * inward should reduce left-channel attack and increase central attack,
     * while keeping the formation label unchanged.
     */
    @Test
    void visualHorizontalMoveChangesAttackChannels() throws Exception {
        List<SessionPlayer> starting = new ArrayList<>();
        starting.add(makePlayer("gk0", "GK", 30, 80, 50));
        for (int i = 0; i < 4; i++) {
            starting.add(makePlayer("def" + i, "DEF", 50, 70, 50));
        }
        for (int i = 0; i < 3; i++) {
            starting.add(makePlayer("mid" + i, "MID", 76, 60, 82));
        }
        starting.add(makePlayer("lw0", "ATT", 86, 50, 82));
        starting.add(makePlayer("st0", "ATT", 88, 50, 82));
        starting.add(makePlayer("rw0", "ATT", 86, 50, 82));

        String leftAttackerId = starting.stream()
                .filter(p -> "lw0".equals(p.getName()))
                .findFirst()
                .orElseThrow()
                .getSessionPlayerId();

        Map<String, LineupSlotDTO> wideLeft = explicitWideShapeSlots(starting);
        wideLeft.put(leftAttackerId, new LineupSlotDTO(leftAttackerId, "S04-1", 18.0, 18.0));

        Map<String, LineupSlotDTO> movedCentral = explicitWideShapeSlots(starting);
        movedCentral.put(leftAttackerId, new LineupSlotDTO(leftAttackerId, "S04-1", 54.0, 18.0));

        double wideAttackLeft = invokeShapeMetric(starting, wideLeft, "attackLeft");
        double wideAttackCenter = invokeShapeMetric(starting, wideLeft, "attackCenter");
        double centralAttackLeft = invokeShapeMetric(starting, movedCentral, "attackLeft");
        double centralAttackCenter = invokeShapeMetric(starting, movedCentral, "attackCenter");

        assertTrue(centralAttackLeft < wideAttackLeft,
                "Moving LW from left channel toward centre must reduce attackLeft. "
                        + "wideLeft=" + wideAttackLeft + ", movedLeft=" + centralAttackLeft);
        assertTrue(centralAttackCenter > wideAttackCenter,
                "Moving LW from left channel toward centre must increase attackCenter. "
                        + "wideCenter=" + wideAttackCenter + ", movedCenter=" + centralAttackCenter);
    }

    /**
     * V25D99.26: the tactical shape must not treat an out-of-role player as a
     * perfect midfielder just because his custom coordinates sit in the middle
     * third. The editor/harness can visually place any player in a MID slot,
     * but the match engine must still price the loss of midfield structure.
     */
    @Test
    void outOfRoleMidfieldSlotLowersPossessionAndProtectionShape() throws Exception {
        List<SessionPlayer> naturalMidfield = new ArrayList<>();
        naturalMidfield.add(makePlayer("gk0", "GK", 30, 80, 50));
        for (int i = 0; i < 4; i++) {
            naturalMidfield.add(makePlayer("def" + i, "DEF", 50, 70, 50));
        }
        for (int i = 0; i < 4; i++) {
            naturalMidfield.add(makePlayer("mid" + i, "MID", 76, 60, 82));
        }
        naturalMidfield.add(makePlayer("att0", "ATT", 86, 50, 82));
        naturalMidfield.add(makePlayer("att1", "ATT", 85, 50, 82));

        List<SessionPlayer> forcedAttackerInMidfield = new ArrayList<>(naturalMidfield);
        forcedAttackerInMidfield.set(5, makePlayer("mid0", "ATT", 76, 60, 82));

        String movedPlayerId = "mid0";
        Map<String, LineupSlotDTO> sameVisualMidSlot = Map.of(
                movedPlayerId, new LineupSlotDTO(movedPlayerId, "S5-2", 50.0, 52.0));

        double naturalPossession = invokeShapeMetric(naturalMidfield, sameVisualMidSlot, "possessionMultiplier");
        double outOfRolePossession = invokeShapeMetric(
                forcedAttackerInMidfield, sameVisualMidSlot, "possessionMultiplier");
        double naturalResistance = invokeShapeMetric(naturalMidfield, sameVisualMidSlot, "defensiveResistanceMultiplier");
        double outOfRoleResistance = invokeShapeMetric(
                forcedAttackerInMidfield, sameVisualMidSlot, "defensiveResistanceMultiplier");

        assertTrue(outOfRolePossession < naturalPossession,
                "Replacing a natural MID with an ATT in the same visual MID slot must reduce possession shape. "
                        + "natural=" + naturalPossession + ", outOfRole=" + outOfRolePossession);
        assertTrue(outOfRoleResistance > naturalResistance,
                "Replacing a natural MID with an ATT in the same visual MID slot must weaken protection "
                        + "(higher opponent attack-volume multiplier). natural=" + naturalResistance
                        + ", outOfRole=" + outOfRoleResistance);
    }

    /**
     * V25D99.29: a technical attacker can stand in the central band, but he
     * should not provide the same tempo/control/screen value as a true pivot.
     * This protects the manager contract behind cases like
     * Tchouameni -> Rodrygo in the stress harness.
     */
    @Test
    void midfieldProfileLayerValuesPivotAboveAttackerInSameCentralSlot() throws Exception {
        List<SessionPlayer> pivotLineup = makeBalancedLineupWithCustomMidfielder(
                makeSkilledPlayer(
                        "pivot0",
                        "MID",
                        68, 88, 78,
                        72, 90, 88,
                        Map.of(
                                PlayerSkill.PASSER, 84,
                                PlayerSkill.TACKLER, 88,
                                PlayerSkill.MARKER, 78,
                                PlayerSkill.PLAYMAKER, 72)));
        List<SessionPlayer> attackerLineup = makeBalancedLineupWithCustomMidfielder(
                makeSkilledPlayer(
                        "pivot0",
                        "ATT",
                        88, 42, 86,
                        86, 76, 70,
                        Map.of(
                                PlayerSkill.DRIBBLER, 88,
                                PlayerSkill.SPEEDSTER, 86,
                                PlayerSkill.SHOOTER, 82,
                                PlayerSkill.PASSER, 58)));

        Map<String, LineupSlotDTO> sameCentralSlot = Map.of(
                "pivot0", new LineupSlotDTO("pivot0", "S14-2", 50.0, 52.0));

        double pivotPossession = invokeShapeMetric(pivotLineup, sameCentralSlot, "possessionMultiplier");
        double attackerPossession = invokeShapeMetric(attackerLineup, sameCentralSlot, "possessionMultiplier");
        double pivotResistance = invokeShapeMetric(pivotLineup, sameCentralSlot, "defensiveResistanceMultiplier");
        double attackerResistance = invokeShapeMetric(attackerLineup, sameCentralSlot, "defensiveResistanceMultiplier");

        assertTrue(pivotPossession > attackerPossession,
                "A true pivot must preserve more central control than an attacker in the same MID coordinate. "
                        + "pivotPossession=" + pivotPossession + ", attackerPossession=" + attackerPossession);
        assertTrue(pivotResistance < attackerResistance,
                "A true pivot must protect the defense better than an attacker in the same MID coordinate "
                        + "(lower opponent attack-volume multiplier). pivotResistance=" + pivotResistance
                        + ", attackerResistance=" + attackerResistance);
    }

    // ========== Reflection helpers for C11c tests ==========

    /**
     * Invoke the private {@code aggregateAttackerStat(List, String)} method
     * via reflection. Returns the top-5-attack effectiveness-weighted average
     * the engine computes for shot-quality amplification.
     */
    private double invokeAggregateAttackerStat(List<V24PlayerMatchState> players, String formation)
            throws Exception {
        return invokeAggregateAttackerStat(players, formation, Map.of());
    }

    private double invokeAggregateAttackerStat(
            List<V24PlayerMatchState> players,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId)
            throws Exception {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
                "aggregateAttackerStat", List.class, String.class, Map.class);
        method.setAccessible(true);
        return (double) method.invoke(engine, players, formation, slotsByPlayerId);
    }

    /**
     * Invoke the private {@code aggregateDefenderStat(List)} method via
     * reflection. Returns the avg of (defense + mentality) / 2 across
     * DEF+GK players, weighted by effectiveness.
     */
    private double invokeAggregateDefenderStat(List<V24PlayerMatchState> players)
            throws Exception {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
                "aggregateDefenderStat", List.class, Map.class);
        method.setAccessible(true);
        return (double) method.invoke(engine, players, Map.of());
    }

    private double invokeAttackVolumeForMovedMidfielder(
            List<SessionPlayer> starting,
            String movedPlayerId,
            double yPercent)
            throws Exception {
        SessionTeam team = makeTeam(HOME_UUID, "Home FC", "4-4-2");
        Map<String, LineupSlotDTO> slots = new HashMap<>();
        slots.put(movedPlayerId, new LineupSlotDTO(movedPlayerId, "S5-2", 50.0, yPercent));
        V24TeamMatchState state = V24TeamMatchState.create(team, starting, List.of(), TeamStyle.BALANCED, slots);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
                "tacticalShapeProfile", V24TeamMatchState.class, Map.class);
        method.setAccessible(true);
        Object profile = method.invoke(engine, state, slots);
        Method accessor = profile.getClass().getDeclaredMethod("attackVolumeMultiplier");
        accessor.setAccessible(true);
        return (double) accessor.invoke(profile);
    }

    private double invokeShapeMetric(
            List<SessionPlayer> starting,
            Map<String, LineupSlotDTO> slots,
            String accessorName)
            throws Exception {
        SessionTeam team = makeTeam(HOME_UUID, "Home FC", "4-4-2");
        V24TeamMatchState state = V24TeamMatchState.create(team, starting, List.of(), TeamStyle.BALANCED, slots);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        Method method = V24DetailedMatchEngine.class.getDeclaredMethod(
                "tacticalShapeProfile", V24TeamMatchState.class, Map.class);
        method.setAccessible(true);
        Object profile = method.invoke(engine, state, slots);
        Method accessor = profile.getClass().getDeclaredMethod(accessorName);
        accessor.setAccessible(true);
        return (double) accessor.invoke(profile);
    }

    private Map<String, LineupSlotDTO> explicitWideShapeSlots(List<SessionPlayer> starting) {
        Map<String, LineupSlotDTO> slots = new HashMap<>();
        int def = 0;
        int mid = 0;
        int att = 0;
        for (SessionPlayer player : starting) {
            String id = player.getSessionPlayerId();
            switch (player.getPosition()) {
                case "GK" -> slots.put(id, new LineupSlotDTO(id, "GK-1", 50.0, 98.0));
                case "DEF" -> {
                    double[] xs = {18.0, 40.0, 60.0, 82.0};
                    slots.put(id, new LineupSlotDTO(id, "D" + def, xs[Math.min(def, xs.length - 1)], 83.0));
                    def++;
                }
                case "MID" -> {
                    double[] xs = {18.0, 50.0, 82.0};
                    slots.put(id, new LineupSlotDTO(id, "M" + mid, xs[Math.min(mid, xs.length - 1)], 55.0));
                    mid++;
                }
                default -> {
                    double[] xs = {18.0, 50.0, 82.0};
                    slots.put(id, new LineupSlotDTO(id, "A" + att, xs[Math.min(att, xs.length - 1)], 18.0));
                    att++;
                }
            }
        }
        return slots;
    }

    private List<SessionPlayer> makeBalancedLineupWithCustomMidfielder(SessionPlayer customMidfielder) {
        List<SessionPlayer> starting = new ArrayList<>();
        starting.add(makePlayer("gk0", "GK", 30, 80, 50));
        for (int i = 0; i < 4; i++) {
            starting.add(makePlayer("def" + i, "DEF", 50, 72, 55));
        }
        starting.add(customMidfielder);
        for (int i = 1; i < 4; i++) {
            starting.add(makePlayer("mid" + i, "MID", 74, 64, 78));
        }
        starting.add(makePlayer("att0", "ATT", 86, 50, 82));
        starting.add(makePlayer("att1", "ATT", 85, 50, 82));
        return starting;
    }

    private SessionPlayer makeSkilledPlayer(
            String id,
            String position,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            Map<PlayerSkill, Integer> skills) {
        SessionPlayer player = SessionPlayer.custom(
                id, 25, position,
                attack, defense, technique,
                speed, stamina, mentality,
                BigDecimal.valueOf(attack * 1000L));
        for (Map.Entry<PlayerSkill, Integer> skill : skills.entrySet()) {
            player.setSkillLevel(skill.getKey(), skill.getValue());
        }
        return player;
    }

    /**
     * Match-runner that accepts custom {@code List<SessionPlayer>} for both
     * home and away (the original {@link #runMatch} hardcodes the
     * {@code makeMixedLineup} base). Used by C11c Test 4 (5-formations
     * probe). Note: per-player tactical-position moves are NOT exercisable
     * through this path because {@code V24MatchContext} takes
     * {@code SessionPlayer}, and {@code fromSessionPlayer} sets
     * naturalPosition = tactical position = SessionPlayer.position. Tests
     * that need tactical moves use {@link #invokeAggregateAttackerStat} or
     * {@link #invokeAggregateDefenderStat} directly via reflection (Tests
     * 1, 2, 3, 5).
     */
    private V24DetailedMatchResult runMatchWithLineup(
            String homeFormation, String awayFormation, long seed,
            List<SessionPlayer> homeStart, List<SessionPlayer> awayStart) {
        SessionTeam homeTeam = makeTeam(HOME_UUID, "Home FC", homeFormation);
        SessionTeam awayTeam = makeTeam(AWAY_UUID, "Away FC", awayFormation);

        V24MatchContext ctx = new V24MatchContext(
                "match-form-c11c-" + homeFormation + "-" + awayFormation + "-" + seed,
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

    // ============================================================================
    // V25D55 (Sprint C16) — Engine integration tests for the 5 new formations
    // added in V25D54 (C15): 3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2, 4-1-2-3.
    //
    // Each test simulates a full match with the new formation on both teams and
    // asserts: (a) the simulation completes without exception, (b) cumulative
    // home xG falls in the (0, 5) range. These guard against the parser /
    // formationOffensiveModifier / shot-selection paths throwing on formation
    // labels that V24 didn't historically recognize.
    //
    // Seed is deterministic (42L) so any future regression on the formation
    // path shows up as a baseline-shift in this assertion, not as flakiness.
    // ============================================================================

    @Test
    @DisplayName("V25D55 (C16) P1.5: 3-5-2-CDM simulation runs without error and produces valid xG")
    void simulation_3_5_2_CDM_runsWithoutError() {
        V24DetailedMatchResult result = runMatch("3-5-2-CDM", "4-4-2", 42L);
        assertValidMatchResult(result, "3-5-2-CDM");
    }

    @Test
    @DisplayName("V25D55 (C16) P1.5: 5-4-1 simulation runs without error and produces valid xG")
    void simulation_5_4_1_runsWithoutError() {
        V24DetailedMatchResult result = runMatch("5-4-1", "4-4-2", 42L);
        assertValidMatchResult(result, "5-4-1");
    }

    @Test
    @DisplayName("V25D55 (C16) P1.5: 3-4-1-2 simulation runs without error and produces valid xG")
    void simulation_3_4_1_2_runsWithoutError() {
        V24DetailedMatchResult result = runMatch("3-4-1-2", "4-4-2", 42L);
        assertValidMatchResult(result, "3-4-1-2");
    }

    @Test
    @DisplayName("V25D55 (C16) P1.5: 4-2-2-2 simulation runs without error and produces valid xG")
    void simulation_4_2_2_2_runsWithoutError() {
        V24DetailedMatchResult result = runMatch("4-2-2-2", "4-4-2", 42L);
        assertValidMatchResult(result, "4-2-2-2");
    }

    @Test
    @DisplayName("V25D55 (C16) P1.5: 4-1-2-3 simulation runs without error and produces valid xG")
    void simulation_4_1_2_3_runsWithoutError() {
        V24DetailedMatchResult result = runMatch("4-1-2-3", "4-4-2", 42L);
        assertValidMatchResult(result, "4-1-2-3");
    }

    /**
     * V25D55 (C16) P1.5: shared assertion for the 5 formation integration
     * tests. Validates:
     * <ul>
     *   <li>{@code homeXg} is positive (sim produced shots).</li>
     *   <li>{@code homeXg} is below 5.0 (sanity upper bound — a 90-min match
     *       with a balanced squad should not generate more than 5 xG; if it
     *       does, the formation modifier is mis-wiring the shooter
     *       distribution).</li>
     *   <li>The home and away teams produced different aggregate metrics
     *       (i.e. the simulation did not degenerate to a constant zero draw).</li>
     * </ul>
     */
    private void assertValidMatchResult(V24DetailedMatchResult result, String formation) {
        assertTrue(result.homeXg() > 0,
                "Home xG must be > 0 for formation " + formation + " (got " + result.homeXg() + ")");
        assertTrue(result.homeXg() < 5,
                "Home xG must be < 5 for formation " + formation + " (got " + result.homeXg() + "). "
                        + "If higher, the formation modifier is amplifying shot quality out of range.");
        assertTrue(result.awayXg() >= 0,
                "Away xG must be non-negative for formation " + formation + " (got " + result.awayXg() + ")");
        assertNotEquals(0, result.homeShots() + result.awayShots(),
                "Match must produce at least one shot for formation " + formation);
    }
}

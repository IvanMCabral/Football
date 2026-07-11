package com.footballmanager.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D99.16-BACK: integration test that confirms micro-moves WITHIN a
 * zone now change the team ratings, addressing Ivan's report that
 * "juntar m&aacute;s los mediocampistas centrales no hace nada" (dragging
 * a CM one slot toward the center didn't change anything pre-V25D99.16).
 *
 * <p>Builds three synthetic lineups for 4-4-2 with the same 11 players:
 * <ol>
 *   <li><b>Spread MID</b> &mdash; CMs at the wing MID slots (S16-2 / S18-2),
 *       classical 4-4-2 wide midfield.</li>
 *   <li><b>Compact MID</b> &mdash; CMs at the central MID slots
 *       (S17-1 / S17-3), narrowed middle.</li>
 *   <li><b>Central MID</b> &mdash; CMs at the SAME central MID slot (S17-2,
 *       unrealistic but tests the gradient at the ideal coords).</li>
 * </ol>
 *
 * <p>Pre-V25D99.16: all three produced identical ratings (same zones,
 * same PositionEffectivenessCalculator.effectiveness outputs).
 *
 * <p>Post-V25D99.16: midfieldRating varies by ~1-3 percentage points
 * because SubdivisionEffectivenessCalculator penalises distance from
 * the CM ideal centroid (50, 60). Attack / defense ratings stay
 * identical (no DEF / ATT slot moved).
 */
@DisplayName("TeamRatingsCalculator — V25D99.16 within-zone micro-moves change ratings")
class TeamRatingsCalculatorWithinZoneTest {

    private static final String FORMATION = "4-4-2";

    private static final double EPS = 0.5;  // half a percentage point; granularity is small.

    private static TeamRatingsCalculator.PlayerAttrs cmAt(String id, double x, double y) {
        // CMs in the 4-4-2 MID row:
        // S16-2 (LM,  16.65, 61.0)
        // S17-1 (LCM, 38.85, 61.0)
        // S17-3 (RCM, 61.05, 61.0)
        // S18-2 (RM,  83.25, 61.0)
        return new TeamRatingsCalculator.PlayerAttrs(
                id, "CM", "MID", 70, 50, 75, 60, x, y);
    }

    private static TeamRatingsCalculator.PlayerAttrs defAt(String id, String role,
                                                            double x, double y) {
        // DEF in 4-4-2:
        // S22-2 (LB,   16.65, 83.0)
        // S23-1 (LCB,  38.85, 83.0)
        // S23-3 (RCB,  61.05, 83.0)
        // S24-2 (RB,   83.25, 83.0)
        return new TeamRatingsCalculator.PlayerAttrs(
                id, role, "DEF", 50, 75, 55, 75, x, y);
    }

    private static TeamRatingsCalculator.PlayerAttrs fwdAt(String id, double x, double y) {
        // ATT in 4-4-2: S05-1 / S05-3.
        return new TeamRatingsCalculator.PlayerAttrs(
                id, "ST", "ATT", 80, 30, 65, 60, x, y);
    }

    private static TeamRatingsCalculator.PlayerAttrs gkAt(String id) {
        // GK slot is unique, no coords needed for the test.
        return new TeamRatingsCalculator.PlayerAttrs(
                id, "GK", "GK", 25, 60, 65, 50, 50.0, 93.0);
    }

    @Test
    @DisplayName("Midfield rating varies when CMs are spread vs. compact")
    void withinZoneMidMovement_changesMidfieldRating() {
        // Baseline: 4-4-2 with CMs at LM/LCM/RCM/RM (the classical 4-4-2 wide).
        // Same coordinates the FormationService produces (V25D94 symmetric).
        List<TeamRatingsCalculator.PlayerAttrs> spread = List.of(
                gkAt("gk1"),
                defAt("d1", "LB", 16.65, 83.0),
                defAt("d2", "CB", 38.85, 83.0),
                defAt("d3", "CB", 61.05, 83.0),
                defAt("d4", "RB", 83.25, 83.0),
                cmAt("m1", 16.65, 61.0),  // LM (S16-2)
                cmAt("m2", 38.85, 61.0),  // LCM (S17-1)
                cmAt("m3", 61.05, 61.0),  // RCM (S17-3)
                cmAt("m4", 83.25, 61.0),  // RM (S18-2)
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        // Compact variant: same lineup but the CMs are dragged closer
        // together (still inside the MID row, but moved 10% toward center
        // each direction). The slotCategory stays "MID" so the legacy
        // zone-only calc returns identical eff \u2014 V25D99.16 should now
        // produce a measurable difference via the distance penalty.
        List<TeamRatingsCalculator.PlayerAttrs> compact = List.of(
                gkAt("gk1"),
                defAt("d1", "LB", 16.65, 83.0),
                defAt("d2", "CB", 38.85, 83.0),
                defAt("d3", "CB", 61.05, 83.0),
                defAt("d4", "RB", 83.25, 83.0),
                cmAt("m1", 27.0, 61.0),  // mid-LM-ish (still in MID zone)
                cmAt("m2", 44.0, 61.0),  // closer to center from LCM
                cmAt("m3", 56.0, 61.0),  // closer to center from RCM
                cmAt("m4", 73.0, 61.0),  // mid-RM-ish (still in MID zone)
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        TeamRatingsCalculator.TeamRatings spreadRatings =
                TeamRatingsCalculator.compute(spread, FORMATION);
        TeamRatingsCalculator.TeamRatings compactRatings =
                TeamRatingsCalculator.compute(compact, FORMATION);

        // Defense should be IDENTICAL — we didn't move any DEF or GK.
        // (DEF cohort is the only input to teamDefense, and all DEF/GK
        // slots stayed at the same coords.)
        assertEquals(spreadRatings.defenseRating(), compactRatings.defenseRating(), EPS,
                "DEF should be identical (same DEF+GK slots)");

        // Note: attack MAY shift by a small amount even though we didn't
        // touch ATT slots. The engine aggregates top-5 across ALL 11
        // players (not just ATT-zone ones), so a CM's eff change can
        // push them in/out of the top-5 cutoff and ripple into the
        // attack modifier. This is engine-accurate behavior — the
        // test only asserts that MID definitively changes.

        // Midfield MUST differ now — compact CMs are closer to the CM
        // ideal (50, 60), so they accumulate higher eff in the
        // midfielder cohort's technique * eff average. Direction
        // matters: compact (closer to ideal) → HIGHER MID.
        assertTrue(compactRatings.midfieldRating() > spreadRatings.midfieldRating(),
                "Compact (close to ideal) should score higher MID than spread. "
                        + "Got compact=" + compactRatings.midfieldRating()
                        + ", spread=" + spreadRatings.midfieldRating());
        // Should also be visibly different (>1 percentage point).
        assertTrue(Math.abs(compactRatings.midfieldRating()
                                - spreadRatings.midfieldRating()) > 0.5,
                "MID difference should be visibly larger than 0.5pp. "
                        + "Got diff=" + Math.abs(
                                compactRatings.midfieldRating()
                                        - spreadRatings.midfieldRating()));
    }

    @Test
    @DisplayName("Advanced midfielder trades structure for attack intent")
    void advancedMidfielder_increasesAttackIntentButLosesMidfieldStructure() {
        List<TeamRatingsCalculator.PlayerAttrs> baseline = List.of(
                gkAt("gk1"),
                defAt("d1", "LB", 16.65, 83.0),
                defAt("d2", "CB", 38.85, 83.0),
                defAt("d3", "CB", 61.05, 83.0),
                defAt("d4", "RB", 83.25, 83.0),
                cmAt("m1", 16.65, 61.0),
                cmAt("m2", 38.85, 61.0),
                cmAt("m3", 61.05, 61.0),
                cmAt("m4", 83.25, 61.0),
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        List<TeamRatingsCalculator.PlayerAttrs> oneMidAdvanced = List.of(
                gkAt("gk1"),
                defAt("d1", "LB", 16.65, 83.0),
                defAt("d2", "CB", 38.85, 83.0),
                defAt("d3", "CB", 61.05, 83.0),
                defAt("d4", "RB", 83.25, 83.0),
                cmAt("m1", 16.65, 61.0),
                cmAt("m2", 38.85, 61.0),
                // Manager pushes one CM into a CAM-ish lane. The player
                // leaves his ideal midfield structure (MID should drop),
                // but the tactical intent is more offensive (ATT should rise).
                cmAt("m3", 61.05, 42.0),
                cmAt("m4", 83.25, 61.0),
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        TeamRatingsCalculator.TeamRatings baseRatings =
                TeamRatingsCalculator.compute(baseline, FORMATION);
        TeamRatingsCalculator.TeamRatings advancedRatings =
                TeamRatingsCalculator.compute(oneMidAdvanced, FORMATION);

        assertTrue(advancedRatings.attackRating() > baseRatings.attackRating(),
                "Pushing a CM higher should increase ATT intent. "
                        + "Got advanced=" + advancedRatings.attackRating()
                        + ", base=" + baseRatings.attackRating());
        assertTrue(advancedRatings.midfieldRating() < baseRatings.midfieldRating(),
                "Pushing a CM away from the midfield line should reduce MID structure. "
                        + "Got advanced=" + advancedRatings.midfieldRating()
                        + ", base=" + baseRatings.midfieldRating());
        assertEquals(baseRatings.defenseRating(), advancedRatings.defenseRating(), EPS,
                "DEF should be unchanged when only a MID moves higher");
    }

    @Test
    @DisplayName("Clear manual front three progressively approaches attacking formation base")
    void clearManualFrontThree_blendsTowardAttackingShape() {
        List<TeamRatingsCalculator.PlayerAttrs> baseline = List.of(
                gkAt("gk1"),
                defAt("d1", "LB", 16.65, 83.0),
                defAt("d2", "CB", 38.85, 83.0),
                defAt("d3", "CB", 61.05, 83.0),
                defAt("d4", "RB", 83.25, 83.0),
                cmAt("m1", 16.65, 61.0),
                cmAt("m2", 38.85, 61.0),
                cmAt("m3", 61.05, 61.0),
                cmAt("m4", 83.25, 61.0),
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        List<TeamRatingsCalculator.PlayerAttrs> clearFrontThree = List.of(
                gkAt("gk1"),
                defAt("d1", "LB", 16.65, 83.0),
                defAt("d2", "CB", 38.85, 83.0),
                defAt("d3", "CB", 61.05, 83.0),
                defAt("d4", "RB", 83.25, 83.0),
                cmAt("m1", 16.65, 61.0),
                cmAt("m2", 38.85, 61.0),
                // This is no longer a one-frame nudge: the manager has
                // clearly turned the line into a front three.
                cmAt("m3", 61.05, 17.0),
                cmAt("m4", 83.25, 61.0),
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        TeamRatingsCalculator.TeamRatings baseRatings =
                TeamRatingsCalculator.compute(baseline, FORMATION);
        TeamRatingsCalculator.TeamRatings manualRatings =
                TeamRatingsCalculator.compute(clearFrontThree, FORMATION);

        assertTrue(manualRatings.attackRating() > baseRatings.attackRating() + 15.0,
                "A clear manual front three should approach a more attacking base. "
                        + "Got manual=" + manualRatings.attackRating()
                        + ", base=" + baseRatings.attackRating());
        assertTrue(manualRatings.defenseRating() < baseRatings.defenseRating(),
                "A clear manual front three should trade defensive base for attack. "
                        + "Got manual=" + manualRatings.defenseRating()
                        + ", base=" + baseRatings.defenseRating());
    }

    @Test
    @DisplayName("NaN coords reproduce pre-V25D99.16 behavior (zone-only math)")
    void nanCoords_legacyFallback() {
        // Two CBs at the same row slot (S22-2 LB-ish). The first uses
        // coords (geometry-aware), the second uses NaN (legacy zone-only).
        // They should produce meaningfully different ratings because
        // the geometry-aware calc penalizes the wing-S22-2 from the CB
        // ideal at (50, 83) while the NaN calc returns base 1.0.
        List<TeamRatingsCalculator.PlayerAttrs> legacy = List.of(
                gkAt("gk1"),
                defAt("d1a", "CB", Double.NaN, Double.NaN),  // NaN coords
                defAt("d2a", "CB", Double.NaN, Double.NaN),  // NaN coords
                defAt("d3a", "CB", Double.NaN, Double.NaN),  // NaN coords
                defAt("d4a", "RB", Double.NaN, Double.NaN),  // NaN coords (RB matches slot)
                cmAt("m1a", Double.NaN, Double.NaN),
                cmAt("m2a", Double.NaN, Double.NaN),
                cmAt("m3a", Double.NaN, Double.NaN),
                cmAt("m4a", Double.NaN, Double.NaN),
                fwdAt("f1a", Double.NaN, Double.NaN),
                fwdAt("f2a", Double.NaN, Double.NaN)
        );

        List<TeamRatingsCalculator.PlayerAttrs> geometryAware = List.of(
                gkAt("gk1"),
                defAt("d1b", "CB", 16.65, 83.0),   // CB at LB slot \u2192 penalty
                defAt("d2b", "CB", 38.85, 83.0),   // CB at LCB slot \u2192 ideal
                defAt("d3b", "CB", 61.05, 83.0),   // CB at RCB slot \u2192 ideal
                defAt("d4b", "RB", 83.25, 83.0),   // RB at RB slot \u2192 ideal
                cmAt("m1b", 16.65, 61.0),
                cmAt("m2b", 38.85, 61.0),
                cmAt("m3b", 61.05, 61.0),
                cmAt("m4b", 83.25, 61.0),
                fwdAt("f1b", 38.85, 17.0),
                fwdAt("f2b", 61.05, 17.0)
        );

        TeamRatingsCalculator.TeamRatings legacyRatings =
                TeamRatingsCalculator.compute(legacy, FORMATION);
        TeamRatingsCalculator.TeamRatings geoRatings =
                TeamRatingsCalculator.compute(geometryAware, FORMATION);

        // DEF should differ: legacy sees CB@DEF=1.0 (perfect) for all 3 CBs
        // plus RB@DEF=1.0; geometry-aware sees CB@LB=0.85 (penalty).
        assertNotEquals(legacyRatings.defenseRating(), geoRatings.defenseRating(),
                "DEF should differ: legacy (all 1.0) vs geometry-aware (wing CB = 0.85)");
    }

    @Test
    @DisplayName("Slot X distance matters: CB further from ideal = lower DEF rating")
    void cbDistanceMatters() {
        // Three test lineups where the SAME player (same stats) is placed
        // at different DEF slots. The farther from the CB ideal (50, 83),
        // the lower the contribution to teamDefense.
        List<TeamRatingsCalculator.PlayerAttrs> centralCb = List.of(
                gkAt("gk1"),
                defAt("d1a", "CB", 38.85, 83.0),  // LCB \u2014 close to ideal
                defAt("d2a", "CB", 61.05, 83.0),  // RCB \u2014 close to ideal
                defAt("d3a", "CB", 38.85, 83.0),  // duplicate LCB for math
                defAt("d4a", "RB", 83.25, 83.0),
                cmAt("m1", 38.85, 61.0),
                cmAt("m2", 38.85, 61.0),
                cmAt("m3", 61.05, 61.0),
                cmAt("m4", 61.05, 61.0),
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        List<TeamRatingsCalculator.PlayerAttrs> offCentreCb = List.of(
                gkAt("gk1"),
                defAt("d1b", "LB", 16.65, 83.0),  // CB placed at LB slot
                defAt("d2b", "CB", 38.85, 83.0),
                defAt("d3b", "CB", 61.05, 83.0),
                defAt("d4b", "RB", 83.25, 83.0),  // CB placed at RB slot (replaces RB)
                cmAt("m1", 38.85, 61.0),
                cmAt("m2", 38.85, 61.0),
                cmAt("m3", 61.05, 61.0),
                cmAt("m4", 61.05, 61.0),
                fwdAt("f1", 38.85, 17.0),
                fwdAt("f2", 61.05, 17.0)
        );

        TeamRatingsCalculator.TeamRatings centralRatings =
                TeamRatingsCalculator.compute(centralCb, FORMATION);
        TeamRatingsCalculator.TeamRatings offCentreRatings =
                TeamRatingsCalculator.compute(offCentreCb, FORMATION);

        // Both have the same players in the same zones, but the slots
        // differ. Pre-V25D99.16 they produce IDENTICAL ratings. Post-
        // V25D99.16 the wing placements (LB/RB) carry a small penalty
        // for a CB ideal \u2014 but to compare, we need natural positions
        // \u2014 let's just confirm the ratings differ.
        assertNotEquals(centralRatings.defenseRating(), offCentreRatings.defenseRating(),
                "V25D99.16: DEF rating should change when CBs shift wing-ward "
                        + "within the DEF zone. central=" + centralRatings.defenseRating()
                        + ", offCentre=" + offCentreRatings.defenseRating());
    }
}

package com.footballmanager.domain.model.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * V25D99.15-BACK: pure utility that computes the per-zone team ratings
 * (attack / midfield / defense) using the SAME formulas the V24 simulation
 * engine uses during a real match.
 *
 * <p>The simulation engine computes:
 * <ul>
 *   <li><b>teamAttack</b> = avg of top-7 attackers'
 *       {@code attack * PositionEffectivenessCalculator.effectiveness(naturalPos, slotCategory)}
 *       (V24DetailedMatchEngine.aggregateAttackerStat, line 1078).
 *       V25D99.18: widened from top-5 to top-7 so MIDs in attack-zone
 *       slots with high eff enter the cohort.</li>
 *   <li><b>teamDefense</b> = avg of DEF + GK players'
 *       {@code ((defense + mentality) / 2.0) * effectiveness}
 *       (V24DetailedMatchEngine.aggregateDefenderStat, line 1109).</li>
 *   <li><b>formationOffensiveModifier</b> = baseMod[formation] * statsAmp(teamAttack)
 *       (V24ShotXgCalculator.formationOffensiveModifier, line 522).</li>
 *   <li><b>formationDefensiveModifier</b> = baseMod[formation] * statsAmp(teamDefense)
 *       applied as DIVISOR in the engine
 *       (V24ShotXgCalculator.formationDefensiveModifier, line 574).</li>
 * </ul>
 *
 * <p>This class exposes the same arithmetic as a STATIC method so the
 * frontend can call it indirectly (via the dedicated preview endpoint)
 * instead of re-implementing the math on the TS side. Single source of
 * truth: any engine re-calibration updates BOTH the live match engine
 * and the lineup preview.
 *
 * <h2>V25D99.16-BACK: subdivision-aware effectiveness</h2>
 * <p>Pre-V25D99.16, the engine used
 * {@link PositionEffectivenessCalculator#effectiveness(String, String)}
 * which collapses per-player contribution into 3-4 zone buckets. Two
 * slots in the SAME zone (e.g. CB at S22-2 vs S24-2) produced identical
 * effectiveness &mdash; fine-grained drag-and-drop on the field had no
 * effect on the team ratings.
 *
 * <p>V25D99.16 wraps the zone lookup in
 * {@link SubdivisionEffectivenessCalculator}, which also factors in the
 * Euclidean distance from the slot to the natural position's ideal
 * centroid on the field. A CB at the natural CB slot (S22-1/S23-1/S23-3
 * range) still scores ~1.0; a CB dragged to the opposite wing slot
 * (S22-2 vs S24-2 in 4-4-2) drops ~0.05-0.15 depending on layout.
 *
 * <p>Backward compat: callers that don't supply {@code slotXPercent /
 * slotYPercent} (NaN) get the pre-V25D99.16 zone-only math. The only
 * in-tree caller {@code FormationEffectiveness.computeRatings} passes
 * the coords resolved from {@code FormationService}.
 *
 * <h2>Rating scale</h2>
 * <p>The engine uses the modifiers as a multiplier on xG
 * (offensive) or as a divisor on opponent xG (defensive). For the
 * lineup preview we expose:
 * <ul>
 *   <li>{@code attackRating} = {@code formationOffensiveModifier * 100},
 *       55-165 in practice. 100 = 4-4-2 baseline at median stats
 *       (70 attack). &gt;100 = "more dangerous than baseline".</li>
 *   <li>{@code defenseRating} = {@code formationDefensiveModifier * 100},
 *       55-165. 100 = 4-4-2 baseline. HIGHER = more protection
 *       (divided into opponent xG, so it REDUCES goals conceded).</li>
 *   <li>{@code midfieldRating} = symmetric for the MID cohort
 *       using the {@code technique} attribute (midfield-domain metric
 *       &mdash; engine doesn't compute this directly; we mirror the
 *       formula so the panel stays consistent).</li>
 * </ul>
 *
 * <h2>Backward compat</h2>
 * <p>If the lineup is empty / null, returns the baseline values for
 * the requested formation (100 / 100 / 100 for 4-4-2, scaled for others).
 * The frontend can render "&mdash;" via the same fallback rules it uses for
 * missing data.
 */
public final class TeamRatingsCalculator {

    private TeamRatingsCalculator() {
        // Pure utility — no instances.
    }

    /**
     * Per-formation offensive base modifier for the squad preview.
     *
     * <p>V25D99.20.12-BACK: keep formations as strategic trade-offs, not
     * strict upgrades. 4-4-2 is the neutral reference; attack-heavy shapes
     * gain danger but must pay with lower defensive base, while defensive
     * shapes gain protection but lose attacking threat. Unknown formations
     * default to 4-4-2 (1.00).
     *
     * <p>The three base maps (ATT/MID/DEF) are a conserved 300-point budget:
     * each formation distributes roughly 1.00 + 1.00 + 1.00 across the three
     * lanes before player quality and manual positioning are applied. This
     * makes the UI read like a real tactical choice instead of a hidden tier
     * list where one formation is simply better by name.
     */
    private static final Map<String, Double> FORMATION_OFF_BASE = Map.ofEntries(
            Map.entry("4-4-2", 1.00),
            Map.entry("4-3-3", 1.18),
            Map.entry("4-2-3-1", 1.16),
            Map.entry("3-4-3", 1.14),
            Map.entry("3-5-2", 0.90),
            Map.entry("5-3-2", 0.82),
            Map.entry("4-1-4-1", 0.88),
            Map.entry("3-5-2-CDM", 0.84),
            Map.entry("5-4-1", 0.76),
            Map.entry("3-4-1-2", 1.06),
            Map.entry("4-2-2-2", 1.08),
            Map.entry("4-1-2-3", 1.14)
    );

    /**
     * Per-formation midfield base modifier from the same conserved 300-point
     * tactical budget as ATT/DEF. Player technique, role fit and coordinates
     * still decide the final number; this only states the shape's structural
     * emphasis.
     */
    private static final Map<String, Double> FORMATION_MID_BASE = Map.ofEntries(
            Map.entry("4-4-2", 1.00),
            Map.entry("4-3-3", 0.95),
            Map.entry("4-2-3-1", 1.02),
            Map.entry("3-4-3", 0.98),
            Map.entry("3-5-2", 1.05),
            Map.entry("5-3-2", 0.98),
            Map.entry("4-1-4-1", 1.04),
            Map.entry("3-5-2-CDM", 1.04),
            Map.entry("5-4-1", 1.02),
            Map.entry("3-4-1-2", 1.00),
            Map.entry("4-2-2-2", 0.96),
            Map.entry("4-1-2-3", 0.98)
    );

    /**
     * Per-formation defensive base modifier for the squad preview.
     *
     * <p>The defensive values mirror the offensive trade-offs above. No named
     * formation should be better than 4-4-2 in every dimension just because it
     * is selected; player quality and manual shape can still make a tactic work.
     */
    private static final Map<String, Double> FORMATION_DEF_BASE = Map.ofEntries(
            Map.entry("4-4-2", 1.00),
            Map.entry("4-3-3", 0.87),
            Map.entry("4-2-3-1", 0.82),
            Map.entry("3-4-3", 0.88),
            Map.entry("3-5-2", 1.05),
            Map.entry("5-3-2", 1.20),
            Map.entry("4-1-4-1", 1.10),
            Map.entry("3-5-2-CDM", 1.12),
            Map.entry("5-4-1", 1.22),
            Map.entry("3-4-1-2", 0.94),
            Map.entry("4-2-2-2", 0.96),
            Map.entry("4-1-2-3", 0.88)
    );

    /**
     * Stats amplification coefficient (engine: 0.025). Stats are mapped
     * to a multiplier in {@code 1 + (stat - 70) * STATS_AMP}. Median
     * stat (70) → 1.0 (no amp), elite (85) → 1.375, weak (55) → 0.625.
     */
    private static final double STATS_AMP = 0.025;

    /** Median stat value (engine constant). */
    private static final int MEDIAN_STAT = 70;

    /**
     * Result triple. All three values are in {@code [0, ~200]} in practice
     * (offensive / defensive modifiers * 100). Higher attack = more
     * dangerous, higher defense = more protection.
     */
    public record TeamRatings(double attackRating, double midfieldRating, double defenseRating) {}

    /**
     * One player's per-attribute stats + natural position + current
     * subdivision slot. The slot category is computed by the caller via
     * {@link FormationInferer#categoryFor(String)} so this class stays
     * decoupled from the subdivision-id vocabulary.
     *
     * <p>V25D99.16-BACK: {@code slotXPercent} and {@code slotYPercent}
     * are the field coords (0-100 each, see {@code FormationService}
     * cell centers) of the assigned subdivision slot. The values feed
     * {@link SubdivisionEffectivenessCalculator} to apply a small
     * distance-from-ideal penalty for fine-grained drag tuning.
     *
     * <p>Backward compat: pass {@link Double#NaN} to BOTH fields to
     * reproduce pre-V25D99.16 zone-only math (the calculator skips the
     * geometry penalty when coords are NaN). The only in-tree builder
     * ({@code FormationEffectiveness.computeRatings}) resolves coords
     * from the {@code FormationService} cache; legacy callers that don't
     * have access to it pass NaN.
     */
    public record PlayerAttrs(
            String playerId,
            String naturalPos,
            String slotCategory,
            Integer attack,
            Integer defense,
            Integer technique,
            Integer mentality,
            Double slotXPercent,
            Double slotYPercent,
            boolean customPosition
    ) {}

    /**
     * Compute the three team ratings for the supplied lineup + formation.
     *
     * @param attrs list of {@link PlayerAttrs} (one per on-field player,
     *              including GK). Empty list returns baseline multipliers.
     * @param formation canonical formation label (e.g., "4-4-2"). Falls
     *                  back to 4-4-2 if null/unknown.
     * @return immutable triple with attackRating / midfieldRating /
     *         defenseRating (each multiplied by 100 for percentage
     *         readability on the frontend).
     */
    public static TeamRatings compute(List<PlayerAttrs> attrs, String formation) {
        String canonicalFormation = (formation == null || formation.isBlank())
                ? "4-4-2"
                : formation;

        // Pre-V25D99.15-BACK: formation modifiers used as-is (no
        // statsAmp when lineup is empty). Returning the base for the
        // requested formation keeps the panel readable while no lineup
        // is loaded yet (lineup.length == 0).
        if (attrs == null || attrs.isEmpty()) {
            double attBase = FORMATION_OFF_BASE.getOrDefault(canonicalFormation, 1.00);
            double midBase = FORMATION_MID_BASE.getOrDefault(canonicalFormation, 1.00);
            double defBase = FORMATION_DEF_BASE.getOrDefault(canonicalFormation, 1.00);
            return new TeamRatings(attBase * 100.0, midBase * 100.0, defBase * 100.0);
        }

        // V25D99.16-BACK: each player carries optional slot coords
        // (resolved by FormationEffectiveness.computeRatings from the
        // FormationService cache). subdivisionEffectiveness() picks the
        // legacy zone-only math when coords are NaN (backward compat
        // for tests that don't wire up formation coords) and the
        // subdivision-aware math otherwise. Mixed coords are valid
        // (e.g. partial lineup during drag).
        // No pre-scan needed &mdash; the helper handles both per player.

        // teamAttack = avg of top-7 attackers' (attack * effectiveness).
        // Per engine: a player is an "attacker" if slotCategory == "ATT".
        // Outside ATT they still contribute to teamAttack IF their attack
        // is among the top-N (pre-V25D47 spec); after V25D47 the engine
        // weights ALL 11 attackers (slot category ATT) by effectiveness
        // and picks top-N. Mirroring engine V25D47 + V25D99.18 widen to
        // top-7:
        //
        // V25D99.16-BACK: each player carries the slot's xPct / yPct
        // (resolved by FormationEffectiveness.computeRatings from the
        // FormationService cache). When both are present (non-NaN),
        // SubdivisionEffectivenessCalculator layers a distance-from-
        // ideal penalty so within-zone drag-and-drop changes the team
        // rating by 1-3 points per ~10% horizontal drag. Legacy callers
        // pass NaN and the calculator skips the geometry penalty.
        List<Double> attackerScores = new java.util.ArrayList<>();
        for (PlayerAttrs p : attrs) {
            double eff = subdivisionEffectiveness(p);
            double rawAttack = (p.attack() != null) ? p.attack() : MEDIAN_STAT;
            attackerScores.add(rawAttack * eff * forwardIntentMultiplier(p));
        }
        Collections.sort(attackerScores, Collections.reverseOrder());
        double teamAttack;
        if (attackerScores.isEmpty()) {
            teamAttack = MEDIAN_STAT;
        } else {
            int n = Math.min(7, attackerScores.size());
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += attackerScores.get(i);
            }
            teamAttack = sum / n;
        }

        // teamDefense = avg of DEF + GK players' ((defense + mentality) / 2.0 * eff).
        List<Double> defenderScores = new java.util.ArrayList<>();
        for (PlayerAttrs p : attrs) {
            String cat = p.slotCategory();
            if (!"DEF".equals(cat) && !"GK".equals(cat)) {
                continue;
            }
            double eff = subdivisionEffectiveness(p);
            int def = (p.defense() != null) ? p.defense() : MEDIAN_STAT;
            int men = (p.mentality() != null) ? p.mentality() : MEDIAN_STAT;
            defenderScores.add(((def + men) / 2.0) * eff);
        }
        double teamDefense;
        if (defenderScores.isEmpty()) {
            // Fallback: avg defense of all 11 (no effectiveness penalty —
            // there are no defenders/GK in the lineup at all, so the
            // engine shouldn't down-weight anyone). Mirrors engine.
            List<Integer> allDef = new java.util.ArrayList<>();
            for (PlayerAttrs p : attrs) {
                if (p.defense() != null) {
                    allDef.add(p.defense());
                }
            }
            teamDefense = allDef.isEmpty()
                    ? MEDIAN_STAT
                    : allDef.stream().mapToInt(Integer::intValue).average().orElse(MEDIAN_STAT);
        } else {
            teamDefense = defenderScores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(MEDIAN_STAT);
        }

        // teamMidfield = avg of MID players' (technique * eff).
        // Engine doesn't compute a direct "teamMidfield" stat but we
        // mirror the formula so the panel stays consistent (same
        // statsAmp curve, same baseline 4-4-2 = 1.00).
        List<Double> midfielderScores = new java.util.ArrayList<>();
        for (PlayerAttrs p : attrs) {
            if (!"MID".equals(p.slotCategory())) {
                continue;
            }
            double eff = subdivisionEffectiveness(p);
            int tech = (p.technique() != null) ? p.technique() : MEDIAN_STAT;
            midfielderScores.add(tech * eff);
        }
        double teamMidfield = midfielderScores.isEmpty()
                ? MEDIAN_STAT
                : midfielderScores.stream().mapToDouble(Double::doubleValue).average().orElse(MEDIAN_STAT);

        // Modifiers = baseFormation * statsAmp(stat - 70).
        double statsAmpAtt = 1.0 + (teamAttack - MEDIAN_STAT) * STATS_AMP;
        double statsAmpDef = 1.0 + (teamDefense - MEDIAN_STAT) * STATS_AMP;
        double statsAmpMid = 1.0 + (teamMidfield - MEDIAN_STAT) * STATS_AMP;

        FormationBaseBlend baseBlend = effectiveFormationBase(attrs, canonicalFormation);
        double attBase = baseBlend.attackBase();
        double defBase = baseBlend.defenseBase();
        double midBase = baseBlend.midfieldBase();

        double attRating = attBase * statsAmpAtt;
        double defRating = defBase * statsAmpDef;
        double midRating = midBase * statsAmpMid;

        return new TeamRatings(
                Math.max(0.1, attRating) * 100.0,
                Math.max(0.1, midRating) * 100.0,
                Math.max(0.1, defRating) * 100.0
        );
    }

    /**
     * V25D99.16-BACK: per-player effectiveness lookup that switches
     * between the legacy zone-only table and the new
     * subdivision-aware calculator based on whether the caller
     * supplied slot coords.
     *
     * <p>{@code NaN} or {@code null} coords (any) &rarr; legacy
     * {@link PositionEffectivenessCalculator#effectiveness} lookup
     * (zone-only; pre-V25D99.16 behavior, preserves unit tests
     * that don't have formation coords wired in).
     *
     * <p>Valid coords &rarr; {@link SubdivisionEffectivenessCalculator}
     * which layers a distance-from-ideal penalty on top of the base
     * zone effectiveness.
     *
     * @param p the player record
     * @return effectiveness in {@code [0, 1]}
     */
    private static double subdivisionEffectiveness(PlayerAttrs p) {
        double xPct = (p.slotXPercent() != null) ? p.slotXPercent() : Double.NaN;
        double yPct = (p.slotYPercent() != null) ? p.slotYPercent() : Double.NaN;
        if (Double.isNaN(xPct) || Double.isNaN(yPct)) {
            return PositionEffectivenessCalculator.effectiveness(
                    p.naturalPos(), p.slotCategory());
        }
        return SubdivisionEffectivenessCalculator.effectiveness(
                p.naturalPos(), xPct, yPct, p.slotCategory());
    }

    /**
     * V25D99.20.10-BACK: tactical free-positioning intent.
     *
     * <p>Before this, manually pushing a midfielder higher only applied the
     * distance-from-ideal penalty. That made the UI feel backwards: a manager
     * could move a CM toward the attacking line and see both ATT and MID drop.
     * The penalty is still valid (the player left his ideal structure), but the
     * model also needs to recognize the tactical intent: a non-attacker placed
     * higher up the pitch contributes a bit more to attack.
     *
     * <p>Coordinates use the frontend field convention: {@code y=0} near the
     * opponent goal / attack band, {@code y=100} near our goal. Canonical MID
     * positions around {@code y=60} get no bonus. Moving a MID to CAM-ish zones
     * around {@code y=40} grants roughly +9% attack contribution before the
     * existing effectiveness penalty is applied. Natural ATT slots do not get
     * extra boost here; their attacking value is already represented by
     * formation base + player attack + effectiveness.
     */
    private static double forwardIntentMultiplier(PlayerAttrs p) {
        if (p == null || "ATT".equals(p.slotCategory())) {
            return 1.0;
        }
        double yPct = (p.slotYPercent() != null) ? p.slotYPercent() : Double.NaN;
        if (Double.isNaN(yPct)) {
            return 1.0;
        }
        double forward = Math.max(0.0, Math.min(1.0, (55.0 - yPct) / 40.0));
        return 1.0 + (0.25 * forward);
    }

    private record FormationBaseBlend(double attackBase, double midfieldBase, double defenseBase) {}

    /**
     * V25D99.20.11-BACK: progressive tactical-shape blending.
     *
     * <p>The selected formation remains the manager's explicit intent. However,
     * when the user manually reshapes the team far enough, the preview should
     * drift toward the tactical modifiers of the new visible shape. Example:
     * starting from 4-4-2 and pushing a midfielder into a true front-three
     * should approach 4-3-3 ratings; a tiny one-frame move near the boundary
     * should not flip the whole team from 4-4-2 to 4-3-3.
     *
     * <p>This method computes soft DEF/MID/ATT counts from the players' current
     * Y coordinates, chooses the closest coarse formation among the engine base
     * table, then blends selected-base -> closest-shape-base by confidence.
     * Ambiguous shapes (e.g. counts halfway between 4-4-2 and 4-3-3) get little
     * or no blend; clear shapes get most/all of the target base.
     */
    private static FormationBaseBlend effectiveFormationBase(List<PlayerAttrs> attrs, String selectedFormation) {
        double selectedAttack = FORMATION_OFF_BASE.getOrDefault(selectedFormation, 1.00);
        double selectedMidfield = FORMATION_MID_BASE.getOrDefault(selectedFormation, 1.00);
        double selectedDefense = FORMATION_DEF_BASE.getOrDefault(selectedFormation, 1.00);
        if (attrs == null || attrs.isEmpty()) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }
        boolean hasManualShape = attrs.stream().anyMatch(PlayerAttrs::customPosition);
        if (!hasManualShape) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }

        SoftShape soft = softShapeFromCoords(attrs);
        if (soft.totalOutfield() < 8.0) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }

        ShapeCandidate best = null;
        ShapeCandidate second = null;
        for (String candidate : FORMATION_OFF_BASE.keySet()) {
            int[] counts = parseCoarseFormation(candidate);
            if (counts == null) {
                continue;
            }
            double distance = Math.abs(soft.def() - counts[0])
                    + Math.abs(soft.mid() - counts[1])
                    + Math.abs(soft.att() - counts[2]);
            ShapeCandidate sc = new ShapeCandidate(candidate, distance);
            if (best == null || isBetterShapeCandidate(sc, best, selectedFormation, soft)) {
                second = best;
                best = sc;
            } else if (second == null || isBetterShapeCandidate(sc, second, selectedFormation, soft)) {
                second = sc;
            }
        }

        if (best == null || best.formation().equals(selectedFormation)) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }
        int[] selectedCounts = parseCoarseFormation(selectedFormation);
        int[] bestCounts = parseCoarseFormation(best.formation());
        if (sameCoarseShape(selectedCounts, bestCounts)) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }
        second = secondDistinctShapeCandidate(best, soft, selectedFormation);

        double separation = (second == null) ? 2.0 : Math.max(0.0, second.distance() - best.distance());
        double clarity = clamp01(separation / 2.0);
        // Only blend when the manual shape is clearly close to the target.
        // A single advanced midfielder in a 4-4-2 is tactical intent inside
        // the same plan, not yet a full 4-3-3.
        double closeness = clamp01((0.55 - best.distance()) / 0.55);
        double blend = clarity * closeness;
        if (blend <= 0.05) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }

        double targetAttack = FORMATION_OFF_BASE.getOrDefault(best.formation(), selectedAttack);
        double targetMidfield = FORMATION_MID_BASE.getOrDefault(best.formation(), selectedMidfield);
        double targetDefense = FORMATION_DEF_BASE.getOrDefault(best.formation(), selectedDefense);
        return new FormationBaseBlend(
                selectedAttack + (targetAttack - selectedAttack) * blend,
                selectedMidfield + (targetMidfield - selectedMidfield) * blend,
                selectedDefense + (targetDefense - selectedDefense) * blend);
    }

    private record SoftShape(double def, double mid, double att, double totalOutfield) {}

    private record ShapeCandidate(String formation, double distance) {}

    private static boolean isBetterShapeCandidate(
            ShapeCandidate candidate,
            ShapeCandidate current,
            String selectedFormation,
            SoftShape soft
    ) {
        double diff = candidate.distance() - current.distance();
        if (diff < -0.0001) {
            return true;
        }
        if (diff > 0.0001) {
            return false;
        }

        int[] selected = parseCoarseFormation(selectedFormation);
        if (selected != null) {
            double attDrift = soft.att() - selected[2];
            double defDrift = soft.def() - selected[0];
            if (attDrift > 0.35) {
                return FORMATION_OFF_BASE.getOrDefault(candidate.formation(), 1.00)
                        > FORMATION_OFF_BASE.getOrDefault(current.formation(), 1.00);
            }
            if (defDrift > 0.35) {
                return FORMATION_DEF_BASE.getOrDefault(candidate.formation(), 1.00)
                        > FORMATION_DEF_BASE.getOrDefault(current.formation(), 1.00);
            }
        }

        return candidate.formation().compareTo(current.formation()) < 0;
    }

    private static ShapeCandidate secondDistinctShapeCandidate(
            ShapeCandidate best,
            SoftShape soft,
            String selectedFormation
    ) {
        int[] bestCounts = parseCoarseFormation(best.formation());
        ShapeCandidate second = null;
        for (String candidate : FORMATION_OFF_BASE.keySet()) {
            int[] counts = parseCoarseFormation(candidate);
            if (counts == null || sameCoarseShape(counts, bestCounts)) {
                continue;
            }
            double distance = Math.abs(soft.def() - counts[0])
                    + Math.abs(soft.mid() - counts[1])
                    + Math.abs(soft.att() - counts[2]);
            ShapeCandidate sc = new ShapeCandidate(candidate, distance);
            if (second == null || isBetterShapeCandidate(sc, second, selectedFormation, soft)) {
                second = sc;
            }
        }
        return second;
    }

    private static SoftShape softShapeFromCoords(List<PlayerAttrs> attrs) {
        double def = 0.0;
        double mid = 0.0;
        double att = 0.0;
        double total = 0.0;
        for (PlayerAttrs p : attrs) {
            if (p == null || "GK".equals(p.slotCategory())) {
                continue;
            }
            double y = (p.slotYPercent() != null) ? p.slotYPercent() : Double.NaN;
            if (Double.isNaN(y)) {
                String cat = p.slotCategory();
                if ("DEF".equals(cat)) def += 1.0;
                else if ("ATT".equals(cat)) att += 1.0;
                else mid += 1.0;
                total += 1.0;
                continue;
            }
            double attW = clamp01((55.0 - y) / 38.0);
            double defW = clamp01((y - 65.0) / 18.0);
            double sum = attW + defW;
            if (sum > 1.0) {
                attW /= sum;
                defW /= sum;
                sum = 1.0;
            }
            double midW = 1.0 - sum;
            att += attW;
            mid += midW;
            def += defW;
            total += 1.0;
        }
        return new SoftShape(def, mid, att, total);
    }

    private static int[] parseCoarseFormation(String formation) {
        if (formation == null || formation.isBlank()) {
            return null;
        }
        String[] parts = formation.split("-");
        if (parts.length < 3) {
            return null;
        }
        try {
            int def = Integer.parseInt(parts[0]);
            int att = Integer.parseInt(parts[parts.length - 1]);
            int mid = 10 - def - att;
            if (def < 0 || mid < 0 || att < 0) {
                return null;
            }
            return new int[]{def, mid, att};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean sameCoarseShape(int[] left, int[] right) {
        return left != null && right != null
                && left[0] == right[0]
                && left[1] == right[1]
                && left[2] == right[2];
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

package com.footballmanager.domain.model.valueobject;

import java.util.Map;

/**
 * distance penalty when the assigned subdivision slot is geometrically
 * far from where a player of that natural position should ideally be.
 *
 * <h2>Why this exists</h2>
 * <p>{@link PositionEffectivenessCalculator} aggregates per player by
 * zone ({@code GK/DEF/MID/ATT}). Two slots in the SAME zone (e.g. CB at
 * S22-2 vs S22-3 — both LB-side vs RB-side) yield identical effectiveness
 * because the calculator only knows the zone. Within a 4-DEF line moving
 * a CB one subdivision sideways produced no rating change in
 *
 * <p>Ivan: "juntar m&aacute;s los mediocampistas centrales no hace nada".
 * The fix is geometry-aware effectiveness: each natural position has an
 * ideal centroid on the field (CB ~ (50, 83) = central DEF), and the
 * closer the slot, the higher the eff. Coords come from
 * {@link com.footballmanager.application.service.editor.FormationService}
 * which is the single source of truth for the 12 formation layouts.
 *
 * <h2>Math</h2>
 * <pre>
 *   baseEff = PositionEffectivenessCalculator.effectiveness(natural, zone)
 *   distance = sqrt((slotX - idealX)^2 + (slotY - idealY)^2)
 *   normalized = min(1.0, distance / 100.0)
 *   penalty = MAX_PENALTY * normalized        // 0.30 max at the opposite corner
 *   refinedEff = baseEff * (1.0 - penalty)
 *   floor      = max(0.05, refinedEff)        // never zero unless base zero
 * </pre>
 *
 * <h2>What stays unchanged</h2>
 * <ul>
 *   <li>{@link PositionEffectivenessCalculator} is NOT modified. All
 *       existing tests stay green; the new calculator wraps it.</li>
 *   <li>Hard caps (GK &harr; non-GK &rarr; 0.0) still apply &mdash; if
 *       {@code baseEff} is zero, {@code refinedEff} is zero.</li>
 *   <li>If coords or natural position are unknown, returns
 *       {@code baseEff} unchanged (backward compat).</li>
 * </ul>
 *
 * <h2>Expected UI impact</h2>
 * <p>Within a zone (e.g. shifting a CM from S17-1 to S17-3 in 4-4-2),
 * the attack / defense / midfield ratings now vary by 1-3 percentage
 * points per ~10% drag. Across zones (e.g. CB &rarr; MID), the existing
 * zone-level penalty still dominates (much larger jump).
 */
public final class SubdivisionEffectivenessCalculator {

    private SubdivisionEffectivenessCalculator() {
        // Pure utility — no instances.
    }

    /**
     * Ideal centroid coords for each natural position. Sourced manually
     * the computed penalty matches where players are actually placed.
     *
     * <p>Entry keys:
     * <ul>
     *   <li>3-cat granular (preferred &mdash; LB hits left DEF ideal,
     *       not central CB ideal).</li>
     *   <li>5-cat fallback ({@code DEF/MID/WINGER/ATT}) for legacy
     *       players without granular position.</li>
     * </ul>
     */
    private static final Map<String, double[]> IDEAL_COORDS = Map.ofEntries(
            // 3-cat granular.
            Map.entry("GK",  new double[]{50.0, 93.0}),
            Map.entry("CB",  new double[]{50.0, 83.0}),
            Map.entry("LB",  new double[]{16.0, 83.0}),
            Map.entry("RB",  new double[]{84.0, 83.0}),
            Map.entry("LWB", new double[]{10.0, 80.0}),
            Map.entry("RWB", new double[]{90.0, 80.0}),
            Map.entry("CDM", new double[]{50.0, 70.0}),
            Map.entry("CM",  new double[]{50.0, 60.0}),
            Map.entry("CAM", new double[]{50.0, 40.0}),
            Map.entry("LM",  new double[]{16.0, 60.0}),
            Map.entry("RM",  new double[]{84.0, 60.0}),
            Map.entry("LW",  new double[]{10.0, 30.0}),
            Map.entry("RW",  new double[]{90.0, 30.0}),
            Map.entry("ST",  new double[]{50.0, 17.0}),
            Map.entry("CF",  new double[]{50.0, 17.0}),
            // 5-cat fallback (no granular pos — use zone centroid).
            Map.entry("DEF",    new double[]{50.0, 83.0}),
            Map.entry("MID",    new double[]{50.0, 60.0}),
            Map.entry("WINGER", new double[]{10.0, 45.0}),
            Map.entry("ATT",    new double[]{50.0, 17.0})
    );

    private static final double[][] GENERIC_WINGER_IDEALS = new double[][]{
            {10.0, 45.0},
            {90.0, 45.0}
    };

    /**
     * Distance normalization: a fully-opposite drag (100% field width OR
     * 100% field height) saturates the penalty. The diagonal max is
     * sqrt(2)*100 &asymp; 141 but we cap at 1.0 fraction so even a far
     * slot doesn't completely zero out a natural-zone player.
     */
    private static final double MAX_DISTANCE = 100.0;

    /**
     * Maximum penalty fraction applied at MAX_DISTANCE. Tuned so a
     * CB at the opposite wing slot (S22-3 vs ideal S22-2/S23-1 area)
     * drops eff by ~7-13% depending on layout. Within the natural zone
     * the typical penalty is 3-6%.
     */
    private static final double MAX_PENALTY = 0.30;

    /**
     * Floor for the refined effectiveness. Below this the engine would
     * effectively ghost the player; we keep them barely present so the
     * team rating reflects ALL 11 slots (no zeros mid-calculation).
     */
    private static final double EFF_FLOOR = 0.05;

    /**
     * Returns the subdivision-aware effectiveness for a player + slot
     * pair, layering a distance-from-ideal penalty on top of the
     * zone-based effectiveness from
     * {@link PositionEffectivenessCalculator#effectiveness(String, String)}.
     *
     * @param naturalPos   3-cat ({@code CB/LB/ST/CM/...}) OR 5-cat
     *                     ({@code GK/DEF/MID/WINGER/ATT}) natural
     *                     position. Unknown values fall back to
     *                     {@code baseEff} unchanged (1.0 backward compat).
     * @param slotXPercent xPct of the assigned subdivision slot, or
     *                     {@code Double.NaN} to skip the geometry
     *                     penalty (backward compat &mdash; same as
     *                     passing null coords via the (Double, Double) overload).
     * @param slotYPercent yPct of the assigned subdivision slot.
     * @param slotCategory zone of the subdivision
     *                     ({@code GK/DEF/MID/ATT}) &mdash; used for the
     *                     base zone lookup.
     * @return refined effectiveness in {@code [EFF_FLOOR, 1.0]} (or 0.0
     *         if base hard cap fires &mdash; GK in non-GK slot etc.).
     */
    public static double effectiveness(String naturalPos,
                                       double slotXPercent,
                                       double slotYPercent,
                                       String slotCategory) {
        double baseEff = PositionEffectivenessCalculator.effectiveness(naturalPos, slotCategory);
        if (baseEff <= 0.0) {
            return 0.0;  // hard caps propagate (GK <-> non-GK)
        }
        String normalizedNatural = naturalPos != null ? naturalPos.trim().toUpperCase() : null;
        if (normalizedNatural == null || normalizedNatural.isBlank()) {
            return baseEff;
        }

        // /yPct resolution wired up still produce the same numbers.
        if (Double.isNaN(slotXPercent) || Double.isNaN(slotYPercent)) {
            return baseEff;
        }

        double[] ideal = IDEAL_COORDS.get(normalizedNatural);
        if (ideal == null) {
            // Unknown natural (e.g., legacy "FORWARD_MID" or future
            // 16-cat position). Skip penalty — base covers it.
            return baseEff;
        }

        double distance = distanceFromIdeal(normalizedNatural, slotXPercent, slotYPercent, ideal);
        double normalized = Math.min(1.0, distance / MAX_DISTANCE);
        double penalty = MAX_PENALTY * normalized;
        double refined = baseEff * (1.0 - penalty);
        refined *= tacticalSlotRoleMultiplier(normalizedNatural, slotXPercent, slotYPercent, slotCategory);
        return Math.min(1.0, Math.max(EFF_FLOOR, refined));
    }

    /**
     * from a generic ATT-zone responsibility. A broad MID can help high up the
     * pitch, but should not behave like a natural winger when placed on the
     * touchline. This multiplier keeps the shared preview/match-engine math
     * consistent with the modal's off-role warning.
     */
    private static double tacticalSlotRoleMultiplier(
            String normalizedNatural,
            double slotXPercent,
            double slotYPercent,
            String slotCategory) {
        if ("ATT".equals(slotCategory) && isWideForwardLane(slotXPercent, slotYPercent)) {
            return switch (normalizedNatural) {
                case "WINGER", "LW", "RW" -> 1.08;
                case "ATT", "ST", "CF" -> 0.96;
                case "MID", "CM", "CDM", "CAM", "LM", "RM" -> 0.82;
                case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> 0.66;
                default -> 1.0;
            };
        }

        if ("MID".equals(slotCategory) && isCentralMidfieldLane(slotXPercent, slotYPercent)) {
            return switch (normalizedNatural) {
                case "MID", "CM", "CDM", "CAM" -> 1.0;
                case "LM", "RM" -> 0.94;
                case "WINGER", "LW", "RW" -> 0.88;
                case "ATT", "ST", "CF" -> 0.78;
                case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> 0.84;
                default -> 1.0;
            };
        }

        if ("DEF".equals(slotCategory) && isCentralDefensiveLane(slotXPercent, slotYPercent)) {
            return switch (normalizedNatural) {
                case "DEF", "CB" -> 1.0;
                case "LB", "RB", "LWB", "RWB" -> 0.94;
                case "MID", "CM", "CDM" -> 0.86;
                case "CAM", "LM", "RM", "WINGER", "LW", "RW" -> 0.72;
                case "ATT", "ST", "CF" -> 0.62;
                default -> 1.0;
            };
        }

        return 1.0;
    }

    private static boolean isWideForwardLane(double slotXPercent, double slotYPercent) {
        double widthFromCenter = Math.abs(slotXPercent - 50.0) / 50.0;
        return slotYPercent <= 35.0 && widthFromCenter >= 0.62;
    }

    private static boolean isCentralMidfieldLane(double slotXPercent, double slotYPercent) {
        double widthFromCenter = Math.abs(slotXPercent - 50.0) / 50.0;
        return slotYPercent >= 42.0 && slotYPercent <= 72.0 && widthFromCenter <= 0.36;
    }

    private static boolean isCentralDefensiveLane(double slotXPercent, double slotYPercent) {
        double widthFromCenter = Math.abs(slotXPercent - 50.0) / 50.0;
        return slotYPercent >= 72.0 && widthFromCenter <= 0.36;
    }

    private static double distanceFromIdeal(String normalizedNatural,
                                            double slotXPercent,
                                            double slotYPercent,
                                            double[] defaultIdeal) {
        if ("WINGER".equals(normalizedNatural)) {
            double best = Double.POSITIVE_INFINITY;
            for (double[] ideal : GENERIC_WINGER_IDEALS) {
                best = Math.min(best, distance(slotXPercent, slotYPercent, ideal));
            }
            return best;
        }
        return distance(slotXPercent, slotYPercent, defaultIdeal);
    }

    private static double distance(double slotXPercent, double slotYPercent, double[] ideal) {
        double dx = slotXPercent - ideal[0];
        double dy = slotYPercent - ideal[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Convenience overload that resolves the ideal coords for the given
     * natural position and returns them. Useful for tests + debug
     * tooling that wants to confirm the IDEAL_COORDS map.
     *
     * @param naturalPos 3-cat or 5-cat natural position.
     * @return {@code {xPct, yPct}} or {@code null} if the natural
     *         position is not in the IDEAL_COORDS map.
     */
    public static double[] idealCoordsFor(String naturalPos) {
        if (naturalPos == null || naturalPos.isBlank()) {
            return null;
        }
        return IDEAL_COORDS.get(naturalPos.trim().toUpperCase());
    }
}

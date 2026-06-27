package com.footballmanager.domain.model.valueobject;

/**
 * V25D47 (Sprint C11a): pure utility that returns a {@code 0..1} effectiveness
 * multiplier based on how well a player's natural position matches the
 * category of the subdivision slot they're assigned to.
 *
 * <p>The engine uses this multiplier to weight player stats when aggregating
 * attack / defense ratings. A perfect match (e.g., a CB playing in a DEF
 * slot) contributes their full stat; a mismatch (e.g., a CB placed in a
 * MID slot) contributes only a fraction — the engine still counts them
 * (the slot is occupied), but the engine understands the player isn't
 * operating at full capacity.
 *
 * <h2>Table (5-category simplification)</h2>
 * <p>The task spec proposed a 15-value {@link com.footballmanager.domain.model.entity.Player.Position}
 * table that distinguishes LWB from LB, LW from LM, etc. for granular
 * carrileflex. {@code SessionPlayer} only stores the 5-category
 * {@code String} position ({@code GK/DEF/MID/WINGER/ATT}), so this
 * implementation collapses to the 5-category table below. The
 * {@code WINGER} row absorbs the carrileroflex (LWB/RWB/LM/RM averaged),
 * so a WINGER placed in a MID slot gets 0.95 effectiveness — same as
 * the task spec's carrileflex for LWB in MID. The MID → MID slot is 1.0
 * (perfect). The CB → MID penalty is 0.8 (matches the task spec's 0.7-0.85
 * range, picking the middle so the engine doesn't over-penalize).
 *
 * <pre>
 *   naturalCategory | GK slot | DEF slot | MID slot | ATT slot
 *   ----------------+---------+----------+----------+----------
 *   GK              |  1.0    |  0.0     |  0.0     |  0.0
 *   DEF             |  0.0    |  1.0     |  0.8     |  0.4
 *   MID             |  0.0    |  0.85    |  1.0     |  0.85
 *   WINGER          |  0.0    |  0.8     |  0.95    |  0.9
 *   ATT             |  0.0    |  0.3     |  0.7     |  1.0
 * </pre>
 *
 * <h2>3-cat → 5-cat mapper (V25D51 / Sprint C13)</h2>
 * <p>{@code SessionPlayer.getPosition()} actually returns one of 15 granular
 * 3-category strings (e.g., {@code CB}, {@code LW}, {@code CM}), NOT the
 * 5-category names used in the switch below. Before this mapper existed,
 * {@code effectiveness(naturalPosition, slotCategory)} fell through to the
 * {@code default → 1.0} arm for every real lineup, making
 * {@code perPlayerEffectiveness} useless (every player scored 1.0 regardless
 * of off-position placement). The mapper collapses 3-cat positions into
 * their 5-cat equivalents:
 *
 * <pre>
 *   GK                  → GK
 *   CB, LB, RB,
 *   LWB, RWB            → DEF
 *   CDM, CM, CAM,
 *   LM, RM              → MID
 *   LW, RW              → WINGER
 *   CF, ST              → ATT
 * </pre>
 *
 * <p>Already-5-cat inputs ({@code GK/DEF/MID/WINGER/ATT}) pass through
 * unchanged, preserving the V25D47 test contract.
 *
 * <h2>Backward compat</h2>
 * <p>If {@code naturalPosition} or {@code slotCategory} is null or unrecognized,
 * the calculator returns {@code 1.0} (no penalty). This preserves the
 * pre-C11a behavior for legacy lineups without tactical positions — the
 * engine still aggregates stats as if every player were perfectly
 * positioned.
 *
 * <p>GK can only play in a GK slot ({@code 0.0} elsewhere). A non-GK
 * cannot play in the GK slot ({@code 0.0}). These zeros are NOT the
 * "backward compat" case — they're hard caps that prevent the engine
 * from counting, say, a CB as a goalkeeper.
 */
public final class PositionEffectivenessCalculator {

    private PositionEffectivenessCalculator() {
        // Pure utility — no instances.
    }

    /**
     * Returns the effectiveness multiplier for a player with the given
     * natural position playing in a slot of the given category.
     *
     * <p>Accepts both 5-category names ({@code GK/DEF/MID/WINGER/ATT})
     * and 15-value 3-category names ({@code CB/LB/RB/LWB/RWB/CDM/CM/CAM/
     * LM/RM/LW/RW/CF/ST}). 3-cat inputs are mapped to their 5-cat
     * equivalents (see {@link #toFiveCategory(String)}) before the
     * effectiveness lookup. Unknown / null / blank inputs return
     * {@code 1.0} (backward compat).
     *
     * @param naturalPosition one of the 15 3-cat names (e.g., {@code "CB"},
     *                        {@code "LW"}, {@code "CM"}) or one of the 5
     *                        5-cat names ({@code "GK"}, {@code "DEF"},
     *                        {@code "MID"}, {@code "WINGER"},
     *                        {@code "ATT"}). Other values (or null/blank)
     *                        → returns {@code 1.0} (backward compat).
     * @param slotCategory     one of {@code "GK"}, {@code "DEF"},
     *                        {@code "MID"}, {@code "ATT"}. Other values
     *                        (or null/blank) → returns {@code 1.0}
     *                        (backward compat).
     * @return multiplier in {@code [0.0, 1.0]} used by the engine to
     *         weight the player's stat contribution.
     */
    public static double effectiveness(String naturalPosition, String slotCategory) {
        if (naturalPosition == null || naturalPosition.isBlank()
                || slotCategory == null || slotCategory.isBlank()) {
            return 1.0;
        }

        // V25D51 (Sprint C13): collapse 3-cat position names to their
        // 5-cat equivalents before the switch. 5-cat names pass through
        // unchanged so the V25D47 test contract is preserved.
        String fiveCatNatural = toFiveCategory(naturalPosition);

        return switch (fiveCatNatural) {
            case "GK" -> switch (slotCategory) {
                case "GK" -> 1.0;
                default  -> 0.0;  // GK can only play in GK
            };
            case "DEF" -> switch (slotCategory) {
                case "GK" -> 0.0;
                case "DEF" -> 1.0;
                case "MID" -> 0.8;
                case "ATT" -> 0.4;
                default  -> 1.0;
            };
            case "MID" -> switch (slotCategory) {
                case "GK" -> 0.0;
                case "DEF" -> 0.85;
                case "MID" -> 1.0;
                case "ATT" -> 0.85;
                default  -> 1.0;
            };
            case "WINGER" -> switch (slotCategory) {
                case "GK" -> 0.0;
                case "DEF" -> 0.8;
                case "MID" -> 0.95;  // carrilero flexibility (LWB/RWB averaged)
                case "ATT" -> 0.9;
                default  -> 1.0;
            };
            case "ATT" -> switch (slotCategory) {
                case "GK" -> 0.0;
                case "DEF" -> 0.3;
                case "MID" -> 0.7;
                case "ATT" -> 1.0;
                default  -> 1.0;
            };
            default -> 1.0;  // unknown natural — no penalty (backward compat)
        };
    }

    /**
     * Maps a 3-category position string ({@code SessionPlayer.getPosition()})
     * to its 5-category equivalent used by the effectiveness switch.
     *
     * <p>Mapping table (V25D51 / Sprint C13 spec):
     * <pre>
     *   GK               → GK
     *   CB, LB, RB,
     *   LWB, RWB         → DEF
     *   CDM, CM, CAM,
     *   LM, RM           → MID
     *   LW, RW           → WINGER
     *   CF, ST           → ATT
     * </pre>
     *
     * <p>Already-5-cat inputs ({@code GK/DEF/MID/WINGER/ATT}) pass through
     * unchanged. Unknown inputs (including {@code null} or blank) are
     * returned as-is so the caller's {@code default → 1.0} arm fires and
     * the engine applies no penalty (backward compat).
     *
     * @param threeCatPosition raw position string from {@code SessionPlayer.getPosition()},
     *                         or {@code null} / blank for legacy players
     * @return 5-cat name, or the original input if unknown / null / blank
     */
    static String toFiveCategory(String threeCatPosition) {
        if (threeCatPosition == null) {
            return null;
        }
        return switch (threeCatPosition) {
            // 5-cat pass-through (preserves V25D47 contract).
            case "GK", "DEF", "MID", "WINGER", "ATT" -> threeCatPosition;
            // 3-cat → 5-cat mapping.
            case "CB", "LB", "RB", "LWB", "RWB"     -> "DEF";
            case "CDM", "CM", "CAM", "LM", "RM"     -> "MID";
            case "LW", "RW"                          -> "WINGER";
            case "CF", "ST"                          -> "ATT";
            // Unknown (legacy / future): return as-is so caller's default
            // arm fires and effectiveness() returns 1.0 (no penalty).
            default -> threeCatPosition;
        };
    }
}
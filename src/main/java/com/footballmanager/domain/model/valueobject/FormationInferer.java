package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;

import java.util.List;

/**
 * V25D47 (Sprint C11a): pure utility that infers a formation label from
 * the subdivision slots assigned to a lineup.
 *
 * <p>The 5 canonical categories used by the engine are
 * {@code GK | DEF | MID | WINGER | ATT}, but for <b>formation inference</b>
 * the task spec reduces them to 4 ({@code GK} is excluded from the label
 * because every lineup has exactly 1 GK; {@code WINGER} is folded into
 * {@code ATT} because the engine's formationOffensiveModifier treats them
 * the same — both are scoring zones). So the inferred label looks like
 * {@code "4-4-2"}, {@code "3-5-2"}, {@code "5-3-2"}, {@code "4-3-3"}, etc.
 *
 * <h2>Subdivision → category mapping</h2>
 * <p>The 81 normal subdivisions are arranged in a 9×3 grid (sector row
 * × sector column). Each sector has 3 sub-slots (left/center/right). The
 * zones map to rows as follows (matches {@code FieldSubdivisionService.zoneForRow}):
 *
 * <pre>
 *   row 0-1 (sectors 1-6):   ATTACK    → "ATT"
 *   row 2-5 (sectors 7-18):  MIDFIELD  → "MID"
 *   row 6-8 (sectors 19-27): DEFENSE   → "DEF"
 *   "GK-1" (special):        GK
 * </pre>
 *
 * <p>The field is rendered vertically (top% low = ATT, top% high = DEF),
 * so a 4-4-2 lineup puts 4 players in rows 6-8 (DEF), 4 in rows 2-5 (MID),
 * 2 in rows 0-1 (ATT), and 1 on the GK slot.
 *
 * <h2>Backward compat</h2>
 * <p>Any malformed input (null/empty/short/wrong-shaped slots) returns the
 * default {@code "4-4-2"} formation rather than throwing. The SquadEditorModal
 * may produce transient states during the edit flow where slots are
 * partial — refusing to infer would crash the engine. Graceful degradation
 * keeps callers safe; the engine will still compute xG using the actual
 * persisted formation via {@code V24FormationParser}.
 *
 * <p>Similarly {@code categoryFor} returns {@code null} for unparseable
 * subdivisionIds so callers can decide whether to skip or default.
 */
public final class FormationInferer {

    private FormationInferer() {
        // Pure utility — no instances.
    }

    /** Default formation used when inference fails or inputs are empty. */
    public static final String DEFAULT_FORMATION = "4-4-2";

    /**
     * Infers a formation label (e.g. {@code "4-4-2"}, {@code "3-5-2"}) from
     * the subdivision slots assigned to a lineup.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>If {@code slots} is null or empty → return {@link #DEFAULT_FORMATION}
     *       (backward compat: legacy lineups without subdivisionId still work).</li>
     *   <li>For each slot, classify via {@link #categoryFor} and tally.</li>
     *   <li>Skip slots with unknown categories (defensive: don't count them).</li>
     *   <li>If the tally doesn't include exactly 1 GK and a total of 11 slots
     *       → graceful degradation to {@link #DEFAULT_FORMATION} (covers the
     *       common case of incomplete lineups during edit, missing players,
     *       or unexpected subdivisionIds from a future field revision).</li>
     *   <li>Otherwise build {@code "{DEF}-{MID}-{ATT}"} (GK excluded from label).</li>
     * </ol>
     *
     * @param slots the 11 slots the manager assigned to the lineup, or null/empty
     *              for the "no data yet" state.
     * @return canonical formation label (one of the 7 supported by the engine),
     *         or {@link #DEFAULT_FORMATION} on any malformed input.
     */
    public static String infer(List<LineupSlotDTO> slots) {
        return infer(slots, null);
    }

    /**
     * V25D55 (Sprint C16): overload that prefers the persisted formation when
     * available. The persisted formation is the canonical label the manager
     * actually selected (e.g., {@code "3-5-2-CDM"}, {@code "5-4-1"} — codes
     * beyond the 3-DIGIT shape produced by slot inference). Slot inference
     * alone only yields {@code "X-Y-Z"} triples and cannot disambiguate the
     * 12 formations the front-end exposes.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If {@code persistedFormation} is non-null and non-blank → return
     *       it as-is (the manager's intent wins, even if the slot assignments
     *       would infer a different label; e.g. a 3-5-2-CDM with one CB
     *       temporarily moved to a MID slot still labels as {@code "3-5-2-CDM"}).</li>
     *   <li>Otherwise → fall back to the slot-inference algorithm (same as
     *       {@link #infer(List)}).</li>
     * </ul>
     *
     * @param slots              the 11 slots the manager assigned, or null/empty.
     * @param persistedFormation the canonical formation label persisted in
     *                           {@code CareerSave.teamStarting11Formation}, or
     *                           {@code null}/blank for saves without that map.
     * @return {@code persistedFormation} when set, otherwise the slot-inferred
     *         label (or {@link #DEFAULT_FORMATION} on malformed input).
     */
    public static String infer(List<LineupSlotDTO> slots, String persistedFormation) {
        if (persistedFormation != null && !persistedFormation.isBlank()) {
            return persistedFormation;
        }
        if (slots == null || slots.isEmpty()) {
            return DEFAULT_FORMATION;
        }

        int gk = 0;
        int def = 0;
        int mid = 0;
        int att = 0;
        int total = 0;
        for (LineupSlotDTO slot : slots) {
            if (slot == null || slot.subdivisionId() == null) continue;
            String cat = categoryFor(slot.subdivisionId());
            if (cat == null) continue;  // unknown — skip (don't count)
            switch (cat) {
                case "GK" -> gk++;
                case "DEF" -> def++;
                case "MID" -> mid++;
                case "ATT" -> att++;
                default -> { /* not a recognized category — skip */ }
            }
            total++;
        }

        // Graceful degradation: malformed (no GK, wrong size, empty after filter)
        if (gk != 1 || total != 11) {
            return DEFAULT_FORMATION;
        }
        return def + "-" + mid + "-" + att;
    }

    /**
     * Classifies a {@code subdivisionId} into a formation category.
     *
     * <p>Handles the two shapes produced by {@code FieldSubdivisionService}:
     * <ul>
     *   <li>{@code "GK-1"} → {@code "GK"} (special GK slot, sector 26 visually
     *       but with a separate identifier to avoid colliding with the
     *       normal {@code S26-1} subdivision).</li>
     *   <li>{@code "S##-#"} → sector number parsed from position 1..2, row
     *       derived via {@code (sector - 1) / 3}, category mapped per the
     *       FieldSubdivisionService zone rules.</li>
     * </ul>
     *
     * @param subdivisionId the slot identifier (e.g., {@code "GK-1"},
     *                      {@code "S07-2"}).
     * @return one of {@code "GK"}, {@code "DEF"}, {@code "MID"},
     *         {@code "ATT"}, or {@code null} if the input is null or
     *         doesn't match either expected shape.
     */
    public static String categoryFor(String subdivisionId) {
        if (subdivisionId == null || subdivisionId.isBlank()) {
            return null;
        }
        if ("GK-1".equals(subdivisionId)) {
            return "GK";
        }
        // Expected shape: S##-#  (e.g. "S01-1", "S22-3")
        if (!subdivisionId.startsWith("S") || subdivisionId.length() < 4) {
            return null;
        }
        int dash = subdivisionId.indexOf('-');
        if (dash < 0 || dash > 3) {
            return null;
        }
        int sector;
        try {
            sector = Integer.parseInt(subdivisionId.substring(1, dash));
        } catch (NumberFormatException e) {
            return null;
        }
        if (sector < 1 || sector > 27) {
            return null;
        }
        // Mirror FieldSubdivisionService.zoneForRow:
        //   row 0-1 → ATTACK (top of field, offensive end)
        //   row 2-5 → MIDFIELD
        //   row 6-8 → DEFENSE (bottom of field, defensive end)
        int sectorRow = (sector - 1) / 3;
        if (sectorRow <= 1) return "ATT";
        if (sectorRow <= 5) return "MID";
        return "DEF";
    }
}
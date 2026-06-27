package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V25D47 (Sprint C11a): aggregate record combining
 * {@link FormationInferer#infer} + {@link PositionEffectivenessCalculator#effectiveness}
 * for a full lineup. Mirrors the response DTO
 * {@code FormationEffectivenessDTO} (1:1 field set).
 *
 * <p>Three fields:
 * <ul>
 *   <li>{@code inferredFormation} — canonical label produced by
 *       {@link FormationInferer#infer(List)} from the lineup's subdivision
 *       slots (e.g., {@code "4-4-2"}, {@code "3-5-2"}, {@code "5-3-2"}).</li>
 *   <li>{@code perPlayerEffectiveness} — map of {@code subdivisionId →
 *       multiplier [0, 1]} where the multiplier is
 *       {@code effectiveness(naturalPosition, slotCategory)} for the player
 *       occupying that subdivision. Players at a perfect-match slot get
 *       {@code 1.0}; mismatches get a reduced multiplier (e.g., a CB in a
 *       MID slot → 0.8). Keyed by subdivisionId so the frontend can
 *       correlate a slot with its effectiveness score without joining
 *       against playerId.</li>
 *   <li>{@code teamAverage} — arithmetic mean of {@code perPlayerEffectiveness}
 *       values. Informational: a quick indicator of "how well does this
 *       lineup fit its formation?". A perfect 4-4-2 with all-natural
 *       positions → 1.0; an experimental 3-5-2 with CB in MID → ~0.85.</li>
 * </ul>
 *
 * <h2>Backward compat</h2>
 * <p>If slots are null/empty/malformed, {@code inferredFormation} falls
 * back to {@code FormationInferer.DEFAULT_FORMATION} ({@code "4-4-2"}) and
 * {@code perPlayerEffectiveness} is empty (all-natural positions default
 * to 1.0 per the {@link PositionEffectivenessCalculator} backward-compat
 * rule when {@code slotCategory} is unknown).
 *
 * <h2>Why this is a record, not a class</h2>
 * <p>Same rationale as {@code ChemistryDetail} (C8) — the data is
 * value-like (immutable snapshot of a computation), and the engine /
 * DTO mapping code reads better when the shape is explicit. Use the
 * static factory {@link #from(List, Map)} or the convenience
 * {@link #empty()} for construction.
 */
public record FormationEffectiveness(
    String inferredFormation,
    Map<String, Double> perPlayerEffectiveness,
    double teamAverage
) {

    /**
     * Static factory: compute the aggregate from the lineup's slots and
     * the playerId → naturalPosition map (typically derived from the
     * 11 SessionPlayers in the lineup).
     *
     * <p>If {@code slots} is null/empty, the inferredFormation defaults to
     * {@code "4-4-2"} and perPlayerEffectiveness is empty (the engine
     * still gets the 4-4-2 default, no penalties applied).
     *
     * <p><b>V25D52 (Sprint C13b):</b> {@code perPlayerEffectiveness} is
     * keyed by {@code subdivisionId} (NOT playerId) — the frontend's
     * {@code FormationEffectivenessDTO} spec correlates each slot with its
     * effectiveness score directly, without joining against playerId.
     * Prior to this fix the map was keyed by playerId, which produced a
     * silent contract mismatch: the frontend looked up by subdivisionId,
     * always got {@code undefined}, and no CSS class / badge was applied.
     *
     * @param slots           the 11 subdivision slots the manager assigned
     *                       (may be null/empty for legacy lineups).
     * @param naturalByPlayer playerId → natural 5-cat position
     *                       ({@code "GK"/"DEF"/"MID"/"WINGER"/"ATT"}).
     *                       Players not in this map are skipped (their
     *                       multiplier defaults to 1.0 if they appear in
     *                       slots but lack naturalPosition).
     * @return populated {@code FormationEffectiveness}.
     */
    public static FormationEffectiveness from(
            List<LineupSlotDTO> slots,
            Map<String, String> naturalByPlayer) {
        return from(slots, naturalByPlayer, null);
    }

    /**
     * V25D55 (Sprint C16): overload that forwards the persisted formation to
     * {@link FormationInferer#infer(List, String)} so the resulting
     * {@code inferredFormation} field matches the label the manager actually
     * selected (e.g. {@code "3-5-2-CDM"}, {@code "5-4-1"}). Without this, the
     * slot-inference algorithm collapses every multi-role formation into the
     * {@code "X-Y-Z"} triple, and the front-end reports a different label
     * than the one shown in the formation modal.
     *
     * @param slots              the 11 subdivision slots the manager assigned
     *                           (may be null/empty for legacy lineups).
     * @param naturalByPlayer    playerId → natural 5-cat position
     *                           ({@code "GK"/"DEF"/"MID"/"WINGER"/"ATT"}).
     * @param persistedFormation canonical formation label from
     *                           {@code CareerSave.teamStarting11Formation}, or
     *                           {@code null} for legacy lineups.
     * @return populated {@code FormationEffectiveness}.
     */
    public static FormationEffectiveness from(
            List<LineupSlotDTO> slots,
            Map<String, String> naturalByPlayer,
            String persistedFormation) {

        String inferred = FormationInferer.infer(slots, persistedFormation);

        Map<String, Double> perPlayer = new LinkedHashMap<>();
        if (slots != null) {
            // Per-player effectiveness, even when naturalByPlayer is null
            // (calculator returns 1.0 for unknown natural — backward compat).
            // Without this loop, an empty/null naturalByPlayer would yield
            // an empty perPlayer map, hiding the lineup's actual composition.
            //
            // V25D52 (Sprint C13b): key by subdivisionId, not playerId — the
            // frontend correlates a slot with its effectiveness score
            // directly (see FormationEffectivenessDTO wire shape).
            Map<String, String> safeNatural = (naturalByPlayer != null) ? naturalByPlayer : Map.of();
            for (LineupSlotDTO slot : slots) {
                if (slot == null) continue;
                if (slot.playerId() == null || slot.subdivisionId() == null) continue;
                String natural = safeNatural.get(slot.playerId());
                String slotCat = FormationInferer.categoryFor(slot.subdivisionId());
                double eff = PositionEffectivenessCalculator.effectiveness(natural, slotCat);
                perPlayer.put(slot.subdivisionId(), eff);
            }
        }

        double avg = perPlayer.isEmpty()
                ? 1.0
                : perPlayer.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);

        return new FormationEffectiveness(inferred, perPlayer, avg);
    }

    /**
     * Backward-compat empty instance: inferredFormation = default, no
     * per-player data, teamAverage = 1.0. Returned by build sites when
     * slots are null/malformed (graceful degradation).
     */
    public static FormationEffectiveness empty() {
        return new FormationEffectiveness(
                FormationInferer.DEFAULT_FORMATION,
                Map.of(),
                1.0);
    }
}
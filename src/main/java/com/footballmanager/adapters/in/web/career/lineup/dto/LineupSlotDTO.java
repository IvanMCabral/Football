package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * Slot persistido en Lineup: player + subdivision (slot) del campo.
 *
 * <p>Nueva shape introducida por MVP1-lineup-cancha-1.
 * Antes solo se persistía {@code List<String>} de player IDs.
 *
 * <p>Si {@code subdivisionId} es null/blank, el back infiere el slot del
 * role del jugador según la formación (backward compat con lineups viejos).
 *
 * the user drags a marker onto the field (not inside any canonical slot),
 * the front records the drop pixel as a percentage on both axes and sends
 * those values through the lineup save / preview pipeline. The preview
 * calculator ({@code FormationEffectiveness.from}) uses them instead of
 * the slot's canonical coords so the
 * {@code SubdivisionEffectivenessCalculator} distance-from-ideal penalty
 * applies to the actual drop point, not the canonical slot center.
 *
 * callers (saves, tests, the /current endpoint before any custom drag).
 * When {@code null}, the canonical coords from
 * {@code FormationService.getCoordsByFormation(formation)} are used.
 */
public record LineupSlotDTO(
    String playerId,
    String subdivisionId,
    Double customXPercent,
    Double customYPercent
) {
    /**
     * that only know about playerId + subdivisionId (persistence layer,
     * legacy tests, internal queries that don't care about overrides).
     * Delegates to the canonical 4-arg constructor with both custom
     * coords set to {@code null} — the calculator then falls back to
     */
    public LineupSlotDTO(String playerId, String subdivisionId) {
        this(playerId, subdivisionId, null, null);
    }
}

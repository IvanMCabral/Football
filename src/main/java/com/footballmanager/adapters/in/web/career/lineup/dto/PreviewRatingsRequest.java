package com.footballmanager.adapters.in.web.career.lineup.dto;

import java.util.List;

/**
 *
 * <p>Body shape:
 * <pre>
 *   {
 *     "formation": "4-4-2",
 *     "slots": [
 *       { "playerId": "sp-1", "subdivisionId": "GK-1" },
 *       { "playerId": "sp-2", "subdivisionId": "S22-1" },
 *       ...
 *     ]
 *   }
 * </pre>
 *
 * <p>The endpoint is read-only: it does NOT persist anything. It's used by
 * the frontend Team Stats panel to refresh the per-zone ratings in
 * real-time as the user drags players around (debounced ~150ms on the
 * front side). The backend uses the same
 * {@link com.footballmanager.domain.model.valueobject.TeamRatingsCalculator}
 * the live simulation engine uses, so the preview matches what the
 * engine will compute on the next match.
 *
 * <p>{@code slots} may be empty / partial — the calculator returns the
 * formation baseline (100/100/100) when no lineup is provided yet.
 */
public record PreviewRatingsRequest(
        String formation,
        List<LineupSlotDTO> slots
) {
}

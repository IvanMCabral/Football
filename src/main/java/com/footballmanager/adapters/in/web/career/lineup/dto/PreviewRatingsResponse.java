package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * V25D99.15-BACK: response body for {@code POST /career/lineup/preview-ratings}.
 *
 * <p>Lightweight response: just the three per-zone ratings, multiplied by
 * 100 for percentage readability. The frontend already has the rest of
 * the lineup state via {@code /career/lineup/current} — this endpoint
 * is meant to be polled cheaply on every drag-drop to refresh only the
 * rating chips/bars in the Team Stats panel.
 *
 * <p>Wire shape:
 * <pre>
 *   { "attackRating": 142.0, "midfieldRating": 105.0, "defenseRating": 95.0 }
 * </pre>
 *
 * <p>Values are in {@code [0, ~200]}; 100 = 4-4-2 baseline at median
 * stats. Higher attack = more dangerous, higher defense = more
 * protection. See {@link com.footballmanager.domain.model.valueobject
 * .TeamRatingsCalculator} for the formulas.
 */
public record PreviewRatingsResponse(
        double attackRating,
        double midfieldRating,
        double defenseRating
) {
}
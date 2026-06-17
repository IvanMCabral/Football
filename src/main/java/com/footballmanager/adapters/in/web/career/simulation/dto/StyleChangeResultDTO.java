package com.footballmanager.adapters.in.web.career.simulation.dto;

import com.footballmanager.application.service.domain.TeamStyle;

/**
 * LIVE-MATCH-F2-LIVE F5 (B4): response body for
 * {@code POST /api/v1/match-engine/matches/{matchId}/style}.
 *
 * <p>Mirrors the F1 {@code SubstitutionResultDTO} contract: {@code success}
 * duplicates the HTTP status (200) and the {@code error} field is set on
 * failure (400/409). On success, {@code currentStyle} reflects the now-active
 * style and {@code minuteApplied} is the live session's authoritative minute.
 */
public record StyleChangeResultDTO(
    boolean success,
    int minuteApplied,
    TeamStyle currentStyle,
    String error
) {

    public static StyleChangeResultDTO ok(int minuteApplied, TeamStyle currentStyle) {
        return new StyleChangeResultDTO(true, minuteApplied, currentStyle, null);
    }

    public static StyleChangeResultDTO error(String errorMessage) {
        return new StyleChangeResultDTO(false, 0, null, errorMessage);
    }
}

package com.footballmanager.adapters.in.web.career.simulation.dto;

import java.util.List;

/**
 * LIVE-MATCH-F2-LIVE F5 (B4): response body for
 * {@code POST /api/v1/match-engine/matches/{matchId}/formation}.
 *
 * <p>Mirrors {@link StyleChangeResultDTO}: {@code success} duplicates the
 * HTTP status (200), {@code currentFormation} is the now-active formation
 * (echoed back from the engine), and {@code error} is set on failure
 * (400/409). {@code minuteApplied} is the live session's authoritative
 * minute at the time the change was applied.
 */
public record FormationChangeResultDTO(
    boolean success,
    int minuteApplied,
    List<FormationSlotDTO> currentFormation,
    String error
) {

    public static FormationChangeResultDTO ok(int minuteApplied, List<FormationSlotDTO> currentFormation) {
        return new FormationChangeResultDTO(true, minuteApplied, currentFormation, null);
    }

    public static FormationChangeResultDTO error(String errorMessage) {
        return new FormationChangeResultDTO(false, 0, null, errorMessage);
    }
}

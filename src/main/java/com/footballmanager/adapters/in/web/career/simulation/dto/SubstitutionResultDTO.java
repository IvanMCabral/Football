package com.footballmanager.adapters.in.web.career.simulation.dto;

/**
 *
 * <p>Returned alongside a 200 OK status. The {@code success} field mirrors
 * the HTTP status (the use case throws on failure and the controller maps to
 * 4xx); we keep it in the body for symmetry with future Phase 2 endpoints
 * that may return partial success.
 */
public record SubstitutionResultDTO(
    boolean success,
    int minuteApplied,
    int substitutionsRemaining,
    String error
) {

    public static SubstitutionResultDTO ok(int minuteApplied, int substitutionsRemaining) {
        return new SubstitutionResultDTO(true, minuteApplied, substitutionsRemaining, null);
    }

    public static SubstitutionResultDTO error(String errorMessage) {
        return new SubstitutionResultDTO(false, 0, 0, errorMessage);
    }
}

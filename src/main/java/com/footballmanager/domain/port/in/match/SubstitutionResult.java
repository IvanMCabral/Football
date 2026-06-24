package com.footballmanager.domain.port.in.match;

/**
 * LIVE-MATCH-F1-POC: result of a manual substitution operation, returned by
 * {@link SubstitutionCommandUseCase#executeSubstitution}.
 *
 * <p>Phase 1 POC design choice (FLAG 1 UX fix): instead of throwing exceptions
 * for business-rule validation failures (player already subbed, max subs reached,
 * player not found, etc.) the use case catches those internally and returns a
 * {@link SubstitutionResult} with {@code success=false} and a descriptive
 * {@code error} message. This keeps the controller's contract flat (always 200
 * OK on success, 200 OK with failure body on validation, 4xx/5xx only for
 * request-shape errors and infrastructure failures) and gives the frontend a
 * uniform shape for snackbar feedback.
 *
 * <p>The HTTP status mapping is:
 * <ul>
 *   <li>{@code success=true} → {@code minuteApplied} and
 *       {@code substitutionsRemaining} populated, {@code error == null}.</li>
 *   <li>{@code success=false} → {@code error} populated with the validation
 *       message, {@code minuteApplied == 0}, {@code substitutionsRemaining == 0}.</li>
 * </ul>
 *
 * <p>Note: per D1=B, {@code substitutionsRemaining} is the engine counter for
 * the team that performed the substitution (max 5 minus used). It is NOT
 * recomputed from accumulated events.
 */
public record SubstitutionResult(
    boolean success,
    int minuteApplied,
    int substitutionsRemaining,
    String error
) {

    /**
     * Success factory.
     *
     * @param minuteApplied          the match minute the substitution was applied at
     * @param substitutionsRemaining the team's remaining substitution budget
     *                               (always in [0, 5] for the default engine)
     */
    public static SubstitutionResult ok(int minuteApplied, int substitutionsRemaining) {
        return new SubstitutionResult(true, minuteApplied, substitutionsRemaining, null);
    }

    /**
     * Failure factory. Use for validation failures caught inside the use case
     * (player not found, max subs reached, player already subbed, etc.).
     *
     * @param errorMessage human-readable error message; may be {@code null} for
     *                     unexpected failures but should be non-null in practice
     */
    public static SubstitutionResult failure(String errorMessage) {
        return new SubstitutionResult(false, 0, 0, errorMessage);
    }
}

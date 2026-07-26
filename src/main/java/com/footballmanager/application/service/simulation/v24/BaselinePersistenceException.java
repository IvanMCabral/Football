package com.footballmanager.application.service.simulation.v24;

/**
 * {@link BaselineState} to Redis fails after all retries.
 *
 * <p>This exception is <b>NOT swallowed</b> by the storage adapter.
 * Call sites ({@code RoundController.buildV24LiveSession} and
 * {@code SubstitutionCommandUseCaseImpl.execute}) wrap the call in
 * {@code onErrorResume(...)} that logs a warn and lets the live match
 * continue (the compare endpoint will simply 404 for that match).
 *
 * {@code CompletableFuture.runAsync(...).get(5s)} in the adapter, which
 * is what caused {@code GET /careers/{id}/matches/{id}/compare} to return
 * 404 even though the match had completed normally (baseline was never
 * persisted).
 */
public class BaselinePersistenceException extends RuntimeException {

    private final String careerId;
    private final String matchId;

    public BaselinePersistenceException(String careerId, String matchId, Throwable cause) {
        super("Failed to persist baseline for careerId=" + careerId
                + ", matchId=" + matchId, cause);
        this.careerId = careerId;
        this.matchId = matchId;
    }

    public String getCareerId() {
        return careerId;
    }

    public String getMatchId() {
        return matchId;
    }
}

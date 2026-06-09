package com.footballmanager.domain.model.entity;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;

import java.util.Objects;

/**
 * V24D6M11 Phase 3B: Result DTO passed to the finish callback.
 *
 * <p>Carries both the SSE-compatible MatchStateSnapshot and, when V24LiveSession
 * is active, the V24DetailedMatchResult containing the full timeline with real
 * player attribution for persistence.
 *
 * <p>v24Result is null when the match used the legacy MatchTickHandler path.
 *
 * @param snapshot  the MatchStateSnapshot (used for SSE and legacy persistence)
 * @param v24Result the V24 detailed result with player-attributed timeline (null for legacy)
 */
public final class MatchFinishedResult {

    private final MatchStateSnapshot snapshot;
    private final V24DetailedMatchResult v24Result;

    public MatchFinishedResult(MatchStateSnapshot snapshot, V24DetailedMatchResult v24Result) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        this.v24Result = v24Result;
    }

    /**
     * The MatchStateSnapshot — used for SSE stream and as fallback for persistence
     * when v24Result is null.
     */
    public MatchStateSnapshot snapshot() {
        return snapshot;
    }

    /**
     * The V24DetailedMatchResult with full player-attributed timeline.
     * Null when the match used the legacy MatchTickHandler path.
     */
    public V24DetailedMatchResult v24Result() {
        return v24Result;
    }

    /**
     * Convenience: true when this result came from a V24LiveSession.
     */
    public boolean isV24() {
        return v24Result != null;
    }
}
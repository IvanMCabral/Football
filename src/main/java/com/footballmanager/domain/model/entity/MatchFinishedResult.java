package com.footballmanager.domain.model.entity;

import java.util.Objects;

/**
 *
 * <p>Carries the SSE-compatible MatchStateSnapshot plus an optional detailed
 * simulation payload owned by the application layer.
 *
 * <p>detailedResult is null when the match used the legacy MatchTickHandler path.
 *
 * @param snapshot       the MatchStateSnapshot used for SSE and legacy persistence
 * @param detailedResult optional application-owned detailed result payload
 */
public final class MatchFinishedResult {

    private final MatchStateSnapshot snapshot;
    private final Object detailedResult;

    public MatchFinishedResult(MatchStateSnapshot snapshot, Object detailedResult) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        this.detailedResult = detailedResult;
    }

    /**
     * The MatchStateSnapshot — used for SSE stream and as fallback for persistence
     * when detailedResult is null.
     */
    public MatchStateSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Null when the match used the legacy MatchTickHandler path.
     */
    public Object detailedResult() {
        return detailedResult;
    }

    /**
     * Convenience: true when this result came from a LiveSession.
     */
    public boolean isDetailedMatch() {
        return detailedResult != null;
    }
}

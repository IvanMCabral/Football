package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Record representing a single
 * applied manual substitution in a V24 match, stored as part of the
 * {@link BaselineState} so the {@code MatchComparisonService} can replay the
 * match from minute 0 with the same sequence of subs the manager actually
 * applied (to compare against the "what if I did nothing" baseline).
 *
 * <p>Mirrors the data carried by {@link V24MatchContext.ScheduledSub} but
 * is stored externally so the match baseline can survive a Redis flush or
 * a CareerSave mutation between the original match and the compare call.
 *
 * <p>schemaVersion: 1 — bumping this requires a migration path.
 */
public record AppliedSubstitution(
        @JsonProperty("teamId") String teamId,
        @JsonProperty("playerOffId") String playerOffId,
        @JsonProperty("playerOnId") String playerOnId,
        @JsonProperty("minute") int minute
) {
    @JsonCreator
    public AppliedSubstitution {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (playerOffId == null || playerOffId.isBlank()) {
            throw new IllegalArgumentException("playerOffId must not be blank");
        }
        if (playerOnId == null || playerOnId.isBlank()) {
            throw new IllegalArgumentException("playerOnId must not be blank");
        }
        if (playerOffId.equals(playerOnId)) {
            throw new IllegalArgumentException(
                    "playerOffId and playerOnId must be different (got '" + playerOffId + "')");
        }
        if (minute < 0 || minute > 90) {
            throw new IllegalArgumentException(
                    "minute must be in [0, 90], got " + minute);
        }
    }

    /**
     * Convenience factory that mirrors the signature of
     * {@link V24MatchContext#withManualSubstitution} so the
     * {@code MatchComparisonService} can apply the sub to a fresh context
     * with one line.
     */
    public V24MatchContext.ScheduledSub toScheduledSub() {
        return new V24MatchContext.ScheduledSub(teamId, playerOffId, playerOnId, minute);
    }
}

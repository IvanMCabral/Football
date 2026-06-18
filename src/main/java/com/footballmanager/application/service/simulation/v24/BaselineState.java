package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Immutable snapshot of the
 * "pre-subs" state of a V24 match, captured at match start so the
 * {@code MatchComparisonService} can reconstruct a "what would have
 * happened if the manager made no substitutions" baseline by replaying the
 * engine with {@code initialContext + subs[]} on demand.
 *
 * <p>Stored in Redis as JSON snapshot at key:
 * {@code career:{careerId}:match-baseline:{matchId}} with TTL 7 days.
 *
 * <p>Why a separate DTO instead of reusing the live-match
 * {@code V24DetailedMatchData}:
 * <ul>
 *   <li>Different key namespace (match-baseline vs match-detail).</li>
 *   <li>Different TTL (7d vs no TTL).</li>
 *   <li>Different shape: BaselineState carries the initial context (pre-subs)
 *       plus the ordered list of subs, NOT a finalized result.</li>
 *   <li>Lifecycle: BaselineState is created at match start and deleted on
 *       match finish; V24DetailedMatchData is created on match finish.</li>
 * </ul>
 *
 * <p>schemaVersion: 1 — bumping requires a migration path.
 * engineVersion: "V24" — identifies the engine that produced this baseline.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class BaselineState {

    private final String careerId;
    private final String matchId;
    private final long seed;
    private final V24MatchContext initialContext;
    private final List<AppliedSubstitution> subs;
    private final String engineVersion;
    private final int schemaVersion;
    private final Instant createdAt;

    @JsonCreator
    public BaselineState(
            @JsonProperty("careerId") String careerId,
            @JsonProperty("matchId") String matchId,
            @JsonProperty("seed") long seed,
            @JsonProperty("initialContext") V24MatchContext initialContext,
            @JsonProperty("subs") List<AppliedSubstitution> subs,
            @JsonProperty("engineVersion") String engineVersion,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("createdAt") Instant createdAt) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        Objects.requireNonNull(initialContext, "initialContext must not be null");
        // F2 contract: the initial context and its lineup must match
        if (!matchId.equals(initialContext.matchId())) {
            throw new IllegalArgumentException(
                    "matchId '" + matchId + "' does not match initialContext.matchId '"
                            + initialContext.matchId() + "'");
        }
        this.careerId = careerId;
        this.matchId = matchId;
        this.seed = seed;
        this.initialContext = initialContext;
        this.subs = (subs != null)
                ? Collections.unmodifiableList(new ArrayList<>(subs))
                : Collections.emptyList();
        this.engineVersion = (engineVersion != null) ? engineVersion : "V24";
        this.schemaVersion = schemaVersion;
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
    }

    /**
     * Factory for the very first snapshot of a match (no subs applied yet).
     * The {@code subs} list is initialized empty; the
     * {@code SubstitutionCommandUseCaseImpl} hook will append to it on
     * every {@code recordManualSubstitution} call.
     */
    public static BaselineState empty(String careerId, long seed, V24MatchContext initialContext) {
        Objects.requireNonNull(initialContext, "initialContext must not be null");
        return new BaselineState(
                careerId,
                initialContext.matchId(),
                seed,
                initialContext,
                Collections.emptyList(),
                "V24",
                1,
                Instant.now());
    }

    /**
     * Returns a new {@code BaselineState} with the given sub appended to the
     * list. The original instance is immutable; the engine consumes the
     * appended sub via {@code V24MatchContext.withManualSubstitution} on
     * replay.
     */
    public BaselineState withAppendedSub(AppliedSubstitution sub) {
        Objects.requireNonNull(sub, "sub must not be null");
        // F2 contract: validate that the sub is applicable to the initial
        // context (playerOff must be in starting XI, playerOn must be on bench).
        // The engine will re-validate at replay time; this is an early
        // sanity check to fail fast at write time.
        initialContext.withManualSubstitution(
                sub.teamId(), sub.playerOffId(), sub.playerOnId(), sub.minute());
        List<AppliedSubstitution> next = new ArrayList<>(subs.size() + 1);
        next.addAll(subs);
        next.add(sub);
        return new BaselineState(
                careerId, matchId, seed, initialContext, next,
                engineVersion, schemaVersion, createdAt);
    }

    // Getters
    @JsonProperty("careerId") public String careerId() { return careerId; }
    @JsonProperty("matchId") public String matchId() { return matchId; }
    @JsonProperty("seed") public long seed() { return seed; }
    @JsonProperty("initialContext") public V24MatchContext initialContext() { return initialContext; }
    @JsonProperty("subs") public List<AppliedSubstitution> subs() { return subs; }
    @JsonProperty("engineVersion") public String engineVersion() { return engineVersion; }
    @JsonProperty("schemaVersion") public int schemaVersion() { return schemaVersion; }
    @JsonProperty("createdAt") public Instant createdAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaselineState that)) return false;
        return seed == that.seed
                && schemaVersion == that.schemaVersion
                && Objects.equals(careerId, that.careerId)
                && Objects.equals(matchId, that.matchId)
                && Objects.equals(initialContext, that.initialContext)
                && Objects.equals(subs, that.subs)
                && Objects.equals(engineVersion, that.engineVersion)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(careerId, matchId, seed, initialContext, subs,
                engineVersion, schemaVersion, createdAt);
    }

    @Override
    public String toString() {
        return "BaselineState{careerId=%s, matchId=%s, seed=%d, subs=%d, schemaVersion=%d}"
                .formatted(careerId, matchId, seed, subs.size(), schemaVersion);
    }
}

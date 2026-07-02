package com.footballmanager.domain.model.entity;

import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MatchStateSnapshot - Snapshot INMUTABLE del estado del partido.
 *
 * Thread-safe por diseño: una vez creado, nunca se modifica.
 * Para cambios, usar los métodos with*() que retornan nuevas instancias.
 *
 * <p>LIVE-MATCH-F3-UI-LIVE BE1: extended with 6 new fields so the F3 UI can
 * render the live possession bar and the current style/formation per team
 * in real time:
 * <ul>
 *   <li>{@code homePossession} / {@code awayPossession} — ints 0-100 read
 *       from {@code V24LiveSnapshot}.</li>
 *   <li>{@code homeStyle} / {@code awayStyle} — String (e.g. "ATTACKING").</li>
 *   <li>{@code homeFormation} / {@code awayFormation} — String (e.g. "4-4-2").</li>
 * </ul>
 *
 * <p>V25D79: extended with 3 new fields so the F4 substitution modal can show
 * per-player live stats and the substitutions counter without an additional
 * round-trip:
 * <ul>
 *   <li>{@code homePlayerRatings} / {@code awayPlayerRatings} — list of
 *       {@link V24PlayerMatchRatingDto}, one per player in the team (starter
 *       + bench). Computed by
 *       {@code V24PlayerMatchStatsModel.computeRatings()} in
 *       {@code MatchSession.adaptV24Snapshot()}. Defaults to empty list.</li>
 *   <li>{@code substitutionsRemaining} — integer in [0, 5], the number of
 *       substitutions the manager team can still make. Computed as
 *       {@code max(0, 5 - count(SUBSTITUTION events))}. Source of truth (D5).
 *       Default 5.</li>
 * </ul>
 *
 * <p>The canonical constructor now takes 18 args. Backward-compatibility
 * constructors (1-arg for tests + 9-arg for the pre-F3 shape) are provided so
 * all existing tests and call sites keep working — the missing fields default
 * to {@code 50} (possession) and {@code "BALANCED"} / {@code "4-4-2"}
 * (style/formation) and {@code empty list} / {@code 5} (V25D79 fields).
 */
public record MatchStateSnapshot(
    UUID matchId,
    UUID homeTeamId,
    UUID awayTeamId,
    int currentMinute,
    MatchStatus status,
    Score score,
    List<MatchEvent> events,
    String careerId,
    String userId,
    // LIVE-MATCH-F3-UI-LIVE BE1 — added 6 fields
    int homePossession,
    int awayPossession,
    String homeStyle,
    String awayStyle,
    String homeFormation,
    String awayFormation,
    // V25D79 — added 3 fields (mod statistics + substitutions)
    List<V24PlayerMatchRatingDto> homePlayerRatings,
    List<V24PlayerMatchRatingDto> awayPlayerRatings,
    int substitutionsRemaining
) implements Serializable {

    /**
     * Constructor con valores por defecto para compatibilidad.
     */
    public MatchStateSnapshot(UUID matchId) {
        this(
            matchId,
            null,
            null,
            0,
            MatchStatus.PAUSED,
            new Score(),
            new ArrayList<>(),
            null,
            null,
            50,
            50,
            "BALANCED",
            "BALANCED",
            "4-4-2",
            "4-4-2",
            List.of(),
            List.of(),
            5
        );
    }

    /**
     * LIVE-MATCH-F3-UI-LIVE BE1: backward-compatibility constructor for the
     * pre-F3 9-arg shape. Defaults the new BE1 fields (possession / style /
     * formation) and V25D79 fields (player ratings / substitutions remaining)
     * to safe values so existing tests/call sites keep passing.
     *
     * <p>V25D79 defaults:
     * <ul>
     *   <li>{@code homePlayerRatings} = {@code List.of()}</li>
     *   <li>{@code awayPlayerRatings} = {@code List.of()}</li>
     *   <li>{@code substitutionsRemaining} = {@code 5} (full quota)</li>
     * </ul>
     */
    public MatchStateSnapshot(
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            int currentMinute,
            MatchStatus status,
            Score score,
            List<MatchEvent> events,
            String careerId,
            String userId) {
        this(
            matchId, homeTeamId, awayTeamId, currentMinute, status, score,
            events, careerId, userId,
            50, 50, "BALANCED", "BALANCED", "4-4-2", "4-4-2",
            List.of(), List.of(), 5
        );
    }

    /**
     * Builder para construcción más legible.
     */
    public static Builder builder() {
        return new Builder();
    }

    public MatchStateSnapshot withEvent(MatchEvent event) {
        List<MatchEvent> newEvents = new ArrayList<>(events);
        newEvents.add(event);
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, score, newEvents, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation,
            homePlayerRatings, awayPlayerRatings, substitutionsRemaining
        );
    }

    public MatchStateSnapshot withMinute(int minute) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            minute, status, score, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation,
            homePlayerRatings, awayPlayerRatings, substitutionsRemaining
        );
    }

    public MatchStateSnapshot withStatus(MatchStatus newStatus) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, newStatus, score, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation,
            homePlayerRatings, awayPlayerRatings, substitutionsRemaining
        );
    }

    public MatchStateSnapshot withScore(Score newScore) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, newScore, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation,
            homePlayerRatings, awayPlayerRatings, substitutionsRemaining
        );
    }

    public MatchStateSnapshot withEvents(List<MatchEvent> newEvents) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, score, newEvents, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation,
            homePlayerRatings, awayPlayerRatings, substitutionsRemaining
        );
    }

    /**
     * V25D79: copy this snapshot but replace the V25D79 fields
     * (player ratings + substitutions remaining). Used by
     * {@code MatchSession.adaptV24Snapshot()} to fill in the per-player live
     * stats + sub counter without touching any other field.
     */
    public MatchStateSnapshot withV25D79Stats(
            List<V24PlayerMatchRatingDto> homeRatings,
            List<V24PlayerMatchRatingDto> awayRatings,
            int subsRemaining) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, score, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation,
            homeRatings, awayRatings, subsRemaining
        );
    }

    public static class Builder {
        private UUID matchId;
        private UUID homeTeamId;
        private UUID awayTeamId;
        private int currentMinute = 0;
        private MatchStatus status = MatchStatus.PAUSED;
        private Score score = new Score();
        private List<MatchEvent> events = new ArrayList<>();
        private String careerId;
        private String userId;
        // LIVE-MATCH-F3-UI-LIVE BE1
        private int homePossession = 50;
        private int awayPossession = 50;
        private String homeStyle = "BALANCED";
        private String awayStyle = "BALANCED";
        private String homeFormation = "4-4-2";
        private String awayFormation = "4-4-2";
        // V25D79
        private List<V24PlayerMatchRatingDto> homePlayerRatings = List.of();
        private List<V24PlayerMatchRatingDto> awayPlayerRatings = List.of();
        private int substitutionsRemaining = 5;

        public Builder matchId(UUID matchId) { this.matchId = matchId; return this; }
        public Builder homeTeamId(UUID homeTeamId) { this.homeTeamId = homeTeamId; return this; }
        public Builder awayTeamId(UUID awayTeamId) { this.awayTeamId = awayTeamId; return this; }
        public Builder currentMinute(int currentMinute) { this.currentMinute = currentMinute; return this; }
        public Builder status(MatchStatus status) { this.status = status; return this; }
        public Builder score(Score score) { this.score = score; return this; }
        public Builder events(List<MatchEvent> events) { this.events = events; return this; }
        public Builder careerId(String careerId) { this.careerId = careerId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder homePossession(int homePossession) { this.homePossession = homePossession; return this; }
        public Builder awayPossession(int awayPossession) { this.awayPossession = awayPossession; return this; }
        public Builder homeStyle(String homeStyle) { this.homeStyle = homeStyle; return this; }
        public Builder awayStyle(String awayStyle) { this.awayStyle = awayStyle; return this; }
        public Builder homeFormation(String homeFormation) { this.homeFormation = homeFormation; return this; }
        public Builder awayFormation(String awayFormation) { this.awayFormation = awayFormation; return this; }
        public Builder homePlayerRatings(List<V24PlayerMatchRatingDto> homePlayerRatings) { this.homePlayerRatings = homePlayerRatings; return this; }
        public Builder awayPlayerRatings(List<V24PlayerMatchRatingDto> awayPlayerRatings) { this.awayPlayerRatings = awayPlayerRatings; return this; }
        public Builder substitutionsRemaining(int substitutionsRemaining) { this.substitutionsRemaining = substitutionsRemaining; return this; }

        public MatchStateSnapshot build() {
            return new MatchStateSnapshot(
                matchId, homeTeamId, awayTeamId,
                currentMinute, status, score, events, careerId, userId,
                homePossession, awayPossession, homeStyle, awayStyle,
                homeFormation, awayFormation,
                homePlayerRatings, awayPlayerRatings, substitutionsRemaining
            );
        }
    }
}

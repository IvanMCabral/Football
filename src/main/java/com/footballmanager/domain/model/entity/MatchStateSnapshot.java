package com.footballmanager.domain.model.entity;

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
 * The canonical constructor now takes 15 args. A backward-compatibility
 * constructor (9 args) is provided so all existing tests and call sites that
 * pre-date F3 keep working — the missing fields default to
 * {@code 50} (possession) and {@code "BALANCED"} / {@code "4-4-2"}
 * (style/formation).
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
    String awayFormation
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
            "4-4-2"
        );
    }

    /**
     * LIVE-MATCH-F3-UI-LIVE BE1: backward-compatibility constructor for the
     * pre-F3 9-arg shape. Defaults the new fields to safe values so existing
     * tests/call sites keep passing.
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
            50, 50, "BALANCED", "BALANCED", "4-4-2", "4-4-2"
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
            homeFormation, awayFormation
        );
    }

    public MatchStateSnapshot withMinute(int minute) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            minute, status, score, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation
        );
    }

    public MatchStateSnapshot withStatus(MatchStatus newStatus) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, newStatus, score, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation
        );
    }

    public MatchStateSnapshot withScore(Score newScore) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, newScore, events, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation
        );
    }

    public MatchStateSnapshot withEvents(List<MatchEvent> newEvents) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, score, newEvents, careerId, userId,
            homePossession, awayPossession, homeStyle, awayStyle,
            homeFormation, awayFormation
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

        public MatchStateSnapshot build() {
            return new MatchStateSnapshot(
                matchId, homeTeamId, awayTeamId,
                currentMinute, status, score, events, careerId, userId,
                homePossession, awayPossession, homeStyle, awayStyle,
                homeFormation, awayFormation
            );
        }
    }
}

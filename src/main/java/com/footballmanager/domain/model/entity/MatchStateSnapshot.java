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
    String userId
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
            null
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
            currentMinute, status, score, newEvents, careerId, userId
        );
    }

    public MatchStateSnapshot withMinute(int minute) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            minute, status, score, events, careerId, userId
        );
    }

    public MatchStateSnapshot withStatus(MatchStatus newStatus) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, newStatus, score, events, careerId, userId
        );
    }

    public MatchStateSnapshot withScore(Score newScore) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, newScore, events, careerId, userId
        );
    }

    public MatchStateSnapshot withEvents(List<MatchEvent> newEvents) {
        return new MatchStateSnapshot(
            matchId, homeTeamId, awayTeamId,
            currentMinute, status, score, newEvents, careerId, userId
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

        public Builder matchId(UUID matchId) { this.matchId = matchId; return this; }
        public Builder homeTeamId(UUID homeTeamId) { this.homeTeamId = homeTeamId; return this; }
        public Builder awayTeamId(UUID awayTeamId) { this.awayTeamId = awayTeamId; return this; }
        public Builder currentMinute(int currentMinute) { this.currentMinute = currentMinute; return this; }
        public Builder status(MatchStatus status) { this.status = status; return this; }
        public Builder score(Score score) { this.score = score; return this; }
        public Builder events(List<MatchEvent> events) { this.events = events; return this; }
        public Builder careerId(String careerId) { this.careerId = careerId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }

        public MatchStateSnapshot build() {
            return new MatchStateSnapshot(
                matchId, homeTeamId, awayTeamId,
                currentMinute, status, score, events, careerId, userId
            );
        }
    }
}

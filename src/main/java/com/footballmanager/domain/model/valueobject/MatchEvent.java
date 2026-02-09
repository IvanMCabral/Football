package com.footballmanager.domain.model.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * MatchEvent: Evento ocurrido durante un partido en vivo.
 * 
 * Simple DTO sin lógica de negocio.
 * Los cálculos relacionados (ej: ¿es gol local?) se hacen en RuntimeMatch o TournamentState.
 */
@Getter
public class MatchEvent {
    private final String eventId;
    private final MatchEventType type;
    private final String teamId;       // sessionTeamId que generó el evento
    private final String playerId;     // sessionPlayerId que generó el evento
    private final int minute;

    @JsonCreator
    public MatchEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("type") MatchEventType type,
            @JsonProperty("teamId") String teamId,
            @JsonProperty("playerId") String playerId,
            @JsonProperty("minute") int minute) {
        this.eventId = eventId;
        this.type = type;
        this.teamId = teamId;
        this.playerId = playerId;
        this.minute = minute;
    }

    public static MatchEvent of(MatchEventType type, int minute, String teamId, String playerId) {
        return new MatchEvent(java.util.UUID.randomUUID().toString(), type, teamId, playerId, minute);
    }
}

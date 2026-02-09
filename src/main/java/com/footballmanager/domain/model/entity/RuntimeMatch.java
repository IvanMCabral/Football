package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.domain.model.valueobject.MatchEvent;
import com.footballmanager.domain.model.valueobject.MatchEventType;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RuntimeMatch: Estado temporal de un partido EN VIVO.
 * 
 * RESPONSABILIDADES:
 * - Mantener estado del partido (minuto, goles, eventos)
 * - Existir SOLO mientras el partido está activo (TTL 2h)
 * - Exponer datos raw para que TournamentState calcule resultados
 * 
 * NO RESPONSABILIDADES (pertenecen a TournamentState):
 * - Construir MatchResultData
 * - Actualizar standings
 * - Persistir resultados finales
 * 
 * Separación de responsabilidades:
 * - RuntimeMatch (Redis+TTL): Estado efímero del partido
 * - TournamentState (CareerSave): Lógica de negocio y persistencia
 */
@Getter
public class RuntimeMatch {
    private final String matchId;         // ID del MatchFixture original
    private final String careerId;        // Career a la que pertenece
    private final String homeTeamId;      // sessionTeamId
    private final String awayTeamId;      // sessionTeamId
    private final int round;
    
    private int currentMinute;            // 0-90
    private MatchStatus status;           // IN_PROGRESS, FINISHED
    
    private int homeGoals;
    private int awayGoals;
    
    private final List<MatchEvent> events;  // Goles, tarjetas, etc.
    
    private final LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @JsonCreator
    public RuntimeMatch(
            @JsonProperty("matchId") String matchId,
            @JsonProperty("careerId") String careerId,
            @JsonProperty("homeTeamId") String homeTeamId,
            @JsonProperty("awayTeamId") String awayTeamId,
            @JsonProperty("round") int round,
            @JsonProperty("currentMinute") int currentMinute,
            @JsonProperty("status") MatchStatus status,
            @JsonProperty("homeGoals") int homeGoals,
            @JsonProperty("awayGoals") int awayGoals,
            @JsonProperty("events") List<MatchEvent> events,
            @JsonProperty("startedAt") LocalDateTime startedAt,
            @JsonProperty("finishedAt") LocalDateTime finishedAt) {
        this.matchId = matchId;
        this.careerId = careerId;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.round = round;
        this.currentMinute = currentMinute;
        this.status = status;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.events = events != null ? events : new ArrayList<>();
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * Constructor para crear un nuevo partido (uso en servicio).
     */
    public RuntimeMatch(
            String matchId,
            String careerId,
            String homeTeamId,
            String awayTeamId,
            int round) {
        this(matchId, careerId, homeTeamId, awayTeamId, round,
                0, MatchStatus.IN_PROGRESS, 0, 0, new ArrayList<>(),
                LocalDateTime.now(), null);
    }

    // ============ COMANDOS ============
    
    /**
     * Avanza el partido 1 minuto.
     * 
     * @throws IllegalStateException si el partido no está en progreso o ya finalizó
     */
    public void advanceMinute() {
        if (status != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("No se puede avanzar minuto si el partido no está en progreso");
        }
        if (currentMinute >= 90) {
            throw new IllegalStateException("El partido ya alcanzó los 90 minutos");
        }
        this.currentMinute++;
    }

    /**
     * Registra un gol durante el partido.
     * 
     * @param teamId ID del equipo que anotó (sessionTeamId)
     * @param playerId ID del jugador que anotó (sessionPlayerId)
     * @param minute Minuto en el que ocurrió el gol
     * @throws IllegalStateException si el partido no está en progreso
     * @throws IllegalArgumentException si el teamId no corresponde a este partido
     */
    public void recordGoal(String teamId, String playerId, int minute) {
        if (status != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("No se puede registrar gol si el partido no está en progreso");
        }
        
        if (teamId.equals(homeTeamId)) {
            homeGoals++;
        } else if (teamId.equals(awayTeamId)) {
            awayGoals++;
        } else {
            throw new IllegalArgumentException("TeamId no corresponde a este partido: " + teamId);
        }
        
        MatchEvent event = new MatchEvent(
            UUID.randomUUID().toString(),
            MatchEventType.GOAL,
            teamId,
            playerId,
            minute
        );
        events.add(event);
    }

    /**
     * Finaliza el partido.
     * 
     * @throws IllegalStateException si el partido ya está finalizado
     */
    public void finish() {
        if (status == MatchStatus.FINISHED) {
            throw new IllegalStateException("El partido ya está finalizado");
        }
        this.status = MatchStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
        this.currentMinute = 90;
    }

    // ============ QUERIES (datos raw para TournamentState) ============
    
    public boolean isFinished() {
        return status == MatchStatus.FINISHED;
    }

    public boolean isInProgress() {
        return status == MatchStatus.IN_PROGRESS;
    }

    /**
     * NO hay getMatchResult() - TournamentState construye el resultado.
     * RuntimeMatch SOLO expone datos raw:
     * - getHomeGoals()
     * - getAwayGoals()
     * - getEvents()
     */
}

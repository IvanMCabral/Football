package com.footballmanager.application.service.match.session;

import com.footballmanager.application.engine.match.MatchEventGenerator;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manejador de ticks para MatchSession.
 *
 * Thread-safe: usa estado inmutable (MatchStateSnapshot).
 */
@Component
public class MatchTickHandler {

    // Partido de 90 minutos
    private static final int MATCH_DURATION = 90;
    // Minutos a avanzar por tick (para que 90min duren ~5 segundos: 90/5 = 18 min/tick)
    private static final int MINUTES_PER_TICK = 18;

    private final MatchEventGenerator eventGenerator;

    public MatchTickHandler(MatchEventGenerator eventGenerator) {
        this.eventGenerator = eventGenerator;
    }

    public MatchTickHandler() {
        this.eventGenerator = new MatchEventGenerator();
    }

    /**
     * Procesa un tick del partido y retorna una lista de resultados - uno por cada minuto avanzado.
     *
     * @param state Estado actual (inmutable)
     * @param commandQueue Cola de comandos pendientes
     * @param paused Si el partido está pausado
     * @param running Si el partido está ejecutándose
     * @return Lista de TickResults - uno por cada minuto avanzado
     */
    public List<TickResult> processTick(
            MatchStateSnapshot state,
            ConcurrentLinkedQueue<MatchCommand> commandQueue,
            boolean paused,
            boolean running) {

        // Si está pausado o no está corriendo, lista vacía
        if (paused || !running) {
            return List.of();
        }

        int currentMinute = state.currentMinute();

        // Si el partido ya llegó a 90 minutos, asegurar que estado sea FINISHED
        if (currentMinute >= MATCH_DURATION) {
            // Retornar un resultado con estado FINISHED
            MatchStateSnapshot finishedState = new MatchStateSnapshot(
                state.matchId(),
                state.homeTeamId(),
                state.awayTeamId(),
                currentMinute,
                MatchStatus.FINISHED,
                state.score(),
                state.events(),
                state.careerId(),
                state.userId()
            );
            return List.of(new TickResult(false, true, finishedState, List.of()));
        }

        // Calcular siguiente minuto
        int nextMinute = Math.min(currentMinute + MINUTES_PER_TICK, MATCH_DURATION);

        Score currentScore = state.score();
        List<MatchEvent> cumulativeEvents = new ArrayList<>(state.events());

        // Generar eventos para CADA minuto del rango avanzado
        List<TickResult> results = new ArrayList<>();

        for (int minute = currentMinute + 1; minute <= nextMinute; minute++) {
            int homeScore = currentScore.home();
            int awayScore = currentScore.away();
            List<MatchEvent> events = eventGenerator.generateEvents(minute, homeScore, awayScore);

            // Procesar eventos y actualizar score
            for (MatchEvent event : events) {
                cumulativeEvents.add(event);

                if (event.getEventType() == MatchEvent.EventType.GOAL) {
                    if (event.getDescription().contains("local")) {
                        currentScore = currentScore.withHomeIncrement();
                    } else {
                        currentScore = currentScore.withAwayIncrement();
                    }
                }
            }

            // Determinar estado para este minuto
            MatchStatus newStatus;
            if (minute >= MATCH_DURATION) {
                newStatus = MatchStatus.FINISHED;
            } else if (state.status() == MatchStatus.PAUSED) {
                newStatus = MatchStatus.RUNNING;
            } else {
                newStatus = state.status();
            }

            // Crear snapshot para este minuto
            MatchStateSnapshot minuteState = new MatchStateSnapshot(
                state.matchId(),
                state.homeTeamId(),
                state.awayTeamId(),
                minute,
                newStatus,
                currentScore,
                new ArrayList<>(cumulativeEvents),
                state.careerId(),
                state.userId()
            );

            boolean isFinished = minute >= MATCH_DURATION;
            results.add(new TickResult(true, isFinished, minuteState, events));
        }

        return results;
    }

    public boolean isMatchFinished(int currentMinute) {
        return currentMinute >= MATCH_DURATION;
    }

    /**
     * Resultado del procesamiento de un minuto.
     */
    public record TickResult(
        boolean minuteAdvanced,
        boolean matchFinished,
        MatchStateSnapshot newState,
        List<MatchEvent> events
    ) {}
}

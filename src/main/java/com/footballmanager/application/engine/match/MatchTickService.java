package com.footballmanager.application.engine.match;

import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Servicio que gestiona el avance del tiempo en un partido.
 * Responsable de: avanzar minutos, aplicar comandos pendientes, generar eventos.
 *
 * Principio SRP: solo gestiona el "tick" del partido, no persistence ni streaming.
 */
@Service
@RequiredArgsConstructor
public class MatchTickService {

    private static final int MATCH_DURATION_MINUTES = 90;
    private static final int PERSIST_INTERVAL_MINUTES = 5;

    private final MatchEventGenerator eventGenerator;
    private final MatchCommandHandler commandHandler;

    /**
     * Resultado de un tick del partido.
     */
    public record TickResult(
        boolean minuteAdvanced,
        boolean matchFinished,
        boolean shouldPersist,
        List<MatchEvent> newEvents
    ) {}

    /**
     * Avanza el partido un minuto.
     *
     * @param state Estado actual del partido (se modifica)
     * @param commandQueue Cola de comandos pendientes
     * @param isPaused Si el partido está pausado
     * @param isRunning Si el motor está corriendo
     * @return Resultado del tick
     */
    public TickResult advanceOneMinute(
            MatchState state,
            ConcurrentLinkedQueue<MatchCommand> commandQueue,
            boolean isPaused,
            boolean isRunning) {

        // Si está pausado, no avanzamos
        if (isPaused) {
            return new TickResult(false, false, false, List.of());
        }

        // Si no está corriendo, no avanzamos
        if (!isRunning) {
            return new TickResult(false, false, false, List.of());
        }

        int currentMinute = state.getCurrentMinute();

        // Verificar si el partido terminó
        if (currentMinute >= MATCH_DURATION_MINUTES) {
            return new TickResult(false, true, false, List.of());
        }

        // Avanzar un minuto
        state.setCurrentMinute(currentMinute + 1);
        int newMinute = state.getCurrentMinute();

        // Aplicar comandos pendientes
        applyPendingCommands(commandQueue, state);

        // Generar eventos para este minuto
        int homeScore = state.getScore().home();
        int awayScore = state.getScore().away();
        List<MatchEvent> events = eventGenerator.generateEvents(newMinute, homeScore, awayScore);

        // Agregar eventos al estado
        if (!events.isEmpty()) {
            state.getEvents().clear();
            state.getEvents().addAll(events);
            updateScoreFromEvents(state, events);
        }

        // Determinar si debemos persistir
        boolean shouldPersist = newMinute % PERSIST_INTERVAL_MINUTES == 0 || !events.isEmpty();

        // Verificar si el partido terminó con este minuto
        boolean matchFinished = newMinute >= MATCH_DURATION_MINUTES;

        return new TickResult(true, matchFinished, shouldPersist, events);
    }

    /**
     * Aplica todos los comandos pendientes de la cola.
     */
    private void applyPendingCommands(ConcurrentLinkedQueue<MatchCommand> commandQueue, MatchState state) {
        MatchCommand command;
        boolean commandsApplied = false;

        while ((command = commandQueue.poll()) != null) {
            if (commandHandler.isCommandValid(command, state)) {
                commandHandler.handleCommand(command, state);
                commandsApplied = true;
            }
        }

        // Si se aplicaron comandos, limpiar eventos del minuto anterior
        if (commandsApplied && state.getEvents().isEmpty()) {
            state.getEvents().clear();
        }
    }

    /**
     * Actualiza el marcador basándose en los eventos del minuto.
     * @deprecated Usar MatchTickHandler que maneja Score inmutable
     */
    @Deprecated
    private void updateScoreFromEvents(MatchState state, List<MatchEvent> events) {
        // Score ahora es inmutable, esta lógica no aplica directamente
        // La actualización de score se hace en MatchTickHandler
    }

    /**
     * Verifica si el partido ha terminado.
     */
    public boolean isMatchFinished(int currentMinute) {
        return currentMinute >= MATCH_DURATION_MINUTES;
    }

    /**
     * Obtiene la duración total del partido en minutos.
     */
    public int getMatchDuration() {
        return MATCH_DURATION_MINUTES;
    }
}

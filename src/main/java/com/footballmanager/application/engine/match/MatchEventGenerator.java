package com.footballmanager.application.engine.match;

import com.footballmanager.domain.model.entity.MatchEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Random;

/**
 * Servicio de dominio que genera eventos aleatorios durante un partido.
 * Responsable de: goles, tarjetas, lesiones, etc.
 *
 * Principio SRP: solo genera eventos, no modifica el estado directamente.
 */
@Service
public class MatchEventGenerator {

    private static final double GOAL_PROBABILITY = 0.05;      // 5% por minuto
    private static final double CARD_PROBABILITY = 0.02;      // 2% por minuto
    private static final double INJURY_PROBABILITY = 0.005;   // 0.5% por minuto

    private final Random random = new Random();

    /**
     * Genera eventos para un minuto específico.
     * @param currentMinute Minute del partido
     * @param homeScore Goles actuales del equipo local
     * @param awayScore Goles actuales del equipo visitante
     * @return Lista de eventos generados (puede estar vacía)
     */
    public java.util.List<MatchEvent> generateEvents(int currentMinute, int homeScore, int awayScore) {
        java.util.List<MatchEvent> events = new ArrayList<>();

        // Probabilidad de gol
        if (random.nextDouble() < GOAL_PROBABILITY) {
            events.add(generateGoalEvent(currentMinute, homeScore, awayScore));
        }

        // Probabilidad de tarjeta amarilla
        if (random.nextDouble() < CARD_PROBABILITY) {
            events.add(generateCardEvent(currentMinute));
        }

        // Probabilidad de lesión
        if (random.nextDouble() < INJURY_PROBABILITY) {
            events.add(generateInjuryEvent(currentMinute));
        }

        return events;
    }

    /**
     * Genera un evento de gol.
     */
    private MatchEvent generateGoalEvent(int minute, int homeScore, int awayScore) {
        boolean homeScores = random.nextDouble() < 0.5;
        String scorer = homeScores ? "Jugador local" : "Jugador visitante";
        String description = homeScores ? "Gol del equipo local" : "Gol del equipo visitante";

        return MatchEvent.of(
            MatchEvent.EventType.GOAL,
            minute,
            scorer,
            description
        );
    }

    /**
     * Genera un evento de tarjeta.
     */
    private MatchEvent generateCardEvent(int minute) {
        return MatchEvent.of(
            MatchEvent.EventType.CARD,
            minute,
            "Jugador",
            "Tarjeta amarilla"
        );
    }

    /**
     * Genera un evento de lesión.
     */
    private MatchEvent generateInjuryEvent(int minute) {
        return MatchEvent.of(
            MatchEvent.EventType.INJURY,
            minute,
            "Jugador",
            "Lesión menor"
        );
    }

    /**
     * Determina si hubo gol basándose en probabilidades.
     */
    public boolean isGoalScored() {
        return random.nextDouble() < GOAL_PROBABILITY;
    }

    /**
     * Determina qué equipo anota.
     * @return true si anota el equipo local, false si el visitante
     */
    public boolean isHomeTeamScorer() {
        return random.nextDouble() < 0.5;
    }
}

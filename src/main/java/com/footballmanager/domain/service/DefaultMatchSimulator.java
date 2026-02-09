package com.footballmanager.domain.service;

import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Implementación del simulador de partidos.
 *
 * Proporciona dos modos de simulación:
 * - simulateReal: Simulación detallada con eventos por minuto
 * - simulateQuick: Simulación rápida basada en OVR de equipos
 */
@Component
public class DefaultMatchSimulator implements MatchSimulator {

    private static final double GOAL_PROBABILITY_PER_MINUTE = 0.05;
    private static final int HALF_TIME = 45;
    private static final int FULL_TIME = 90;

    private final Random random;

    public DefaultMatchSimulator(Random random) {
        this.random = random;
    }

    public DefaultMatchSimulator() {
        this(new Random());
    }

    @Override
    public MatchState simulateReal(MatchState state, int toMinute) {
        if (toMinute < state.getCurrentMinute()) {
            return state;
        }

        if (state.getStatus() == MatchStatus.FINISHED) {
            return state;
        }

        int from = state.getCurrentMinute() + 1;

        for (int minute = from; minute <= toMinute; minute++) {
            state.setCurrentMinute(minute);
            simulateMinuteEvents(state, minute);
            updateMatchStatus(state, minute);
        }

        return state;
    }

    @Override
    public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
        // Calcular diferencia de OVR
        int ovrDiff = homeOvr - awayOvr;

        // Probabilidad base de gol por equipo
        double homeGoalProb = 0.8 + (ovrDiff * 0.02); // 0.8 + bonus OVR
        double awayGoalProb = 0.8 - (ovrDiff * 0.02); // 0.8 - penalty OVR

        // Limitar probabilidades
        homeGoalProb = Math.max(0.3, Math.min(1.5, homeGoalProb));
        awayGoalProb = Math.max(0.3, Math.min(1.5, awayGoalProb));

        // Generar goles con distribución de Poisson aproximada
        int homeGoals = calculateGoals(homeGoalProb);
        int awayGoals = calculateGoals(awayGoalProb);

        // Evitar empates con probabilidad baja (añadir small noise)
        if (homeGoals == awayGoals && random.nextDouble() < 0.1) {
            if (random.nextBoolean()) homeGoals++;
            else awayGoals++;
        }

        return new MatchResult(homeGoals, awayGoals);
    }

    /**
     * Calcula número de goles usando distribución aproximadamente Poisson.
     */
    private int calculateGoals(double goalProbability) {
        int goals = 0;
        for (int i = 0; i < 5; i++) { // 5 oportunidades de gol por partido
            if (random.nextDouble() < goalProbability) {
                goals++;
            }
        }
        return goals;
    }

    private void simulateMinuteEvents(MatchState state, int minute) {
        if (random.nextDouble() < GOAL_PROBABILITY_PER_MINUTE) {
            simulateGoal(state, minute);
        }
    }

    private void simulateGoal(MatchState state, int minute) {
        boolean homeScores = random.nextBoolean();

        // Score ahora es inmutable, usar setter para actualizar
        if (homeScores) {
            int newHome = state.getScore().home() + 1;
            state.setScore(new com.footballmanager.domain.model.valueobject.Score(newHome, state.getScore().away()));
            state.getEvents().add(MatchEvent.of(MatchEvent.EventType.GOAL, minute, "PlayerHome", "HOME"));
        } else {
            int newAway = state.getScore().away() + 1;
            state.setScore(new com.footballmanager.domain.model.valueobject.Score(state.getScore().home(), newAway));
            state.getEvents().add(MatchEvent.of(MatchEvent.EventType.GOAL, minute, "PlayerAway", "AWAY"));
        }
    }

    private void updateMatchStatus(MatchState state, int minute) {
        if (minute >= FULL_TIME) {
            state.setStatus(MatchStatus.FINISHED);
        } else if (minute == HALF_TIME) {
            state.setStatus(MatchStatus.RUNNING);
        } else {
            state.setStatus(MatchStatus.RUNNING);
        }
    }
}

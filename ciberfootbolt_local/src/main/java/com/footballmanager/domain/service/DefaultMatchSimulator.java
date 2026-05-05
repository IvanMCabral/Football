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
        double ovrDiff = homeOvr - awayOvr;

        // Final calibrated Poisson goal model
        double baseTotalLambda = 2.60;
        double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
        double totalLambda = clamp(baseTotalLambda + imbalanceBoost, 2.3, 3.05);

        double homeBaseShare = 0.52;
        double strengthShift = ovrDiff / 220.0;
        double homeShare = clamp(homeBaseShare + strengthShift, 0.25, 0.75);
        double awayShare = 1.0 - homeShare;

        double homeLambda = totalLambda * homeShare;
        double awayLambda = totalLambda * awayShare;

        int homeGoals = poissonSample(homeLambda);
        int awayGoals = poissonSample(awayLambda);

        return new MatchResult(homeGoals, awayGoals);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int poissonSample(double lambda) {
        if (lambda <= 0) return 0;
        if (lambda < 30) {
            double L = Math.exp(-lambda);
            double p = 1.0;
            int k = 0;
            do {
                k++;
                p *= random.nextDouble();
            } while (p > L);
            return k - 1;
        } else {
            double mean = lambda + 0.5;
            double variance = lambda;
            double u = random.nextGaussian() * Math.sqrt(variance) + mean;
            return Math.max(0, (int) Math.round(u));
        }
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

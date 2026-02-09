package com.footballmanager.application.engine.round;

import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;

import java.util.List;

/**
 * Servicio que calcula el estado general de una jornada.
 * Responsable de: determinar si la jornada está en progreso, terminada, etc.
 *
 * Principio SRP: solo calcula el estado de la jornada.
 *
 * Thread-safe: usa MatchStateSnapshot inmutable.
 */
public class RoundStatusCalculator {

    /**
     * Calcula el estado de la jornada (usando MatchStateSnapshot inmutable).
     */
    public RoundState.RoundStatus calculate(List<MatchStateSnapshot> matches) {
        if (matches == null || matches.isEmpty()) {
            return RoundState.RoundStatus.NOT_STARTED;
        }

        long running = countStatus(matches, MatchStatus.RUNNING);
        long finished = countStatus(matches, MatchStatus.FINISHED);
        long paused = countStatus(matches, MatchStatus.PAUSED);

        return calculateStatus(running, finished, paused, matches.size());
    }

    /**
     * Calcula el estado de la jornada (versión legacy para MatchState mutable).
     */
    public RoundState.RoundStatus calculateLegacy(List<MatchState> matches) {
        if (matches == null || matches.isEmpty()) {
            return RoundState.RoundStatus.NOT_STARTED;
        }

        long running = countStatusLegacy(matches, MatchStatus.RUNNING);
        long finished = countStatusLegacy(matches, MatchStatus.FINISHED);
        long paused = countStatusLegacy(matches, MatchStatus.PAUSED);

        return calculateStatus(running, finished, paused, matches.size());
    }

    private RoundState.RoundStatus calculateStatus(long running, long finished, long paused, int total) {
        if (finished == total) {
            return RoundState.RoundStatus.FINISHED;
        } else if (running > 0) {
            return RoundState.RoundStatus.IN_PROGRESS;
        } else if (paused > 0) {
            return RoundState.RoundStatus.PAUSED;
        } else {
            return RoundState.RoundStatus.NOT_STARTED;
        }
    }

    /**
     * Verifica si todos los partidos han terminado (MatchStateSnapshot).
     */
    public boolean allFinished(List<MatchStateSnapshot> matches) {
        if (matches == null || matches.isEmpty()) {
            return false;
        }
        return matches.stream().allMatch(m -> m.status() == MatchStatus.FINISHED);
    }

    /**
     * Verifica si todos los partidos han terminado (legacy MatchState).
     */
    public boolean allFinishedLegacy(List<MatchState> matches) {
        if (matches == null || matches.isEmpty()) {
            return false;
        }
        return matches.stream().allMatch(m -> m.getStatus() == MatchStatus.FINISHED);
    }

    /**
     * Verifica si algún partido está en curso (MatchStateSnapshot).
     */
    public boolean anyRunning(List<MatchStateSnapshot> matches) {
        if (matches == null || matches.isEmpty()) {
            return false;
        }
        return matches.stream().anyMatch(m -> m.status() == MatchStatus.RUNNING);
    }

    /**
     * Verifica si algún partido está pausado (MatchStateSnapshot).
     */
    public boolean anyPaused(List<MatchStateSnapshot> matches) {
        if (matches == null || matches.isEmpty()) {
            return false;
        }
        return matches.stream().anyMatch(m -> m.status() == MatchStatus.PAUSED);
    }

    /**
     * Cuenta cuántos partidos tienen un estado específico (MatchStateSnapshot).
     */
    private long countStatus(List<MatchStateSnapshot> matches, MatchStatus status) {
        return matches.stream()
            .filter(m -> m.status() == status)
            .count();
    }

    /**
     * Cuenta cuántos partidos tienen un estado específico (legacy MatchState).
     */
    private long countStatusLegacy(List<MatchState> matches, MatchStatus status) {
        return matches.stream()
            .filter(m -> m.getStatus() == status)
            .count();
    }
}

package com.footballmanager.application.service.match.session;

import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gestiona el ciclo de vida de una sesion de partido.
 * Responsabilidad: start, pause, resume, stop.
 */
public class MatchLifecycleManager {

    private final UUID matchId;
    private final UUID userId;
    private final MatchState state;
    private final MatchStateRepository stateRepository;
    private final MatchStatePersister persister;
    private final MatchTickHandler tickHandler;

    private boolean running = false;
    private boolean paused = false;

    public MatchLifecycleManager(UUID userId, UUID matchId, MatchState state, MatchStateRepository stateRepository,
                                 MatchTickHandler tickHandler, CareerWriteContext lifecycleContext) {
        this.userId = userId;
        this.matchId = matchId;
        this.state = state;
        this.stateRepository = stateRepository;
        this.persister = new MatchStatePersister(matchId, userId, stateRepository, lifecycleContext);
        this.tickHandler = tickHandler;
    }

    public void start(Runnable tickRunner) {
        if (running) {
            return;
        }

        if (tickHandler.isMatchFinished(state.getCurrentMinute())) {
            state.setStatus(MatchStatus.FINISHED);
            return;
        }

        state.setStatus(MatchStatus.RUNNING);
        running = true;
        paused = false;

        tickRunner.run();
    }

    public Mono<Void> pause() {
        if (!running) {
            return Mono.empty();
        }
        if (paused) {
            return Mono.empty();
        }

        paused = true;
        state.setStatus(MatchStatus.PAUSED);
        return persister.persist(state);
    }

    public void resume() {
        if (!running) {
            return;
        }
        if (!paused) {
            return;
        }
        if (tickHandler.isMatchFinished(state.getCurrentMinute())) {
            return;
        }

        paused = false;
        state.setStatus(MatchStatus.RUNNING);
    }

    public Mono<Void> stop() {
        running = false;
        paused = false;
        state.setStatus(MatchStatus.CANCELLED);
        return persister.persist(state);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isFinished() {
        return state.getStatus() == MatchStatus.FINISHED;
    }

    public boolean canAdvanceMinute() {
        return running && !paused && !tickHandler.isMatchFinished(state.getCurrentMinute());
    }

    public MatchState getState() {
        return state;
    }
}

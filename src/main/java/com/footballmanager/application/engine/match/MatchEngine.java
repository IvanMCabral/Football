package com.footballmanager.application.engine.match;

import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import reactor.core.publisher.Flux;

/**
 * Wrapper de compatibilidad para MatchSession.
 * Thread-safe: usa MatchStateSnapshot inmutable.
 */
public class MatchEngine {

    private final MatchSession session;

    public MatchEngine(MatchSession session) {
        this.session = session;
    }

    public void setOnFinishCallback(java.util.function.Consumer<MatchStateSnapshot> callback) {
        session.setOnFinishCallback(callback);
    }

    public Flux<MatchStateSnapshot> getStateStream() {
        return session.getStateStream();
    }

    public void start() {
        session.start();
    }

    public void advanceTick() {
        session.advanceTick();
    }

    public void pause() {
        session.pause();
    }

    public void resume() {
        session.resume();
    }

    public void stop() {
        session.stop();
    }

    public void queueCommand(MatchCommand command) {
        throw new UnsupportedOperationException("Use ExecuteMatchCommandUseCase.execute() instead");
    }

    public MatchStateSnapshot getCurrentState() {
        return session.getCurrentState();
    }

    public boolean isRunning() {
        return session.isRunning();
    }

    public boolean isFinished() {
        return session.isFinished();
    }

    public boolean isPaused() {
        return session.isPaused();
    }
}

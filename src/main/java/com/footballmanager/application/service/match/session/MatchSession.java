package com.footballmanager.application.service.match.session;

import com.footballmanager.application.engine.match.MatchCommandHandler;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Sesión interna de un partido en curso.
 *
 * Thread-safe: usa estado inmutable (MatchStateSnapshot) volatile.
 */
public class MatchSession {

    public final UUID matchId;
    private volatile MatchStateSnapshot currentState;
    private final MatchTickHandler tickHandler;
    private final ConcurrentLinkedQueue<MatchCommand> commandQueue;
    private final Sinks.Many<MatchStateSnapshot> stateSink;

    private Consumer<MatchStateSnapshot> onFinishCallback;
    private volatile boolean finishCallbackExecuted = false;

    public MatchSession(UUID userId, UUID matchId, MatchState state, MatchTickHandler tickHandler) {
        this.matchId = matchId;
        this.currentState = convertToSnapshot(matchId, state);
        this.tickHandler = tickHandler;
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.stateSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    private MatchStateSnapshot convertToSnapshot(UUID matchId, MatchState state) {
        com.footballmanager.domain.model.valueobject.Score newScore =
            new com.footballmanager.domain.model.valueobject.Score(
                state.getScore().home(),
                state.getScore().away()
            );
        return new MatchStateSnapshot(
            matchId,
            state.getHomeTeamId(),
            state.getAwayTeamId(),
            state.getCurrentMinute(),
            state.getStatus(),
            newScore,
            new ArrayList<>(state.getEvents()),
            state.getCareerId(),
            state.getUserId()
        );
    }

    public Flux<MatchStateSnapshot> getStateStream() {
        return stateSink.asFlux();
    }

    public void setOnFinishCallback(Consumer<MatchStateSnapshot> callback) {
        this.onFinishCallback = callback;
    }

    public void start() {
        this.currentState = currentState.withStatus(MatchStatus.RUNNING);
        emitState();
    }

    public MatchStateSnapshot advanceTick() {
        if (isFinished()) {
            return currentState;
        }

        List<MatchTickHandler.TickResult> results = tickHandler.processTick(
            currentState,
            commandQueue,
            false,
            true
        );

        MatchStateSnapshot newState = currentState;
        for (MatchTickHandler.TickResult result : results) {
            newState = result.newState();
        }

        this.currentState = newState;
        emitState();

        if (isFinished() && onFinishCallback != null && !finishCallbackExecuted) {
            finishCallbackExecuted = true;
            try {
                onFinishCallback.accept(currentState);
            } catch (Exception ignored) {
            }
        }

        return currentState;
    }

    public void pause() {
        this.currentState = currentState.withStatus(MatchStatus.PAUSED);
        emitState();
    }

    public void resume() {
        this.currentState = currentState.withStatus(MatchStatus.RUNNING);
        emitState();
    }

    public boolean queueCommand(MatchCommand command, MatchCommandHandler commandHandler) {
        if (commandHandler.isCommandValid(command, currentState)) {
            MatchStateSnapshot newState = commandHandler.handleCommand(command, currentState);
            this.currentState = newState;
            emitState();
            return true;
        }
        return false;
    }

    public void stop() {
        emitState();
    }

    public boolean isRunning() {
        return currentState != null && currentState.status() == MatchStatus.RUNNING;
    }

    public boolean isPaused() {
        return currentState != null && currentState.status() == MatchStatus.PAUSED;
    }

    public boolean isFinished() {
        return currentState != null &&
            currentState.status() == MatchStatus.FINISHED;
    }

    public MatchStateSnapshot getCurrentState() {
        return currentState;
    }

    private void emitState() {
        stateSink.tryEmitNext(currentState);
    }
}

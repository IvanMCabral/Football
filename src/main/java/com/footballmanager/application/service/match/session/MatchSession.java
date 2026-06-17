package com.footballmanager.application.service.match.session;

import com.footballmanager.application.engine.match.MatchCommandHandler;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24LiveSnapshot;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
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
 * <p>Thread-safe: usa estado inmutable (MatchStateSnapshot) volatile.
 *
 * <p>V24D6M11: When v24LiveSession is non-null, uses V24DetailedMatchEngine
 * via V24LiveSession.tick() for tick-by-tick SSE simulation. The legacy
 * path (v24LiveSession == null) uses MatchTickHandler.
 */
public class MatchSession {

    public final UUID matchId;
    private volatile MatchStateSnapshot currentState;
    private final MatchTickHandler tickHandler;
    private final ConcurrentLinkedQueue<MatchCommand> commandQueue;
    private final Sinks.Many<MatchStateSnapshot> stateSink;
    /** V24 live session — null means legacy path (use MatchTickHandler). */
    private final V24LiveSession v24LiveSession;

    /**
     * LIVE-MATCH-F1-POC: public accessor for the V24LiveSession.
     * Returns null if this session is on the legacy (non-V24) path.
     * Callers that need V24-specific behavior (manual substitutions, etc.)
     * must null-check.
     */
    public V24LiveSession getV24LiveSession() {
        return v24LiveSession;
    }

    private Consumer<MatchFinishedResult> onFinishCallback;
    private volatile boolean finishCallbackExecuted = false;

    /**
     * Legacy constructor — no V24LiveSession.
     * Uses MatchTickHandler for event generation.
     */
    public MatchSession(UUID userId, UUID matchId, MatchState state, MatchTickHandler tickHandler) {
        this(userId, matchId, state, tickHandler, null);
    }

    /**
     * Full constructor with optional V24LiveSession.
     *
     * @param v24LiveSession null for legacy path; non-null to use V24DetailedMatchEngine
     */
    public MatchSession(UUID userId, UUID matchId, MatchState state,
                        MatchTickHandler tickHandler, V24LiveSession v24LiveSession) {
        this.matchId = matchId;
        this.currentState = convertToSnapshot(matchId, state);
        this.tickHandler = tickHandler;
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.stateSink = Sinks.many().multicast().onBackpressureBuffer();
        this.v24LiveSession = v24LiveSession;
    }

    private MatchStateSnapshot convertToSnapshot(UUID matchId, MatchState state) {
        Score newScore = new Score(
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

    /**
     * Set callback for V24 path (receives MatchFinishedResult with V24DetailedMatchResult).
     */
    public void setOnFinishCallback(Consumer<MatchFinishedResult> callback) {
        this.onFinishCallback = callback;
    }

    /**
     * Backward-compatible method for legacy callers that expect MatchStateSnapshot.
     * Delegates to the new callback type by wrapping the legacy consumer.
     * Used by MatchEngine (via MatchSession.setOnFinishCallback) to maintain backward compat.
     */
    public void setOnFinishCallbackLegacy(Consumer<MatchStateSnapshot> legacyCallback) {
        this.onFinishCallback = result -> legacyCallback.accept(result.snapshot());
    }

    public void start() {
        this.currentState = currentState.withStatus(MatchStatus.RUNNING);
        emitState();
    }

    public MatchStateSnapshot advanceTick() {
        if (isFinished()) {
            return currentState;
        }

        if (v24LiveSession != null) {
            // V24 path: use V24LiveSession.tick() — no MatchTickHandler involved
            V24LiveSnapshot snap = v24LiveSession.tick();
            this.currentState = adaptV24Snapshot(snap);
            emitState();
        } else {
            // Legacy path: use MatchTickHandler
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
        }

        if (isFinished() && onFinishCallback != null && !finishCallbackExecuted) {
            finishCallbackExecuted = true;
            try {
                V24DetailedMatchResult v24Result = (v24LiveSession != null)
                        ? v24LiveSession.finalResult()
                        : null;
                onFinishCallback.accept(new MatchFinishedResult(currentState, v24Result));
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

    /**
     * Adapt V24LiveSnapshot to MatchStateSnapshot for SSE stream.
     * Events are converted from V24MatchEvent → domain MatchEvent.
     */
    private MatchStateSnapshot adaptV24Snapshot(V24LiveSnapshot snap) {
        UUID homeTeamId = snap.homeTeamId() != null ? UUID.fromString(snap.homeTeamId()) : null;
        UUID awayTeamId = snap.awayTeamId() != null ? UUID.fromString(snap.awayTeamId()) : null;

        List<MatchEvent> adaptedEvents = new ArrayList<>();
        for (V24MatchEvent e : snap.allEvents()) {
            adaptedEvents.add(toDomainMatchEvent(e));
        }

        return new MatchStateSnapshot(
                currentState.matchId(),
                homeTeamId,
                awayTeamId,
                snap.minute(),
                snap.isFinished() ? MatchStatus.FINISHED : MatchStatus.RUNNING,
                new Score(snap.homeGoals(), snap.awayGoals()),
                adaptedEvents,
                currentState.careerId(),
                currentState.userId()
        );
    }

    /**
     * Convert a V24MatchEvent to domain MatchEvent, preserving player attribution.
     * Used for SSE stream — no information loss since V24MatchEvent has all needed fields.
     */
    private MatchEvent toDomainMatchEvent(V24MatchEvent e) {
        MatchEvent.EventType domainType = toDomainEventType(e.type());
        return MatchEvent.of(
                domainType,
                e.minute(),
                e.playerId(),
                e.playerName(),
                e.teamId(),
                e.description()
        );
    }

    /**
     * Map V24MatchEventType to domain MatchEvent.EventType.
     * Every V24MatchEventType maps explicitly — no lossy fallbacks.
     */
    private MatchEvent.EventType toDomainEventType(V24MatchEventType v24Type) {
        if (v24Type == null) {
            throw new IllegalArgumentException("V24MatchEventType cannot be null");
        }
        return switch (v24Type) {
            case GOAL -> MatchEvent.EventType.GOAL;
            case SHOT -> MatchEvent.EventType.SHOT;
            case SHOT_ON_TARGET -> MatchEvent.EventType.SHOT_ON_TARGET;
            case SAVE -> MatchEvent.EventType.SAVE;
            case MISS -> MatchEvent.EventType.MISS;
            case BLOCK -> MatchEvent.EventType.BLOCK;
            case CHANCE_CREATED -> MatchEvent.EventType.CHANCE_CREATED;
            case FOUL -> MatchEvent.EventType.FOUL;
            case YELLOW_CARD -> MatchEvent.EventType.YELLOW_CARD;
            case RED_CARD -> MatchEvent.EventType.RED_CARD;
            case INJURY -> MatchEvent.EventType.INJURY;
            case CORNER -> MatchEvent.EventType.CORNER;
            case OFFSIDE -> MatchEvent.EventType.OFFSIDE;
            case SUBSTITUTION -> MatchEvent.EventType.SUBSTITUTION;
            // LIVE-MATCH-F2-LIVE F5: tactical change maps 1:1 (description carries the payload).
            case TACTICAL_CHANGE -> MatchEvent.EventType.TACTICAL_CHANGE;
        };
    }
}
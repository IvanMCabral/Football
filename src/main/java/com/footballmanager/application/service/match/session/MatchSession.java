package com.footballmanager.application.service.match.session;

import com.footballmanager.application.engine.match.MatchCommandHandler;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24LiveSnapshot;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchTimeline;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchState;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchStatsModel;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import com.footballmanager.domain.model.entity.SessionPlayer;
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
        // V25D87.1-BACK-F1: align with CareerNotificationService's
        // replay().latest() pattern — see RoundEngine.stateSink for the
        // full rationale. MatchSession feeds the per-match SSE stream
        // consumed by startMatchUseCase / live components.
        this.stateSink = Sinks.many().replay().latest();
        this.v24LiveSession = v24LiveSession;
    }

    private MatchStateSnapshot convertToSnapshot(UUID matchId, MatchState state) {
        Score newScore = new Score(
                state.getScore().home(),
                state.getScore().away()
        );
        // LIVE-MATCH-F3-UI-LIVE BE1: legacy path (no V24LiveSession) — defaults
        // for the new possession/style/formation fields. The 9-arg constructor
        // applies safe defaults (50/50, BALANCED, 4-4-2).
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

    /**
     * Refresh the exposed match state from the V24 live engine without
     * advancing the match clock.
     *
     * <p>This keeps the UI/API snapshot aligned with manager actions applied
     * while the round is paused (for example queued injury substitution
     * modals). Without this, the next API read can still show the pre-action
     * lineup until the following tick.
     */
    public synchronized MatchStateSnapshot refreshV24Snapshot() {
        if (v24LiveSession == null) {
            return currentState;
        }
        this.currentState = adaptV24Snapshot(v24LiveSession.snapshot());
        emitState();
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
     *
     * <p>LIVE-MATCH-F3-UI-LIVE BE1: propagates the 6 new fields
     * (homePossession, awayPossession, homeStyle, awayStyle, homeFormation,
     * awayFormation) so the F3 UI can render the possession bar and the
     * current style/formation per team in real time.
     *
     * <p>V25D79: computes the new {@code homePlayerRatings} /
     * {@code awayPlayerRatings} (per-player live stats via
     * {@link V24PlayerMatchStatsModel#computeRatings(java.util.Collection, V24MatchTimeline)})
     * and {@code substitutionsRemaining} (max(0, 5 - count(SUBSTITUTION events))).
     * The ratings are computed against the LIVE partial timeline (events up to
     * {@code snap.minute()}), not the cached full-match engine result, so the
     * F4 substitution modal shows stats that update minute-by-minute.
     *
     * <p>Package-private (default visibility) so {@code MatchSessionV25D79Test}
     * can drive it with controlled inputs. Not part of the public API.
     */
    MatchStateSnapshot adaptV24Snapshot(V24LiveSnapshot snap) {
        UUID homeTeamId = parseSnapshotTeamId(snap.homeTeamId(), currentState != null ? currentState.homeTeamId() : null);
        UUID awayTeamId = parseSnapshotTeamId(snap.awayTeamId(), currentState != null ? currentState.awayTeamId() : null);

        List<MatchEvent> adaptedEvents = new ArrayList<>();
        for (V24MatchEvent e : snap.allEvents()) {
            adaptedEvents.add(toDomainMatchEvent(e));
        }

        // V25D79: per-player live stats. Build the partial timeline (events up
        // to currentMinute — snap.allEvents() is already filtered by
        // V24LiveSession.buildSnapshot()) so the ratings reflect the live
        // match, NOT the final 90-minute projection.
        List<V24PlayerMatchRatingDto> homePlayerRatings = List.of();
        List<V24PlayerMatchRatingDto> awayPlayerRatings = List.of();
        V24MatchContext ctx = v24LiveSession.context();
        if (ctx != null) {
            String homeIdStr = snap.homeTeamId();
            String awayIdStr = snap.awayTeamId();
            List<V24PlayerMatchState> homeStates = buildPlayerStates(
                    homeIdStr, ctx.homeStartingPlayers(), ctx.homeBenchPlayers());
            List<V24PlayerMatchState> awayStates = buildPlayerStates(
                    awayIdStr, ctx.awayStartingPlayers(), ctx.awayBenchPlayers());

            V24MatchTimeline liveTimeline = new V24MatchTimeline();
            for (V24MatchEvent e : snap.allEvents()) {
                liveTimeline.addEvent(e);
            }

            V24PlayerMatchStatsModel statsModel = new V24PlayerMatchStatsModel();
            homePlayerRatings = statsModel.computeRatings(homeStates, liveTimeline);
            awayPlayerRatings = statsModel.computeRatings(awayStates, liveTimeline);
        }

        // V25D79 (D5): substitutions remaining. The match starts at 5 subs;
        // each SUBSTITUTION event decrements the count. Floor at 0 so a buggy
        // engine (e.g. emitting SUBSTITUTION events for tactical changes) does
        // not produce a negative counter.
        long subsDone = adaptedEvents.stream()
                .filter(e -> e.getEventType() == MatchEvent.EventType.SUBSTITUTION)
                .count();
        int substitutionsRemaining = (int) Math.max(0, MatchStateSnapshot.MAX_SUBSTITUTIONS - subsDone);

        return new MatchStateSnapshot(
                currentState.matchId(),
                homeTeamId,
                awayTeamId,
                snap.minute(),
                snap.isFinished() ? MatchStatus.FINISHED : MatchStatus.RUNNING,
                new Score(snap.homeGoals(), snap.awayGoals()),
                adaptedEvents,
                currentState.careerId(),
                currentState.userId(),
                // LIVE-MATCH-F3-UI-LIVE BE1
                snap.homePossession(),
                snap.awayPossession(),
                snap.homeStyle(),
                snap.awayStyle(),
                snap.homeFormation(),
                snap.awayFormation(),
                // V25D79
                homePlayerRatings,
                awayPlayerRatings,
                substitutionsRemaining,
                snap.homeSlots(),
                snap.awaySlots()
        );
    }

    private UUID parseSnapshotTeamId(String rawTeamId, UUID fallbackTeamId) {
        if (rawTeamId == null || rawTeamId.isBlank()) {
            return fallbackTeamId;
        }
        try {
            return UUID.fromString(rawTeamId);
        } catch (IllegalArgumentException ignored) {
            return fallbackTeamId;
        }
    }

    /**
     * V25D79 helper: build a list of {@link V24PlayerMatchState} for the home
     * or away team from the {@code V24MatchContext}'s starting + bench lists
     * (both {@link SessionPlayer}). Returns an empty list when the team has no
     * players (defensive — never crashes SSE).
     */
    private List<V24PlayerMatchState> buildPlayerStates(
            String teamId,
            List<SessionPlayer> starting,
            List<SessionPlayer> bench) {
        if (teamId == null) {
            return List.of();
        }
        List<V24PlayerMatchState> states = new ArrayList<>();
        if (starting != null) {
            for (SessionPlayer p : starting) {
                if (p == null) continue;
                states.add(V24PlayerMatchState.fromSessionPlayer(p, teamId));
            }
        }
        if (bench != null) {
            for (SessionPlayer p : bench) {
                if (p == null) continue;
                states.add(V24PlayerMatchState.fromSessionPlayer(p, teamId));
            }
        }
        return states;
    }

    /**
     * Convert a V24MatchEvent to domain MatchEvent, preserving player attribution.
     * Used for SSE stream — no information loss since V24MatchEvent has all needed fields.
     *
     * <p>LIVE-MATCH-F3-UI-LIVE BE2: for SUBSTITUTION events, the
     * {@code relatedPlayerName} from V24MatchEvent is propagated to the domain
     * {@code playerOnName} so the F3 UI can render "Salió X, entró Y" in the
     * timeline without resolving IDs.
     */
    private MatchEvent toDomainMatchEvent(V24MatchEvent e) {
        MatchEvent.EventType domainType = toDomainEventType(e.type());
        return MatchEvent.of(
                domainType,
                e.minute(),
                e.playerId(),
                e.playerName(),
                e.teamId(),
                e.description(),
                null,
                e.relatedPlayerId(),
                e.relatedPlayerName(),
                // LIVE-MATCH-F3-UI-LIVE BE2: expose the ON-player name as the
                // legacy playerOnName field too. Before V25, a 7-arg overload
                // accidentally stored this value as matchId, making reloads
                // lose substitution identity.
                e.relatedPlayerName()
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

package com.footballmanager.application.service.match.session;

import com.footballmanager.application.engine.match.MatchCommandHandler;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.LiveSessionContextView;
import com.footballmanager.application.service.simulation.detailed.LiveSnapshot;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.MatchTimeline;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.domain.model.valueobject.PlayerMatchRating;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchState;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchStatsModel;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Sesión interna de un partido en curso.
 *
 * <p>Thread-safe: usa estado inmutable (MatchStateSnapshot) volatile.
 *
 * via LiveSession.tick() for tick-by-tick SSE simulation. The legacy
 * path (detailedMatchSession == null) uses MatchTickHandler.
 */
public class MatchSession {

    private static final Logger log = LoggerFactory.getLogger(MatchSession.class);

    public final UUID matchId;
    private final UUID userId;
    private volatile MatchStateSnapshot currentState;
    private final MatchTickHandler tickHandler;
    private final ConcurrentLinkedQueue<MatchCommand> commandQueue;
    private final Sinks.Many<MatchStateSnapshot> stateSink;
    /** detailed live session — null means legacy path (use MatchTickHandler). */
    private final LiveSession detailedMatchSession;
    private final String lifecycleGeneration;

    /**
     * Returns null if this session is on the legacy (classic) path.
     * Callers that need detailed-match-specific behavior (manual substitutions, etc.)
     * must null-check.
     */
    public LiveSession getLiveSession() {
        return detailedMatchSession;
    }

    private Consumer<MatchFinishedResult> onFinishCallback;
    /** Guards the only externally visible terminal transition for this session. */
    private final AtomicBoolean terminalPublicationStarted = new AtomicBoolean(false);
    private final AtomicBoolean finishCallbackExecuted = new AtomicBoolean(false);

    /**
     * Legacy constructor — no LiveSession.
     * Uses MatchTickHandler for event generation.
     */
    public MatchSession(UUID userId, UUID matchId, MatchState state, MatchTickHandler tickHandler) {
        this(userId, matchId, state, tickHandler, null);
    }

    /**
     * Full constructor with optional LiveSession.
     *
     */
    public MatchSession(UUID userId, UUID matchId, MatchState state,
                        MatchTickHandler tickHandler, LiveSession detailedMatchSession) {
        this(userId, matchId, state, tickHandler, detailedMatchSession, null);
    }

    public MatchSession(UUID userId, UUID matchId, MatchState state,
                        MatchTickHandler tickHandler, LiveSession detailedMatchSession,
                        String lifecycleGeneration) {
        this.userId = userId;
        this.matchId = matchId;
        this.lifecycleGeneration = lifecycleGeneration;
        state.setLifecycleGeneration(lifecycleGeneration);
        this.currentState = convertToSnapshot(matchId, state);
        this.tickHandler = tickHandler;
        this.commandQueue = new ConcurrentLinkedQueue<>();
        // replay().latest() pattern — see RoundEngine.stateSink for the
        // full rationale. MatchSession feeds the per-match SSE stream
        // consumed by startMatchUseCase / live components.
        this.stateSink = Sinks.many().replay().latest();
        this.detailedMatchSession = detailedMatchSession;
    }

    public String getLifecycleGeneration() {
        return lifecycleGeneration;
    }

    private MatchStateSnapshot convertToSnapshot(UUID matchId, MatchState state) {
        Score newScore = new Score(
                state.getScore().home(),
                state.getScore().away()
        );
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
        if (terminalPublicationStarted.get() || isFinished()) {
            return;
        }
        this.currentState = currentState.withStatus(MatchStatus.RUNNING);
        emitState();
    }

    public MatchStateSnapshot advanceTick() {
        if (isFinished()) {
            return currentState;
        }

        if (detailedMatchSession != null) {
            // detailed match path: use LiveSession.tick() — no MatchTickHandler involved
            LiveSnapshot snap = detailedMatchSession.tick();
            if (snap.isFinished()) {
                return publishCanonicalDetailedTerminal(snap);
            }
            this.currentState = adaptDetailedSnapshot(snap);
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
            if (newState.status() == MatchStatus.FINISHED) {
                return publishLegacyTerminal(newState);
            }
            this.currentState = newState;
            emitState();
        }

        return currentState;
    }

    /**
     * Publishes the detailed-match terminal transition exactly once.
     *
     * <p>The completed {@link DetailedMatchResult} is read before any state is
     * marked FINISHED or sent to the replaying SSE sink. This makes the
     * terminal snapshot atomic from a connected client's perspective:
     * RUNNING snapshots may be stale while the engine is live, but a stale
     * FINISHED snapshot is never emitted.
     */
    private MatchStateSnapshot publishCanonicalDetailedTerminal(LiveSnapshot finalLiveSnapshot) {
        if (!terminalPublicationStarted.compareAndSet(false, true)) {
            return currentState;
        }
        try {
            DetailedMatchResult detailedResult = detailedMatchSession.finalResult();
            if (detailedResult == null) {
                throw new IllegalStateException("detailed match finished without a final result");
            }

            MatchStateSnapshot finalMetadata = adaptDetailedSnapshot(finalLiveSnapshot, MatchStatus.RUNNING);
            MatchStateSnapshot canonicalTerminal = finalizeDetailedSnapshot(finalMetadata, detailedResult);
            this.currentState = canonicalTerminal;
            emitState();
            invokeFinishCallback(canonicalTerminal, detailedResult);
            return canonicalTerminal;
        } catch (Exception exception) {
            log.error("canonical final match result was not published for matchId={}", matchId, exception);
            return currentState;
        }
    }

    private MatchStateSnapshot publishLegacyTerminal(MatchStateSnapshot terminalState) {
        if (!terminalPublicationStarted.compareAndSet(false, true)) {
            return currentState;
        }
        this.currentState = terminalState;
        emitState();
        invokeFinishCallback(terminalState, null);
        return terminalState;
    }

    private void invokeFinishCallback(MatchStateSnapshot canonicalTerminal, DetailedMatchResult detailedResult) {
        Consumer<MatchFinishedResult> callback = onFinishCallback;
        if (callback != null && finishCallbackExecuted.compareAndSet(false, true)) {
            callback.accept(new MatchFinishedResult(canonicalTerminal, detailedResult));
        }
    }

    /**
     * Creates the one final score projection for the detailed-match path.
     *
     * <p>The last SSE snapshot is a live, event-derived view. It is useful
     * while the match is running, but it is not the final authority: it may
     * still contain a deduplicated incremental timeline. At final whistle the
     * completed {@link DetailedMatchResult} is the engine's immutable result.
     * Every downstream sink (fixture, summary, standings, history and detail)
     * receives the {@link MatchFinishedResult} built from this normalized
     * snapshot, so they cannot consume a different final score.
     */
    MatchStateSnapshot finalizeDetailedSnapshot(DetailedMatchResult result) {
        return finalizeDetailedSnapshot(currentState, result);
    }

    private MatchStateSnapshot finalizeDetailedSnapshot(MatchStateSnapshot finalMetadata, DetailedMatchResult result) {
        if (!matchId.toString().equals(result.matchId())) {
            throw new IllegalStateException("detailed result matchId does not match live session");
        }
        if (finalMetadata.homeTeamId() != null
                && !finalMetadata.homeTeamId().toString().equals(result.homeTeamId())) {
            throw new IllegalStateException("detailed result home team does not match live session");
        }
        if (finalMetadata.awayTeamId() != null
                && !finalMetadata.awayTeamId().toString().equals(result.awayTeamId())) {
            throw new IllegalStateException("detailed result away team does not match live session");
        }

        List<DetailedMatchEvent> finalEvents = new ArrayList<>(result.timeline().events());
        for (DetailedMatchEvent event : detailedMatchSession.accumulatedEvents()) {
            if ((event.type() == DetailedMatchEventType.SUBSTITUTION
                    || event.type() == DetailedMatchEventType.TACTICAL_CHANGE)
                    && !finalEvents.contains(event)) {
                finalEvents.add(event);
            }
        }
        long homeGoalEvents = finalEvents.stream()
                .filter(event -> event.type() == DetailedMatchEventType.GOAL)
                .filter(event -> result.homeTeamId().equals(event.teamId()))
                .count();
        long awayGoalEvents = finalEvents.stream()
                .filter(event -> event.type() == DetailedMatchEventType.GOAL)
                .filter(event -> result.awayTeamId().equals(event.teamId()))
                .count();
        if (homeGoalEvents != result.homeGoals() || awayGoalEvents != result.awayGoals()) {
            throw new IllegalStateException("detailed final score does not reconcile with its goal events");
        }

        List<MatchEvent> authoritativeEvents = new ArrayList<>(finalEvents.size());
        for (DetailedMatchEvent event : finalEvents) {
            authoritativeEvents.add(toDomainMatchEvent(event));
        }
        return finalMetadata
                .withScore(new Score(result.homeGoals(), result.awayGoals()))
                .withEvents(authoritativeEvents)
                .withStatus(MatchStatus.FINISHED);
    }

    /**
     * Refresh the exposed match state from the detailed live engine without
     * advancing the match clock.
     *
     * <p>This keeps the UI/API snapshot aligned with manager actions applied
     * while the round is paused (for example queued injury substitution
     * modals). Without this, the next API read can still show the pre-action
     * lineup until the following tick.
     */
    public synchronized MatchStateSnapshot refreshDetailedSnapshot() {
        if (detailedMatchSession == null || terminalPublicationStarted.get() || isFinished()) {
            return currentState;
        }
        LiveSnapshot snapshot = detailedMatchSession.snapshot();
        if (snapshot.isFinished()) {
            return publishCanonicalDetailedTerminal(snapshot);
        }
        this.currentState = adaptDetailedSnapshot(snapshot);
        emitState();
        return currentState;
    }

    public void pause() {
        if (terminalPublicationStarted.get() || isFinished()) {
            return;
        }
        this.currentState = currentState.withStatus(MatchStatus.PAUSED);
        emitState();
    }

    public void resume() {
        if (terminalPublicationStarted.get() || isFinished()) {
            return;
        }
        this.currentState = currentState.withStatus(MatchStatus.RUNNING);
        emitState();
    }

    public boolean queueCommand(MatchCommand command, MatchCommandHandler commandHandler) {
        if (terminalPublicationStarted.get() || isFinished()) {
            return false;
        }
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

    public boolean belongsTo(UUID requestedUserId, String careerId) {
        if (userId == null || requestedUserId == null || !userId.equals(requestedUserId)) {
            return false;
        }
        String sessionCareerId = currentState == null ? null : currentState.careerId();
        return careerId == null || careerId.isBlank() || careerId.equals(sessionCareerId);
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
        if (terminalPublicationStarted.get() && currentState.status() != MatchStatus.FINISHED) {
            return;
        }
        stateSink.tryEmitNext(currentState);
    }

    /**
     * Adapt LiveSnapshot to MatchStateSnapshot for SSE stream.
     * Events are converted from DetailedMatchEvent → domain DetailedMatchEvent.
     *
     * (homePossession, awayPossession, homeStyle, awayStyle, homeFormation,
     * awayFormation) so the F3 UI can render the possession bar and the
     * current style/formation per team in real time.
     *
     * {@code awayPlayerRatings} (per-player live stats via
     * {@link PlayerMatchStatsModel#computeRatings(java.util.Collection, MatchTimeline)})
     * and {@code substitutionsRemaining} (max(0, 5 - count(SUBSTITUTION events))).
     * The ratings are computed against the LIVE partial timeline (events up to
     * {@code snap.minute()}), not the cached full-match engine result, so the
     * F4 substitution modal shows stats that update minute-by-minute.
     *
     * can drive it with controlled inputs. Not part of the public API.
     */
    MatchStateSnapshot adaptDetailedSnapshot(LiveSnapshot snap) {
        return adaptDetailedSnapshot(snap, snap.isFinished() ? MatchStatus.FINISHED : MatchStatus.RUNNING);
    }

    private MatchStateSnapshot adaptDetailedSnapshot(LiveSnapshot snap, MatchStatus status) {
        UUID homeTeamId = parseSnapshotTeamId(snap.homeTeamId(), currentState != null ? currentState.homeTeamId() : null);
        UUID awayTeamId = parseSnapshotTeamId(snap.awayTeamId(), currentState != null ? currentState.awayTeamId() : null);

        List<MatchEvent> adaptedEvents = new ArrayList<>();
        for (DetailedMatchEvent e : snap.allEvents()) {
            adaptedEvents.add(toDomainMatchEvent(e));
        }

        // to currentMinute — snap.allEvents() is already filtered by
        // LiveSession.buildSnapshot()) so the ratings reflect the live
        // match, NOT the final 90-minute projection.
        List<PlayerMatchRatingDto> homePlayerRatings = List.of();
        List<PlayerMatchRatingDto> awayPlayerRatings = List.of();
        LiveSessionContextView ctx = detailedMatchSession.contextView();
        if (ctx != null) {
            String homeIdStr = snap.homeTeamId();
            String awayIdStr = snap.awayTeamId();
            List<PlayerMatchState> homeStates = buildPlayerStates(
                    homeIdStr, ctx.homeStartingPlayers(), ctx.homeBenchPlayers());
            List<PlayerMatchState> awayStates = buildPlayerStates(
                    awayIdStr, ctx.awayStartingPlayers(), ctx.awayBenchPlayers());

            MatchTimeline liveTimeline = new MatchTimeline();
            for (DetailedMatchEvent e : snap.allEvents()) {
                liveTimeline.addEvent(e);
            }

            PlayerMatchStatsModel statsModel = new PlayerMatchStatsModel();
            homePlayerRatings = statsModel.computeRatings(homeStates, liveTimeline);
            awayPlayerRatings = statsModel.computeRatings(awayStates, liveTimeline);
        }

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
                status,
                new Score(snap.homeGoals(), snap.awayGoals()),
                adaptedEvents,
                currentState.careerId(),
                currentState.userId(),
                snap.homePossession(),
                snap.awayPossession(),
                snap.homeStyle(),
                snap.awayStyle(),
                snap.homeFormation(),
                snap.awayFormation(),
                toDomainRatings(homePlayerRatings),
                toDomainRatings(awayPlayerRatings),
                substitutionsRemaining,
                snap.homeSlots(),
                snap.awaySlots()
        );
    }

    private List<PlayerMatchRating> toDomainRatings(List<PlayerMatchRatingDto> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            return List.of();
        }
        return ratings.stream()
                .map(rating -> new PlayerMatchRating(
                        rating.playerId(),
                        rating.playerName(),
                        rating.teamId(),
                        rating.position(),
                        rating.rating(),
                        rating.goals(),
                        rating.assists(),
                        rating.keyPasses(),
                        rating.shots(),
                        rating.yellowCards(),
                        rating.redCards(),
                        rating.injuries(),
                        rating.fouls(),
                        rating.substitutedIn(),
                        rating.substitutedOut()))
                .toList();
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
     * or away team from the safe context view's starting + bench lists.
     * Returns an empty list when the team has no
     * players (defensive — never crashes SSE).
     */
    private List<PlayerMatchState> buildPlayerStates(
            String teamId,
            List<LiveSessionContextView.PlayerContextView> starting,
            List<LiveSessionContextView.PlayerContextView> bench) {
        if (teamId == null) {
            return List.of();
        }
        List<PlayerMatchState> states = new ArrayList<>();
        if (starting != null) {
            for (LiveSessionContextView.PlayerContextView p : starting) {
                if (p == null) continue;
                states.add(PlayerMatchState.fromContextView(p, teamId));
            }
        }
        if (bench != null) {
            for (LiveSessionContextView.PlayerContextView p : bench) {
                if (p == null) continue;
                states.add(PlayerMatchState.fromContextView(p, teamId));
            }
        }
        return states;
    }

    /**
     * Convert a DetailedMatchEvent to domain DetailedMatchEvent, preserving player attribution.
     * Used for SSE stream — no information loss since DetailedMatchEvent has all needed fields.
     *
     * {@code relatedPlayerName} from DetailedMatchEvent is propagated to the domain
     * {@code playerOnName} so the F3 UI can render "Salió X, entró Y" in the
     * timeline without resolving IDs.
     */
    private MatchEvent toDomainMatchEvent(DetailedMatchEvent e) {
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
                // legacy playerOnName field too. Before V25, a 7-arg overload
                // accidentally stored this value as matchId, making reloads
                // lose substitution identity.
                e.relatedPlayerName()
        );
    }

    /**
     * Map DetailedMatchEventType to domain DetailedMatchEventType.
     * Every DetailedMatchEventType maps explicitly — no lossy fallbacks.
     */
    private MatchEvent.EventType toDomainEventType(com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType detailedEventType) {
        if (detailedEventType == null) {
            throw new IllegalArgumentException("DetailedMatchEventType cannot be null");
        }
        return switch (detailedEventType) {
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
            case TACTICAL_CHANGE -> MatchEvent.EventType.TACTICAL_CHANGE;
        };
    }
}

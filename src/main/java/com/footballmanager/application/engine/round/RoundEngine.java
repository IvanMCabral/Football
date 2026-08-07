package com.footballmanager.application.engine.round;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Motor que coordina MÚLTIPLES partidos de una jornada.
 *
 * Thread-safe: usa MatchStateSnapshot inmutable.
 */
@Slf4j
public class RoundEngine {

    private static final Duration ROUND_TICK_INTERVAL = Duration.ofMillis(500);

    private final UUID roundId;
    private volatile UUID ownerId;
    private volatile String careerId;
    private volatile String lifecycleGeneration;
    private final Map<UUID, MatchEngine> matchEngines;
    private final RoundStatusCalculator statusCalculator;
    private final Sinks.Many<RoundState> stateSink;
    private volatile RoundState latestState;

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tickFuture;

    public RoundEngine(UUID roundId) {
        this(roundId, new RoundStatusCalculator());
    }

    public RoundEngine(UUID roundId, RoundStatusCalculator statusCalculator) {
        this.roundId = roundId;
        this.matchEngines = new ConcurrentHashMap<>();
        this.statusCalculator = statusCalculator;
        // Keep the latest round state available for SSE clients.
        this.stateSink = Sinks.many().replay().latest();
        this.latestState = new RoundState(
            roundId,
            Instant.now(),
            List.of(),
            RoundState.RoundStatus.NOT_STARTED);
    }

    public void registerMatch(UUID matchId, MatchEngine engine) {
        matchEngines.put(matchId, engine);
    }

    public synchronized void start() {
        if (matchEngines.isEmpty() || isRunning) {
            return;
        }

        matchEngines.values().forEach(MatchEngine::start);
        emitRoundState();

        isRunning = true;
        isPaused = false;
        startScheduler();
    }

    private void startScheduler() {
        shutdownScheduler(5, TimeUnit.SECONDS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "round-engine-" + roundId);
            t.setDaemon(true);
            return t;
        });
        tickFuture = scheduler.scheduleAtFixedRate(this::executeTick,
            ROUND_TICK_INTERVAL.toMillis(), ROUND_TICK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void shutdownScheduler(long timeout, TimeUnit unit) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(timeout, unit)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private synchronized void executeTick() {
        if (!isRunning || isPaused) {
            return;
        }

        for (MatchEngine engine : matchEngines.values()) {
            if (!engine.isFinished() && !engine.isPaused()) {
                int currentMinute = engine.getCurrentState().currentMinute();
                if (currentMinute < 90) {
                    try {
                        engine.advanceTick();
                    } catch (Exception e) {
                        // log and continue — let other matches progress.
                        // round-engine scheduler must keep firing ticks.
                        log.error("[ROUND-ENGINE] match advanced Tick FAILED for roundId={}: "
                                + "suppressing this match in this tick. Other matches continue.",
                                roundId, e);
                    }
                }
            }
        }

        List<MatchStateSnapshot> states = getMatchStates();
        if (statusCalculator.allFinished(states)) {
            emitCompletedState();
            stop();
        } else {
            emitRoundState();
        }
    }

    public synchronized void pauseAll() {
        if (!isRunning || isPaused) {
            return;
        }
        isPaused = true;
        matchEngines.values().forEach(MatchEngine::pause);
        emitRoundState();
    }

    public synchronized void resumeAll() {
        if (!isRunning || !isPaused) {
            return;
        }
        isPaused = false;
        matchEngines.values().forEach(MatchEngine::resume);
        emitRoundState();
    }

    private void emitRoundState() {
        List<MatchStateSnapshot> matchStates = getMatchStates();
        RoundState roundState = new RoundState(roundId, Instant.now(), matchStates,
            statusCalculator.calculate(matchStates));
        latestState = roundState;
        stateSink.tryEmitNext(roundState);
    }

    /** Assigns the exact owner metadata before the engine is published. */
    public void setOwner(UUID ownerId, String careerId) {
        setOwner(ownerId, careerId, null);
    }

    /** Assigns owner and fencing generation before publication. */
    public void setOwner(UUID ownerId, String careerId, String lifecycleGeneration) {
        this.ownerId = ownerId;
        this.careerId = careerId;
        this.lifecycleGeneration = lifecycleGeneration;
    }

    public String getLifecycleGeneration() {
        return lifecycleGeneration;
    }

    public boolean belongsTo(UUID requestedOwnerId, String requestedCareerId) {
        if (ownerId == null || requestedOwnerId == null || !ownerId.equals(requestedOwnerId)) {
            return false;
        }
        return requestedCareerId == null || requestedCareerId.isBlank()
                || requestedCareerId.equals(careerId);
    }

    public void emitCompletedState() {
        List<MatchStateSnapshot> matchStates = getMatchStates();
        RoundState completedState = new RoundState(roundId, Instant.now(), matchStates,
            RoundState.RoundStatus.COMPLETED);
        latestState = completedState;
        stateSink.tryEmitNext(completedState);
    }

    public synchronized void stop() {
        isRunning = false;
        isPaused = false;

        if (tickFuture != null) {
            tickFuture.cancel(false);
            tickFuture = null;
        }

        shutdownScheduler(5, TimeUnit.SECONDS);

        matchEngines.values().forEach(MatchEngine::stop);
        matchEngines.clear();
    }

    public Flux<RoundState> getStateStream() {
        return stateSink.asFlux();
    }

    /**
     * Returns the authoritative state for idempotent start requests. The
     * latest state is retained even after completion so a repeated request
     * cannot create a second scheduler or advance the career again.
     */
    public RoundState getLatestState() {
        return latestState;
    }

    public List<MatchStateSnapshot> getMatchStates() {
        return matchEngines.values().stream()
            .map(MatchEngine::getCurrentState)
            .collect(Collectors.toList());
    }

    public MatchEngine getMatchEngine(UUID matchId) {
        return matchEngines.get(matchId);
    }

    /**
     * Returns the current snapshot for a match registered in this round.
     */
    public MatchStateSnapshot getCurrentMatchSnapshot(UUID matchId) {
        MatchEngine engine = matchEngines.get(matchId);
        if (engine != null) {
            MatchStateSnapshot current = engine.getCurrentState();
            if (current != null) {
                return current;
            }
        }

        // Once a round completes, stop() releases the live MatchEngine
        // instances. Keep serving the immutable terminal snapshot retained in
        // latestState so a refresh or the final persistence callback cannot
        // fall back to an empty 0-0 state while the round is being cleaned up.
        return latestState.getMatches().stream()
            .filter(snapshot -> matchId.equals(snapshot.matchId()))
            .findFirst()
            .orElse(null);
    }

    public int getMatchCount() {
        return matchEngines.size();
    }

    public java.util.Set<UUID> getMatchIds() {
        return matchEngines.keySet();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }
}

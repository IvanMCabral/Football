package com.footballmanager.application.engine.round;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
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
public class RoundEngine {

    private static final Duration ROUND_TICK_INTERVAL = Duration.ofMillis(500);

    private final UUID roundId;
    private final Map<UUID, MatchEngine> matchEngines;
    private final RoundStatusCalculator statusCalculator;
    private final Sinks.Many<RoundState> stateSink;

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
        this.stateSink = Sinks.many().multicast().onBackpressureBuffer();
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
                    engine.advanceTick();
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
        stateSink.tryEmitNext(roundState);
    }

    public void emitCompletedState() {
        List<MatchStateSnapshot> matchStates = getMatchStates();
        RoundState completedState = new RoundState(roundId, Instant.now(), matchStates,
            RoundState.RoundStatus.COMPLETED);
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

    public List<MatchStateSnapshot> getMatchStates() {
        return matchEngines.values().stream()
            .map(MatchEngine::getCurrentState)
            .collect(Collectors.toList());
    }

    public MatchEngine getMatchEngine(UUID matchId) {
        return matchEngines.get(matchId);
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

package com.footballmanager.application.service.simulation.v24;

import java.util.List;
import java.util.Random;

/**
 * V24D6M11: Per-minute tick driver for live SSE matches.
 *
 * <p>Wraps V24DetailedMatchEngine to provide tick-by-tick simulation
 * compatible with the live SSE stream (MatchSession.advanceTick()).
 *
 * <p>Usage:
 * <pre>
 * V24LiveSession session = new V24LiveSession(context, seed);
 * while (!session.isFinished()) {
 *     V24LiveSnapshot snap = session.tick();
 *     // send snap via SSE
 * }
 * V24DetailedMatchResult finalResult = session.finalResult();
 * </pre>
 *
 * <p>Deterministic: same context + same seed = identical sequence of snapshots.
 */
public final class V24LiveSession {

    private final V24MatchContext context;
    private final long seed;
    private final Random random;

    private final V24DetailedMatchEngine engine;
    private final List<V24MatchEvent> accumulatedEvents;
    private int homeGoals;
    private int awayGoals;
    private int currentMinute;
    private int ticksRun;
    private boolean finished;

    public V24LiveSession(V24MatchContext context, long seed) {
        this.context = context;
        this.seed = seed;
        this.random = new Random(seed);
        this.engine = new V24DetailedMatchEngine();
        this.accumulatedEvents = new java.util.ArrayList<>();
        this.homeGoals = 0;
        this.awayGoals = 0;
        this.currentMinute = 0;
        this.ticksRun = 0;
        this.finished = false;
    }

    /**
     * Advance simulation by one tick (one match minute).
     *
     * @return V24LiveSnapshot with current state and events accumulated so far.
     */
    public V24LiveSnapshot tick() {
        if (finished) {
            return buildSnapshot();
        }

        // Each tick advances the clock inside the engine
        // Run a full match simulation each tick but only accumulate 1 minute of events
        // For efficiency, we run the engine once per tick but track per-minute state
        // Since V24DetailedMatchEngine.simulate() is a full 90-minute simulation,
        // we need to run it only once and then provide per-minute snapshots.
        // For tick-by-tick SSE, we run simulate() once lazily and then serve snapshots.

        if (ticksRun == 0) {
            // First tick: run the full engine simulation
            V24DetailedMatchResult result = engine.simulate(context, seed);
            // Extract home/away goals from result
            this.homeGoals = result.homeGoals();
            this.awayGoals = result.awayGoals();
            // Collect all events in order
            this.accumulatedEvents.addAll(result.timeline().events());
        }

        ticksRun++;
        currentMinute = Math.min(ticksRun, 90);

        // Determine if match is finished (after 90 ticks)
        if (currentMinute >= 90) {
            finished = true;
        }

        return buildSnapshot();
    }

    /**
     * Check if the match has finished (90 ticks have run).
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Get the accumulated per-minute possession totals.
     * Returns percentage for home team.
     */
    private int homePossession() {
        // Use engine's possession baseline (simplified for tick)
        double homeBase = switch (context.homeStyle()) {
            case BALANCED -> 0.50;
            case ATTACKING -> 0.55;
            case POSSESSION -> 0.60;
            case COUNTER -> 0.45;
            case DEFENSIVE -> 0.40;
        };
        return (int) Math.round(homeBase * 100);
    }

    private int awayPossession() {
        return 100 - homePossession();
    }

    /**
     * Build the current snapshot from accumulated state.
     */
    private V24LiveSnapshot buildSnapshot() {
        // Return events that occurred up to currentMinute
        List<V24MatchEvent> eventsSoFar = new java.util.ArrayList<>();
        for (V24MatchEvent e : accumulatedEvents) {
            if (e.minute() <= currentMinute) {
                eventsSoFar.add(e);
            }
        }

        // Count goals so far
        int homeGoalsSoFar = 0;
        int awayGoalsSoFar = 0;
        for (V24MatchEvent e : eventsSoFar) {
            if (e.type() == V24MatchEventType.GOAL) {
                if (e.teamId() != null && e.teamId().equals(context.homeTeamId())) {
                    homeGoalsSoFar++;
                } else if (e.teamId() != null && e.teamId().equals(context.awayTeamId())) {
                    awayGoalsSoFar++;
                } else if (e.teamId() != null && e.teamId().equals("HOME")) {
                    homeGoalsSoFar++;
                } else if (e.teamId() != null && e.teamId().equals("AWAY")) {
                    awayGoalsSoFar++;
                }
            }
        }

        return new V24LiveSnapshot(
                context.matchId(),
                currentMinute,
                homeGoalsSoFar,
                awayGoalsSoFar,
                context.homeTeamId(),
                context.awayTeamId(),
                finished,
                eventsSoFar,
                homePossession(),
                awayPossession()
        );
    }

    /**
     * Final result after match is finished.
     * Runs the engine if not yet run, then returns V24DetailedMatchResult
     * with full timeline for persistence.
     */
    public V24DetailedMatchResult finalResult() {
        if (ticksRun == 0) {
            return engine.simulate(context, seed);
        }
        // Already simulated once — re-run to get clean final result
        return engine.simulate(context, seed);
    }
}
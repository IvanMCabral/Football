package com.footballmanager.application.service.simulation.v24;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * V24D6M11: Per-minute tick driver for live SSE matches.
 *
 * <p>Wraps V24DetailedMatchEngine to provide tick-by-tick simulation
 * compatible with the live SSE stream (MatchSession.advanceTick()).
 *
 * <p>Usage (LIVE-MATCH-F1-POC / pre-F1):
 * <pre>
 * V24LiveSession session = new V24LiveSession(context, seed);
 * while (!session.isFinished()) {
 *     V24LiveSnapshot snap = session.tick();
 *     // send snap via SSE
 * }
 * V24DetailedMatchResult finalResult = session.finalResult();
 * </pre>
 *
 * <p>Usage (LIVE-MATCH-F2-LIVE F1 — replay path):
 * <pre>
 * V24LiveSession session = new V24LiveSession(context, seed);
 * session.tick(); // establish the initial cache
 *
 * // Manager applies a substitution: mutate the effective context + replay.
 * session.mutateContext(ctx -> ctx.withSubstitution("home-starter-9", "home-bench-9"));
 *
 * // Continue ticking — the snapshots reflect the substitution's effect.
 * while (!session.isFinished()) session.tick();
 * V24DetailedMatchResult result = session.finalResult(); // includes the substitution
 * </pre>
 *
 * <p>Deterministic: same context + same seed + same substitutions = identical
 * sequence of snapshots AND identical final result. The replay path (B3)
 * preserves this by routing every draw through a single
 * {@link CachingRandomWrapper} and invalidating from the right minute on
 * every mutation.
 *
 * <p><b>Live changes for LIVE-MATCH-F2-LIVE F1 B3</b>:
 * <ul>
 *   <li>Field renamed {@code Random random} → {@code CachingRandomWrapper
 *       cachedRandom} so the engine's replay-path overload
 *       {@code simulate(ctx, Random)} intercepts all draws.</li>
 *   <li>Field renamed {@code context} → {@code effectiveContext} (still the
 *       same value at construction; {@link #mutateContext} replaces it).</li>
 *   <li>{@link #tick()} now runs {@code engine.simulate(effectiveContext,
 *       cachedRandom)} on EVERY tick. The wrapper's cache makes this
 *       deterministic — same context + no mutations → same draws → same
 *       result every tick. CPU cost: 1 simulation per tick (acceptable per
 *       F1 metric tick() ≤ 5ms).</li>
 *   <li>{@link #replayFromMinute(int)} (NEW): invalidates the cache from
 *       the given minute onward and re-runs the engine.</li>
 *   <li>{@link #mutateContext(UnaryOperator)} (NEW): applies a mutation to
 *       the effective context then calls {@code replayFromMinute(currentMinute)}
 *       so subsequent ticks see the mutated state. // LIVE-MATCH-F2-F2: wire aquí
 *       — F2 will replace the legacy {@code SubstitutionCommandUseCaseImpl}
 *       body with a call to this method instead of just appending to the
 *       event cache.</li>
 *   <li>{@link #finalResult()} uses the cached {@code V24DetailedMatchResult}
 *       when available (B3 of F1).</li>
 *   <li>{@code homePossession()}/{@code awayPossession()} read from the
 *       cached engine result (real possession ticks), no longer static
 *       per-style (B3 of F1).</li>
 * </ul>
 */
public final class V24LiveSession {

    private static final Logger log = LoggerFactory.getLogger(V24LiveSession.class);

    /** The "effective" context that the engine actually simulates. Mutated by {@link #mutateContext}. */
    private volatile V24MatchContext effectiveContext;

    private final long seed;

    /**
     * The single source of randomness for the engine's replay-path overload.
     * All doubles consumed by {@link V24DetailedMatchEngine#simulate} are
     * intercepted and cached by this wrapper. See {@link CachingRandomWrapper}.
     */
    private final CachingRandomWrapper cachedRandom;

    /**
     * Maps each match minute (1-90) to the index in {@link #cachedRandom}'s
     * double cache where that minute's draws begin. Used by
     * {@link #replayFromMinute(int)} to translate a minute argument into a
     * precise cache-truncation index.
     *
     * <p>Built ONCE in the constructor with the expected uniform
     * distribution (~150 doubles/minute for the expected ~13.500 per match).
     * The actual count may differ slightly; the replay still preserves
     * determinism for the prefix that was NOT invalidated.
     */
    private final DoubleCacheIndex cacheIndex;

    private final V24DetailedMatchEngine engine;

    /**
     * The cached engine result from the most recent {@code tick()} /
     * {@code replayFromMinute()} call. Used by:
     * <ul>
     *   <li>{@link #finalResult()} — avoid re-running the engine when the
     *       result is already cached.</li>
     *   <li>{@link #homePossession()} / {@link #awayPossession()} — derive
     *       the live possession percentages from the engine's real
     *       possessionTicks counter.</li>
     * </ul>
     */
    private volatile V24DetailedMatchResult cachedResult;

    /**
     * The full engine timeline (all 90 minutes) — replaced wholesale on each
     * {@link #tick()} / {@link #replayFromMinute(int)} call.
     */
    private final List<V24MatchEvent> engineTimeline;

    /**
     * Manual events added via {@link #recordManualSubstitution} (POC F1
     * legacy method) — preserved across ticks. {@link #accumulatedEvents()}
     * returns the concatenation {@code engineTimeline_filtered_by_currentMinute + manualEvents}.
     */
    private final List<V24MatchEvent> manualEvents;

    private int homeGoals;
    private int awayGoals;
    private int currentMinute;
    private int ticksRun;
    private boolean finished;

    public V24LiveSession(V24MatchContext context, long seed) {
        this.effectiveContext = context;
        this.seed = seed;
        this.cachedRandom = new CachingRandomWrapper(seed);
        this.engine = new V24DetailedMatchEngine();
        // Estimated cache size: ~13.500 doubles per match per the analysis.
        // Real consumption will be measured in B5's V24LiveSessionConcurrencyTest
        // and used to refine the index (B2 already supports this via
        // buildFromWrapperBoundaryMarks when instrumented).
        this.cacheIndex = DoubleCacheIndex.buildUniformApproximation(13_500);
        this.engineTimeline = new ArrayList<>();
        this.manualEvents = new ArrayList<>();
        this.homeGoals = 0;
        this.awayGoals = 0;
        this.currentMinute = 0;
        this.ticksRun = 0;
        this.finished = false;
    }

    /**
     * Advance simulation by one tick (one match minute).
     *
     * <p>Calls {@code engine.simulate(effectiveContext, cachedRandom)} on
     * every tick. The {@link CachingRandomWrapper} replays the same cached
     * doubles on every call (LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007), so the
     * engine sees the IDENTICAL draw sequence on every tick when no
     * mutation has happened — the live score is stable across ticks
     * (no flicker).
     *
     * <p>For mutations, {@link #mutateContext} calls
     * {@link CachingRandomWrapper#invalidateFromIndex(int)} which truncates
     * the cache from a given minute onward, so the next engine call sees
     * the same prefix + new draws. The F2 substitution contract is
     * preserved (manual subs still alter the result).
     *
     * <p>Cost: one full simulation per tick. Empirically &lt; 5ms (F1 metric).
     *
     * @return V24LiveSnapshot with current state and events accumulated so far.
     */
    public synchronized V24LiveSnapshot tick() {
        if (finished) {
            return buildSnapshot();
        }

        // LIVE-MATCH-F2-LIVE F1 B3: every tick runs the engine through the
        // caching wrapper. The wrapper captures every draw so future
        // mutateContext() calls can replay from the right minute.
        // LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: rewind the wrapper before
        // the engine call so the engine replays the SAME cached draws
        // (the previous BUG-007 behavior was that the wrapper consumed a
        // fresh batch of doubles on every call, producing a different
        // timeline per tick and a flickering score in the F3 live UI).
        cachedRandom.rewind();
        V24DetailedMatchResult result = engine.simulate(effectiveContext, cachedRandom);
        this.cachedResult = result;
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        // Replace engineTimeline with the new simulation's timeline.
        // (manualEvents are kept separately — see recordManualSubstitution.)
        this.engineTimeline.clear();
        this.engineTimeline.addAll(result.timeline().events());

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
     * LIVE-MATCH-F2-LIVE F1 B3 — bug colateral 1 fix.
     *
     * <p>Previously returned a STATIC per-style constant (e.g. 50% for
     * BALANCED). Now reads the engine's real possessionTicks counter from
     * the cached engine result. Falls back to 50/50 only if no engine run
     * has happened yet (first tick not yet executed).
     */
    private int homePossession() {
        if (cachedResult != null) {
            return cachedResult.homePossession();
        }
        return 50;
    }

    private int awayPossession() {
        if (cachedResult != null) {
            return cachedResult.awayPossession();
        }
        return 50;
    }

    /**
     * Build the current snapshot from accumulated state.
     */
    private V24LiveSnapshot buildSnapshot() {
        // Return engine events that occurred up to currentMinute, plus
        // any manual events (recorded via recordManualSubstitution) that
        // happened at or before currentMinute.
        List<V24MatchEvent> eventsSoFar = new ArrayList<>();
        for (V24MatchEvent e : engineTimeline) {
            if (e.minute() <= currentMinute) {
                eventsSoFar.add(e);
            }
        }
        for (V24MatchEvent e : manualEvents) {
            if (e.minute() <= currentMinute) {
                eventsSoFar.add(e);
            }
        }

        // Count goals so far (defensive: also works if cachedResult is stale)
        int homeGoalsSoFar = 0;
        int awayGoalsSoFar = 0;
        for (V24MatchEvent e : eventsSoFar) {
            if (e.type() == V24MatchEventType.GOAL) {
                if (e.teamId() != null && e.teamId().equals(effectiveContext.homeTeamId())) {
                    homeGoalsSoFar++;
                } else if (e.teamId() != null && e.teamId().equals(effectiveContext.awayTeamId())) {
                    awayGoalsSoFar++;
                }
            }
        }

        return new V24LiveSnapshot(
                effectiveContext.matchId(),
                currentMinute,
                homeGoalsSoFar,
                awayGoalsSoFar,
                effectiveContext.homeTeamId(),
                effectiveContext.awayTeamId(),
                finished,
                eventsSoFar,
                homePossession(),
                awayPossession(),
                effectiveContext.homeStyle() != null ? effectiveContext.homeStyle().name() : null,
                effectiveContext.awayStyle() != null ? effectiveContext.awayStyle().name() : null,
                effectiveContext.homeFormation(),
                effectiveContext.awayFormation()
        );
    }

    /**
     * LIVE-MATCH-F2-LIVE F1 B3 — bug colateral 2 fix.
     *
     * <p>Previously ALWAYS re-ran the engine (CPU waste). Now uses the
     * cached engine result from the last {@link #tick()} /
     * {@link #replayFromMinute(int)} call. If no tick has happened yet,
     * runs the engine once and caches.
     */
    public V24DetailedMatchResult finalResult() {
        if (cachedResult == null) {
            // No tick has happened yet — run once and cache.
            // LIVE-MATCH-F3-UI-LIVE F5.1 BUG-007: rewind the wrapper first
            // so the engine consumes from the start of the cache. After
            // this first call, the cache is populated and the next
            // engine call (from tick() or replayFromMinute()) will replay
            // the same draws for determinism.
            cachedRandom.rewind();
            this.cachedResult = engine.simulate(effectiveContext, cachedRandom);
            this.homeGoals = cachedResult.homeGoals();
            this.awayGoals = cachedResult.awayGoals();
            this.engineTimeline.clear();
            this.engineTimeline.addAll(cachedResult.timeline().events());
        }
        return cachedResult;
    }

    // ========== LIVE-MATCH-F2-LIVE F2: manual substitution injection (alters result) ==========

    /**
     * LIVE-MATCH-F2-LIVE F2: record a manual substitution event from the user.
     *
     * <p>F2 wire: this method now drives the substitution through the F1
     * replay path so {@code homeGoals}/{@code awayGoals} actually change.
     * It extracts the off/on player IDs and the teamId from the
     * {@link V24MatchEvent} and invokes {@link #mutateContext} with
     * {@link V24MatchContext#withManualSubstitution}, which swaps the two
     * players between the starting and bench lists of the target team.
     * {@code mutateContext} triggers {@link #replayFromMinute(int)} from
     * {@code currentMinute} so the engine's next tick picks up the new
     * lineup and recomputes goals/xG from that minute onward.
     *
     * <p>This is the SINGLE entry point for F2 option (a) of the prompt:
     * any caller (use case, tests, future direct consumers) that hands a
     * valid SUBSTITUTION event to this method gets a match result that
     * reflects the sub. The POC F1 D1=B invariant (substitution is
     * UI-only) has been removed.
     *
     * <p>The event is also appended to {@code manualEvents} (after the
     * mutate+replay) so the F3 UI can render the substitution in the
     * timeline regardless of which minute it was applied at.
     *
     * <p>Validation: if the event's playerOffId is not in the starting XI
     * of the event's teamId, the {@code withManualSubstitution} helper
     * throws {@link IllegalArgumentException}, which propagates up (this
     * preserves the F0 contract that invalid subs are rejected). The
     * engine's own {@link V24SubstitutionEngine#manualSubstitute} checks
     * are a superset and run in the use case path.
     *
     * @param event the substitution event (must be of type {@link V24MatchEventType#SUBSTITUTION})
     * @throws IllegalStateException    if the match has already finished
     * @throws IllegalArgumentException if the event type is not SUBSTITUTION,
     *                                  or if playerOffId is not in starting XI,
     *                                  or if playerOnId is not on the bench
     */
    public synchronized void recordManualSubstitution(V24MatchEvent event) {
        if (finished) {
            throw new IllegalStateException("Match already finished");
        }
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.type() != V24MatchEventType.SUBSTITUTION) {
            throw new IllegalArgumentException(
                "Expected SUBSTITUTION event, got " + event.type());
        }
        // F2 WIRE: drive the substitution through the F1 replay path.
        // Extract the off/on player IDs and teamId from the event, then
        // delegate to withManualSubstitution which swaps the players in
        // the effective context. mutateContext triggers replayFromMinute
        // automatically (when currentMinute >= 1), so the engine's next
        // tick uses the new lineup.
        final String teamId = event.teamId();
        final String playerOffId = event.playerId();
        final String playerOnId = event.relatedPlayerId();
        final int minute = event.minute();

        mutateContext(ctx -> ctx.withManualSubstitution(
            teamId, playerOffId, playerOnId, minute));

        // Append the event to manualEvents AFTER the swap so the timeline
        // shows the substitution regardless of which minute it was applied
        // at. manualEvents is preserved across replays (engineTimeline is
        // replaced, manualEvents is not).
        this.manualEvents.add(event);
        log.info("[LIVE-MATCH-F2-F2] Manual substitution recorded + applied: teamId={} off={} on={} minute={}",
            teamId, playerOffId, playerOnId, minute);
    }

    // ========== LIVE-MATCH-F2-LIVE F5 (B3.2): tactical change event recording ==========

    /**
     * LIVE-MATCH-F2-LIVE F5 (B3.2): record a tactical change event (style/formation)
     * initiated by the manager. Mirrors the {@link #recordManualSubstitution} contract:
     * the event is appended to the live session so the F3 UI can render it, but
     * the goals/xG are recomputed by the {@link #replayFromMinute(int)} call that
     * the {@code TacticalChangeService} drives through {@link #mutateContext}.
     *
     * <p>{@code synchronized} for the same reason as {@code recordManualSubstitution}:
     * the {@code RoundEngine} scheduler ticks every 500ms and can race with a
     * controller POST. The event list mutation must be atomic w.r.t. tick().
     *
     * <p>Per the F5 spec: NO mutation of {@code effectiveContext} here (the
     * service does that via {@code mutateContext} first). NO trigger of
     * {@code replayFromMinute} here either — the service is the single owner
     * of the mutate + replay sequence.
     *
     * @param event the tactical-change event (must be of type {@link V24MatchEventType#TACTICAL_CHANGE})
     * @throws IllegalStateException    if the match has already finished
     * @throws IllegalArgumentException if event is null or its type is not TACTICAL_CHANGE
     */
    public synchronized void recordTacticalChange(V24MatchEvent event) {
        if (finished) {
            throw new IllegalStateException("Match already finished");
        }
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.type() != V24MatchEventType.TACTICAL_CHANGE) {
            throw new IllegalArgumentException(
                "Expected TACTICAL_CHANGE event, got " + event.type());
        }
        this.manualEvents.add(event);
        log.info("[LIVE-MATCH-F2-F5] Tactical change recorded: minute={} teamId={} description='{}'",
            event.minute(), event.teamId(), event.description());
    }

    // ========== LIVE-MATCH-F2-LIVE F1 B3 — replay path (NEW API) ==========

    /**
     * LIVE-MATCH-F2-LIVE F1 B3 — invalidate the double cache from the
     * start of {@code fromMinute} onward and re-run the engine. The result
     * replaces the cached result, so subsequent {@link #tick()} calls and
     * {@link #finalResult()} see the re-played match.
     *
     * <p>Determinism contract: replaying with the SAME {@code effectiveContext}
     * produces the SAME result as the original run for all minutes &lt;
     * {@code fromMinute} (the prefix is preserved). Minutes &ge;
     * {@code fromMinute} may differ if the engine's draw sequence is
     * affected (currently it always re-runs the full 90 minutes, so the
     * prefix is exactly preserved up to {@code cacheIndex.indexForMinute(fromMinute)}
     * doubles).
     *
     * @param fromMinute 1-indexed match minute in [1, currentMinute]; from this
     *                   minute onward the cache is discarded and the engine
     *                   re-runs with the new draws.
     * @throws IllegalArgumentException if fromMinute is out of range
     * @throws IllegalStateException    if the match has already finished
     */
    public synchronized void replayFromMinute(int fromMinute) {
        if (finished) {
            throw new IllegalStateException("Match already finished — cannot replay");
        }
        if (fromMinute < 1 || fromMinute > currentMinute) {
            throw new IllegalArgumentException(
                "fromMinute must be in [1, " + currentMinute + "], got " + fromMinute);
        }
        // Truncate the cache at the start of fromMinute.
        int index = cacheIndex.indexForMinute(fromMinute);
        cachedRandom.invalidateFromIndex(index);
        log.debug("[LIVE-MATCH-F2-F1] replayFromMinute({}) invalidated cache index {}, replaying engine",
            fromMinute, index);

        // Re-run the engine with the (possibly mutated) effective context.
        V24DetailedMatchResult result = engine.simulate(effectiveContext, cachedRandom);
        this.cachedResult = result;
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        // Replace engineTimeline with the new simulation's timeline.
        // (manualEvents are preserved across replays — they were added by
        // recordManualSubstitution and remain visible regardless of replays.)
        this.engineTimeline.clear();
        this.engineTimeline.addAll(result.timeline().events());
        log.info("[LIVE-MATCH-F2-F1] replay complete: homeGoals={} awayGoals={} events={}",
            homeGoals, awayGoals, engineTimeline.size() + manualEvents.size());
    }

    /**
     * LIVE-MATCH-F2-LIVE F1 B3 — apply a mutation to the {@code effectiveContext}
     * and trigger a replay from the current minute so subsequent ticks reflect
     * the new state.
     *
     * <p>// LIVE-MATCH-F2-F2: wire aquí — Phase 2 (F2 of LIVE-MATCH-F2-LIVE)
     * will replace the body of {@code SubstitutionCommandUseCaseImpl.executeSubstitution}
     * to call this method (with a UnaryOperator that applies the substitution
     * to the V24TeamMatchState) instead of just appending to
     * {@code accumulatedEvents} via {@code recordManualSubstitution}.
     *
     * <p>For F1 this method exists as the API surface for F2 to call into.
     *
     * @param mutator a function that takes the current effective context
     *                and returns a new (mutated) context. Must not be null.
     * @throws IllegalStateException    if the match has already finished
     * @throws IllegalArgumentException if mutator returns null
     */
    public synchronized void mutateContext(UnaryOperator<V24MatchContext> mutator) {
        if (mutator == null) {
            throw new IllegalArgumentException("mutator must not be null");
        }
        if (finished) {
            throw new IllegalStateException("Match already finished — cannot mutate");
        }
        V24MatchContext next = mutator.apply(effectiveContext);
        if (next == null) {
            throw new IllegalArgumentException("mutator must not return null");
        }
        this.effectiveContext = next;
        log.debug("[LIVE-MATCH-F2-F1] mutateContext applied, triggering replay from currentMinute={}",
            currentMinute);
        // Replay from the current minute — past draws are preserved,
        // future draws will use the mutated context.
        if (currentMinute >= 1) {
            replayFromMinute(currentMinute);
        }
    }

    /**
     * LIVE-MATCH-F1-POC: expose current minute for SubstitutionCommandUseCase.
     * The session's current minute is the authoritative time reference —
     * the request's {@code requestedMinute} from the API body is overridden
     * by this value to avoid drift between client clock and server tick.
     */
    public int currentMinute() {
        return currentMinute;
    }

    /**
     * LIVE-MATCH-F1-POC: expose context (read-only view) for SubstitutionCommandUseCase.
     * Consumers MUST treat the returned object as immutable; mutations are not
     * expected outside of {@link V24PlayerMatchState} per-player mutations.
     *
     * <p>NOTE: for F1 we return the EFFECTIVE context (which may differ from
     * the original constructor argument if {@link #mutateContext} has been
     * called). Callers needing the original context should hold their own
     * reference from the constructor.
     */
    public V24MatchContext context() {
        return effectiveContext;
    }

    /**
     * LIVE-MATCH-F1-POC: read-only accessor for accumulated events.
     * Returns an unmodifiable CONCATENATED view of:
     * <ul>
     *   <li>Engine events up to {@link #currentMinute()} (from the latest
     *       {@link #tick()} or {@link #replayFromMinute(int)} call)</li>
     *   <li>Plus all manual events added via
     *       {@link #recordManualSubstitution} (POC F1 legacy).</li>
     * </ul>
     * Consumers cannot mutate the returned list (unmodifiable view).
     */
    public List<V24MatchEvent> accumulatedEvents() {
        List<V24MatchEvent> combined = new ArrayList<>(engineTimeline.size() + manualEvents.size());
        combined.addAll(engineTimeline);
        combined.addAll(manualEvents);
        return java.util.Collections.unmodifiableList(combined);
    }
}

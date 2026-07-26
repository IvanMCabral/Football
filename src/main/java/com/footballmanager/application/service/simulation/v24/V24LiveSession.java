package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.FormationSlotDTO;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 *
 * compatible with the live SSE stream (MatchSession.advanceTick()).
 *
 * <pre>
 * V24LiveSession session = new V24LiveSession(context, seed);
 * while (!session.isFinished()) {
 *     V24LiveSnapshot snap = session.tick();
 *     // send snap via SSE
 * }
 * </pre>
 *
 * <pre>
 * V24LiveSession session = new V24LiveSession(context, seed);
 * session.tick(); // establish the initial cache
 *
 * // Manager applies a substitution: mutate the effective context + replay.
 * session.mutateContext(ctx -> ctx.withSubstitution("home-starter-9", "home-bench-9"));
 *
 * // Continue ticking — the snapshots reflect the substitution's effect.
 * while (!session.isFinished()) session.tick();
 * </pre>
 *
 * <p>Deterministic: same context + same seed + same substitutions = identical
 * sequence of snapshots AND identical final result. The replay path (B3)
 * preserves this by routing every draw through a single
 * {@link CachingRandomWrapper} and invalidating from the right minute on
 * every mutation.
 *
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
 *       — F2 will replace the legacy {@code SubstitutionCommandUseCaseImpl}
 *       body with a call to this method instead of just appending to the
 *       event cache.</li>
 *       when available (B3 of F1).</li>
 *   <li>{@code homePossession}/{@code awayPossession} (in the snapshot)
 *       are now derived from the eventsSoFar subset in {@link #buildSnapshot()}
 *       changes minute-by-minute instead of the final 56%/44% from the
 *       first tick onward.</li>
 * </ul>
 */
public final class V24LiveSession {

    private static final Logger log = LoggerFactory.getLogger(V24LiveSession.class);

    /** The "effective" context that the engine actually simulates. Mutated by {@link #mutateContext}. */
    private volatile V24MatchContext effectiveContext;

    private final long seed;

    /**
     * The single source of randomness for the engine's replay-path overload.
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

        // caching wrapper. The wrapper captures every draw so future
        // mutateContext() calls can replay from the right minute.
        // the engine call so the engine replays the SAME cached draws
        // fresh batch of doubles on every call, producing a different
        // timeline per tick and a flickering score in the F3 live UI).
        cachedRandom.rewind();
        // runs only minutes [1, ticksRun+1] instead of the full 90, so
        // each live SSE tick costs ~1ms / 90 instead of ~1ms. The
        // CachingRandomWrapper replay contract is preserved because the
        // wrapper replays the same draws from minute 1 on every call —
        // minute [1..ticksRun+1] draws match the unbounded prefix.
        int maxMinute = Math.min(ticksRun + 1, 90);
        V24DetailedMatchResult result = engine.simulate(effectiveContext, cachedRandom, maxMinute);
        this.cachedResult = result;
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        ticksRun++;
        currentMinute = Math.min(ticksRun, 90);
        // Merge instead of replacing: in live mode, events already shown to the
        // user must not disappear on the next bounded replay. Manager actions
        // that intentionally recalculate the match still use replayFromMinute().
        mergeVisibleEngineTimeline(result.timeline().events(), currentMinute);

        // Determine if match is finished (after 90 ticks)
        if (currentMinute >= 90) {
            finished = true;
        }

        return buildSnapshot();
    }

    private void mergeVisibleEngineTimeline(List<V24MatchEvent> newEvents, int upToMinute) {
        Map<String, V24MatchEvent> merged = new LinkedHashMap<>();
        for (V24MatchEvent event : this.engineTimeline) {
            if (event.minute() <= upToMinute) {
                merged.put(eventKey(event), event);
            }
        }
        for (V24MatchEvent event : newEvents) {
            if (event.minute() <= upToMinute) {
                merged.putIfAbsent(eventKey(event), event);
            }
        }
        this.engineTimeline.clear();
        this.engineTimeline.addAll(merged.values());
        this.engineTimeline.sort(Comparator.comparingInt(V24MatchEvent::minute));
    }

    private String eventKey(V24MatchEvent event) {
        return event.minute()
            + "|" + event.type()
            + "|" + nullSafe(event.teamId())
            + "|" + nullSafe(event.playerId())
            + "|" + nullSafe(event.relatedPlayerId())
            + "|" + event.description()
            + "|" + event.xg();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Return the current live snapshot without advancing the clock.
     *
     * <p>Used after manual manager actions (for example substitutions while
     * the round is paused by a modal) so API/SSE consumers immediately read
     * the same lineup that the engine already mutated internally.
     */
    public synchronized V24LiveSnapshot snapshot() {
        return buildSnapshot();
    }

    /**
     * Check if the match has finished (90 ticks have run).
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * now derived from the eventsSoFar subset in {@link #buildSnapshot()}
     * (see {@link #derivePossessionFromEvents}). The previous
     * {@code homePossession()}/{@code awayPossession()} private helpers
     * that read from the cached engine result have been removed because
     * the cached result is the FINAL possession, not the per-minute value.
     */

    /**
     * "noise" for the user-facing live UI. The engine still emits them
     * to the internal timeline (and to the persistence path) — they are
     * ONLY filtered from the snapshot that goes out via SSE.
     *
     * <p>The implicit "importance threshold" measured by Iván in F5.1 is
     * <b>~30-50 important events per match</b>: without this filter the
     * ~30-50 are "interesting" for a manager watching live (goals, shots,
     * subs, cards, possession-relevant events). The six types below
     * constitute the filtered set — each one represents a transient
     * micro-event the manager does NOT need to see tick-by-tick.
     *
     * <p>{@link #NOISE_EVENT_THRESHOLD_MIN} is the minimum number of
     * filtered types. If a future change drops the Set size below this
     * reaching the SSE consumer) and the team should review the
     * filter list explicitly rather than accept a silent regression.
     *
     * <p>Note: SHOT is kept (the spec says "SHOT_ON_TARGET (incluye
     * saves y goals)" — we keep SHOT separately so the UI can still
     * distinguish a shot off target from a chance created). The current
     * engine represents non-goal attempts as MISS or BLOCK; MISS stays
     * hidden as noise, while BLOCK is kept because a blocked shot is useful
     * tactical feedback (pressure, central congestion, defensive line).
     * SAVE is kept because it represents a visible event (the goalkeeper
     * stopped a shot on target).
     */
    public static final int NOISE_EVENT_THRESHOLD_MIN = 5;

    private static final java.util.Set<V24MatchEventType> NOISE_EVENTS = java.util.Set.of(
        V24MatchEventType.CHANCE_CREATED,
        V24MatchEventType.OFFSIDE,
        V24MatchEventType.CORNER,
        V24MatchEventType.FOUL,
        V24MatchEventType.MISS
    );

    /**
     * filter Set size at class-load time. If a future refactor drops
     * the Set below the documented minimum, fail fast — better to
     * crash at startup than to ship a regression where 60+ events per
     * match flood the SSE consumer.
     */
    static {
        if (NOISE_EVENTS.size() < NOISE_EVENT_THRESHOLD_MIN) {
            throw new IllegalStateException(
                "NOISE_EVENTS Set shrunk below the BUG-009 threshold: "
                    + "expected at least " + NOISE_EVENT_THRESHOLD_MIN
                    + " filtered types, found " + NOISE_EVENTS.size()
                    + ". Review the F5.2 noise filter contract before shipping.");
        }
    }

    /**
     * Build the current snapshot from accumulated state.
     *
     * events list returned to the SSE consumer. Possession and goals are
     * STILL derived from the un-filtered {@code eventsSoFar} (the noise
     * events are part of the possession story), so the score and
     * possession remain accurate. Only the visible UI event list is
     * trimmed.
     *
     * {@code awayPossession} from the {@code eventsSoFar} subset (i.e.
     * the events that occurred up to {@code currentMinute}), NOT from
     * the cached engine result. The previous behaviour returned the
     * FINAL possession at every tick, so the live UI showed 56% / 44%
     * (the final value) even at minute 2.
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

        // subset, NOT from the cached engine result (which is the FINAL
        // value). We count team-attributed events as a proxy for possession
        // activity. The formula is the ratio of home team-attributed events
        // to the total team-attributed events in the visible window, with
        // a defensive 50/50 default when no team-attributed events exist
        // yet (minute 0-1).
        int homePossession = derivePossessionFromEvents(eventsSoFar, true);
        int awayPossession = 100 - homePossession;

        // The eventsSoFar list (used for score + possession calculation
        // above) is NOT filtered — possession needs the full picture. The
        // list that goes to the consumer (eventsForSse) is the trimmed one.
        List<V24MatchEvent> eventsForSse = new ArrayList<>(eventsSoFar.size());
        for (V24MatchEvent e : eventsSoFar) {
            if (!NOISE_EVENTS.contains(e.type())) {
                eventsForSse.add(e);
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
                eventsForSse,
                homePossession,
                awayPossession,
                effectiveContext.homeStyle() != null ? effectiveContext.homeStyle().name() : null,
                effectiveContext.awayStyle() != null ? effectiveContext.awayStyle().name() : null,
                effectiveContext.homeFormation(),
                effectiveContext.awayFormation(),
                buildLiveSlotsForSnapshot(
                        startersForSnapshot(true),
                        slotsForSnapshot(true)),
                buildLiveSlotsForSnapshot(
                        startersForSnapshot(false),
                        slotsForSnapshot(false))
        );
    }

    private List<SessionPlayer> startersForSnapshot(boolean home) {
        String teamId = home ? effectiveContext.homeTeamId() : effectiveContext.awayTeamId();
        List<SessionPlayer> starters = new ArrayList<>(
                home ? effectiveContext.homeStartingPlayers() : effectiveContext.awayStartingPlayers());
        List<SessionPlayer> bench = home ? effectiveContext.homeBenchPlayers() : effectiveContext.awayBenchPlayers();

        for (V24MatchContext.ScheduledSub sub : scheduledSubsForSnapshot()) {
            if (!teamId.equals(sub.teamId()) || sub.effectiveMinute() > currentMinute) {
                continue;
            }
            SessionPlayer playerOn = findPlayerById(playersForSnapshotLookup(home), sub.playerOnId());
            if (playerOn == null) {
                continue;
            }
            for (int i = 0; i < starters.size(); i++) {
                SessionPlayer player = starters.get(i);
                if (player != null && sub.playerOffId().equals(player.getSessionPlayerId())) {
                    starters.set(i, playerOn);
                    break;
                }
            }
        }
        return starters;
    }

    private Map<String, LineupSlotDTO> slotsForSnapshot(boolean home) {
        String teamId = home ? effectiveContext.homeTeamId() : effectiveContext.awayTeamId();
        Map<String, LineupSlotDTO> base = home
                ? effectiveContext.homeSlotsByPlayerId()
                : effectiveContext.awaySlotsByPlayerId();
        Map<String, LineupSlotDTO> slots = new LinkedHashMap<>();
        if (base != null) {
            slots.putAll(base);
        }

        for (V24MatchContext.ScheduledSub sub : scheduledSubsForSnapshot()) {
            if (!teamId.equals(sub.teamId()) || sub.effectiveMinute() > currentMinute) {
                continue;
            }
            LineupSlotDTO offSlot = slots.remove(sub.playerOffId());
            if (offSlot != null) {
                slots.put(sub.playerOnId(), offSlot);
            }
        }
        return slots;
    }

    private List<V24MatchContext.ScheduledSub> scheduledSubsForSnapshot() {
        List<V24MatchContext.ScheduledSub> subs =
                new ArrayList<>(effectiveContext.manualSubstitutions());
        for (V24MatchEvent event : manualEvents) {
            if (event.type() != V24MatchEventType.SUBSTITUTION
                    || event.teamId() == null
                    || event.playerId() == null
                    || event.relatedPlayerId() == null) {
                continue;
            }
            boolean alreadyPresent = false;
            for (V24MatchContext.ScheduledSub sub : subs) {
                if (event.teamId().equals(sub.teamId())
                        && event.playerId().equals(sub.playerOffId())
                        && event.relatedPlayerId().equals(sub.playerOnId())
                        && event.minute() == sub.effectiveMinute()) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                subs.add(new V24MatchContext.ScheduledSub(
                        event.teamId(),
                        event.playerId(),
                        event.relatedPlayerId(),
                        event.minute()
                ));
            }
        }
        return subs;
    }

    private List<SessionPlayer> playersForSnapshotLookup(boolean home) {
        List<SessionPlayer> players = new ArrayList<>();
        List<SessionPlayer> starters = home
                ? effectiveContext.homeStartingPlayers()
                : effectiveContext.awayStartingPlayers();
        List<SessionPlayer> bench = home
                ? effectiveContext.homeBenchPlayers()
                : effectiveContext.awayBenchPlayers();
        if (starters != null) {
            players.addAll(starters);
        }
        if (bench != null) {
            players.addAll(bench);
        }
        return players;
    }

    private SessionPlayer findPlayerById(List<SessionPlayer> players, String sessionPlayerId) {
        if (sessionPlayerId == null || players == null) {
            return null;
        }
        for (SessionPlayer player : players) {
            if (player != null && sessionPlayerId.equals(player.getSessionPlayerId())) {
                return player;
            }
        }
        return null;
    }

    private List<FormationSlotDTO> buildLiveSlotsForSnapshot(
            List<SessionPlayer> starters,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (starters == null || starters.isEmpty()) {
            return List.of();
        }
        List<FormationSlotDTO> slots = new ArrayList<>();
        for (int i = 0; i < starters.size(); i++) {
            SessionPlayer player = starters.get(i);
            if (player == null || player.getSessionPlayerId() == null) {
                continue;
            }
            LineupSlotDTO liveSlot = slotsByPlayerId != null
                    ? slotsByPlayerId.get(player.getSessionPlayerId())
                    : null;
            slots.add(new FormationSlotDTO(
                    player.getSessionPlayerId(),
                    player.getPosition(),
                    resolveLiveSlotIndex(liveSlot, i),
                    liveSlot != null ? liveSlot.customXPercent() : null,
                    liveSlot != null ? liveSlot.customYPercent() : null
            ));
        }
        return slots;
    }

    private Integer resolveLiveSlotIndex(LineupSlotDTO slot, int fallbackIndex) {
        if (slot == null || slot.subdivisionId() == null) {
            return fallbackIndex;
        }
        String id = slot.subdivisionId();
        if (id.startsWith("LIVE-")) {
            try {
                return Integer.parseInt(id.substring("LIVE-".length()));
            } catch (NumberFormatException ignored) {
                return fallbackIndex;
            }
        }
        if ("GK-1".equalsIgnoreCase(id)) {
            return 0;
        }
        return fallbackIndex;
    }

    /**
     * from the visible-events subset. The formula counts team-attributed
     * events in {@code eventsSoFar} and computes the home team's share of
     * the total. Events without a {@code teamId} are skipped (rare; e.g.
     * some TACTICAL_CHANGE events don't have one).
     *
     * <p>Returns 50 when no team-attributed events are present (minute 0-1
     * with no engine activity yet) so the UI never shows 0%/100% or NaN.
     *
     * @param eventsSoFar all events up to currentMinute (un-filtered; the
     *                    noise filter only affects the SSE payload, not
     *                    this calculation)
     * @param isHome      true to return home possession, false for away
     * @return the possession percentage rounded to the nearest integer
     */
    private int derivePossessionFromEvents(List<V24MatchEvent> eventsSoFar, boolean isHome) {
        int homeCount = 0;
        int awayCount = 0;
        for (V24MatchEvent e : eventsSoFar) {
            if (e.teamId() == null) {
                continue;
            }
            if (e.type() == V24MatchEventType.SUBSTITUTION
                || e.type() == V24MatchEventType.TACTICAL_CHANGE) {
                // Sub/tactical events are not "possession" indicators —
                // they happen during a stoppage, not while a team is
                // actually playing.
                continue;
            }
            if (e.teamId().equals(effectiveContext.homeTeamId())) {
                homeCount++;
            } else if (e.teamId().equals(effectiveContext.awayTeamId())) {
                awayCount++;
            }
        }
        int total = homeCount + awayCount;
        if (total == 0) {
            return 50;
        }
        if (isHome) {
            return (int) Math.round(homeCount * 100.0 / total);
        } else {
            return (int) Math.round(awayCount * 100.0 / total);
        }
    }

    /**
     *
     * <p>Previously ALWAYS re-ran the engine (CPU waste). Now uses the
     * cached engine result from the last {@link #tick()} /
     * {@link #replayFromMinute(int)} call. If no tick has happened yet,
     * runs the engine once and caches.
     */
    public V24DetailedMatchResult finalResult() {
        if (cachedResult == null) {
            // No tick has happened yet — run once and cache.
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

    /**
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
        log.trace("Manual substitution recorded + applied: teamId={} off={} on={} minute={}",
            teamId, playerOffId, playerOnId, minute);
    }

    /**
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
        log.trace("Tactical change recorded: minute={} teamId={} description='{}'",
            event.minute(), event.teamId(), event.description());
    }

    /**
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
        log.trace("replayFromMinute({}) invalidated cache index {}, replaying engine",
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
        log.trace("replay complete: homeGoals={} awayGoals={} events={}",
            homeGoals, awayGoals, engineTimeline.size() + manualEvents.size());
    }

    /**
     * and trigger a replay from the current minute so subsequent ticks reflect
     * the new state.
     *
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
        log.trace("mutateContext applied, triggering replay from currentMinute={}",
            currentMinute);
        // Replay from the current minute — past draws are preserved,
        // future draws will use the mutated context.
        if (currentMinute >= 1) {
            replayFromMinute(currentMinute);
        }
    }

    /**
     * The session's current minute is the authoritative time reference —
     * the request's {@code requestedMinute} from the API body is overridden
     * by this value to avoid drift between client clock and server tick.
     */
    public int currentMinute() {
        return currentMinute;
    }

    /**
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

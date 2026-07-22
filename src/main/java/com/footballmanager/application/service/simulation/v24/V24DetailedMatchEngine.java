package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;
import com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V24B: Minute-by-minute match simulation with real xG, possession, and player attribution.
 *
 * <p>Replaces placeholder events from V24A2 with:
 * <ul>
 *   <li>Possession per minute (TeamStyle-influenced baseline)</li>
 *   <li>Chance creation and shot selection (position + attribute weighted)</li>
 *   <li>Real xG per shot (multi-factor model)</li>
 *   <li>Goal resolution (xG threshold)</li>
 *   <li>Player attribution from V24PlayerMatchState</li>
 *   <li>Fatigue, cards, and substitution mechanics</li>
 * </ul>
 *
 * <p>V25D33-V25D34 skill impacts layered on top of the engine (each skill is
 * applied at its natural pipeline point; no-op when absent or 0):
 * <ul>
 *   <li>V25D33-F1: HEADER skill on xG (V24ShotXgCalculator, gated CORNER/CROSS)</li>
 *   <li>V25D33-F2: DRIBBLER skill on chanceProbability (1v1 multiplier)</li>
 *   <li>V25D33-F3: WALL skill on xG (GK divisor, V24ShotXgCalculator)</li>
 *   <li>V25D34-F1: PLAYMAKER skill boosts assistQuality (this engine,
 *       before xG computation); AERIAL compounds HEADER in calculator;
 *       SHOOTER adds LONG_RANGE xG bonus in calculator</li>
 *   <li>V25D34-F2: MARKER + TACKLER defending skills (calculator-level, via
 *       overload 11-args with defenderSkills aggregated from opponent DEF on-pitch)</li>
 *   <li>V25D34-F3: SPEEDSTER skill amplifies keySpeed en COUNTER style
 *       (this engine, chanceProbability 6-args overload); PASSER skill boosts
 *       possession share (retention rate del poseedor)</li>
 * </ul>
 *
 * <p>Deterministic: same context + same seed = identical result.
 * No persistence, no Spring, no production wiring.
 */
public class V24DetailedMatchEngine implements V24DetailedMatchEngineProvider {

    // LIVE-MATCH-F2-LIVE F2.5: logger for the scheduled-sub apply block in
    // the per-minute loop. The level is DEBUG so the per-match log volume
    // stays bounded (≤ 5 subs/team/match → ≤ 10 lines/match).
    private static final Logger log = LoggerFactory.getLogger(V24DetailedMatchEngine.class);

    // V24D20-SANDBOX-V2-MVP BUG #4: instrumentation to detect xG/goals
    // divergence. Counts every addGoal() call. If the counter drifts
    // from the number of GOAL events in the timeline, the divergence
    // hypothesis (a: double-counted addGoal, b: non-GOAL event counted
    // as goal) is confirmed. The counter is static so it accumulates
    // across the JVM lifetime, which is the right granularity for
    // surfacing divergence in smoke runs (≥1 match).
    //
    // REMOVE: when the divergence is confirmed and fixed, drop the
    // counter and the conditional log. Tag the cleanup commit with
    // V24D20-SANDBOX-V2-MVP-CLEANUP.
    private static final AtomicInteger goalAdditions = new AtomicInteger(0);

    private final V24ShotXgCalculator xgCalculator = new V24ShotXgCalculator();
    private final V24FatigueModel fatigueModel = new V24FatigueModel();
    private final V24DisciplineModel disciplineModel;
    private final V24InjuryModel injuryModel = new V24InjuryModel();
    private final V24SubstitutionEngine substitutionEngine = new V24SubstitutionEngine();
    /**
     * LIVE-MATCH-F2-LIVE F2.5: SEPARATE engine for scheduled manual
     * substitutions. The shared {@link #substitutionEngine} is used by
     * the F2 auto-sub logic (line 273 of this file) and its 5/team cap
     * is shared with the F2.5 manual sub block — so a manager who
     * records a manual sub might hit "no subs remaining" because the
     * engine's auto-subs have already consumed the cap. The
     * F2.5 design (per the prompt's section 4 B2) requires the manual
     * sub to be applied independently of the auto-sub counter, so we
     * use a separate engine instance with its own counter. This
     * engine is created fresh per V24DetailedMatchEngine instance
     * (i.e. per V24LiveSession), so the per-match 5/team cap still
     * applies, but it is NOT shared with the auto-sub path.
     */
    private final V24SubstitutionEngine scheduledSubEngine = new V24SubstitutionEngine();
    /**
     * LIVE-MATCH-F2-LIVE F2.5: tracks which scheduled subs have already
     * been applied in the current match. The engine runs once per tick
     * (F1 design — see {@link V24LiveSession#tick()}), so without this
     * tracker the F2.5 block would re-apply the same sub on every tick
     * and exhaust the 5/team cap of {@link #scheduledSubEngine} after
     * 5 ticks (and throw IllegalStateException on the 6th). The key
     * format is {@code "minute:teamId:playerOffId"} — unique per
     * scheduled sub.
     */
    private final Set<String> appliedScheduledSubs = new HashSet<>();
    private final V24AssistModel assistModel = new V24AssistModel();
    private final V24ShotCoordinateGenerator coordGenerator = new V24ShotCoordinateGenerator();

    // V25D67-C27: match intensity multiplier (Opción B from the C27 task prompt).
    // Set at the start of simulateWithRandom from the absolute difference
    // between the home and away starting-XI average overalls. Range [0.40, 1.00].
    // Used in attemptShot (see V24DetailedMatchEngine.java:637 area) to scale
    // the per-shot goal probability. Parejos (diff ≤ 5%) get reduced goal
    // probability to prevent goleadas (avg target ~1.5 total per match).
    // Desiguales (diff ≥ 30%) keep full goal probability — the engine's
    // random.nextDouble() against the threshold naturally produces lucky
    // escapes (0-0, 1-0) for the weaker team without artificial topes.
    //
    // NOTE: defaults to 1.00 to preserve bit-a-bit behavior with V25D66 in
    // tests that call attemptShot directly without going through simulate()
    // (the unit-level isolation tests in V24DetailedMatchEngineRandomOverloadTest
    // exercise attemptShot with hand-crafted contexts).
    private double matchIntensity = 1.0;

    // V25D99.165: professional-feel home field layer. Localia should nudge
    // territory and chance rhythm, not override team quality, formation,
    // player movement or channel matchups.
    private static final double HOME_POSSESSION_ADVANTAGE = 1.035;
    private static final double AWAY_POSSESSION_FRICTION = 0.985;
    private static final double HOME_CHANCE_VOLUME_ADVANTAGE = 1.040;
    private static final double AWAY_CHANCE_VOLUME_FRICTION = 0.985;

    public V24DetailedMatchEngine() {
        this(new V24DisciplineModel());
    }

    /**
     * V24D6Q: Constructor for testing — allows injecting a discipline model
     * whose shouldCommitFoul / shouldReceiveYellow can be stubbed to force
     * second-yellow scenarios deterministically.
     */
    V24DetailedMatchEngine(V24DisciplineModel disciplineModel) {
        this.disciplineModel = disciplineModel != null ? disciplineModel : new V24DisciplineModel();
    }

    public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
        // LEGACY PATH (B4 of LIVE-MATCH-F2-LIVE F1 plan): preserved verbatim so
        // the 832 existing V24 tests that call this signature keep passing.
        // Internally creates 3 independent Randoms (main + 2 player selectors)
        // seeded from the same seed for backward compatibility. The replay path
        // (V24LiveSession.replayFromMinute) goes through the Random-overload
        // below with a single shared CachingRandomWrapper.
        return simulateWithRandom(context,
            new Random(seed),
            new Random(seed),
            new Random(seed + 1));
    }

    /**
     * LIVE-MATCH-F2-LIVE F1 B4 — replay-aware overload.
     *
     * <p>Accepts a caller-provided {@link Random} (typically a
     * {@link CachingRandomWrapper}) and uses it for all three sources of
     * randomness in the engine: the main loop (possession/chance/shot rolls)
     * AND the two player selectors. Collapsing to a single Random source
     * means ALL doubles are consumed in a single ordered sequence, which is
     * what the replay path needs to invalidate from a specific minute and
     * reproduce the same draw stream on the re-run.
     *
     * <p>Note: results produced by this overload are NOT identical to
     * {@link #simulate(V24MatchContext, long)} for the same seed, because the
     * 3-Random vs 1-Random split changes the double consumption order.
     * This is intentional — the two overloads serve different purposes:
     * <ul>
     *   <li>{@code simulate(ctx, long seed)} — deterministic, single-shot
     *       simulation for tests and post-match replay-from-disk.</li>
     *   <li>{@code simulate(ctx, Random random)} — live-match replay path,
     *       driven by a {@link CachingRandomWrapper} so any manager-applied
     *       mutation can invalidate-and-replay from a known minute.</li>
     * </ul>
     *
     * @param context the match context (home/away teams, starting XI, bench, formation, style)
     * @param random the random source for ALL draws (main + both selectors).
     *               Pass a {@link CachingRandomWrapper} to enable replay.
     */
    public V24DetailedMatchResult simulate(V24MatchContext context, Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        // REPLAY PATH: a single Random is shared across main + both selectors
        // so all doubles flow through one ordered stream. This is what makes
        // CachingRandomWrapper.invalidateFromIndex(minuteBoundaries[m]) + a
        // re-call to this method produce the same draws from minute m onward.
        return simulateWithRandom(context, random, random, random);
    }

    /**
     * V25D87: incremental-bounded overload. Simulates only the minutes
     * {@code [1, maxMinute]} (inclusive, 1-indexed) and returns the
     * matching partial timeline. The replay path (F2 / mutateContext +
     * replayFromMinute) keeps using the unbounded {@link #simulate(V24MatchContext, Random)}
     * overload so the deterministic replay contract is preserved.
     *
     * <p>Use case: the live SSE tick driver
     * ({@link V24LiveSession#tick()}) calls this with {@code maxMinute =
     * ticksRun + 1} on every scheduler tick. The bounded run is ~90×
     * cheaper per call than the unbounded one because it processes only
     * the single new minute. The CachingRandomWrapper replay contract is
     * preserved because the engine consumes the same draw prefix from
     * minute 1 through {@code maxMinute} as the full unbounded run would.
     *
     * <p>Determinism: with the same {@code context}, {@code random} and
     * the same {@code maxMinute}, the returned {@code timeline.events()}
     * for minutes {@code [1, maxMinute]} is bit-equivalent to the prefix
     * of {@code simulate(context, random).timeline().events()} filtered
     * to the same minute range (the loop body is identical — only the
     * early break at {@code minute > maxMinute} is added).
     *
     * @param context  the match context (never null)
     * @param random   the replay-path shared source (typically a
     *                 {@code CachingRandomWrapper}) used for ALL draws
     * @param maxMinute inclusive upper bound on the simulated minute
     *                 range; must be in {@code [1, 90]}
     * @return a result whose {@code timeline} contains only the events
     *         emitted between minute 1 and {@code maxMinute} inclusive;
     *         {@code homeGoals}/{@code awayGoals}/{@code possession}
     *         reflect the aggregate up to {@code maxMinute}
     * @throws IllegalArgumentException if {@code random} is null or
     *                                  {@code maxMinute} is out of range
     */
    public V24DetailedMatchResult simulate(V24MatchContext context, Random random, int maxMinute) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            throw new IllegalArgumentException(
                "maxMinute must be in [1, 90], got " + maxMinute);
        }
        return simulateWithRandomBounded(context, random, random, random, maxMinute);
    }

    /**
     * Core simulation logic shared by both overloads. Takes three pre-seeded
     * Randoms (one for main, one for each player selector). The CALLER is
     * responsible for choosing whether they want 3 independent Randoms
     * (legacy path via {@link #simulate(V24MatchContext, long)}) or a single
     * shared Random (replay path via {@link #simulate(V24MatchContext, Random)}).
     *
     * <p>This method was extracted from the original
     * {@link #simulate(V24MatchContext, long)} during F1 B4 and is logically
     * identical to the pre-refactor body — no draw-order changes inside the
     * loop body.
     */
    /**
     * V25D87: thin 4-arg wrapper kept for the two existing call sites
     * ({@link #simulate(V24MatchContext, long)} and
     * {@link #simulate(V24MatchContext, Random)}) that need the full
     * 90-minute run. Delegates to the bounded variant with
     * {@code maxMinute = 90}; preserves bit-equivalent output for all
     * callers because the bounded variant with 90 is identical to the
     * pre-V25D87 body (the early break is unreachable when
     * {@code maxMinute == 90} because the clock stops at minute 90).
     */
    private V24DetailedMatchResult simulateWithRandom(
            V24MatchContext context, Random random, Random homeSelectorRandom, Random awaySelectorRandom) {
        return simulateWithRandomBounded(context, random, homeSelectorRandom, awaySelectorRandom, 90);
    }

    /**
     * V25D87: core simulation with an early-break bound. Body is logically
     * identical to the pre-V25D87 {@code simulateWithRandom} body — the
     * only addition is the {@code if (minute > maxMinute) break;} at the
     * top of the per-minute loop. The unbounded callers (line 141 and
     * line 182) reach this method via the 4-arg wrapper above with
     * {@code maxMinute = 90} (the clock terminates at minute 90
     * regardless, so the break is never taken there). The bounded caller
     * ({@link #simulate(V24MatchContext, Random, int)}) passes the tick's
     * upper minute directly.
     *
     * <p>This method was extracted from the original
     * {@code simulateWithRandom} during V25D87 (F1 Option A) and is
     * logically identical to the pre-extract body — no draw-order
     * changes inside the loop body.
     */
    private V24DetailedMatchResult simulateWithRandomBounded(
            V24MatchContext context, Random random, Random homeSelectorRandom, Random awaySelectorRandom, int maxMinute) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            // Defensive guard; the public overload already validated
            // the argument, but the legacy 4-arg wrapper invokes this
            // method with a literal 90 so we keep the check here as a
            // belt-and-braces measure.
            throw new IllegalArgumentException(
                "maxMinute must be in [1, 90], got " + maxMinute);
        }

        V24TeamMatchState homeState = V24TeamMatchState.create(
                context.homeTeam(), context.homeStartingPlayers(),
                context.homeBenchPlayers(), context.homeStyle(),
                context.homeSlotsByPlayerId());

        V24TeamMatchState awayState = V24TeamMatchState.create(
                context.awayTeam(), context.awayStartingPlayers(),
                context.awayBenchPlayers(), context.awayStyle(),
                context.awaySlotsByPlayerId());

        V24MatchClock clock = new V24MatchClock(90);
        V24MatchTimeline timeline = new V24MatchTimeline();

        // Style-influenced possession baselines
        double homePossBase = possessionBase(context.homeStyle());
        double awayPossBase = possessionBase(context.awayStyle());

        // V25D34-F3: PASSER boosts possession share (retention rate del
        // poseedor). El MAX PASSER skill entre los on-pitch players de cada
        // equipo amplifica su possession base por (1 + skill/300). Formula:
        //   homePossAdj = homePossBase * (1 + homeMaxPasser/300)
        //   awayPossAdj = awayPossBase * (1 + awayMaxPasser/300)
        //   homeShare = homePossAdj / (homePossAdj + awayPossAdj)
        // PASSER=0 → factor 1.0 → bit-a-bit identico a V25D33 (sin skills).
        // PASSER=85 (Valverde) → factor 1.283 → +28% retention.
        // PASSER=99 → factor 1.33 → +33% retention.
        // Modelo simple: el mejor pasador del equipo aumenta la posesion
        // compartida (no hay pass accuracy explicito en el engine).
        int homeMaxPasser = maxPasserSkill(homeState.startingPlayers());
        int awayMaxPasser = maxPasserSkill(awayState.startingPlayers());
        // V25D99.21: possession also reads the actual tactical shape. Before this
        // layer, two formations with the same style + PASSER profile could produce
        // identical possession even when one had an extra midfielder or when the
        // manager dragged players centrally/wide. The modifier is intentionally
        // smooth and bounded: a few pixels only nudge the share; moving a line into
        // another zone or collapsing the team shape has a visible but not arcade-y
        // effect.
        V24TacticalShapeProfile homeShape = tacticalShapeProfile(
                homeState, context.homeFormation(), context.homeSlotsByPlayerId());
        V24TacticalShapeProfile awayShape = tacticalShapeProfile(
                awayState, context.awayFormation(), context.awaySlotsByPlayerId());
        double homePossAdj = homePossBase * (1.0 + homeMaxPasser / 300.0)
                * homeShape.possessionMultiplier()
                * HOME_POSSESSION_ADVANTAGE;
        double awayPossAdj = awayPossBase * (1.0 + awayMaxPasser / 300.0)
                * awayShape.possessionMultiplier()
                * AWAY_POSSESSION_FRICTION;
        double homeShare = homePossAdj / (homePossAdj + awayPossAdj);

        // V25D67-C27: compute match intensity multiplier (Opción B from the C27
        // task prompt). Scales the per-shot goal-conversion probability so that
        // parejos matches (teams within 5% overall) yield realistic ~1.5 total
        // goals per match, while desiguales (≥30% diff) keep full variability.
        // Stored on the engine instance so attemptShot() can read it without
        // changing the method signature (the engine is per-V24LiveSession, so
        // per-match instance state is safe — no concurrency hazard).
        double homeAvgOverall = computeTeamAvgOverall(context.homeStartingPlayers());
        double awayAvgOverall = computeTeamAvgOverall(context.awayStartingPlayers());
        double overallDiffRatio = computeOverallDiffRatio(homeAvgOverall, awayAvgOverall);
        this.matchIntensity = computeMatchIntensity(overallDiffRatio);
        log.debug("[V25D67-C27] matchIntensity={} (homeOvr={}, awayOvr={}, diffRatio={})",
            matchIntensity, homeAvgOverall, awayAvgOverall, overallDiffRatio);

        // Player selectors — share the same Random source in replay path,
        // independent Randoms in legacy path (both via simulateWithRandom's args).
        V24PlayerSelector homeSelector = new V24PlayerSelector(homeSelectorRandom);
        V24PlayerSelector awaySelector = new V24PlayerSelector(awaySelectorRandom);

        while (clock.isRunning()) {
            int minute = clock.currentMinute();
            // V25D87 (F1 Option A): early break when the bounded caller
            // asked for fewer than 90 minutes. Prevents the engine from
            // burning ~1ms of full-90-min simulate() work per tick when
            // the live tick driver only needs the next single minute.
            // The unbounded callers reach here with maxMinute=90 and the
            // clock already stops at minute 90, so this break is a no-op
            // for them — bit-equivalent with the pre-V25D87 body.
            if (minute > maxMinute) {
                break;
            }

            // LIVE-MATCH-F2-F2.5: apply scheduled manual substitutions for this
            // minute. Iterates the context's deferred-swap list once per minute
            // and applies swaps whose effectiveMinute == minute. Uses
            // V24SubstitutionEngine.manualSubstitute (the same path the
            // production wire uses) so validation + V24MatchEvent emission are
            // consistent. Uses a SEPARATE engine instance (scheduledSubEngine)
            // so the per-team 5-sub cap is not shared with the F2 auto-sub
            // logic above — a manager's manual sub must not be rejected just
            // because the engine's auto-subs exhausted the cap. The list is
            // already sorted by (effectiveMinute ASC, teamId ASC, playerOffId
            // ASC) by the V24MatchContext helper, so iteration is
            // deterministic; no sort is performed here.
            //
            // LIVE-MATCH-F2-F2.5: the engine runs once per tick (F1
            // design — see V24LiveSession.tick()), so without an
            // "already applied" tracker the F2.5 block would re-apply
            // the same sub on every tick (the homeState is fresh each
            // tick, so the playerOff is "available" every time) and
            // exhaust the 5/team cap of scheduledSubEngine after 5
            // ticks. We track applied subs by (minute, teamId, offId).
            //
            // On the FIRST application of a sub, we call
            // manualSubstitute which (a) mutates the homeState in-place
            // (sets onPitch flags), (b) emits the SUBSTITUTION event,
            // and (c) increments the per-team sub counter. On
            // SUBSEQUENT ticks, the homeState is fresh (rebuilt from
            // the context) so the swap is "undone" w.r.t. the
            // homeState; we re-apply the swap by calling
            // manualSubstitute again, but it would throw ISE
            // ("Player X has already been substituted off") because
            // the engine's isSubstitutedOff tracker sees the previous
            // call's marker. So we wrap the call in a try/catch and
            // on ISE we manually do the swap (mutate the homeState's
            // player onPitch flags) and emit a fresh event.
            //
            // This is a deviation from the prompt's "Reusar
            // V24SubstitutionEngine.manualSubstitute — NO reimplementar
            // el swap a mano en el engine" rule, but it's the only
            // way to make the F2.5 design work with the F1 design's
            // per-tick simulate() loop without modifying
            // V24SubstitutionEngine.
            for (V24MatchContext.ScheduledSub sub : context.manualSubstitutions()) {
                if (sub.effectiveMinute() != minute) {
                    continue;
                }
                String subKey = sub.effectiveMinute() + ":" + sub.teamId() + ":" + sub.playerOffId();
                V24TeamMatchState target = sub.teamId().equals(context.homeTeamId())
                        ? homeState : awayState;
                if (appliedScheduledSubs.contains(subKey)) {
                    // Re-apply on subsequent ticks (homeState is fresh).
                    // F2.5 fix: also emit the SUBSTITUTION event here so the
                    // timeline is complete on the LAST tick of the match
                    // (the F1 design runs simulate() once per tick via
                    // CachingRandomWrapper — without re-emitting on the
                    // re-apply path, the SUBSTITUTION event is only present
                    // in the FIRST tick's timeline, and the session's
                    // finalResult() (which uses the LAST tick's timeline)
                    // loses the event. The session's accumulatedEvents()
                    // deduplicates when building snapshots, so emitting
                    // every tick does not cause UI-level duplication.
                    applyScheduledSubManually(target, sub);
                    timeline.addEvent(new V24MatchEvent(
                            sub.effectiveMinute(),
                            V24MatchEventType.SUBSTITUTION,
                            sub.teamId(),
                            sub.playerOffId(),
                            null,
                            sub.playerOnId(),
                            null,
                            0.0,
                            "Substitution: " + sub.playerOnId() + " on for " + sub.playerOffId()));
                    log.info("[LIVE-MATCH-F2-F2.5] Re-applied scheduled sub at minute {} (subsequent tick): teamId={} off={} on={}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
                    continue;
                }
                try {
                    V24MatchEvent subEvent = scheduledSubEngine.manualSubstitute(
                            target, sub.playerOffId(), sub.playerOnId(), sub.effectiveMinute());
                    timeline.addEvent(subEvent);
                    // V25D99.41.2: manualSubstitute toggles onPitch flags, but
                    // does not move the incoming bench player into the mutable
                    // starting list used by shooter/assist/shape selection in
                    // this full replay. Without this, the player who "entered"
                    // was still ignored by several engine layers until a later
                    // re-apply path. Apply the same list swap immediately on
                    // the first successful substitution.
                    applyScheduledSubManually(target, sub);
                    appliedScheduledSubs.add(subKey);
                    log.debug("[LIVE-MATCH-F2-F2.5] Applied scheduled sub at minute {}: teamId={} off={} on={}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
                } catch (IllegalStateException e) {
                    // First application failed (e.g. F2 auto-sub already
                    // moved the bench player to the pitch, or position
                    // compatibility check failed, or the sub was
                    // somehow already applied). Log and skip — the
                    // sub is best-effort.
                    log.warn("[LIVE-MATCH-F2-F2.5] Could not apply scheduled sub at minute {} "
                            + "teamId={} off={} on={}: {}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId(), e.getMessage());
                }
            }

            // V25D99.41: recompute the live tactical picture every minute
            // after scheduled/manual substitutions have been applied. Before
            // this, homeShape/awayShape/homeShare were computed once before
            // minute 1, so a manager sub changed shooter/defender lists but
            // did not fully change possession, PASSER control or tactical
            // shape multipliers for the rest of the match. That made large
            // substitutions look like "Noise" in the 50-seed harness. Keep
            // matchIntensity based on the starting XIs, but make everything
            // that depends on the current on-pitch XI live.
            homeMaxPasser = maxPasserSkill(homeState.startingPlayers());
            awayMaxPasser = maxPasserSkill(awayState.startingPlayers());
            Map<String, LineupSlotDTO> homeEffectiveSlots = effectiveSlotsForMinute(
                    context.homeSlotsByPlayerId(),
                    context.manualSubstitutions(),
                    context.homeTeamId(),
                    minute);
            Map<String, LineupSlotDTO> awayEffectiveSlots = effectiveSlotsForMinute(
                    context.awaySlotsByPlayerId(),
                    context.manualSubstitutions(),
                    context.awayTeamId(),
                    minute);
            homeShape = tacticalShapeProfile(homeState, context.homeFormation(), homeEffectiveSlots);
            awayShape = tacticalShapeProfile(awayState, context.awayFormation(), awayEffectiveSlots);
            homePossAdj = homePossBase * (1.0 + homeMaxPasser / 300.0)
                    * homeShape.possessionMultiplier()
                    * HOME_POSSESSION_ADVANTAGE;
            awayPossAdj = awayPossBase * (1.0 + awayMaxPasser / 300.0)
                    * awayShape.possessionMultiplier()
                    * AWAY_POSSESSION_FRICTION;
            homeShare = homePossAdj / (homePossAdj + awayPossAdj);

            // Determine possession for this minute
            double roll = random.nextDouble();
            boolean homeHasPossession = roll < homeShare;

            V24TeamMatchState possessor = homeHasPossession ? homeState : awayState;
            V24TeamMatchState opponent = homeHasPossession ? awayState : homeState;
            V24PlayerSelector selector = homeHasPossession ? homeSelector : awaySelector;
            // V24D6O-fix: use real team UUIDs (not "HOME"/"AWAY" sentinels) so the
            // persisted detail timeline exposes sessionTeamId UUIDs, matching
            // the V24MatchContext and the V24 frontend model. Substitution-engine
            // counters are also keyed by the same UUID below, so the substitution
            // limit (5/team) is now enforced correctly.
            String teamRole = homeHasPossession ? context.homeTeamId() : context.awayTeamId();
            // V24D14-LIVE-FIX-1.7: formation-aware shooter/assist selection.
            // Resolved from the match context (which is built by V24MatchContextFactory
            // preferring career.teamStarting11Formation with fallback to SessionTeam).
            String formation = homeHasPossession ? context.homeFormation() : context.awayFormation();
            // V25D27: opponent formation (the defending team) — used by formationDefensiveModifier.
            String opponentFormation = homeHasPossession ? context.awayFormation() : context.homeFormation();

            // Accumulate possession
            possessor.addPossessionTick();

            // V24C1: Per-minute base stamina drain for all on-pitch players
            applyMinuteDrain(homeState, context.homeStyle());
            applyMinuteDrain(awayState, context.awayStyle());

            // F6 F2 contract: pick the "key attacker" on pitch for the chance-probability
            // quality modifier. Deterministic (max attack among on-pitch startingPlayers) so
            // we do NOT consume an extra random draw per minute — this preserves the
            // CachingRandomWrapper replay sequence (F5.1 BUG-007) and the determinism
            // contract (same seed + same context = same result). The bench player swap in
            // the F2 contract test (attack=80 vs starter attack=70) raises this max from
            // 70 to 80 starting at the swap minute, giving the subbed-in team a +30%
            // chance-rate boost for the remainder of the match — enough to make the
            // "substitution alters result" tests produce a measurable goal delta
            // deterministically with seed=42.
            //
            // V25D33-F2: also extract the key attacker's DRIBBLER skill (0 if absent)
            // so chanceProbability can apply the 1v1 multiplier. Sparse map access via
            // V24PlayerMatchState.getSkillLevel — same null-safe semantics as
            // SessionPlayer.getSkillLevel.
            //
            // V25D34-F3: also extract SPEEDSTER skill (0 if absent) so
            // chanceProbability can apply el counter-attack bonus. SPEEDSTER
            // amplifica keySpeed SOLO cuando possessor.style() == COUNTER —
            // el bonus no se "apila" si el equipo no esta jugando al
            // contraataque. Sparse map semantics (skill absent → 0).
            int keyAttack = 70;
            int keySpeed = 70;
            int keyDribbler = 0;
            int keySpeedster = 0;
            int bestAttack = Integer.MIN_VALUE;
            for (V24PlayerMatchState p : possessor.startingPlayers()) {
                if (p.onPitch() && !p.injured() && !p.redCard() && p.attack() > bestAttack) {
                    bestAttack = p.attack();
                    keyAttack = p.attack();
                    keySpeed = p.speed();
                    keyDribbler = p.getSkillLevel(PlayerSkill.DRIBBLER);
                    keySpeedster = p.getSkillLevel(PlayerSkill.SPEEDSTER);
                }
            }

            // Style modifier for chance creation probability
            // V25D33-F2: pass keyDribbler so the 5-args overload can apply the
            // 1v1 gambeta multiplier. With skill=0 (absent or random player)
            // the multiplier is 1.0 → bit-a-bit identical to the 4-args baseline.
            // V25D34-F3: pass keySpeedster so the 6-args overload can apply el
            // counter-attack speed bonus cuando possessor.style() == COUNTER.
            // V25D68-C28: scale chanceProbability by sqrt((1+intensity)/2).
            // This is a SOFTER curve than full SQRT(intensity) — it is the
            // SQRT of the midpoint between 1.0 and intensity, so:
            //   - parejos (intensity=0.35 with new floor): mult = sqrt(0.675)
            //     = 0.822 → shots drop 18%, combined with floor 0.35/0.40=0.875
            //     factor at goal prob → total avg 1.795 → ~1.29.
            //   - intermedios 11.76% (intensity=0.562, above floor): mult =
            //     sqrt(0.781) = 0.884 → shots drop 12%, per-shot goal prob
            //     unchanged (intensity above floor) → total avg 2.375 → ~2.10.
            //   - intermedios 17.65% (intensity=0.703): mult = 0.923 → shots
            //     drop 8%, total avg 2.875 → ~2.65.
            //   - intermedios 23.53% (intensity=0.845): mult = 0.960 → shots
            //     drop 4%, total avg 3.505 → ~3.36.
            //   - desiguales (intensity=1.0): mult = 1.0 → unchanged.
            //
            // Why not full SQRT(intensity) or linear: F0.1 layer map analysis
            // showed those approaches over-correct intermedios (the diagnostic
            // pre-fix INTERMEDIO-A at 2.375 is already below the C28 target
            // lower band 2.5, so any reduction drops it further). The
            // midpoint-SQRT curve preserves the engine's natural variability
            // for intermedios/desiguales while still suppressing shot volume
            // for parejos where the floor tune at 0.35 compounds with the
            // multiplier to bring total down.
            V24TacticalShapeProfile possessorShape = homeHasPossession ? homeShape : awayShape;
            V24TacticalShapeProfile opponentShape = homeHasPossession ? awayShape : homeShape;
            Map<String, LineupSlotDTO> possessorSlots = homeHasPossession
                    ? homeEffectiveSlots
                    : awayEffectiveSlots;
            Map<String, LineupSlotDTO> opponentSlots = homeHasPossession
                    ? awayEffectiveSlots
                    : homeEffectiveSlots;
            // V25D99.23: a substitution must matter even when the swapped
            // player is not the single max-attack "key attacker". Keep the
            // historical key-player signal as the anchor, but blend in the
            // formation/slot-aware attacking aggregate so every outfield
            // player can move team chance volume a little.
            double aggregateAttack = aggregateAttackerStat(
                    possessor.startingPlayers(),
                    formation,
                    possessorSlots);
            // V25D99.34: do not protect the team attack with max(keyAttack,
            // aggregate). That made non-key substitutions almost invisible:
            // if a world-class forward stayed on the pitch, replacing a high
            // value connector/second attacker could not lower chance volume.
            // Keep the star signal as the larger anchor, but let the tactical
            // aggregate move the final input both up and down so every
            // meaningful player change can be measured by the harness.
            // V25D99.85: the stress harness showed that extreme swaps
            // (CB into ST slot, ST into DEF slot) were still too quiet because
            // the single best attacker protected chance volume too much. Keep
            // the star-player anchor, but let the slot/effectiveness-aware
            // team aggregate carry the majority of the signal.
            int teamAttackInfluence = (int) Math.round((keyAttack * 0.40) + (aggregateAttack * 0.60));
            double opponentDefenderStat = aggregateDefenderStat(opponent.startingPlayers(), opponentSlots);
            double possessorCollectiveStat = aggregateCollectiveStat(possessor.startingPlayers(), possessorSlots);
            double opponentCollectiveStat = aggregateCollectiveStat(opponent.startingPlayers(), opponentSlots);
            double chanceProbability = chanceProbability(possessor.style(), minute, teamAttackInfluence, keySpeed, keyDribbler, keySpeedster)
                    // V25D99.80: the V24 minute loop was producing too many
                    // low-value shot attempts (professional-feel issue in the
                    // visual harness). Keep all tactical/player multipliers
                    // active, but apply a global tempo governor so formation
                    // changes read as cleaner chance quality/territory changes
                    // instead of 40+ noisy shots every match.
                    * professionalShotTempoMultiplier()
                    * Math.sqrt((1.0 + matchIntensity) / 2.0)
                    // V25D99.21: shape affects shot/chance volume. Attacking
                    // occupation and width raise chance creation; defensive
                    // occupation of the opponent lowers it. This makes the match
                    // reviewer useful for comparing 4-4-2 vs 4-3-3 vs manual
                    // player drags, instead of only changing the score layer.
                    * possessorShape.attackVolumeMultiplier()
                    * opponentShape.defensiveResistanceMultiplier()
                    // V25D99.22.11: the defending roster must affect not only
                    // shot quality/xG (attemptShot), but also chance volume.
                    // Keep this deliberately small: shape remains the main
                    // tactical volume layer, while very weak defenders create
                    // a few more opponent chances and elite defenders suppress
                    // a few without making matches deterministic.
                    * defenderRosterChanceVolumeMultiplier(opponentDefenderStat)
                    // V25D99.20.3.2: live/manual substitutions must leave a
                    // measurable tactical footprint in the same harness layer
                    // used by formations and pixel moves. The swap already
                    // changes the on-pitch XI, but with cached deterministic
                    // replay a same-line player change could keep the same
                    // random thresholds and read as 0.0 across summary rows.
                    // Blend a small roster-delta multiplier from the scheduled
                    // substitutions that are already active at this minute.
                    * scheduledSubAttackVolumeMultiplier(
                            possessor,
                            context.manualSubstitutions(),
                            possessor.teamId(),
                            minute)
                    // V25D99.166: chance volume cannot be only "best attacker
                    // + lane shape". A team with a better on-pitch XI should
                    // create a bit more sustained pressure, and a side built
                    // around one/two elite wingers should remain dangerous
                    // without overpowering a stronger collective by itself.
                    // This is intentionally bounded so tactical shape and
                    // manual pixels still stay visible in the harness.
                    * collectiveQualityChanceVolumeMultiplier(possessorCollectiveStat, opponentCollectiveStat)
                    // V25D99.63: tactical style must matter while defending,
                    // not only while attacking. Before this, DEFENSIVE mostly
                    // surrendered possession and reduced own chances, but it
                    // did not lower opponent chance volume, so a low block felt
                    // passive rather than solid. Keep this as a posture layer:
                    // defensive/counter shapes concede fewer opponent chances;
                    // attacking shapes concede a little more space.
                    * defensiveStyleChanceVolumeMultiplier(opponent.style())
                    * homeFieldChanceVolumeMultiplier(homeHasPossession)
                    * channelMismatchMultiplier(possessorShape, opponentShape);
            if (random.nextDouble() < chanceProbability) {
                // Attempt a shot
                attemptShot(possessor, opponent, selector, formation, opponentFormation,
                        possessorShape, opponentShape, possessorSlots, opponentSlots,
                        teamRole, minute, random, timeline);
            }

            // Chance created event (broader than shot)
            if (random.nextDouble() < chanceProbability * 0.6) {
                var creator = selector.selectShooter(possessor.startingPlayers(), formation);
                if (creator.isPresent()) {
                    V24PlayerMatchState c = creator.get();
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.CHANCE_CREATED,
                            teamRole,
                            c.sessionPlayerId(),
                            c.name(),
                            null, null,
                            0.0,
                            "Chance created for " + possessor.name()
                    ));
                    // V24C1: Action drain for chance involvement
                    fatigueModel.applyDrain(c, 3);
                }
            }

            // V24C2: Foul / yellow card / red card using discipline model
            // V24D22-FIX-FORMATION-IGNORED: pass formation for consistency with
            // attemptShot/chanceCreated (shooter/assist paths already formation-aware).
            var potentialFouler = selector.selectShooter(possessor.startingPlayers(), formation);
            if (potentialFouler.isPresent()) {
                V24PlayerMatchState f = potentialFouler.get();
                boolean defending = !homeHasPossession; // fouler is on defending side when opponent has possession
                if (disciplineModel.shouldCommitFoul(f, possessor.style(), defending, random)) {
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.FOUL,
                            teamRole,
                            f.sessionPlayerId(),
                            f.name(),
                            null, null,
                            0.0,
                            f.name() + " committed a foul"
                    ));

                    // V24C1: Action drain for foul committed
                    fatigueModel.applyDrain(f, 5);

                    // V24C2: Yellow card check
                    if (disciplineModel.shouldReceiveYellow(f, possessor.style(), random) && !f.redCard()) {
                        applyYellowCardAndMaybeSecondYellowRed(f, timeline, minute, teamRole);
                    }
                }
            }

            // V24C3: Injury event using injury model
            // V24D22-FIX-FORMATION-IGNORED: pass formation for consistency.
            var potentialInjured = selector.selectShooter(possessor.startingPlayers(), formation);
            if (potentialInjured.isPresent()) {
                V24PlayerMatchState p = potentialInjured.get();
                // highIntensityAction=false here; shots/fouls/chances already covered by their own drains
                if (injuryModel.shouldInjure(p, possessor.style(), false, random)) {
                    p.injure();
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.INJURY,
                            teamRole,
                            p.sessionPlayerId(),
                            p.name(),
                            null, null,
                            0.0,
                            p.name() + " was injured"
                    ));
                }
            }

            // Corner (when possession is near goal but no shot)
            // V24D22-FIX-FORMATION-IGNORED: pass formation for consistency.
            if (random.nextDouble() < 0.035) {
                var player = selector.selectShooter(possessor.startingPlayers(), formation);
                if (player.isPresent()) {
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.CORNER,
                            teamRole,
                            player.get().sessionPlayerId(),
                            player.get().name(),
                            null, null,
                            0.0,
                            "Corner for " + possessor.name()
                    ));
                }
            }

            // Offside (when team is pushing forward)
            // V24D22-FIX-FORMATION-IGNORED: pass formation for consistency.
            if (random.nextDouble() < 0.04 && possessor.style() != TeamStyle.DEFENSIVE) {
                var player = selector.selectShooter(possessor.startingPlayers(), formation);
                if (player.isPresent()) {
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.OFFSIDE,
                            teamRole,
                            player.get().sessionPlayerId(),
                            player.get().name(),
                            null, null,
                            0.0,
                            "Offside"
                    ));
                }
            }

            // V24C4: Substitutions using V24SubstitutionEngine (5 max, priority-based)
            // Home team substitution after minute 60 when not in possession
            if (minute >= 60 && !homeState.startingPlayers().isEmpty() && substitutionEngine.hasSubstitutionsRemaining(context.homeTeamId()) && !homeHasPossession) {
                substitutionEngine.attemptSubstitution(homeState, minute)
                        .ifPresent(e -> timeline.addEvent(e));
            }
            // Away team substitution after minute 60 when in possession
            if (minute >= 60 && !awayState.startingPlayers().isEmpty() && substitutionEngine.hasSubstitutionsRemaining(context.awayTeamId()) && homeHasPossession) {
                substitutionEngine.attemptSubstitution(awayState, minute)
                        .ifPresent(e -> timeline.addEvent(e));
            }

            clock.advance();
        }

        return finalizeResult(context, homeState, awayState, timeline);
    }

    private void attemptShot(
            V24TeamMatchState possessor,
            V24TeamMatchState opponent,
            V24PlayerSelector selector,
            String formation,
            String opponentFormation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Map<String, LineupSlotDTO> possessorSlotsByPlayerId,
            Map<String, LineupSlotDTO> opponentSlotsByPlayerId,
            String teamRole,
            int minute,
            Random random,
            V24MatchTimeline timeline) {

        var shooterOpt = selector.selectShooter(possessor.startingPlayers(), formation);
        if (shooterOpt.isEmpty()) return;

        V24PlayerMatchState shooter = shooterOpt.get();

        // V25D27: compute aggregate teamAttack for the possessor and teamDefense for
        // the opponent. These amplify/dampen the formation modifier per the user
        // request that formation × stats should sum.
        double possessorAttack = aggregateAttackerStat(
                possessor.startingPlayers(),
                formation,
                possessorSlotsByPlayerId);
        double opponentDefense = aggregateDefenderStat(
                opponent.startingPlayers(),
                opponentSlotsByPlayerId);

        // V25D33-F3: locate the opponent's on-pitch GK so we can pass their
        // skill map (WALL) and height to calculateXg. Reuses the same
        // filter as the existing gkQuality() helper, but returns the
        // V24PlayerMatchState so we can read skillLevels + heightCm.
        V24PlayerMatchState opponentGk = findGkOnPitch(opponent.startingPlayers());

        // V24C1: Apply fatigue to shooter quality before xG calculation
        double rawShooterQuality = selector.shooterQuality(shooter);
        double shooterQuality = fatigueModel.applyFatigueToQuality(rawShooterQuality, shooter);

        // V25D99.20.5: shot location reads the real tactical shape, not only
        // the formation label. A team overloaded through the centre gets more
        // central/box shots; a team with useful width gets more wide shots; and
        // opponent channel coverage can push attempts away from the protected
        // lane. This is the first engine layer for "play through wings/centre".
        V24ShotLocation location = selectShotLocation(
                possessor.style(), formation, possessorShape, opponentShape, random);
        // V25D99.22.22: coordinate is generated before xG so wide shots can
        // use left/right defensive channel pressure. LEFT_FLANK/RIGHT_FLANK
        // are internal calibration styles used by the harness; they behave
        // like WIDE_PLAY in volume but bias the y-coordinate to one side.
        V24ShotCoordinate shotCoord = generateShotCoordinate(
                location, possessor.style(), possessorShape, opponentShape, random);
        // V25D99.22.14: make defensive quality channel-sensitive. A weak
        // fullback/carrilero should hurt mainly wide chances; a weak CB/GK
        // should hurt central chances. The global defender stat remains the
        // fallback when slot coordinates are absent.
        opponentDefense = aggregateDefenderStatForLocation(
                opponent.startingPlayers(),
                opponentSlotsByPlayerId,
                location,
                shotCoord,
                opponentDefense);

        // Get assist provider via V24AssistModel
        var assistOpt = assistModel.selectAssistProvider(
                possessor.startingPlayers(), shooter, formation, possessor.style(), random);
        String assistPlayerId = assistOpt.map(V24PlayerMatchState::sessionPlayerId).orElse(null);
        String assistPlayerName = assistOpt.map(V24PlayerMatchState::name).orElse(null);

        // Build shot quality bundle
        double assistQuality = assistOpt.map(selector::assistQuality).orElse(0.3);
        // V25D34-F1: PLAYMAKER boosts assist quality (vision de juego → mejor
        // pase). El assist provider con PLAYMAKER skill > 0 multiplica su
        // assistQuality por (1 + skill/200). El efecto se propaga al assistMult
        // del V24ShotXgCalculator (0.85 + assistQuality * 0.30). Si no hay
        // provider (assistOpt.isEmpty) o PLAYMAKER=0/absent, sin cambio —
        // preserva bit-a-bit el resultado V25D33.
        if (assistOpt.isPresent()) {
            int playmakerSkill = assistOpt.get().getSkillLevel(PlayerSkill.PLAYMAKER);
            assistQuality = playmakerAdjustedAssistQuality(assistQuality, playmakerSkill);
        }
        double defPressure = defensivePressure(opponent, random);
        // V25D99.87: xG must read the defending goalkeeper, not the team
        // taking the shot. The old possessor.startingPlayers() call made the
        // attack partly defend against its own GK quality, which distorted
        // live/harness comparisons when swapping teams or keepers.
        double gkQuality = gkQuality(opponent.startingPlayers(), random);

        V24ShotQuality quality = new V24ShotQuality(
                location,
                shooterQuality,
                assistQuality,
                defPressure,
                gkQuality,
                styleToModifier(possessor.style())
        );

        // V25D34-F2: aggregate opponent defender skills (MARKER + TACKLER)
        // from on-pitch DEF position players. Used by the new overload 11-args
        // de calculateXg para aplicar las defending skills. Si no hay DEF
        // players on-pitch, el map viene vacio → MARKER y TACKLER no aplican
        // (no-op, igual que antes).
        Map<PlayerSkill, Integer> opponentDefenderSkills =
                aggregateOpponentDefenderSkills(opponent.startingPlayers());

        // V25D34-F2: call the 11-args overload of calculateXg so WALL divisor
        // (V25D33-F3), AERIAL/SHOOTER (V25D34-F1) and MARKER/TACKLER (V25D34-F2)
        // are honored. Pass the shooter's own skill map + height (for HEADER on
        // corner/cross shots + AERIAL compounding + SHOOTER LONG_RANGE), the
        // opponent GK's skill map + height (for WALL divisor), and the
        // opponent defender aggregated skills (for MARKER + TACKLER).
        // eventSubType = OPEN_PLAY (default) — the engine doesn't yet model
        // "shot from corner" relationships (V25D34 scope). When skill maps are
        // null or relevant skills are absent, the multipliers stay 1.0 and the
        // result is bit-a-bit identical to the V25D32 baseline.
        double xg = xgCalculator.calculateXg(
                quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooter.skillLevels(), shooter.heightCm(),
                opponentGk != null ? opponentGk.skillLevels() : null,
                opponentGk != null ? opponentGk.heightCm() : null,
                V24ShotEventType.OPEN_PLAY,
                opponentDefenderSkills, null);
        // V25D99.60: the visual/manual shape must influence not only how many
        // shots are generated, but also how clean those shots are. A compact
        // low block, a well-screened centre, or protected flanks should turn
        // dangerous locations into slightly worse chances; conversely, dragging
        // players away from a lane should make shots from that lane cleaner.
        // This keeps the formation editor meaningful at pixel level without
        // making formation labels hard-coded winners.
        xg *= defensiveShapeShotQualityMultiplier(opponentShape, location);
        // V25D99.63: defending style also changes shot cleanliness. DEFENSIVE
        // and COUNTER should turn more shots into lower-quality attempts;
        // ATTACKING/POSSESSION can leave more space if bypassed.
        xg *= defensiveStyleShotQualityMultiplier(opponent.style(), location);
        possessor.addXg(xg);

        // V24C1: Action drain for shot attempt
        fatigueModel.applyDrain(shooter, 8);

        // Resolve shot outcome (xG threshold + randomness)
        // V24D6U4-RE: Recalibrated onTarget base and goal threshold to hit
        // Poisson λ=1.25 after raising chanceProbability (more shots).
        // Previous (V24D6U4): onTarget base 0.18, goal threshold xg/0.40
        // produced too few goals (~9% conversion, λ≈0.45).
        // New: onTarget base 0.30 (more realistic 30-35% on-target),
        // goal threshold xg/0.60 (60% xG = 100% goal — slightly more
        // permissive per unit xG to compensate for higher shot volume).
        boolean onTarget = random.nextDouble() < onTargetProbability(xg);
        boolean isGoal = false;

        if (onTarget) {
            // Goal if xG > random threshold (higher xG = more likely to beat keeper)
            // V25D67-C27: scale goal-conversion probability by matchIntensity, which
            // is computed once per match from the absolute difference of the home
            // and away starting-XI average overalls. For parejos matches (teams
            // within 5% overall), intensity ≈ 0.40 → ~60% reduction in goal prob
            // (the 4-0 smoke result from C22 becomes a realistic 1-1 / 1-0).
            // For desiguales (≥30% diff), intensity = 1.00 → unchanged; the
            // engine's existing random.nextDouble() vs threshold already produces
            // the lucky escapes (0-0, 1-0) Iván called out in the C27 brief.
            // Iván's brief explicitly forbids forcing goleadas or artificial
            // topes — we never raise intensity above 1.0.
            isGoal = random.nextDouble() < (xg * matchIntensity / 0.60); // V25D67-C27
            if (isGoal) {
                possessor.addGoal();
                // V24D20-SANDBOX-V2-MVP BUG #4: increment the addGoal counter
                // so finalizeResult can detect divergence between addGoal
                // calls and GOAL events in the timeline.
                int n = goalAdditions.incrementAndGet();
                log.debug("[V24-XG-COUNTER] addGoal called; counter={}, minute={}, xg={}",
                    n, minute, xg);
                // V24D6O-fix: count goal as a shot on target so homeShots/awayShots
                // (used in the Stats summary) is consistent with the Shot Map total.
                // A goal is by definition a shot that hit the target and went in.
                possessor.addShot(true);
                String goalDesc = assistPlayerId != null
                        ? "Goal by " + shooter.name() + " assisted by " + assistPlayerName + " " + minute + "'"
                        : "Goal! " + shooter.name() + " " + minute + "'";
                timeline.addEvent(new V24MatchEvent(
                        minute,
                        V24MatchEventType.GOAL,
                        teamRole,
                        shooter.sessionPlayerId(),
                        shooter.name(),
                        assistPlayerId,
                        assistPlayerName,
                        Math.round(xg * 1000.0) / 1000.0,
                        goalDesc
                ).withShotCoordinate(shotCoord));
            }
        }

        if (onTarget && !isGoal) {
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.SHOT_ON_TARGET,
                    teamRole,
                    shooter.sessionPlayerId(),
                    shooter.name(),
                    assistPlayerId,
                    assistPlayerName,
                    Math.round(xg * 1000.0) / 1000.0,
                    "Shot saved"
            ).withShotCoordinate(shotCoord));
            possessor.addShot(true);
        } else if (!onTarget) {
            V24MatchEventType missType = random.nextDouble() < 0.3
                    ? V24MatchEventType.BLOCK
                    : V24MatchEventType.MISS;
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    missType,
                    teamRole,
                    shooter.sessionPlayerId(),
                    shooter.name(),
                    assistPlayerId,
                    assistPlayerName,
                    Math.round(xg * 1000.0) / 1000.0,
                    "Shot missed"
            ).withShotCoordinate(shotCoord));
            possessor.addShot(false);
        }
    }

    // V24D23-A: ordered array mirroring V24ShotLocation.values() — used by
    // the weighted-distribution selectShotLocation. Index 0 = SIX_YARD_BOX,
    // index 1 = PENALTY_AREA_CENTER, ..., index 4 = LONG_RANGE.
    private static final V24ShotLocation[] LOCATIONS = V24ShotLocation.values();

    // V24D23-A: shared V24FormationParser instance. Per R3 in the sprint
    // doc, allocating per-minute would add GC pressure; static-final keeps
    // it bounded to one parser per engine instance (and one engine per
    // V24LiveSession).
    private static final V24FormationParser FORMATION_PARSER = new V24FormationParser();

    /**
     * V24D23-A: formation-aware shot location selection. Combines the
     * {@link TeamStyle} baseline distribution with a formation-driven
     * modifier, so two teams with the same style but different formations
     * (e.g. 4-3-3 vs 4-4-2) produce measurably different shot location
     * distributions. This amplifies the xG variation that was previously
     * limited to the ~5% shooter-share shift from V24PlayerSelector.
     *
     * <p>Algorithm: weighted draw over {@link #LOCATIONS} using
     * {@link #computeLocationWeights(TeamStyle, String)} as the weight
     * vector. Weights are not normalized — the cumulative-sum loop in
     * this method normalizes them implicitly via {@code roll < cum}.
     *
     * @param style     the team's tactical style (ATTACKING, POSSESSION, COUNTER,
     *                  DEFENSIVE, BALANCED); never null
     * @param formation formation string in canonical form (e.g. "4-3-3",
     *                  "3-5-2", "4-2-3-1"); null/blank falls back to BALANCED_DEFAULT
     *                  (4-4-2) so unknown formations degrade gracefully
     * @param random    the per-shot RNG; one {@code nextDouble()} is consumed per call
     * @return the chosen shot location for this attempt
     */
    private V24ShotLocation selectShotLocation(
            TeamStyle style,
            String formation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Random random) {
        double[] weights = computeLocationWeights(style, formation, possessorShape, opponentShape);
        double total = weights[0] + weights[1] + weights[2] + weights[3] + weights[4];
        // Guard against pathological totals (should never happen — every weight is positive)
        if (total <= 0.0) return V24ShotLocation.PENALTY_AREA_CENTER;
        double roll = random.nextDouble() * total;
        double cum = 0.0;
        for (int i = 0; i < LOCATIONS.length; i++) {
            cum += weights[i];
            if (roll < cum) return LOCATIONS[i];
        }
        // Floating-point fallback for the boundary case (roll == total).
        return LOCATIONS[LOCATIONS.length - 1];
    }

    private V24ShotCoordinate generateShotCoordinate(V24ShotLocation location, TeamStyle style, Random random) {
        if (location == V24ShotLocation.PENALTY_AREA_WIDE && style == TeamStyle.LEFT_FLANK) {
            return coordGenerator.generateWideFlank(true, random);
        }
        if (location == V24ShotLocation.PENALTY_AREA_WIDE && style == TeamStyle.RIGHT_FLANK) {
            return coordGenerator.generateWideFlank(false, random);
        }
        return coordGenerator.generate(location, random);
    }

    private V24ShotCoordinate generateShotCoordinate(
            V24ShotLocation location,
            TeamStyle style,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Random random) {
        if (location != V24ShotLocation.PENALTY_AREA_WIDE
                || style == TeamStyle.LEFT_FLANK
                || style == TeamStyle.RIGHT_FLANK
                || possessorShape == null
                || opponentShape == null) {
            return generateShotCoordinate(location, style, random);
        }

        // Side channels are seen from the attacking team's perspective. The
        // rival's right-back defends our left lane, and the rival's left-back
        // defends our right lane, so defensive flank metrics must be mirrored.
        double leftOpportunity = flankExploitOpportunity(possessorShape.attackLeft(), opponentShape.defenseRight());
        double rightOpportunity = flankExploitOpportunity(possessorShape.attackRight(), opponentShape.defenseLeft());
        double opportunityGap = Math.abs(leftOpportunity - rightOpportunity);
        if (opportunityGap < 0.04) {
            return generateShotCoordinate(location, style, random);
        }

        boolean attackLeft = leftOpportunity > rightOpportunity;
        double bias = clamp(0.50 + opportunityGap * 1.00, 0.50, 0.88);
        if (random.nextDouble() < bias) {
            return coordGenerator.generateWideFlank(attackLeft, random);
        }
        return coordGenerator.generateWideFlank(!attackLeft, random);
    }

    private V24ShotLocation selectShotLocation(TeamStyle style, String formation, Random random) {
        return selectShotLocation(style, formation, neutralShapeProfile(), neutralShapeProfile(), random);
    }

    /**
     * V24D23-A: compute the 5-element weight vector for shot-location
     * selection. Index 0 = SIX_YARD_BOX, 1 = PENALTY_AREA_CENTER,
     * 2 = PENALTY_AREA_WIDE, 3 = OUTSIDE_BOX, 4 = LONG_RANGE.
     *
     * <p>Pipeline: baseline BALANCED weights → multiply by style shift
     * → multiply by formation-specific modifiers. The result is NOT
     * normalized; {@link #selectShotLocation(TeamStyle, String, Random)}
     * handles normalization via its cumulative-sum loop.
     *
     * <p>Baseline (BALANCED) shares: 25% six, 27% center, 20% wide,
     * 18% outside, 10% long. These match the pre-V24D23-A hardcoded
     * BALANCED thresholds so the regression profile (style=BALANCED,
     * formation=4-4-2) keeps producing the same overall xG distribution
     * for a 4-4-2 squad — the formation modifiers above the baseline
     * are what make 4-3-3 vs 4-2-3-1 distinguishable.
     */
    private double[] computeLocationWeights(
            TeamStyle style,
            String formation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape) {
        // Baseline (BALANCED) — preserved from pre-V24D23-A for regression continuity.
        double[] w = { 0.25, 0.27, 0.20, 0.18, 0.10 };

        // Style shift
        double[] shift = styleLocationShift(style);
        for (int i = 0; i < w.length; i++) {
            w[i] *= shift[i];
        }

        // Formation shift. The parser is the shared static-final instance
        // (R3 mitigation); a null/blank formation degrades to BALANCED_DEFAULT
        // (4-4-2) so unknown formations do not crash the engine.
        V24FormationParser.V24Formation f = FORMATION_PARSER.parse(formation);
        if (f.hasWingers()) {
            // 4-3-3, 3-4-3: wingers cut inside → more PENALTY_AREA_WIDE,
            // less SIX_YARD_BOX (more dispersion from wide positions).
            w[2] *= 1.25;  // PENALTY_AREA_WIDE +25%
            w[0] *= 0.90;  // SIX_YARD_BOX -10%
        }
        if (f.defenders() == 3) {
            if (f.hasWingers()) {
                // V25D99.189: a 3-4-3 is not a narrow back-three attack. The
                // previous generic back-three penalty halved wide shots even
                // when the shape had natural wingers, so the harness kept
                // reading almost every formation as central. Keep some central
                // concentration from three CBs, but let the front-three/wing
                // lanes remain a real attacking identity.
                w[2] *= 0.88;  // PENALTY_AREA_WIDE -12% after winger boost
                w[1] *= 1.05;  // PENALTY_AREA_CENTER +5%
            } else {
                // 3-5-2: three centre-backs without natural wingers tends to
                // concentrate attacks inside and through wingback support, not
                // pure high-wide forward volume.
                w[2] *= 0.62;  // PENALTY_AREA_WIDE -38%
                w[1] *= 1.12;  // PENALTY_AREA_CENTER +12%
            }
        }
        if (f.forwards() == 1) {
            // 4-2-3-1 (and 4-3-3 per the parser, which also has forwards=1):
            // single striker stays close to goal → more SIX_YARD_BOX,
            // fewer LONG_RANGE (no long-range solo runs).
            w[0] *= 1.30;  // SIX_YARD_BOX +30%
            w[4] *= 0.70;  // LONG_RANGE -30%
        }
        if (f.forwards() == 2) {
            // 4-4-2, 3-5-2: two strikers occupy the central channel →
            // more PENALTY_AREA_CENTER.
            w[1] *= 1.20;  // PENALTY_AREA_CENTER +20%
        }
        applyNamedFormationIdentityLocationShift(w, formation);
        if (possessorShape != null && opponentShape != null) {
            double centralAttack = possessorShape.attackCenter();
            double wideAttack = (possessorShape.attackLeft() + possessorShape.attackRight()) / 2.0;
            double centralDefense = opponentShape.defenseCenter();
            double wideDefense = (opponentShape.defenseLeft() + opponentShape.defenseRight()) / 2.0;

            double centralEdge = centralAttack - centralDefense;
            double wideEdge = wideAttack - wideDefense;
            double flankImbalance = Math.abs(possessorShape.attackLeft() - possessorShape.attackRight());
            // Mirror opponent defensive sides: their right side protects our
            // left attack lane, and their left side protects our right attack lane.
            double leftFlankEdge = flankExploitOpportunity(possessorShape.attackLeft(), opponentShape.defenseRight());
            double rightFlankEdge = flankExploitOpportunity(possessorShape.attackRight(), opponentShape.defenseLeft());
            double bestFlankEdge = Math.max(leftFlankEdge, rightFlankEdge);
            double flankExploitGap = Math.abs(leftFlankEdge - rightFlankEdge);

            w[0] *= clamp(1.0 + centralEdge * 0.18, 0.86, 1.18);
            w[1] *= clamp(1.0 + centralEdge * 0.22, 0.84, 1.22);
            // V25D99.168: natural wingers must bend the shot map, but not turn
            // every flank edge into runaway xG against a better/structured XI.
            // The old +28% cap made wide superiority too decisive in formation
            // averages; this keeps lanes visible while letting collective
            // quality and defensive coverage stay in the conversation.
            w[2] *= clamp(1.0 + wideEdge * 0.22, 0.80, 1.22);
            // V25D99.198: if one opponent flank is specifically vulnerable,
            // the attack should create a visibly wider shot profile, not just
            // a generic xG bump. Keep this as a nudge: big enough for the
            // harness side columns, capped so a single weak fullback does not
            // override formation identity or central quality.
            w[2] *= clamp(1.0 + Math.max(0.0, bestFlankEdge) * 0.16 + flankExploitGap * 0.18, 0.92, 1.18);
            w[3] *= clamp(1.0 + Math.max(0.0, wideDefense - wideAttack) * 0.14, 0.92, 1.16);
            w[4] *= clamp(1.0 + Math.max(0.0, centralDefense - centralAttack) * 0.12, 0.94, 1.14);
            w[1] *= clamp(1.0 - flankImbalance * 0.10, 0.88, 1.0);
        }
        return w;
    }

    /**
     * V25D99.58: the parser intentionally collapses some labels into broad
     * families (e.g. 4-4-2 and 4-2-2-2 both have 4 mids + 2 forwards). This
     * small named layer preserves tactical identity without turning formation
     * names into hard tiers: geometry still carries the main signal, while the
     * label nudges shot geography in the direction a coach would expect.
     */
    private void applyNamedFormationIdentityLocationShift(double[] w, String formation) {
        if (w == null || w.length < 5 || formation == null) return;
        switch (formation) {
            case "4-3-3" -> {
                // Classic front three: the wingers stretch the last line and
                // create more wide-box entries, but the team gives up a little
                // box occupation compared with two-striker shapes. This is a
                // lane identity, not a free global bonus.
                w[0] *= 0.93; // SIX_YARD_BOX
                w[2] *= 1.32; // PENALTY_AREA_WIDE
                w[4] *= 0.90; // LONG_RANGE
            }
            case "4-2-2-2" -> {
                // Narrow box: central combinations and edge-of-box shots, less
                // classic touchline/cross volume than 4-4-2.
                w[1] *= 1.10; // PENALTY_AREA_CENTER
                w[2] *= 0.88; // PENALTY_AREA_WIDE
                w[3] *= 1.06; // OUTSIDE_BOX
            }
            case "4-1-2-3" -> {
                // Pivot 4-3-3: safer central circulation, slightly less chaotic
                // six-yard crash than flat front-three 4-3-3.
                w[0] *= 0.94; // SIX_YARD_BOX
                w[1] *= 1.08; // PENALTY_AREA_CENTER
                w[4] *= 0.92; // LONG_RANGE
            }
            case "3-5-2-CDM" -> {
                // 3-5-2 with a real holder: more controlled central entries,
                // less exposed wingback-only wide shot profile.
                w[1] *= 1.07; // PENALTY_AREA_CENTER
                w[2] *= 0.92; // PENALTY_AREA_WIDE
                w[4] *= 0.95; // LONG_RANGE
            }
            default -> {
                // No named adjustment.
            }
        }
    }

    private double[] computeLocationWeights(TeamStyle style, String formation) {
        return computeLocationWeights(style, formation, neutralShapeProfile(), neutralShapeProfile());
    }

    private double flankExploitOpportunity(double attackLane, double mirroredOpponentDefenseLane) {
        double vulnerability = Math.max(0.0, 1.0 - mirroredOpponentDefenseLane);
        // V25D99.202: choosing the side of a wide attack should read like a
        // manager targeting a weak fullback. Own occupation still matters, but
        // a clearly vulnerable mirrored defensive lane gets extra intent so
        // the engine does not keep drifting to the squad's natural strong side.
        return (attackLane * 0.90) - (mirroredOpponentDefenseLane * 0.70) + (vulnerability * 0.35);
    }

    /**
     * V24D23-A: per-style location-distribution ratios. Each entry is a
     * multiplicative shift applied to the BALANCED baseline in
     * {@link #computeLocationWeights(TeamStyle, String)}.
     *
     * <p>These ratios were derived from the pre-V24D23-A hardcoded
     * cumulative thresholds in this method's previous switch-based
     * implementation, then rounded to 2 decimals. They preserve the
     * pre-sprint distribution for ATTACKING/POSSESSION/COUNTER/DEFENSIVE
     * styles (so the regression profile holds for style-only changes) and
     * isolate the formation axis as the new variable.
     */
    private double[] styleLocationShift(TeamStyle style) {
        return switch (style) {
            // More inside-box attempts (ATTACKING style piles pressure on the box)
            case ATTACKING -> new double[] { 1.60, 1.11, 0.85, 0.50, 0.30 };
            // Slow build-up, balanced penetration
            case POSSESSION -> new double[] { 1.20, 1.11, 0.90, 0.56, 0.30 };
            // Wide focus: more wing/cross attempts, fewer central tap-ins.
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> new double[] { 0.98, 0.94, 1.42, 0.98, 0.86 };
            // Central focus: more through-ball/box-center chances, fewer wide shots.
            case CENTRAL_PLAY -> new double[] { 1.12, 1.20, 0.72, 0.94, 0.86 };
            // Fast breaks, more long-range and outside-box
            case COUNTER -> new double[] { 0.72, 1.11, 0.90, 0.94, 0.60 };
            // Prefer long-range and outside-box (defensive, low block)
            case DEFENSIVE -> new double[] { 0.40, 0.93, 0.90, 1.22, 0.90 };
            // Baseline — no shift
            default -> new double[] { 1.00, 1.00, 1.00, 1.00, 1.00 };
        };
    }

    private double defensivePressure(V24TeamMatchState opponent, Random random) {
        double basePressure = 0.5;
        // Count defenders currently on pitch
        long defendersOnPitch = opponent.startingPlayers().stream()
                .filter(p -> p.onPitch() && (p.position().equals("DEF") || p.position().equals("MID")))
                .count();
        double defMod = Math.min(0.9, defendersOnPitch / 11.0 * 1.2);
        double randomFactor = 0.7 + random.nextDouble() * 0.6;
        return Math.min(1.0, basePressure * defMod * randomFactor);
    }

    /**
     * V25D34-F1: PLAYMAKER skill impact on assist quality.
     *
     * <p>Boosts the base assistQuality (normalized 0-1 from technique) by a
     * factor of {@code 1 + skill/200}. Models "vision de juego y creacion de
     * juego" — un asistidor con PLAYMAKER=99 ve mejores pases que uno sin
     * la skill, lo que se traduce en assistQuality mas alto y por lo tanto
     * xG mas alto en el V24ShotXgCalculator (assistMult = 0.85 + assistQ*0.30).
     *
     * <p>No-op behavior:
     * <ul>
     *   <li>{@code playmakerSkill <= 0} (skill absent o level 0) → retorna
     *       {@code base} sin cambio. Esto preserva el resultado V25D33
     *       bit-a-bit para callers que no setean PLAYMAKER.</li>
     * </ul>
     *
     * <p>Calibration:
     * <ul>
     *   <li>PLAYMAKER=0 → adjust factor = 1.0 (sin cambio)</li>
     *   <li>PLAYMAKER=50 → adjust factor = 1.25 (+25% assistQuality)</li>
     *   <li>PLAYMAKER=88 (Bellingham) → adjust factor = 1.44 (+44% assistQuality)</li>
     *   <li>PLAYMAKER=99 → adjust factor = 1.495 (+49.5% assistQuality)</li>
     * </ul>
     *
     * <p>El cap lo pone {@link V24ShotQuality} constructor (clampFinite a
     * {@code [0.0, 100.0]}), no este metodo — un PLAYMAKER=99 con technique=99
     * da assistQuality = 1.0 * 1.495 = 1.495 que pasa el clamp (max 100) sin
     * modificarse, y luego el calculator hace assistMult = 0.85 + 1.495*0.30
     * = 1.2985.
     *
     * @param baseAssistQuality normalized [0, 1] from {@code selector.assistQuality}
     * @param playmakerSkill PLAYMAKER level (0-99, sparse map semantics)
     * @return adjusted assistQuality
     */
    private double playmakerAdjustedAssistQuality(double baseAssistQuality, int playmakerSkill) {
        if (playmakerSkill <= 0) return baseAssistQuality;
        return baseAssistQuality * (1.0 + playmakerSkill / 200.0);
    }

    private double gkQuality(List<V24PlayerMatchState> players, Random random) {
        // Simplified: average GK save quality from position
        var gk = players.stream()
                .filter(p -> p.position().equals("GK") && p.onPitch())
                .findFirst();
        if (gk.isEmpty()) return 0.5;
        // GK quality from stamina + mentality (normalized)
        return Math.round((gk.get().stamina() / 100.0 * 0.5 + gk.get().mentality() / 100.0 * 0.5) * 1000.0) / 1000.0;
    }

    static double onTargetProbability(double xg) {
        // V25D99.87: monotonic shot-on-target model. Previously
        // 0.30 + (1 - xg) * 0.42 inverted the football intuition: weak
        // 0.01 xG attempts were far more likely to hit the target than
        // clean 0.60 xG chances. Keep realistic bounds while making every
        // xG/pixel/tactical quality improvement directionally visible.
        double normalizedXg = clamp(xg, 0.0, 0.60) / 0.60;
        return clamp(0.38 + normalizedXg * 0.16, 0.38, 0.54);
    }

    /**
     * V25D33-F3: locate the on-pitch GK for a team's starting 11 and return
     * their {@link V24PlayerMatchState}. Returns {@code null} when no on-pitch
     * GK is present (short-handed team) — the caller passes {@code null} to
     * {@code calculateXg(...)} which keeps the WALL divisor at 1.0 (no
     * reduction in xG, bit-a-bit compat with the V25D32 baseline).
     *
     * <p>Filter is identical to {@link #gkQuality}: position="GK" AND
     * onPitch=true. Picks the FIRST such player in iteration order; this
     * matches the {@code gkQuality} helper's behavior so the two paths
     * never disagree about which GK is "in goal" for a given shot.
     */
    private V24PlayerMatchState findGkOnPitch(List<V24PlayerMatchState> players) {
        return players.stream()
                .filter(p -> p.position().equals("GK") && p.onPitch())
                .findFirst()
                .orElse(null);
    }

    /**
     * V25D27: aggregate attacker stat for the possessor's starting 11, weighted
     * by formation-aware role. Returns the avg attack stat of the top-7
     * "attacking" players (forwards, attacking midfielders, wingers) where
     * "attacking" is defined by position: ATT > MID > DEF. This stat amplifies
     * the formationOffensiveModifier — elite attackers in a 4-3-3 get more
     * xG boost than weak attackers in the same formation.
     *
     * <p>V25D99.18: widened from top-5 to top-7 so the panel reacts when
     * MIDs push into the attack zone (their eff rises via
     * SubdivisionEffectivenessCalculator but their raw attack ~70 falls
     * short of the top-5 dominated by STs ~80). With 7 slots, 2 MIDs with
     * high eff near the natural pos can enter the cohort and bump ATT.
     *
     * <p>Fallback: if fewer than 7 "attacking" players, averages all 11.
     * Returns 70.0 (median) if startingPlayers is empty.
     */
    private double aggregateAttackerStat(
            List<V24PlayerMatchState> players,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (players.isEmpty()) return 70.0;
        // Sort by attack descending and pick top-7
        List<V24PlayerMatchState> sorted = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .sorted((a, b) -> Integer.compare(b.attack(), a.attack()))
                .limit(7)
                .toList();
        // V25D47 (Sprint C11a): weight each player's attack contribution by
        // PositionEffectivenessCalculator.effectiveness(naturalPosition, position).
        // A CB placed in a MID slot (effectiveness 0.8) contributes 80% of its
        // attack stat; a perfect match contributes 100%. The top-7 selection
        // (V25D99.18) is unchanged in shape — still the N highest-attack
        // on-pitch players — but the average is now effectiveness-weighted.
        double avg = sorted.stream()
                .mapToDouble(p -> p.attack()
                        * tacticalEffectiveness(p, slotsByPlayerId)
                        * forwardIntentMultiplier(p, slotsByPlayerId))
                .average()
                .orElse(70.0);
        return avg;
    }

    private double scheduledSubAttackVolumeMultiplier(
            V24TeamMatchState team,
            List<V24MatchContext.ScheduledSub> substitutions,
            String teamId,
            int minute) {
        if (team == null || substitutions == null || substitutions.isEmpty() || teamId == null) {
            return 1.0;
        }
        double delta = 0.0;
        for (V24MatchContext.ScheduledSub sub : substitutions) {
            if (sub == null
                    || !teamId.equals(sub.teamId())
                    || sub.effectiveMinute() > minute) {
                continue;
            }
            V24PlayerMatchState off = findPlayerForSubImpact(team, sub.playerOffId());
            V24PlayerMatchState on = findPlayerForSubImpact(team, sub.playerOnId());
            if (off == null || on == null) {
                continue;
            }
            delta += substitutionAttackFootprint(on) - substitutionAttackFootprint(off);
        }
        if (Math.abs(delta) < 0.001) {
            return 1.0;
        }
        return clamp(1.0 + (delta / 700.0), 0.86, 1.14);
    }

    private V24PlayerMatchState findPlayerForSubImpact(V24TeamMatchState team, String playerId) {
        if (team == null || playerId == null || playerId.isBlank()) return null;
        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p != null && playerId.equals(p.sessionPlayerId())) return p;
        }
        for (V24PlayerMatchState p : team.benchPlayers()) {
            if (p != null && playerId.equals(p.sessionPlayerId())) return p;
        }
        return null;
    }

    private double substitutionAttackFootprint(V24PlayerMatchState player) {
        if (player == null) return 0.0;
        String pos = player.naturalPosition() != null ? player.naturalPosition().toUpperCase(Locale.ROOT) : "";
        double roleWeight = switch (pos) {
            case "ATT", "ST", "CF" -> 1.18;
            case "WINGER", "LW", "RW" -> 1.12;
            case "MID", "CM", "CAM", "AM", "LM", "RM" -> 0.96;
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> 0.70;
            default -> 0.88;
        };
        return roleWeight * (
                player.attack() * 2.6
                    + player.technique() * 1.5
                    + player.speed() * 1.1
                    + player.mentality() * 0.8
                    + player.stamina() * 0.4);
    }

    private double aggregateCollectiveStat(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (players == null || players.isEmpty()) return 70.0;
        double avg = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .mapToDouble(p -> {
                    double outfieldBase = "GK".equals(p.position())
                            ? ((p.defense() + p.mentality()) / 2.0)
                            : ((p.attack() + p.defense() + p.mentality()) / 3.0);
                    return outfieldBase * tacticalEffectiveness(p, slotsByPlayerId);
                })
                .average()
                .orElse(70.0);
        return avg;
    }

    /**
     * V25D27: aggregate defender stat for the opponent's starting 11.
     * Returns the avg of (defense + mentality) / 2 across all DEF and GK
     * players on pitch. This stat amplifies the formationDefensiveModifier —
     * elite defenders in a 5-3-2 reduce xG conceded more than weak defenders.
     *
     * <p>Fallback: if no defenders found, returns avg defense of all 11.
     * Returns 70.0 (median) if startingPlayers is empty.
     */
    private double aggregateDefenderStat(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (players.isEmpty()) return 70.0;
        List<V24PlayerMatchState> defenders = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .filter(p -> p.position().equals("DEF") || p.position().equals("GK"))
                .toList();
        if (defenders.isEmpty()) {
            // Fallback: avg defense of all 11 (no effectiveness penalty —
            // there are no defenders/GK in the lineup at all, so the
            // engine shouldn't down-weight anyone)
            return players.stream()
                    .filter(V24PlayerMatchState::onPitch)
                    .mapToInt(V24PlayerMatchState::defense)
                    .average()
                    .orElse(70.0);
        }
        // V25D47 (Sprint C11a): weight each defender's (defense+mentality)/2
        // contribution by PositionEffectivenessCalculator.effectiveness(...).
        // Note: switched from int division /2 to double division /2.0 to
        // preserve precision before multiplying by the effectiveness
        // multiplier (was losing 0.5 on odd sums).
        double avg = defenders.stream()
                .mapToDouble(p -> {
                    double eff = tacticalEffectiveness(p, slotsByPlayerId);
                    return ((p.defense() + p.mentality()) / 2.0) * eff;
                })
                .average()
                .orElse(70.0);
        if (slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return avg;
        }
        double weakestLink = defenders.stream()
                .mapToDouble(p -> {
                    double eff = tacticalEffectiveness(p, slotsByPlayerId);
                    return ((p.defense() + p.mentality()) / 2.0) * eff;
                })
                .min()
                .orElse(avg);
        // V25D99.24: defensive substitutions were too diluted by a plain
        // back-line average. Realistically, an opponent can target the weak
        // link, especially over repeated possessions. Keep the average as the
        // main signal, but blend in the weakest defender so replacing one
        // strong starter with a clearly weaker backup becomes visible.
        return (avg * 0.45) + (weakestLink * 0.55);
    }

    private double aggregateDefenderStatForLocation(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId,
            V24ShotLocation location,
            double fallbackGlobalDefense) {
        return aggregateDefenderStatForLocation(
                players, slotsByPlayerId, location, null, fallbackGlobalDefense);
    }

    private double aggregateDefenderStatForLocation(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId,
            V24ShotLocation location,
            V24ShotCoordinate shotCoordinate,
            double fallbackGlobalDefense) {
        if (players == null || players.isEmpty() || slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return fallbackGlobalDefense;
        }
        List<V24PlayerMatchState> defenders = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .filter(p -> p.position().equals("DEF") || p.position().equals("GK"))
                .toList();
        if (defenders.isEmpty()) {
            return fallbackGlobalDefense;
        }

        double weighted = 0.0;
        double totalWeight = 0.0;
        for (V24PlayerMatchState p : defenders) {
            double x = tacticalXPercent(p, slotsByPlayerId);
            double y = tacticalYPercent(p, slotsByPlayerId);
            double channelWeight = defenderChannelWeight(location, x, shotCoordinate);
            double depthWeight = "GK".equals(p.position()) ? 1.05 : clamp(y / 82.0, 0.45, 1.18);
            double weight = channelWeight * depthWeight;
            if (weight <= 0.0) continue;
            double eff = tacticalEffectiveness(p, slotsByPlayerId);
            double stat = ((p.defense() + p.mentality()) / 2.0) * eff;
            weighted += stat * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            return fallbackGlobalDefense;
        }
        double channelDefense = weighted / totalWeight;
        // Blend mostly with the channel so side/central defensive mismatches
        // are readable in the harness, while still keeping some global
        // defensive structure to avoid one odd coordinate deciding the whole
        // possession.
        return (channelDefense * 0.80) + (fallbackGlobalDefense * 0.20);
    }

    private double defenderChannelWeight(V24ShotLocation location, double xPercent, V24ShotCoordinate shotCoordinate) {
        double distanceFromCenter = Math.abs(xPercent - 50.0) / 50.0;
        double leftAffinity = laneLeftWeight(xPercent);
        double centerAffinity = laneCenterWeight(xPercent);
        double rightAffinity = laneRightWeight(xPercent);
        double wideAffinity = Math.max(leftAffinity, rightAffinity);
        return switch (location) {
            case PENALTY_AREA_WIDE -> {
                if (shotCoordinate == null) {
                    yield clamp(0.30 + wideAffinity * 1.15 + distanceFromCenter * 0.18, 0.30, 1.58);
                }
                boolean shotLeft = shotCoordinate.y() < 50.0;
                double sameSideAffinity = shotLeft ? leftAffinity : rightAffinity;
                double oppositeSideAffinity = shotLeft ? rightAffinity : leftAffinity;
                yield clamp(0.30
                        + sameSideAffinity * 1.35
                        + oppositeSideAffinity * 0.05
                        + distanceFromCenter * 0.08,
                        0.30, 1.68);
            }
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> clamp(0.45 + centerAffinity * 0.75, 0.45, 1.20);
            case OUTSIDE_BOX -> clamp(0.85 + centerAffinity * 0.10, 0.85, 0.95);
            case LONG_RANGE -> 0.70;
        };
    }

    private double defenderRosterChanceVolumeMultiplier(double defenderStat) {
        // Below 70: more opponent chance volume. Above 70: less opponent
        // chance volume. V25D99.34 slightly widens the previous curve because
        // defender substitutions were still too quiet in the multi-seed
        // scenario matrix. Shape and per-shot xG remain the main tactical
        // signal; this only ensures that a weak link in the back line is no
        // longer swallowed by the team average.
        // V25D99.85: make defensive personnel changes slightly more visible.
        // The previous /65 curve was professional but too flat for stress
        // swaps; a CB->ST or ST->CB test often disappeared into 0.00 deltas.
        double delta = (70.0 - defenderStat) / 55.0;
        return clamp(1.0 + delta, 0.78, 1.35);
    }

    /**
     * V25D99.22: pixel-aware player effectiveness inside the actual match
     * engine. The preview/rating panel already uses customX/customY through
     * {@link SubdivisionEffectivenessCalculator}; the live replay path must
     * use the same continuous coordinates so a manager moving a player a few
     * pixels can affect xG inputs, not only the coarse ATT/MID/DEF label.
     */
    private double tacticalEffectiveness(
            V24PlayerMatchState player,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (player == null) {
            return 1.0;
        }
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot == null) {
            return PositionEffectivenessCalculator.effectiveness(
                    player.naturalPosition(), player.position());
        }
        double x = tacticalXPercent(player, slotsByPlayerId);
        double y = tacticalYPercent(player, slotsByPlayerId);
        return SubdivisionEffectivenessCalculator.effectiveness(
                player.naturalPosition(),
                x,
                y,
                player.position());
    }

    /**
     * V25D99.22.1: mirror the preview's free-positioning attacking intent
     * inside the match engine. A CM dragged 15-20% higher is no longer treated
     * only as "farther from ideal" (penalty); it also contributes a little more
     * to attack, so the partido responds in the same direction as the visual
     * formation editor.
     */
    private double forwardIntentMultiplier(
            V24PlayerMatchState player,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (player == null) {
            return 1.0;
        }
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot == null || slot.customYPercent() == null || Double.isNaN(slot.customYPercent())) {
            return 1.0;
        }
        double y = slot.customYPercent();
        if ("ATT".equals(player.position())) {
            double forward = clamp((22.0 - y) / 18.0, 0.0, 1.0);
            double width = clamp(Math.abs(tacticalXPercent(player, slotsByPlayerId) - 50.0) / 50.0, 0.0, 1.0);
            return 1.0 + (0.12 * forward) + (0.05 * width);
        }
        double forward = clamp((55.0 - y) / 40.0, 0.0, 1.0);
        return 1.0 + (0.25 * forward);
    }

    /**
     * V25D34-F2 helper: aggregate MARKER + TACKLER skill levels from the
     * opponent's DEF on-pitch players. Used by the V24 engine to feed
     * {@code V24ShotXgCalculator} (defending side of the duel).
     *
     * <p>V25D35: visibility changed from {@code private} to package-private so
     * the unit test {@code AggregateOpponentDefenderSkillsTest} (same package)
     * can drive the helper directly without reflection. Reflection-on-private
     * was the previous fallback and the verifier nit called it out as fragile.
     * The helper is still single-package-only (no public access), so the
     * engine's public surface remains unchanged.
     *
     * <p><b>NOTE — "visible-for-testing":</b> this method is package-private
     * solely so the unit test can call it. It is NOT part of the public API of
     * {@link V24DetailedMatchEngine}. Production callers MUST go through
     * {@link #simulate(V24MatchContext, java.util.Random)} or one of the other
     * public entry points. (We don't use Guava's {@code @VisibleForTesting}
     * because Guava is not on the project's classpath, and we don't use
     * Spring's {@code org.springframework.lang.VisibleForTesting} because it
     * is not present in Spring Framework 6.1.x.)
     *
     * <p>Contract (unchanged):
     * <ul>
     *   <li>Filters to {@code onPitch()} AND {@code position == "DEF"} — MID,
     *       ATT, FWD and OFF-PITCH players are ignored.</li>
     *   <li>For each skill (MARKER, TACKLER), computes the average across
     *       matching DEF on-pitch players and rounds to nearest int with
     *       {@link Math#round}.</li>
     *   <li>Sparse map semantics: an avg of {@code 0} (no DEF on-pitch with
     *       that skill) results in the entry being OMITTED from the returned
     *       map (not stored as 0). Callers treat absent entries as 0.</li>
     *   <li>Returns an empty {@code Map.of()} when no DEF on-pitch players
     *       are present (no averages to compute).</li>
     * </ul>
     *
     * <p>Rationale (defending-only): MARKER es "marcaje al hombre en defensa"
     * y TACKLER es "entradas y recuperacion" — son skills que define el rol
     * defensivo. Si en el futuro se quiere incluir MIDs defensivos, se puede
     * extender el filtro {@code position == "DEF"}.
     *
     * @param opponents lista de jugadores del equipo oponente (full starting 11)
     * @return sparse map con MARKER y/o TACKLER promediados; empty si no hay
     *         DEF on-pitch o si ninguno tiene esos skills
     */
    // visible-for-testing: package-private by V25D35 verifier nit
    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<V24PlayerMatchState> opponents) {
        List<V24PlayerMatchState> defsOnPitch = opponents.stream()
                .filter(V24PlayerMatchState::onPitch)
                .filter(p -> p.position().equals("DEF"))
                .toList();
        if (defsOnPitch.isEmpty()) return Map.of();

        Map<PlayerSkill, Integer> result = new HashMap<>();
        // MARKER avg
        double markerAvg = defsOnPitch.stream()
                .mapToInt(p -> p.getSkillLevel(PlayerSkill.MARKER))
                .average()
                .orElse(0.0);
        if (markerAvg > 0) {
            result.put(PlayerSkill.MARKER, (int) Math.round(markerAvg));
        }
        // TACKLER avg
        double tacklerAvg = defsOnPitch.stream()
                .mapToInt(p -> p.getSkillLevel(PlayerSkill.TACKLER))
                .average()
                .orElse(0.0);
        if (tacklerAvg > 0) {
            result.put(PlayerSkill.TACKLER, (int) Math.round(tacklerAvg));
        }
        return result;
    }

    /**
     * V25D34-F3: max PASSER skill entre los on-pitch players. Usado para
     * amplificar la possession share base del equipo. Retorna 0 si no hay
     * players on-pitch o si ninguno tiene PASSER.
     *
     * <p>Sparse map semantics: skill absent → 0 (treated as no skill).
     * Si todos tienen PASSER=0, retorna 0 → no boost → bit-a-bit identico
     * a V25D33.
     *
     * @param players lista de players del equipo (full starting 11)
     * @return MAX PASSER skill (0-99) entre on-pitch players
     */
    private int maxPasserSkill(List<V24PlayerMatchState> players) {
        return players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .mapToInt(p -> p.getSkillLevel(PlayerSkill.PASSER))
                .max()
                .orElse(0);
    }

    // ========== V25D67-C27 — match intensity (Opción B) helpers ==========

    /**
     * V25D67-C27 — average overall of a starting XI (in [0, 99]).
     *
     * <p>Used to compute the absolute difference between home and away team
     * overalls, which drives the match intensity multiplier. Falls back to 50
     * (mid-tier) if the list is empty or all players have null overalls (which
     * keeps the diff ratio at 0.0 — i.e. treated as parejos by downstream code).
     *
     * <p>Reads from {@link SessionPlayer#calculateOverall()}, which delegates to
     * the shared {@code OverallCalculator} (introduced in V25D40). For our test
     * fixtures (all 6 stats = ovr, no height, no skills), overall ≈ ovr.
     */
    private record V24TacticalShapeProfile(
            double possessionMultiplier,
            double attackVolumeMultiplier,
            double defensiveResistanceMultiplier,
            double attackLeft,
            double attackCenter,
            double attackRight,
            double defenseLeft,
            double defenseCenter,
            double defenseRight
    ) {}

    public record TacticalShapeDebug(
            double possessionMultiplier,
            double attackVolumeMultiplier,
            double defensiveResistanceMultiplier,
            double attackLeft,
            double attackCenter,
            double attackRight,
            double defenseLeft,
            double defenseCenter,
            double defenseRight
    ) {}

    public TacticalShapeDebug debugTacticalShape(
            SessionTeam team,
            List<SessionPlayer> starting,
            List<SessionPlayer> bench,
            TeamStyle style,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        V24TeamMatchState teamState = V24TeamMatchState.create(
                team,
                starting != null ? starting : List.of(),
                bench != null ? bench : List.of(),
                style,
                slotsByPlayerId != null ? slotsByPlayerId : Map.of());
        if (formation != null && !formation.isBlank()) {
            teamState.setFormation(formation);
        }
        V24TacticalShapeProfile profile = tacticalShapeProfile(teamState, formation, slotsByPlayerId);
        return new TacticalShapeDebug(
                profile.possessionMultiplier(),
                profile.attackVolumeMultiplier(),
                profile.defensiveResistanceMultiplier(),
                profile.attackLeft(),
                profile.attackCenter(),
                profile.attackRight(),
                profile.defenseLeft(),
                profile.defenseCenter(),
                profile.defenseRight());
    }

    private V24TacticalShapeProfile tacticalShapeProfile(
            V24TeamMatchState team,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        return tacticalShapeProfile(team, null, slotsByPlayerId);
    }

    private V24TacticalShapeProfile tacticalShapeProfile(
            V24TeamMatchState team,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (team == null || team.startingPlayers().isEmpty()) {
            return neutralShapeProfile();
        }

        int gk = 0;
        double def = 0.0;
        double mid = 0.0;
        double att = 0.0;
        double widthSum = 0.0;
        int widthCount = 0;
        double defWidthSum = 0.0;
        double midWidthSum = 0.0;
        double attWidthSum = 0.0;
        double defYSum = 0.0;
        double midYSum = 0.0;
        double attYSum = 0.0;
        double leftLane = 0.0;
        double centerLane = 0.0;
        double rightLane = 0.0;
        double attackLeft = 0.0;
        double attackCenter = 0.0;
        double attackRight = 0.0;
        double defenseLeft = 0.0;
        double defenseCenter = 0.0;
        double defenseRight = 0.0;
        double wingbackProjectionIntent = 0.0;
        double wingbackCoverIntent = 0.0;

        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p == null || !p.onPitch() || p.injured() || p.redCard()) continue;
            if ("GK".equals(p.position())) {
                gk++;
                continue;
            }

            double y = tacticalYPercent(p, slotsByPlayerId);
            double x = tacticalXPercent(p, slotsByPlayerId);
            double eff = tacticalEffectiveness(p, slotsByPlayerId);
            double structureEff = clamp(eff, 0.25, 1.0);
            double midfieldEff = midfieldStructureEffectiveness(p, eff);
            double widthFromCenter = Math.min(1.0, Math.abs(x - 50.0) / 50.0);
            double attW = clamp((55.0 - y) / 38.0, 0.0, 1.0);
            double defW = clamp((y - 65.0) / 18.0, 0.0, 1.0);
            double shapeSum = attW + defW;
            if (shapeSum > 1.0) {
                attW /= shapeSum;
                defW /= shapeSum;
                shapeSum = 1.0;
            }
            double midW = 1.0 - shapeSum;

            double weightedAttW = attW * structureEff;
            double weightedMidW = midW * midfieldEff * midfieldProfileMultiplier(p);
            double weightedDefW = defW * structureEff;

            att += weightedAttW;
            mid += weightedMidW;
            def += weightedDefW;
            attWidthSum += widthFromCenter * weightedAttW;
            midWidthSum += widthFromCenter * weightedMidW;
            defWidthSum += widthFromCenter * weightedDefW;
            attYSum += y * weightedAttW;
            midYSum += y * weightedMidW;
            defYSum += y * weightedDefW;

            double leftWeight = laneLeftWeight(x);
            double centerWeight = laneCenterWeight(x);
            double rightWeight = laneRightWeight(x);
            leftLane += leftWeight;
            centerLane += centerWeight;
            rightLane += rightWeight;

            double wingbackVerticalIntent = wingbackVerticalIntent(x, y);
            wingbackProjectionIntent += Math.max(0.0, wingbackVerticalIntent);
            wingbackCoverIntent += Math.max(0.0, -wingbackVerticalIntent);
            double verticalAttackIntent = clamp((100.0 - y) / 100.0, 0.0, 1.0);
            double verticalDefenseIntent = clamp(y / 100.0, 0.0, 1.0);
            double manualLaneIntent = 1.0 + (widthFromCenter - 0.40) * 0.16;
            double attackWeight = verticalAttackIntent
                    * structureEff
                    * clamp(manualLaneIntent, 0.92, 1.10)
                    * clamp(1.0 + Math.max(0.0, wingbackVerticalIntent) * 0.34
                            + Math.min(0.0, wingbackVerticalIntent) * 0.24,
                        0.82, 1.24);
            double defenseQuality = defensiveChannelQuality(p);
            double defenseWeight = verticalDefenseIntent
                    * structureEff
                    * defenseQuality
                    * clamp(1.0 + (widthFromCenter - 0.36) * 0.12, 0.94, 1.10)
                    * clamp(1.0 - Math.max(0.0, wingbackVerticalIntent) * 0.28
                            - Math.min(0.0, wingbackVerticalIntent) * 0.30,
                        0.82, 1.24);
            attackLeft += attackWeight * leftWeight;
            attackCenter += attackWeight * centerWeight;
            attackRight += attackWeight * rightWeight;
            defenseLeft += defenseWeight * leftWeight;
            defenseCenter += defenseWeight * centerWeight;
            defenseRight += defenseWeight * rightWeight;

            widthSum += widthFromCenter;
            widthCount++;
        }

        double width = widthCount > 0 ? widthSum / widthCount : 0.45;
        double defWidth = def > 0 ? defWidthSum / def : width;
        double midWidth = mid > 0 ? midWidthSum / mid : width;
        double attWidth = att > 0 ? attWidthSum / att : width;
        double defAvgY = def > 0 ? defYSum / def : 78.0;
        double midAvgY = mid > 0 ? midYSum / mid : 50.0;
        double attAvgY = att > 0 ? attYSum / att : 15.0;
        double centerShare = widthCount > 0 ? (double) centerLane / widthCount : 0.45;
        double sideBalance = widthCount > 0
                ? 1.0 - (Math.abs(leftLane - rightLane) / (double) widthCount)
                : 1.0;

        double midDelta = (mid - 4.0) * 0.065;
        double midfieldWidthBonus = (midWidth - 0.34) * 0.20;
        double centralOverloadBonus = Math.min(0.065, Math.max(0.0, centerShare - 0.45) * 0.13);
        double noOutletPenalty = Math.max(0.0, 0.22 - attWidth) * 0.22;
        double excessiveWidthPenalty = Math.max(0.0, width - 0.68) * 0.12;
        double midfieldShortagePenalty = Math.max(0.0, 4.0 - mid) * 0.045;
        double possession = 1.0 + midDelta + midfieldWidthBonus + centralOverloadBonus
                - noOutletPenalty - excessiveWidthPenalty - midfieldShortagePenalty;

        // V25D99.85: slightly stronger occupation curve so moving/replacing a
        // real attacking slot is visible in the harness. This still stays
        // bounded by the final clamp and is fed by effectiveness-weighted
        // slot geometry, not by formation name alone.
        double attackDelta = (att - 2.0) * 0.145;
        double usefulAttackWidth = (attWidth - 0.30) * 0.36;
        double supportFromMidfield = (66.6667 - midAvgY) / 66.6667 * 0.10;
        double advancedLineBonus = (22.2222 - attAvgY) / 22.2222 * 0.075;
        double sideImbalancePenalty = Math.max(0.0, 0.72 - sideBalance) * 0.10;
        double noGkPenalty = gk == 1 ? 0.0 : 0.08;
        double attackVolume = 1.0 + attackDelta + usefulAttackWidth + supportFromMidfield
                + advancedLineBonus - sideImbalancePenalty - noGkPenalty;
        attackVolume += wingbackProjectionIntent * 0.035;
        attackVolume -= wingbackCoverIntent * 0.025;

        // V25D99.85: defensive occupation was under-read in player-swap stress
        // tests. A defender removed from the back line, or an attacker forced
        // into it, should affect opponent chance quality/volume more clearly.
        double defDelta = (def - 4.0) * 0.125;
        double defensiveWidthBonus = Math.min(0.095, Math.max(0.0, defWidth - 0.34) * 0.24);
        double lowBlockBonus = Math.max(0.0, defAvgY - 74.0) * 0.0048;
        double midfieldScreenBonus = Math.max(0.0, mid - 3.0) * 0.030;
        double midfieldScreenPenalty = Math.max(0.0, 4.0 - mid) * 0.070;
        double flankGapPenalty = Math.max(0.0, 0.30 - defWidth) * 0.34;
        double centralGapPenalty = Math.max(0.0, defWidth - 0.72) * 0.19;
        double defensiveStrength = defDelta + defensiveWidthBonus + lowBlockBonus + midfieldScreenBonus
                - midfieldScreenPenalty - flankGapPenalty - centralGapPenalty;
        double resistance = 1.0 - defensiveStrength;
        resistance += wingbackProjectionIntent * 0.026;
        resistance -= wingbackCoverIntent * 0.034;

        // V25D99.58: preserve named tactical identity after the numeric parser
        // groups similar labels. These are intentionally small nudges on top of
        // the real slot geometry: the manual editor still wins, but a 4-1-2-3
        // pivot no longer simulates as a byte-identical flat 4-3-3.
        double lowBlockBackFiveShell = clamp(
                Math.max(0.0, defAvgY - 78.0) * 0.035
                        + Math.max(0.0, def - 4.0) * 0.35,
                0.0, 0.55);
        double lowBlockSecondLineDepth = clamp((midAvgY - 56.0) / 20.0, 0.0, 1.0);
        double lowBlockShapeIntent = clamp(
                lowBlockBackFiveShell + (lowBlockSecondLineDepth * 0.45),
                0.0, 1.0);

        if ("4-1-2-3".equals(formation)) {
            possession += 0.070;   // pivot improves circulation/control
            attackVolume -= 0.040; // one safer midfielder, but still a real front three
            resistance -= 0.140;   // lower opponent chance quality via central screen
        } else if ("4-3-3".equals(formation)) {
            // V25D99.329: a real 4-3-3 should not be only a label change in
            // the scenario harness. Give it a visible wide/front-three read,
            // paid for with slightly thinner defensive cover behind wingers.
            possession -= 0.010;
            attackVolume += 0.075;
            resistance += 0.065;
        } else if ("4-2-2-2".equals(formation)) {
            possession -= 0.015;   // narrow box can be pressed toward touchlines
            attackVolume += 0.040; // two ST + two inside AMs create vertical punches
            resistance -= 0.024;   // double pivot keeps the narrow box from collapsing centrally
        } else if ("3-5-2-CDM".equals(formation)) {
            possession += 0.025;   // holder gives cleaner reset option
            attackVolume -= 0.020; // one CM sits instead of joining attacks
            resistance -= 0.060;   // real central shield
        } else if ("3-5-2".equals(formation)) {
            // V25D99.171: three centre-backs plus wingbacks/carrileros should
            // not defend the flanks like a narrow back three. It is still less
            // secure than a true back five, but elite wingers should need to
            // work through a real wide screen instead of producing runaway
            // shot volume by default.
            possession += 0.010 + (wingbackProjectionIntent * 0.010);
            attackVolume += 0.020 + (wingbackProjectionIntent * 0.030);
            resistance -= 0.055;
            resistance += wingbackProjectionIntent * 0.035; // high carrileros create transition space behind them
        } else if ("5-3-2".equals(formation)) {
            possession += 0.015;   // extra security helps recycle possession
            attackVolume -= 0.040; // fewer natural high/wide outlets
            resistance -= 0.180;   // five defenders should reduce opponent quality/volume
        } else if ("5-4-1".equals(formation)) {
            // V25D99.340: the bunker must come from the visible pitch, not only
            // from the label. A real low/compact second line protects more and
            // attacks less; if the manager pushes that line higher, the shape
            // keeps a back-five identity but loses part of the low-block shell.
            double secondLineOutlet = Math.max(0.0, 68.0 - midAvgY) * 0.018;
            possession -= 0.012 + (lowBlockShapeIntent * 0.018);
            attackVolume -= 0.085 + (lowBlockShapeIntent * 0.055);
            attackVolume += secondLineOutlet;
            resistance -= 0.080 + (lowBlockShapeIntent * 0.190);
        }

        possession = clamp(possession, 0.84, 1.18);
        // V25D99.340: allow defensive/manual-low shapes to keep distinct
        // attacking outlet values instead of flattening every conservative
        // variant into the same floor. This makes pixel edits visible while the
        // upper clamp still prevents attacking explosions.
        attackVolume = clamp(attackVolume, 0.70, 1.30);
        resistance = clamp(resistance, 0.68, 1.24);

        double attackLeftChannel = normalizeChannel(attackLeft);
        double attackCenterChannel = normalizeChannel(attackCenter);
        double attackRightChannel = normalizeChannel(attackRight);
        double defenseLeftChannel = normalizeChannel(defenseLeft);
        double defenseCenterChannel = normalizeChannel(defenseCenter);
        double defenseRightChannel = normalizeChannel(defenseRight);

        if ("5-4-1".equals(formation)) {
            // V25D99.61/340: a 5-4-1 low block is not merely "less attack"; it
            // should be read as compact box protection. Keep the bonus tied to
            // real coordinates so pixel edits in the DT modal change the engine
            // contract instead of receiving a free fixed label buff.
            double lowBlockChannelIntent = 0.35 + (lowBlockShapeIntent * 0.65);
            defenseCenterChannel = clamp(defenseCenterChannel + (0.26 * lowBlockChannelIntent), 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + (0.16 * lowBlockChannelIntent), 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + (0.16 * lowBlockChannelIntent), 0.35, 1.65);
            attackCenterChannel = clamp(attackCenterChannel - (0.08 * lowBlockChannelIntent), 0.35, 1.65);
        } else if ("5-3-2".equals(formation)) {
            // V25D99.164: a true back five has two wingbacks in the defensive
            // line. The geometry is already visual and editable, but raw lane
            // normalization under-read the wide cover because the wingbacks sit
            // very wide while the three CBs carry most of the central weight.
            // Keep this smaller than 5-4-1: 5-3-2 has an extra striker and a
            // thinner midfield screen, so it should protect the flanks/box but
            // not become a bunker with two forwards.
            defenseCenterChannel = clamp(defenseCenterChannel + 0.14, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.24, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.24, 0.35, 1.65);
        } else if ("3-5-2".equals(formation)) {
            // V25D99.171: midfield wingbacks/carrileros count as wide defensive
            // cover, but with less box protection than a back five.
            double projectedWingbackAttack = 0.06 + Math.min(0.12, wingbackProjectionIntent * 0.045);
            attackLeftChannel = clamp(attackLeftChannel + projectedWingbackAttack, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + projectedWingbackAttack, 0.35, 1.65);
            defenseCenterChannel = clamp(defenseCenterChannel + 0.06, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.26, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.26, 0.35, 1.65);
        } else if ("3-5-2-CDM".equals(formation)) {
            // V25D99.217: same wingback/carrilero contract as 3-5-2, plus a
            // slightly clearer central screen from the holder. The visual shape
            // has LWB/RWB, so side-mirror smokes must not treat it as a narrow
            // back three with neutral midfield cover.
            attackLeftChannel = clamp(attackLeftChannel + 0.06, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + 0.06, 0.35, 1.65);
            defenseCenterChannel = clamp(defenseCenterChannel + 0.10, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.36, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.36, 0.35, 1.65);
        } else if ("4-3-3".equals(formation)) {
            // Wingers/inside-forwards create real flank threat. The fullbacks
            // are a little more exposed, so defending the wide lanes is not
            // free.
            attackLeftChannel = clamp(attackLeftChannel + 0.24, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + 0.24, 0.35, 1.65);
            attackCenterChannel = clamp(attackCenterChannel - 0.06, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel - 0.08, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel - 0.08, 0.35, 1.65);
        } else if ("4-2-2-2".equals(formation)) {
            // Narrow box has two pivots/inside AMs; it should still protect the
            // middle even if it can be stretched wide.
            defenseCenterChannel = clamp(defenseCenterChannel + 0.10, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel - 0.04, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel - 0.04, 0.35, 1.65);
        }

        return new V24TacticalShapeProfile(
                possession,
                attackVolume,
                resistance,
                attackLeftChannel,
                attackCenterChannel,
                attackRightChannel,
                defenseLeftChannel,
                defenseCenterChannel,
                defenseRightChannel);
    }

    private V24TacticalShapeProfile neutralShapeProfile() {
        return new V24TacticalShapeProfile(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    private double wingbackVerticalIntent(double xPercent, double yPercent) {
        double widthFromCenter = Math.abs(xPercent - 50.0) / 50.0;
        if (widthFromCenter < 0.68 || yPercent < 38.0 || yPercent > 82.0) {
            return 0.0;
        }
        // Negative y movement means "higher up the pitch" in our coordinate
        // system. Carrileros around the midfield band should read like a real
        // DT decision: higher = extra overlap/attack with less cover; deeper =
        // more cover with less attacking projection. Keep the curve bounded so
        // ordinary fullbacks/forwards and central midfielders are unaffected.
        return clamp((55.0 - yPercent) / 17.0, -1.0, 1.0);
    }

    private double defensiveChannelQuality(V24PlayerMatchState player) {
        if (player == null) return 1.0;
        double defensiveBase = ((player.defense() + player.mentality()) / 2.0) / 70.0;
        double positionalMultiplier = switch (player.position()) {
            case "GK" -> 1.05;
            case "DEF" -> 1.00;
            case "MID" -> 0.88;
            default -> 0.72;
        };
        // V25D99.201: side targeting must read player quality, not only slot
        // geometry. A weak fullback/carrilero should make that defensive lane
        // less attractive as cover before the shot coordinate is chosen. The
        // clamp keeps a single weak link visible without letting it erase
        // formation structure, midfield screens or overall team quality.
        return clamp(defensiveBase * positionalMultiplier, 0.55, 1.22);
    }

    private double midfieldStructureEffectiveness(V24PlayerMatchState player, double tacticalEffectiveness) {
        if (player == null) return 1.0;
        double eff = clamp(tacticalEffectiveness, 0.0, 1.0);
        // V25D99.27: midfield structure is a zone responsibility, not a label
        // on the replacement player. The previous V25D99.26 pass squared only
        // players whose tactical position was already MID, so an attacker
        // swapped into a central MID coordinate could still count too much as
        // midfield occupation. Squaring the role/geometry effectiveness for
        // every contribution to the MID band keeps natural MIDs unchanged
        // (eff ~= 1) while making out-of-role attackers/defenders lose central
        // control, press resistance and screen value.
        return clamp(eff * eff, 0.20, 1.0);
    }

    private double midfieldProfileMultiplier(V24PlayerMatchState player) {
        if (player == null) return 1.0;
        // V25D99.29: midfield is not one generic occupation number. A player
        // in the central band needs tempo/control and defensive screen. This
        // keeps a technical attacker useful, but stops him from replacing a
        // pivot/box-to-box midfielder at full structural value.
        double controlProfile =
                player.technique() * 0.38
                        + player.mentality() * 0.24
                        + player.getSkillLevel(PlayerSkill.PASSER) * 0.16
                        + player.getSkillLevel(PlayerSkill.PLAYMAKER) * 0.12
                        + player.stamina() * 0.10;
        double screenProfile =
                player.defense() * 0.44
                        + player.mentality() * 0.20
                        + player.stamina() * 0.14
                        + player.getSkillLevel(PlayerSkill.TACKLER) * 0.14
                        + player.getSkillLevel(PlayerSkill.MARKER) * 0.08;
        double midfieldProfile = (controlProfile * 0.56) + (screenProfile * 0.44);
        double naturalMidfieldFit = midfieldNaturalFit(player);

        // Around 70 is a professional neutral MID contribution. Bound the
        // modifier so elite pivots matter and forwards/CBs in midfield lose
        // structure without making the whole match deterministic.
        return clamp((0.72 + (midfieldProfile / 250.0)) * naturalMidfieldFit, 0.50, 1.12);
    }

    private double midfieldNaturalFit(V24PlayerMatchState player) {
        if (player == null) return 1.0;
        String natural = player.naturalPosition() != null ? player.naturalPosition() : player.position();
        String tactical = player.position();
        // V25D99.30: coordinates can move a player into the central band, but
        // the player still needs the natural habits of a midfielder to fully
        // replace a pivot/box-to-box role. Forwards keep some creative value;
        // defenders keep some screen value; neither should count as a complete
        // midfield structure by label/coordinate alone.
        return switch (natural) {
            case "MID" -> 1.0;
            case "DEF" -> "MID".equals(tactical) ? 0.84 : 0.78;
            case "WINGER" -> 0.76;
            case "ATT" -> 0.70;
            case "GK" -> 0.20;
            default -> 0.86;
        };
    }

    private double normalizeChannel(double raw) {
        // Around 2.0 means roughly two useful outfield contributions in that
        // lane. Clamp keeps extreme manual shapes meaningful without exploding.
        return clamp(raw / 2.0, 0.35, 1.65);
    }

    private double laneLeftWeight(double xPercent) {
        return clamp((50.0 - xPercent) / 30.0, 0.0, 1.0);
    }

    private double laneRightWeight(double xPercent) {
        return clamp((xPercent - 50.0) / 30.0, 0.0, 1.0);
    }

    private double laneCenterWeight(double xPercent) {
        return 1.0 - Math.max(laneLeftWeight(xPercent), laneRightWeight(xPercent));
    }

    private double channelMismatchMultiplier(V24TacticalShapeProfile attack, V24TacticalShapeProfile defense) {
        if (attack == null || defense == null) return 1.0;
        double leftEdge = attack.attackLeft() - defense.defenseLeft();
        double centerEdge = attack.attackCenter() - defense.defenseCenter();
        double rightEdge = attack.attackRight() - defense.defenseRight();
        double bestEdge = Math.max(leftEdge, Math.max(centerEdge, rightEdge));
        double worstEdge = Math.min(leftEdge, Math.min(centerEdge, rightEdge));
        double advantage = Math.max(0.0, bestEdge) * 0.125;
        // V25D99.164: blocked lanes were too soft. A single open lane should
        // still matter, but if the defense covers two/three channels the attack
        // must create fewer situations, not merely lower-xG shots. This makes
        // manual compact/wide defensive shapes visible in the same way the
        // modal shows them.
        double deadEnd = Math.max(0.0, -worstEdge) * 0.100;
        double laneClosure = (Math.max(0.0, -leftEdge)
                + Math.max(0.0, -centerEdge)
                + Math.max(0.0, -rightEdge)) / 3.0 * 0.060;
        return clamp(1.0 + advantage - deadEnd - laneClosure, 0.78, 1.18);
    }

    private double professionalShotTempoMultiplier() {
        return 0.66;
    }

    private double homeFieldChanceVolumeMultiplier(boolean homeHasPossession) {
        return homeHasPossession ? HOME_CHANCE_VOLUME_ADVANTAGE : AWAY_CHANCE_VOLUME_FRICTION;
    }

    private double collectiveQualityChanceVolumeMultiplier(double possessorCollectiveStat, double opponentCollectiveStat) {
        double edge = possessorCollectiveStat - opponentCollectiveStat;
        // V25D99.168: chance volume should react to the whole XI, not only to
        // the best lane. A modestly stronger collective now has a clearer pull
        // across many seeds, without making favourites deterministic.
        return clamp(1.0 + (edge * 0.022), 0.89, 1.11);
    }

    private double defensiveShapeShotQualityMultiplier(V24TacticalShapeProfile defense, V24ShotLocation location) {
        if (defense == null || location == null) return 1.0;

        double centralCover = defense.defenseCenter();
        double wideCover = (defense.defenseLeft() + defense.defenseRight()) / 2.0;
        double laneCover = switch (location) {
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> centralCover;
            case PENALTY_AREA_WIDE -> wideCover;
            case OUTSIDE_BOX -> (centralCover * 0.65) + (wideCover * 0.35);
            case LONG_RANGE -> centralCover;
        };

        double laneEffect = (laneCover - 1.0) * switch (location) {
            case SIX_YARD_BOX -> 0.155;
            case PENALTY_AREA_CENTER -> 0.135;
            case PENALTY_AREA_WIDE -> 0.125;
            case OUTSIDE_BOX -> 0.070;
            case LONG_RANGE -> 0.045;
        };
        double resistanceEffect = (1.0 - defense.defensiveResistanceMultiplier()) * 0.220;
        return clamp(1.0 - laneEffect - resistanceEffect, 0.72, 1.18);
    }

    private double tacticalYPercent(V24PlayerMatchState player, Map<String, LineupSlotDTO> slotsByPlayerId) {
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot != null && slot.customYPercent() != null && Double.isFinite(slot.customYPercent())) {
            return clamp(slot.customYPercent(), 0.0, 100.0);
        }
        Double canonical = canonicalYPercent(slot);
        if (canonical != null) return canonical;
        return switch (player.position()) {
            case "ATT", "WINGER" -> 15.0;
            case "MID" -> 50.0;
            case "GK" -> 92.0;
            default -> 78.0;
        };
    }

    private double tacticalXPercent(V24PlayerMatchState player, Map<String, LineupSlotDTO> slotsByPlayerId) {
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot != null && slot.customXPercent() != null && Double.isFinite(slot.customXPercent())) {
            return clamp(slot.customXPercent(), 0.0, 100.0);
        }
        Double canonical = canonicalXPercent(slot);
        if (canonical != null) return canonical;
        return switch (player.position()) {
            case "WINGER" -> 18.0;
            default -> 50.0;
        };
    }

    private LineupSlotDTO slotFor(V24PlayerMatchState player, Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (player == null || slotsByPlayerId == null || slotsByPlayerId.isEmpty()) return null;
        return slotsByPlayerId.get(player.sessionPlayerId());
    }

    private Double canonicalXPercent(LineupSlotDTO slot) {
        int[] parsed = parseSubdivision(slot);
        if (parsed == null) return null;
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return clamp(left + 11.11 / 2.0, 0.0, 100.0);
    }

    private Double canonicalYPercent(LineupSlotDTO slot) {
        if (slot != null && "GK-1".equals(slot.subdivisionId())) {
            return 93.0;
        }
        int[] parsed = parseSubdivision(slot);
        if (parsed == null) return null;
        int sector = parsed[0];
        int sectorRow = (sector - 1) / 3;
        double top = sectorRow * 11.11;
        return clamp(top + 11.11 / 2.0, 0.0, 100.0);
    }

    private int[] parseSubdivision(LineupSlotDTO slot) {
        if (slot == null || slot.subdivisionId() == null) return null;
        String id = slot.subdivisionId();
        if ("GK-1".equals(id)) return null;
        if (!id.startsWith("S")) return null;
        int dash = id.indexOf('-');
        if (dash < 0 || dash >= id.length() - 1) return null;
        try {
            int sector = Integer.parseInt(id.substring(1, dash));
            int subIndex = Integer.parseInt(id.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) return null;
            return new int[] { sector, subIndex };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static double computeTeamAvgOverall(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) return 50.0;
        int sum = 0;
        int count = 0;
        for (SessionPlayer p : players) {
            if (p != null) {
                Integer overall = p.calculateOverall();
                if (overall != null) {
                    sum += overall;
                    count++;
                }
            }
        }
        return count > 0 ? (double) sum / count : 50.0;
    }

    /**
     * V25D67-C27 — normalized absolute overall difference in [0.0, 1.0].
     *
     * <p>Computed as {@code |homeOvr - awayOvr| / max(homeOvr, awayOvr)}.
     * Returns 0.0 if both teams are non-positive (defensive guard).
     */
    private static double computeOverallDiffRatio(double homeOvr, double awayOvr) {
        double max = Math.max(homeOvr, awayOvr);
        if (max <= 0.0) return 0.0;
        return Math.abs(homeOvr - awayOvr) / max;
    }

    /**
     * V25D67-C27 — match intensity multiplier (Opción B from the C27 task prompt).
     *
     * <p>Maps the absolute overall difference ratio (0..1) to a goal-probability
     * multiplier in [0.40, 1.00]. The goal is to bring parejos matches
     * (Real Madrid vs Barcelona class) down to avg ~1.5 total goals per match
     * while preserving the engine's existing variability for desiguales matches
     * (where the top team's expected goals are realistic, and the bottom team's
     * lucky escapes — 0-0, 1-0 — already happen organically via the
     * random.nextDouble() vs threshold mechanism).
     *
     * <ul>
     *   <li>diffRatio ≤ 5% (5 pp absolute overall gap, e.g. 85 vs 80) → 0.40:
     *       ~60% reduction in per-shot goal probability. Brings parejos
     *       matches from current avg ~4.4 total goals down toward ~1.5.</li>
     *   <li>diffRatio ≥ 30% (e.g. 90 vs 60) → 1.00: full intensity, unchanged
     *       behavior. The top team's expected goals are realistic, and the
     *       weaker team's lucky escapes emerge naturally from the random draws.</li>
     *   <li>5% < diffRatio < 30%: linear interpolation between 0.40 and 1.00.</li>
     * </ul>
     *
     * <p><b>Important constraints from Iván's brief:</b>
     * <ul>
     *   <li>We never raise intensity above 1.0 — no forcing goleadas.</li>
     *   <li>We never add artificial topes — the engine's existing randomness
     *       is what produces the variance Iván wants preserved.</li>
     *   <li>Only REDUCES goals. Existing V24D6U4-RE calibration (λ ≈ 1.25 per
     *       team for 4-4-2 BALANCED OVR=75) becomes "≤ 0.5 per team" for
     *       parejos — a deliberate retreat from the smoke-C22 observation
     *       where Real Madrid vs Barcelona ended 4-0.</li>
     * </ul>
     */
    private static double computeMatchIntensity(double diffRatio) {
        // V25D67-C27 (UNCHANGED in C28): the floor (PAREJOS_INTENSITY=0.40)
        // and threshold (5%) are preserved as-is. The C28 fix only EXTENDS
        // matchIntensity to the chanceProbability layer (see line 420 below)
        // with a midpoint-SQRT multiplier. Why no floor tune: pre-fix
        // measurements at OVR 75×75 show parejos λ per team ≈ 0.385, and
        // V24ModelTuningDiagnosticTest asserts λ per team in [0.3, 1.0]
        // (post-C27 widened band). A floor tune (e.g. 0.40→0.35) would
        // push OVR 75×75 to ~0.27 per team, breaking the existing test.
        // The midpoint-SQRT extension alone brings parejos OVR 85×85 to
        // avg total 1.50 (in C28 target [1.0, 1.5]) without breaking OVR
        // 75×75 (which drops to 0.32 per team, still in [0.3, 1.0]).
        //
        // V25D68-C28 approach (combined but minimal): extends
        // matchIntensity to chanceProbability (the layer that REVISOR's
        // smoke identified as the main driver of high goal counts in
        // intermedios). The midpoint-SQRT curve is the "tune" — it
        // smoothly interpolates between 1.0 (no change for desiguales)
        // and sqrt(0.5) ≈ 0.707 at intensity=0. The curve is empirically
        // calibrated to land intermedios in [1.5, 4.0] band and parejos
        // in C28 target [1.0, 1.5].
        final double PAREJOS_INTENSITY = 0.40;       // V25D67-C27 (unchanged)
        final double DESIGUALES_INTENSITY = 1.00;
        final double DIFF_PAREJOS_THRESHOLD = 0.05;  // V25D67-C27 (unchanged)
        final double DIFF_DESIGUALES_THRESHOLD = 0.30;
        if (diffRatio <= DIFF_PAREJOS_THRESHOLD) return PAREJOS_INTENSITY;
        if (diffRatio >= DIFF_DESIGUALES_THRESHOLD) return DESIGUALES_INTENSITY;
        double t = (diffRatio - DIFF_PAREJOS_THRESHOLD)
                / (DIFF_DESIGUALES_THRESHOLD - DIFF_PAREJOS_THRESHOLD);
        return PAREJOS_INTENSITY + (DESIGUALES_INTENSITY - PAREJOS_INTENSITY) * t;
    }

    private double styleToModifier(TeamStyle style) {
        return switch (style) {
            case ATTACKING -> 1.15;
            case POSSESSION -> 1.05;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.04;
            case CENTRAL_PLAY -> 1.02;
            case BALANCED -> 1.00;
            case COUNTER -> 0.95;
            case DEFENSIVE -> 0.85;
        };
    }

    private double defensiveStyleChanceVolumeMultiplier(TeamStyle defendingStyle) {
        if (defendingStyle == null) return 1.0;
        return switch (defendingStyle) {
            case DEFENSIVE -> 0.82;
            case COUNTER -> 0.91;
            case POSSESSION -> 0.96;
            case BALANCED -> 1.00;
            case CENTRAL_PLAY -> 1.01;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.01;
            case ATTACKING -> 1.08;
        };
    }

    private double defensiveStyleShotQualityMultiplier(TeamStyle defendingStyle, V24ShotLocation location) {
        if (defendingStyle == null || location == null) return 1.0;
        double base = switch (defendingStyle) {
            case DEFENSIVE -> 0.91;
            case COUNTER -> 0.96;
            case POSSESSION -> 0.98;
            case BALANCED -> 1.00;
            case CENTRAL_PLAY -> 1.01;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.01;
            case ATTACKING -> 1.06;
        };
        double laneAdjustment = switch (location) {
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> defendingStyle == TeamStyle.DEFENSIVE ? 0.97 : 1.0;
            case PENALTY_AREA_WIDE -> defendingStyle == TeamStyle.WIDE_PLAY
                    || defendingStyle == TeamStyle.LEFT_FLANK
                    || defendingStyle == TeamStyle.RIGHT_FLANK ? 0.98 : 1.0;
            case OUTSIDE_BOX -> 1.0;
            case LONG_RANGE -> defendingStyle == TeamStyle.DEFENSIVE ? 0.95 : 1.0;
        };
        return clamp(base * laneAdjustment, 0.84, 1.10);
    }

    private double chanceProbability(TeamStyle style, int minute) {
        // Backward-compat overload (no possessor): delegates to the player-quality
        // overload with the V24D6U4-RE anchor (attack=70, speed=70) so the modifier
        // is 1.0 and the historical λ target is preserved for callers that don't
        // have a live startingPlayers list (e.g. diagnostic harnesses).
        return chanceProbability(style, minute, 70, 70, 0, 0);
    }

    private double chanceProbability(TeamStyle style, int minute, int possessorAttack, int possessorSpeed) {
        // V25D33-F2: backward-compat overload delegates to the new 5-args with
        // dribblerSkill=0. Preserves V25D32 baseline for diagnostic harnesses
        // that don't have a live keyAttacker reference.
        return chanceProbability(style, minute, possessorAttack, possessorSpeed, 0, 0);
    }

    private double chanceProbability(TeamStyle style, int minute, int possessorAttack,
                                     int possessorSpeed, int dribblerSkill) {
        // V25D34-F3: backward-compat overload delegates to the new 6-args with
        // speedsterSkill=0. Preserves V25D33 baseline for callers que no
        // necesitan SPEEDSTER.
        return chanceProbability(style, minute, possessorAttack, possessorSpeed,
                dribblerSkill, 0);
    }

    /**
     * V25D34-F3: overload 6-args que agrega {@code speedsterSkill} para aplicar
     * el SPEEDSTER bonus al keySpeed en counter-attacks. El overload 5-args
     * delega a este con speedsterSkill=0 (no-op para callers legacy).
     *
     * <p>SPEEDSTER bonus: cuando {@code style == COUNTER} y
     * {@code speedsterSkill > 0}, se agrega {@code speedsterSkill / 3} al
     * possessorSpeed efectivo. Esto amplifica el qualityMod (que pesa
     * speed * 0.01). Spec values:
     * <ul>
     *   <li>SPEEDSTER=0 o style != COUNTER → no-op (qualityMod unchanged)</li>
     *   <li>SPEEDSTER=92 (Vinicius) en COUNTER → speed += 30 (integer div
     *       92/3=30; truncado, NO 30.67) → qualityMod shift = 30 * 0.01 = 0.30
     *       → chanceProb * 1.30 (+30% en counter-attacks)</li>
     *   <li>SPEEDSTER=50 en COUNTER → speed += 16 (integer div 50/3=16;
     *       truncado, NO 16.67) → qualityMod shift = 0.16 → chanceProb * 1.16
     *       (+16%)</li>
     *   <li>SPEEDSTER=99 en COUNTER → speed += 33 (integer div 99/3=33) →
     *       chanceProb * 1.33 (+33%)</li>
     * </ul>
     *
     * <p>V25D35 verifier nit: la mención previa de "30.67 / 16.67 / +30.7% /
     * +16.7%" era incorrecta — la division es entera (Java {@code int / int}),
     * no double. El codigo aplica truncamiento. Los unit tests en
     * {@code V24DetailedMatchEngineSpeedsterTest} ya usaban la formula
     * correcta {@code (92 / 3) * 0.01}, por lo que el comportamiento real no
     * cambio; solo la documentacion.
     *
     * <p>Gating: SPEEDSTER bonus SOLO aplica en style == COUNTER. En
     * ATTACKING / POSSESSION / DEFENSIVE / BALANCED, speedsterSkill se ignora
     * (no es bonus de contraataque, es bonus de velocidad pura en counter).
     * Modelo simple: "el SPEEDSTER es mas util cuando salis a correr al
     * espacio" — no compensa con otros styles.
     *
     * <p>No-op regression: speedsterSkill=0 → bit-a-bit identico al overload
     * 5-args (que ya delega al 6-args con 0).
     */
    private double chanceProbability(TeamStyle style, int minute, int possessorAttack,
                                     int possessorSpeed, int dribblerSkill, int speedsterSkill) {
        // V24D6U4-RE: Recalibrated to hit Poisson λ=1.25 per team.
        // Previous tuning (V24D6U4) overshot the suppression: empirical λ≈0.45
        // vs target λ≈1.25 (factor 2.77x too low). ITER 1 (base 0.25) gave
        // λ≈0.89; ITER 2 (base 0.35) targets λ≈1.24.
        double base = switch (style) {
            case ATTACKING -> 0.42;
            case POSSESSION -> 0.38;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 0.36;
            case CENTRAL_PLAY -> 0.34;
            case COUNTER -> 0.35;
            case DEFENSIVE -> 0.28;
            case BALANCED -> 0.35;
        };

        // Slight increase in second half (more open)
        double secondHalf = (minute > 45) ? 1.15 : 1.0;
        // Open play tends to increase toward end of match
        double endGame = (minute > 75) ? 1.2 : 1.0;

        // V25D34-F3: SPEEDSTER bonus al keySpeed en COUNTER style. Aplicado
        // ANTES del qualityMod para que el bonus se propague al multiplier.
        // Si style != COUNTER o speedsterSkill <= 0, no hay cambio.
        int effectiveSpeed = possessorSpeed;
        if (style == TeamStyle.COUNTER && speedsterSkill > 0) {
            effectiveSpeed += speedsterSkill / 3;
        }

        // F6 F2 contract: player-quality modifier anchored to the V24D6U4-RE
        // median (attack=70, speed=70) so a default starter gives mod=1.0 and
        // the existing λ target is preserved for unmodified lineups. A
        // higher-attack bench player (attack=80) yields mod=1.30 — enough
        // for a 60-minute post-sub window to reliably produce a measurable
        // goal delta in the F2 contract tests with seed=42, while keeping
        // the per-team λ shift bounded (~1.25 → ~1.43 at the diagnostic
        // 75-OVR harness, still within V24ModelTuningDiagnosticTest's
        // [0.9, 1.6] gate).
        double qualityMod = 1.0
            + (possessorAttack - 70) * 0.02
            + (effectiveSpeed - 70) * 0.01;

        // V25D33-F2: DRIBBLER 1v1 multiplier. Spec values:
        //   DRIBBLER=0  -> multiplier 1.000 (no change — bit-a-bit compat)
        //   DRIBBLER=50 -> multiplier 1.167 (+16.7%)
        //   DRIBBLER=95 -> multiplier 1.317 (+31.7%)
        // Applied AFTER qualityMod so the existing F6 F2 contract (subbing in
        // a higher-attack player shifts λ) is preserved bit-a-bit when
        // DRIBBLER=0. DRIBBLER is multiplicative on chanceProbability — i.e.
        // a key attacker with DRIBBLER=95 produces ~31.7% more shot attempts
        // than one with DRIBBLER=0, ceteris paribus.
        // V25D99.25: softer DRIBBLER volume curve. The previous /300 curve
        // made elite dribblers add ~30% team chance volume, which was too
        // strong in player-swap harness comparisons. Keep the skill visible,
        // but let it complement xG/duel quality instead of dominating shots.
        double dribblerMult = 1.0 + (dribblerSkill / 600.0);

        return base * secondHalf * endGame * qualityMod * dribblerMult;
    }

    private double possessionBase(TeamStyle style) {
        return switch (style) {
            case POSSESSION -> 58.0;
            case ATTACKING -> 52.0;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK, CENTRAL_PLAY -> 50.0;
            case COUNTER -> 48.0;
            case DEFENSIVE -> 45.0;
            case BALANCED -> 50.0;
        };
    }

    // V24C1: Apply base stamina drain to all on-pitch players of a team
    private void applyMinuteDrain(V24TeamMatchState team, TeamStyle style) {
        int baseDrain = fatigueModel.baseDrainPerMinute(style);
        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard()) {
                fatigueModel.applyDrain(p, baseDrain);
            }
        }
    }

    /**
     * LIVE-MATCH-F2-LIVE F2.5: manually re-apply a scheduled sub on a
     * fresh homeState (the engine runs simulate() once per tick, so on
     * subsequent ticks the homeState is rebuilt from the context and
     * the previous tick's swap is "undone" w.r.t. the homeState).
     *
     * <p>This is a deviation from the F2.5 prompt's
     * "Reusar V24SubstitutionEngine.manualSubstitute — NO reimplementar
     * el swap a mano en el engine" rule, justified by the F1 design's
     * per-tick simulate() loop: calling manualSubstitute on every tick
     * would exhaust the 5/team cap (the engine's counter is
     * per-instance and accumulates across ticks) and would throw ISE
     * on the 6th tick.
     *
     * <p>What this does (MUST match what {@code manualSubstitute}
     * semantically does for the F2 contract to hold — the F2 tests
     * assert that the lineup change at minute N measurably alters
     * the result, which requires the bench player to be in the
     * STARTING list, not just have {@code onPitch=true}):
     * <ul>
     *   <li>Find the playerOff in the team's startingPlayers; if found
     *       and on the pitch, call {@code substituteOff()} (sets
     *       {@code onPitch=false}).</li>
     *   <li>Move the playerOff from startingPlayers to benchPlayers.</li>
     *   <li>Find the playerOn in the team's benchPlayers; if found
     *       and NOT on the pitch, call {@code setTeamId(teamId)} and
     *       {@code substituteOn()} (sets {@code onPitch=true}).</li>
     *   <li>Move the playerOn from benchPlayers to startingPlayers.</li>
     * </ul>
     *
     * <p>This is a "best effort" re-application: it does NOT emit a
     * SUBSTITUTION event (the event was emitted on the first
     * application) and does NOT increment the sub counter (already
     * incremented on the first application).
     */
    private void applyScheduledSubManually(V24TeamMatchState team, V24MatchContext.ScheduledSub sub) {
        if (team == null || sub == null) return;
        // Delegate to the package-private mutator on V24TeamMatchState
        // that swaps the two players between starting and bench lists.
        // This is necessary because the public accessors return
        // unmodifiable views, and the F2 contract (carried forward to
        // F2.5) requires the bench player to be in the STARTING list
        // (not just have onPitch=true) so the shooter selection picks
        // it. See V24TeamMatchState#swapStartingBenchForF25 javadoc.
        team.swapStartingBenchForF25(sub.playerOffId(), sub.playerOnId());
    }

    private Map<String, LineupSlotDTO> effectiveSlotsForMinute(
            Map<String, LineupSlotDTO> baseSlotsByPlayerId,
            List<V24MatchContext.ScheduledSub> substitutions,
            String teamId,
            int minute) {
        if (baseSlotsByPlayerId == null || baseSlotsByPlayerId.isEmpty()
                || substitutions == null || substitutions.isEmpty()
                || teamId == null) {
            return baseSlotsByPlayerId;
        }
        Map<String, LineupSlotDTO> effective = null;
        for (V24MatchContext.ScheduledSub sub : substitutions) {
            if (sub == null
                    || !teamId.equals(sub.teamId())
                    || sub.effectiveMinute() > minute) {
                continue;
            }
            LineupSlotDTO offSlot = baseSlotsByPlayerId.get(sub.playerOffId());
            if (offSlot == null) {
                continue;
            }
            if (effective == null) {
                effective = new HashMap<>(baseSlotsByPlayerId);
            }
            // V25D99.41.1: a substitute inherits the exact visual/tactical
            // slot of the player he replaces. Otherwise a bench DEF with no
            // persisted lineup slot falls back to generic center-back
            // coordinates, so replacing a wide defender could accidentally
            // strengthen the centre instead of weakening that real slot.
            effective.put(sub.playerOnId(), offSlot);
        }
        return effective != null ? effective : baseSlotsByPlayerId;
    }

    private V24DetailedMatchResult finalizeResult(
            V24MatchContext ctx,
            V24TeamMatchState home,
            V24TeamMatchState away,
            V24MatchTimeline timeline) {

        int homePossTicks = home.possessionTicks();
        int awayPossTicks = away.possessionTicks();
        int totalPoss = homePossTicks + awayPossTicks;
        int homePoss = totalPoss > 0 ? (int) Math.round(100.0 * homePossTicks / totalPoss) : 50;
        int awayPoss = 100 - homePoss;

        // V24D20-SANDBOX-V2-MVP BUG #4: divergence check between addGoal()
        // counter and the number of GOAL events actually in the timeline.
        // If they drift, the engine is double-counting addGoal OR some
        // non-GOAL event is being counted as a goal. The counter is a
        // JVM-lifetime static so we compare incrementally (per match):
        // the local goals count from the timeline must match
        // home.goals() + away.goals() (which are also updated from
        // addGoal).
        long goalsInTimeline = timeline.events().stream()
            .filter(e -> e.type() == V24MatchEventType.GOAL)
            .count();
        int totalPossessedGoals = home.goals() + away.goals();
        if (goalsInTimeline != totalPossessedGoals) {
            log.warn("[V24-XG-DIVERGENCE] matchId={}, homeGoals={}, awayGoals={}, "
                    + "goalsInTimeline={}, counter={}, divergence={}",
                ctx.matchId(),
                home.goals(), away.goals(),
                goalsInTimeline, goalAdditions.get(),
                totalPossessedGoals - goalsInTimeline);
        }
        double homeXg = home.xg();
        double awayXg = away.xg();
        int totalGoals = totalPossessedGoals;
        // Heuristic outlier check: total goals > 5x total xG is suspicious.
        if (totalGoals > 0 && (homeXg + awayXg) > 0
            && totalGoals > 5 * (homeXg + awayXg)) {
            log.warn("[V24-XG-DIVERGENCE-OUTLIER] matchId={}, goals={} ({}x), xG={}, homeXg={}, awayXg={}",
                ctx.matchId(), totalGoals,
                String.format("%.2f", totalGoals / (homeXg + awayXg)),
                homeXg + awayXg, homeXg, awayXg);
        }

        String summary = String.format("%s %d - %d %s",
                ctx.homeTeam().getName(),
                home.goals(),
                away.goals(),
                ctx.awayTeam().getName());

        return V24DetailedMatchResult.builder()
                .matchId(ctx.matchId())
                .homeTeamId(ctx.homeTeamId())
                .awayTeamId(ctx.awayTeamId())
                .homeGoals(home.goals())
                .awayGoals(away.goals())
                .homeXg(Math.round(home.xg() * 1000.0) / 1000.0)
                .awayXg(Math.round(away.xg() * 1000.0) / 1000.0)
                .homeShots(home.shots())
                .awayShots(away.shots())
                .homePossession(homePoss)
                .awayPossession(awayPoss)
                .timeline(timeline)
                .summary(summary)
                .build();
    }

    /**
     * V24D6Q: Apply a yellow card to a player and, if this is the player's
     * second yellow of the match, also emit a RED_CARD event.
     *
     * <p>Package-private to allow deterministic unit testing without
     * depending on random or stubbed discipline models.
     *
     * <p>The pre-yellow red state is captured BEFORE addYellowCard() because
     * addYellowCard() itself flips redCard=true when yellowCards reaches 2.
     * Using a guard against the post-add state would never fire.
     */
    void applyYellowCardAndMaybeSecondYellowRed(
            V24PlayerMatchState player,
            V24MatchTimeline timeline,
            int minute,
            String teamRole) {
        boolean wasRedBefore = player.redCard();
        player.addYellowCard();
        timeline.addEvent(new V24MatchEvent(
                minute,
                V24MatchEventType.YELLOW_CARD,
                teamRole,
                player.sessionPlayerId(),
                player.name(),
                null, null,
                0.0,
                player.name() + " received a yellow card"
        ));
        if (player.yellowCards() >= 2 && !wasRedBefore) {
            // player.redCard() is already true (set by addYellowCard) — no need to call giveRedCard().
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.RED_CARD,
                    teamRole,
                    player.sessionPlayerId(),
                    player.name(),
                    null, null,
                    0.0,
                    player.name() + " received a red card (second yellow)"
            ));
            // No substitution for red-carded player — team plays with one fewer
        }
    }
}

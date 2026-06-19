package com.footballmanager.application.service.simulation.v24;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Produces a
 * {@link MatchComparison} for a finished V24 match by replaying the
 * engine with the match's initial context + applied substitutions and
 * diffing the result against the live match.
 *
 * <p>Algorithm (single pass, ~50-100ms end-to-end):
 * <ol>
 *   <li>Read {@code live} from {@link V24DetailedMatchStoragePort} (already
 *       persisted at match finish).</li>
 *   <li>Read {@code baseline} from {@link BaselineStateStoragePort} (TTL 7d,
 *       created at match start, deleted at match finish).</li>
 *   <li>Build a fresh {@code V24MatchContext} by applying each persisted
 *       sub in order to {@code baseline.initialContext()} via
 *       {@link V24MatchContext#withManualSubstitution}. The F2.5 design
 *       means the engine applies the swaps in its per-minute loop.</li>
 *   <li>Re-run the engine standalone with a fresh
 *       {@link CachingRandomWrapper} and the same seed as the live match:
 *       <pre>engine.simulate(ctx, new CachingRandomWrapper(baseline.seed()))</pre>
 *       Same seed + same context + same sub sequence ⇒ deterministic
 *       baseline result identical to what the live engine produced (modulo
 *       the substitutions' impact on the engine's draw consumption).</li>
 *   <li>Convert the {@code V24DetailedMatchResult} to a
 *       {@code V24DetailedMatchData} via
 *       {@link V24DetailedMatchData#fromResult} with empty player ratings
 *       (we don't have ratings for the baseline, and they aren't required
 *       for the diff view).</li>
 *   <li>Compute the {@link MatchComparisonDiff} via
 *       {@link MatchComparisonDiff#calculate}.</li>
 *   <li>Return the {@link MatchComparison}.</li>
 * </ol>
 *
 * <p>Why standalone (not via {@code V24LiveSession}):
 * <ul>
 *   <li>The live session is {@code finished} after the match — its
 *       {@code mutateContext} throws {@code IllegalStateException}.</li>
 *   <li>The standalone path is a single full simulation (~50ms), no
 *       per-minute tick loop needed.</li>
 *   <li>The engine's {@code appliedScheduledSubs} tracker is
 *       per-instance, so a fresh engine per call is correct and safe.</li>
 * </ul>
 *
 * <p>No cache: each call re-runs the engine. The baseline read (Redis)
 * + live read (Redis) + engine simulation (~50ms) gives a total of
 * ~100ms per call. Acceptable for on-demand UI clicks; caching would
 * add invalidation complexity for marginal speed gain.
 */
@Service
public class MatchComparisonService {

    private static final Logger log = LoggerFactory.getLogger(MatchComparisonService.class);

    private final V24DetailedMatchStoragePort detailStoragePort;
    private final BaselineStateStoragePort baselineStoragePort;

    public MatchComparisonService(
            V24DetailedMatchStoragePort detailStoragePort,
            BaselineStateStoragePort baselineStoragePort) {
        this.detailStoragePort = detailStoragePort;
        this.baselineStoragePort = baselineStoragePort;
    }

    /**
     * Compute the comparison for a finished match.
     *
     * @param careerId the career id
     * @param matchId  the match id
     * @return the comparison
     * @throws BaselineNotFoundException if no baseline state exists for the match
     *         (either match hasn't started yet, has already been cleaned up,
     *         or has been running for more than 7d)
     * @throws LiveDetailNotFoundException if no V24 detail exists for the match
     *         (match hasn't finished yet, or the V24 path was disabled)
     */
    /**
     * V24D15-CLEANUP (BUG_COMPARE_404 — TRUE ROOT CAUSE): the previous
     * implementation returned {@code MatchComparison} synchronously and
     * internally called {@code .blockOptional()} on the storage ports
     * — which threw {@code IllegalStateException("blockOptional() is
     * blocking, which is not supported in thread parallel-N")} on every
     * call from a Reactor parallel scheduler, silently returning
     * {@code Optional.empty()}. The controller's "Baseline not found"
     * log was ALWAYS wrong — the baseline was in Redis, but the read
     * was being aborted before any byte came back.
     *
     * <p>Fix: return {@code Mono<MatchComparison>} so the call composes
     * correctly with the Reactor scheduler end-to-end.
     *
     * @param careerId the career id
     * @param matchId  the match id
     * @return Mono emitting the comparison, or erroring with
     *         {@link BaselineNotFoundException} /
     *         {@link LiveDetailNotFoundException} when the corresponding
     *         storage is missing.
     */
    public Mono<MatchComparison> getComparison(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId must not be blank"));
        }

        // V24D15-CLEANUP (BUG_COMPARE_404): read detail first on a worker
        // thread, then chain baseline (now Mono). This order matches the
        // test semantics (detail empty -> live error, baseline empty ->
        // baseline error) and avoids the Mono.zip race where the error
        // from one side cancels the other's worker before its mock is
        // actually invoked.
        final String fCareerId = careerId;
        final String fMatchId = matchId;
        Mono<V24DetailedMatchData> liveMono = Mono.fromCallable(() ->
                        detailStoragePort.findByMatchId(fCareerId, fMatchId)
                                .orElseThrow(() -> new LiveDetailNotFoundException(fCareerId, fMatchId)))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());

        Mono<BaselineState> baselineMono = baselineStoragePort.findByMatchId(fCareerId, fMatchId)
                .switchIfEmpty(Mono.error(new BaselineNotFoundException(fCareerId, fMatchId)))
                .map(opt -> opt.orElseThrow(() -> new BaselineNotFoundException(fCareerId, fMatchId)));

        return liveMono.flatMap(live ->
                baselineMono.flatMap(baseline -> {
                    // 3. Build the context with subs applied (F2.5 deferred design)
                    V24MatchContext ctx = baseline.initialContext();
                    for (AppliedSubstitution sub : baseline.subs()) {
                        ctx = ctx.withManualSubstitution(
                                sub.teamId(), sub.playerOffId(), sub.playerOnId(), sub.minute());
                    }

                    // 4. Re-run engine standalone (engine.simulate is sync)
                    final BaselineState baselineFinal = baseline;
                    final V24MatchContext ctxFinal = ctx;
                    final V24DetailedMatchData liveFinal = live;
                    return Mono.fromCallable(() -> {
                        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
                        CachingRandomWrapper random = new CachingRandomWrapper(baselineFinal.seed());
                        V24DetailedMatchResult baselineResult = engine.simulate(ctxFinal, random);

                        log.info("[F6-MATCH-COMPARE] Replayed baseline for matchId={}, seed={}, subs={}, "
                                        + "homeGoals={}-awayGoals={} (vs live {}-{})",
                                matchId, baselineFinal.seed(), baselineFinal.subs().size(),
                                baselineResult.homeGoals(), baselineResult.awayGoals(),
                                liveFinal.homeGoals(), liveFinal.awayGoals());

                        // 5. Convert to V24DetailedMatchData (no player ratings for baseline)
                        V24DetailedMatchData baselineData = V24DetailedMatchData.fromResult(
                                careerId,
                                liveFinal.seasonNumber(),
                                liveFinal.round(),
                                liveFinal.homeTeamName(),
                                liveFinal.awayTeamName(),
                                baselineResult,
                                Collections.<V24PlayerMatchRatingDto>emptyList());

                        // 6. Compute diff
                        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baselineData, live);

                        // 7. Return
                        return new MatchComparison(baselineData, live, diff);
                    }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
                }));
    }

    /**
     * Convenience: peek the baseline state without computing the
     * comparison. Returns empty Mono if the baseline is not available.
     *
     * <p>V24D15-CLEANUP (BUG_COMPARE_404): changed return type from
     * {@code Optional<BaselineState>} to {@code Mono<Optional<BaselineState>>}
     * for the same reason as {@link #getComparison} — the sync read was
     * silently aborting under Reactor parallel scheduling.
     */
    public Mono<Optional<BaselineState>> peekBaseline(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId must not be blank"));
        }
        return baselineStoragePort.findByMatchId(careerId, matchId);
    }

    /**
     * Thrown when the {@code V24DetailedMatchData} for a match is not
     * available. Typically means the match has not finished yet or the
     * V24 path was disabled for the career.
     */
    public static class LiveDetailNotFoundException extends RuntimeException {
        public LiveDetailNotFoundException(String careerId, String matchId) {
            super("Live V24 detail not found for careerId=" + careerId + ", matchId=" + matchId);
        }
    }

    /**
     * Thrown when the {@link BaselineState} for a match is not
     * available. Typically means the baseline was already deleted
     * (post-finish cleanup) or expired (TTL 7d).
     */
    public static class BaselineNotFoundException extends RuntimeException {
        public BaselineNotFoundException(String careerId, String matchId) {
            super("BaselineState not found for careerId=" + careerId + ", matchId=" + matchId
                    + " (already cleaned up, or TTL 7d expired)");
        }
    }
}

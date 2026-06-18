package com.footballmanager.application.service.simulation.v24;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    public MatchComparison getComparison(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }

        // 1. Read live
        V24DetailedMatchData live = detailStoragePort.findByMatchId(careerId, matchId)
                .orElseThrow(() -> new LiveDetailNotFoundException(careerId, matchId));

        // 2. Read baseline
        BaselineState baseline = baselineStoragePort.findByMatchId(careerId, matchId)
                .orElseThrow(() -> new BaselineNotFoundException(careerId, matchId));

        // 3. Build the context with subs applied (F2.5 deferred design)
        V24MatchContext ctx = baseline.initialContext();
        for (AppliedSubstitution sub : baseline.subs()) {
            ctx = ctx.withManualSubstitution(
                    sub.teamId(), sub.playerOffId(), sub.playerOnId(), sub.minute());
        }

        // 4. Re-run engine standalone
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        CachingRandomWrapper random = new CachingRandomWrapper(baseline.seed());
        V24DetailedMatchResult baselineResult = engine.simulate(ctx, random);

        log.info("[F6-MATCH-COMPARE] Replayed baseline for matchId={}, seed={}, subs={}, "
                        + "homeGoals={}-awayGoals={} (vs live {}-{})",
                matchId, baseline.seed(), baseline.subs().size(),
                baselineResult.homeGoals(), baselineResult.awayGoals(),
                live.homeGoals(), live.awayGoals());

        // 5. Convert to V24DetailedMatchData (no player ratings for baseline)
        V24DetailedMatchData baselineData = V24DetailedMatchData.fromResult(
                careerId,
                live.seasonNumber(),
                live.round(),
                live.homeTeamName(),
                live.awayTeamName(),
                baselineResult,
                Collections.<V24PlayerMatchRatingDto>emptyList());

        // 6. Compute diff
        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baselineData, live);

        // 7. Return
        return new MatchComparison(baselineData, live, diff);
    }

    /**
     * Convenience: peek the baseline state without computing the
     * comparison. Returns empty if the baseline is not available.
     */
    public Optional<BaselineState> peekBaseline(String careerId, String matchId) {
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

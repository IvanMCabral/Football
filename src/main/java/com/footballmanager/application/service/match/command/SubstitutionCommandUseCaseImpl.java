package com.footballmanager.application.service.match.command;

import com.footballmanager.application.exception.MinuteInPastException;
import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.AppliedSubstitution;
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchState;
import com.footballmanager.application.service.simulation.v24.V24SubstitutionEngine;
import com.footballmanager.application.service.simulation.v24.V24TeamMatchState;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.port.in.match.SubstitutionCommandUseCase;
import com.footballmanager.domain.port.in.match.SubstitutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LIVE-MATCH-F2-LIVE F2: implementation of {@link SubstitutionCommandUseCase}.
 *
 * <p>F2 wire: manual substitutions now affect the match result via
 * {@link V24LiveSession#mutateContext} + {@link V24LiveSession#replayFromMinute}
 * (the F1 replay infrastructure). The D1=B invariant was removed in F2:
 * swapping {@code playerOffId} out of the starting lineup and
 * {@code playerOnId} in via {@link V24MatchContext#withManualSubstitution}
 * causes the engine's next replay to use the new lineup, so
 * {@code homeGoals}/{@code awayGoals} can change from the baseline.
 *
 * <p>Per-match {@link V24SubstitutionEngine} lifecycle: we keep a
 * {@code Map<UUID matchId, V24SubstitutionEngine>} for the duration of the
 * match so each match has its own counter. The map is cleared when the match
 * finishes (see {@link #onMatchFinished(UUID)}).
 *
 * <p><b>FLAG 1 UX fix:</b> this method NEVER throws
 * {@link IllegalArgumentException}/{@link IllegalStateException} for business
 * validation. Those exceptions (raised by the engine for missing players,
 * max subs reached, already-subbed, etc. — or by
 * {@code V24MatchContext#withManualSubstitution} for invalid teamId / off
 * not in starting / on not in bench / etc.) are caught and translated into
 * a {@link SubstitutionResult#failure(String)} so the controller can forward
 * a uniform 200 OK + {@code success=false} body to the frontend. Only
 * genuinely unexpected runtime errors (NPE, DB, etc.) propagate as
 * {@code Mono.error} for the global handler to surface as 500.
 *
 * <p>Success result fields are populated from authoritative sources:
 * <ul>
 *   <li>{@code substitutionsRemaining} from
 *       {@code engine.substitutionsRemaining(resolvedTeamId)} (post-substitution).</li>
 *   <li>{@code minuteApplied} from {@code liveSession.currentMinute()}, or the
 *       caller-supplied {@code requestedMinute} when non-null.</li>
 * </ul>
 */
@Service
public class SubstitutionCommandUseCaseImpl implements SubstitutionCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubstitutionCommandUseCaseImpl.class);

    private final MatchSessionRegistry matchSessionRegistry;
    private final BaselineStateStoragePort baselineStoragePort;
    private final Map<UUID, V24SubstitutionEngine> enginesByMatchId = new ConcurrentHashMap<>();

    public SubstitutionCommandUseCaseImpl(
            MatchSessionRegistry matchSessionRegistry,
            BaselineStateStoragePort baselineStoragePort) {
        this.matchSessionRegistry = matchSessionRegistry;
        this.baselineStoragePort = baselineStoragePort;
    }

    @Override
    public Mono<SubstitutionResult> executeSubstitution(UUID userId,
                                                        UUID matchId,
                                                        String teamId,
                                                        String playerOffId,
                                                        String playerOnId,
                                                        Integer requestedMinute) {
        return Mono.fromCallable(() -> executeSubstitutionInternal(
                userId, matchId, teamId, playerOffId, playerOnId, requestedMinute))
            .doOnSuccess(result -> {
                if (result.success()) {
                    log.debug("[LIVE-MATCH-F2-F2] Substitution persisted, {} subs remaining, minute={}",
                        result.substitutionsRemaining(), result.minuteApplied());
                } else {
                    log.warn("[LIVE-MATCH-F2-F2] Substitution validation failed for matchId={}: {}",
                        matchId, result.error());
                }
            })
            .doOnError(e -> log.error("[LIVE-MATCH-F2-F2] Unexpected error during substitution for matchId={}",
                matchId, e));
    }

    /**
     * Synchronous core logic. Translates business-rule exceptions into
     * {@link SubstitutionResult#failure(String)} per FLAG 1 fix.
     *
     * <p><b>LIVE-MATCH-F2-F2.5 protocol validation:</b> if the requested
     * {@code requestedMinute} is BEFORE the live session's
     * {@code currentMinute()}, the manager is trying to "change the past"
     * — the engine only applies subs at {@code effectiveMinute ==
     * currentMinute} at the start of the minute loop, so a sub for a
     * past minute would never fire. This is a <b>protocol</b> failure
     * (not a business validation failure), so the
     * {@link IllegalArgumentException} thrown here is <b>not</b> caught
     * by the FLAG 1 catch block below — it propagates up through the
     * {@code Mono.fromCallable} boundary and the
     * {@code GlobalExceptionHandler} translates it to HTTP 422
     * Unprocessable Entity (with code {@code LINEUP_VALIDATION_ERROR})
     * — NOT 200 OK + {@code success=false}.
     */
    private SubstitutionResult executeSubstitutionInternal(UUID userId,
                                                            UUID matchId,
                                                            String teamId,
                                                            String playerOffId,
                                                            String playerOnId,
                                                            Integer requestedMinute) {
        // 1. Resolve the live session for this user/match — these checks
        // are PROTOCOL-level (no live session for the user) and are
        // intentionally NOT caught by the FLAG 1 catch below, so they
        // propagate to GlobalExceptionHandler (HTTP 422).
        MatchSession session = matchSessionRegistry.getSession(userId, matchId)
            .orElseThrow(() -> new IllegalStateException(
                "No active match session for userId=" + userId + " matchId=" + matchId));

        V24LiveSession liveSession = session.getV24LiveSession();
        if (liveSession == null) {
            throw new IllegalStateException(
                "Session has no V24LiveSession (not in V24 path?) for matchId=" + matchId);
        }
        V24MatchContext context = liveSession.context();
        if (context == null) {
            throw new IllegalStateException(
                "V24LiveSession has no context for matchId=" + matchId);
        }

        // 2. LIVE-MATCH-F2-F2.5: protocol validation. Cannot schedule a
        // sub for a minute that is already in the past — the engine only
        // applies subs with effectiveMinute == currentMinute at the start
        // of the minute loop, so a sub with effectiveMinute < currentMinute
        // would never be applied. This is a PROTOCOL failure (manager is
        // trying to change the past), NOT a business validation failure —
        // so we do NOT return 200 + success=false (FLAG 1 UX); instead we
        // throw MinuteInPastException (extends IllegalArgumentException,
        // dedicated handler in GlobalExceptionHandler returns HTTP 400).
        // We perform this check BEFORE the FLAG 1 try/catch so it
        // propagates out of this method without being swallowed.
        int currentMinute = liveSession.currentMinute();
        int minute = requestedMinute != null ? requestedMinute : currentMinute;
        if (minute < currentMinute) {
            log.info("[LIVE-MATCH-F2-F2.5] Rejecting substitution for past minute: matchId={} requestedMinute={} currentMinute={}",
                matchId, minute, currentMinute);
            throw new MinuteInPastException(
                "minute (" + minute + ") must be >= currentMinute ("
                + currentMinute + ") — cannot change the past");
        }

        try {
            // 3. Validate the teamId by looking up the playerOff in the context.
            //    SessionPlayer IDs are Strings (per V24SubstitutionEngine convention).
            String resolvedTeamId = resolveTeamId(context, playerOffId);
            if (teamId != null && !teamId.isBlank() && !teamId.equals(resolvedTeamId)) {
                throw new IllegalStateException(
                    "playerOffId " + playerOffId + " belongs to team " + resolvedTeamId
                    + ", not " + teamId);
            }
            V24TeamMatchState team = buildTeamFromContext(context, resolvedTeamId);

            // 4. Delegate to engine (validates + produces the event).
            V24SubstitutionEngine engine = enginesByMatchId.computeIfAbsent(
                matchId, id -> new V24SubstitutionEngine());
            V24MatchEvent event = engine.manualSubstitute(team, playerOffId, playerOnId, minute);

            // 5. F2 WIRE: drive the substitution through the F1 replay path so
            // homeGoals/awayGoals actually change. The engine call above
            // (engine.manualSubstitute) is still needed because it produces
            // the V24MatchEvent and enforces the per-team substitution limit
            // (5 subs / team), but its mutations to the local V24TeamMatchState
            // are LOST when the method returns.
            //
            // LIVE-MATCH-F5.2 BUG-011: pass the event with REAL player names
            // (playerOff.name() / playerOn.name() populated by
            // V24SubstitutionEngine.manualSubstitute) to
            // V24LiveSession.recordManualSubstitution() so the SSE stream
            // surfaces the actual names ("Vinícius Jr.") instead of the
            // generic "Player 7 RMA" placeholder. The previous code called
            // mutateContext() directly, which dropped the event entirely
            // (the engine replay from minute N+1 replaced engineTimeline
            // and the new engine run did not know about the manual sub).
            // recordManualSubstitution does the mutateContext internally
            // (preserving the F2 replay contract) AND appends the event to
            // manualEvents, which is preserved across replays.
            liveSession.recordManualSubstitution(event);

            // F6 Sprint 2: append this sub to the BaselineState so the
            // compare endpoint can replay the match with the same sub
            // sequence. We do this AFTER liveSession.recordManualSubstitution
            // so the live state is updated first.
            //
            // V24D15-CLEANUP (BUG_COMPARE_404): baselineStoragePort.save
            // now returns Mono<Void>. We subscribe on a bounded-elastic
            // scheduler and surface failures as a warn log so the live
            // sub flow is never blocked by Redis hiccups.
            String careerId = session.getCurrentState() != null
                    ? session.getCurrentState().careerId() : null;
            if (careerId != null && !careerId.isBlank()) {
                // V24D15-CLEANUP (BUG_COMPARE_404): findByMatchId now returns
                // Mono<Optional<...>> (the sync version silently aborted under
                // Reactor parallel scheduling). Use blockOptional on a
                // bounded-elastic scheduler so the block() runs off the
                // caller's thread (V24LiveSession.recordManualSubstitution is
                // called from the same controller flow that hits the
                // /compare endpoint).
                Optional<BaselineState> optBaseline = baselineStoragePort
                        .findByMatchId(careerId, matchId.toString())
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .blockOptional(Duration.ofSeconds(5))
                        .orElse(Optional.empty());
                if (optBaseline.isPresent()) {
                    BaselineState updated = optBaseline.get().withAppendedSub(
                            new AppliedSubstitution(resolvedTeamId, playerOffId, playerOnId, minute));
                    baselineStoragePort.save(careerId, updated)
                            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                            .doOnSuccess(v -> log.info(
                                    "[F6-MATCH-COMPARE] BaselineState updated for matchId={}, sub at minute {} (total subs: {})",
                                    matchId, minute, updated.subs().size()))
                            .onErrorResume(baselineEx -> {
                                // Baseline update failure must NOT fail the
                                // substitution — the live path is the source
                                // of truth, baseline is a best-effort compare
                                // cache.
                                log.warn("[F6-MATCH-COMPARE] Failed to update baseline for matchId={}: {}",
                                        matchId, baselineEx.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            })
                            .subscribe();
                } else {
                    // No baseline exists (legacy path, or V24 path
                    // failed to capture it at match start). Not an
                    // error — the live path still works.
                    log.debug("[F6-MATCH-COMPARE] No BaselineState found for matchId={}, sub not appended",
                            matchId);
                }
            }

            int remaining = engine.substitutionsRemaining(resolvedTeamId);
            log.info("[LIVE-MATCH-F2-F2] Manual substitution applied: matchId={} teamId={} off={} on={} minute={} substitutionsRemaining={}",
                matchId, resolvedTeamId, playerOffId, playerOnId, minute, remaining);

            return SubstitutionResult.ok(minute, remaining);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // FLAG 1 UX fix: validation failures are NOT thrown to the controller;
            // they're returned as a failure result so the frontend gets a uniform
            // snackbar shape regardless of which validator rejected the request.
            return SubstitutionResult.failure(e.getMessage());
        }
    }

    /**
     * LIVE-MATCH-F2-LIVE F2: cleanup hook called when the match finishes. Frees the
     * per-match substitution engine to avoid memory leaks.
     * Wired from the match-finished lifecycle (deferred to Phase 2 integration).
     */
    public void onMatchFinished(UUID matchId) {
        V24SubstitutionEngine removed = enginesByMatchId.remove(matchId);
        if (removed != null) {
            log.debug("[LIVE-MATCH-F2-F2] Cleaned up substitution engine for matchId={}", matchId);
        }
    }

    /**
     * LIVE-MATCH-F2-LIVE F2: resolve the teamId by searching the context's
     * starting lineups and bench for the playerOffId.
     * Returns the teamId or throws IllegalArgumentException if not found.
     */
    private String resolveTeamId(V24MatchContext context, String playerOffId) {
        if (containsPlayer(context.homeStartingPlayers(), playerOffId)) {
            return context.homeTeamId();
        }
        if (containsPlayer(context.awayStartingPlayers(), playerOffId)) {
            return context.awayTeamId();
        }
        if (containsPlayer(context.homeBenchPlayers(), playerOffId)) {
            return context.homeTeamId();
        }
        if (containsPlayer(context.awayBenchPlayers(), playerOffId)) {
            return context.awayTeamId();
        }
        throw new IllegalArgumentException(
            "playerOffId " + playerOffId + " not found in either team's starting lineup or bench");
    }

    private boolean containsPlayer(List<SessionPlayer> players, String sessionPlayerId) {
        if (players == null) {
            return false;
        }
        for (SessionPlayer p : players) {
            if (p != null && playerOffId_equals(p, sessionPlayerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean playerOffId_equals(SessionPlayer p, String sessionPlayerId) {
        // SessionPlayer.getSessionPlayerId() returns String; that's what the engine uses.
        return sessionPlayerId != null && sessionPlayerId.equals(p.getSessionPlayerId());
    }

    /**
     * LIVE-MATCH-F2-LIVE F2: build a {@link V24TeamMatchState} from the context
     * by mapping the SessionPlayer lists to V24PlayerMatchState.
     *
     * <p>We use the {@link V24TeamMatchState#create} factory which internally
     * builds the V24PlayerMatchState objects via {@code V24PlayerMatchState.fromSessionPlayer}.
     * The bench players are auto-marked as substituteOff in the factory.
     */
    private V24TeamMatchState buildTeamFromContext(V24MatchContext context, String teamId) {
        SessionTeam team;
        List<SessionPlayer> starting;
        List<SessionPlayer> bench;
        com.footballmanager.application.service.domain.TeamStyle style;

        if (context.homeTeamId().equals(teamId)) {
            team = context.homeTeam();
            starting = context.homeStartingPlayers();
            bench = context.homeBenchPlayers();
            style = context.homeStyle();
        } else if (context.awayTeamId().equals(teamId)) {
            team = context.awayTeam();
            starting = context.awayStartingPlayers();
            bench = context.awayBenchPlayers();
            style = context.awayStyle();
        } else {
            throw new IllegalArgumentException(
                "teamId " + teamId + " does not match home (" + context.homeTeamId()
                + ") or away (" + context.awayTeamId() + ") of this match");
        }

        return V24TeamMatchState.create(team, new ArrayList<>(starting), new ArrayList<>(bench), style);
    }
}

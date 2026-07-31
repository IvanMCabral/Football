package com.footballmanager.application.service.match.command;

import com.footballmanager.application.exception.MinuteInPastException;
import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.detailed.AppliedSubstitution;
import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.LiveSessionContextView;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchState;
import com.footballmanager.application.service.simulation.detailed.SubstitutionEngine;
import com.footballmanager.application.service.simulation.detailed.TeamMatchState;
import com.footballmanager.domain.port.in.match.SubstitutionCommandUseCase;
import com.footballmanager.domain.port.in.match.SubstitutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * <p>F2 wire: manual substitutions now affect the match result via
 * {@link LiveSession#mutateContext} + {@link LiveSession#replayFromMinute}
 * (the F1 replay infrastructure). The D1=B invariant was removed in F2:
 * swapping {@code playerOffId} out of the starting lineup and
 * {@code playerOnId} in through the live session mutation API
 * causes the engine's next replay to use the new lineup, so
 * {@code homeGoals}/{@code awayGoals} can change from the baseline.
 *
 * <p>Per-match {@link SubstitutionEngine} lifecycle: we keep a
 * {@code Map<UUID matchId, SubstitutionEngine>} for the duration of the
 * match so each match has its own counter. The map is cleared when the match
 * finishes (see {@link #onMatchFinished(UUID)}).
 *
 * <p><b>FLAG 1 UX fix:</b> this method NEVER throws
 * {@link IllegalArgumentException}/{@link IllegalStateException} for business
 * validation. Those exceptions (raised by the engine for missing players,
 * max subs reached, already-subbed, etc. — or by
 * live session validation for invalid teamId / off
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
    private final Map<UUID, SubstitutionEngine> enginesByMatchId = new ConcurrentHashMap<>();

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
            .flatMap(execution -> appendBaselineSubstitution(matchId, execution.baselineAppend())
                .thenReturn(execution.result()))
            .doOnSuccess(result -> {
                if (result.success()) {
                    log.debug("Substitution persisted, {} subs remaining, minute={}",
                        result.substitutionsRemaining(), result.minuteApplied());
                } else {
                    log.warn("Substitution validation failed for matchId={}: {}",
                        matchId, result.error());
                }
            })
            .doOnError(e -> log.error("Unexpected error during substitution for matchId={}",
                matchId, e));
    }

    /**
     * Synchronous core logic. Translates business-rule exceptions into
     * {@link SubstitutionResult#failure(String)} per FLAG 1 fix.
     *
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
    private SubstitutionExecution executeSubstitutionInternal(UUID userId,
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

        LiveSession liveSession = session.getLiveSession();
        if (liveSession == null) {
            throw new IllegalStateException(
                "Session has no LiveSession (not in detailed match path?) for matchId=" + matchId);
        }
        LiveSessionContextView context = liveSession.contextView();
        if (context == null) {
            throw new IllegalStateException(
                "LiveSession has no context for matchId=" + matchId);
        }

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
        int requestedOrCurrentMinute = requestedMinute != null ? requestedMinute : currentMinute;
        int minute = currentMinute <= 0 && requestedOrCurrentMinute <= 0
                ? 1
                : requestedOrCurrentMinute;
        if (minute < currentMinute) {
            log.info("Rejecting substitution for past minute: matchId={} requestedMinute={} currentMinute={}",
                matchId, minute, currentMinute);
            throw new MinuteInPastException(
                "minute (" + minute + ") must be >= currentMinute ("
                + currentMinute + ") — cannot change the past");
        }

        try {
            // 3. Validate the teamId by looking up the playerOff in the context.
            //    SessionPlayer IDs are Strings (per SubstitutionEngine convention).
            String resolvedTeamId = resolveTeamId(context, playerOffId);
            if (teamId != null && !teamId.isBlank() && !teamId.equals(resolvedTeamId)) {
                throw new IllegalStateException(
                    "playerOffId " + playerOffId + " belongs to team " + resolvedTeamId
                    + ", not " + teamId);
            }
            TeamMatchState team = buildTeamFromContext(context, resolvedTeamId);

            // 4. Delegate to engine (validates + produces the event).
            SubstitutionEngine engine = enginesByMatchId.computeIfAbsent(
                matchId, id -> new SubstitutionEngine());
            DetailedMatchEvent event = engine.manualSubstitute(team, playerOffId, playerOnId, minute);

            // 5. F2 WIRE: drive the substitution through the F1 replay path so
            // homeGoals/awayGoals actually change. The engine call above
            // (engine.manualSubstitute) is still needed because it produces
            // the DetailedMatchEvent and enforces the per-team substitution limit
            // (5 subs / team), but its mutations to the local TeamMatchState
            // are LOST when the method returns.
            //
            // (playerOff.name() / playerOn.name() populated by
            // SubstitutionEngine.manualSubstitute) to
            // LiveSession.recordManualSubstitution() so the SSE stream
            // surfaces the actual names ("Vinícius Jr.") instead of the
            // generic "Player 7 RMA" placeholder. The previous code called
            // mutateContext() directly, which dropped the event entirely
            // (the engine replay from minute N+1 replaced engineTimeline
            // and the new engine run did not know about the manual sub).
            // recordManualSubstitution does the mutateContext internally
            // (preserving the F2 replay contract) AND appends the event to
            // manualEvents, which is preserved across replays.
            liveSession.recordManualSubstitution(event);
            session.refreshDetailedSnapshot();

            String careerId = session.getCurrentState() != null
                    ? session.getCurrentState().careerId() : null;
            BaselineAppendCommand baselineAppend = null;
            if (careerId != null && !careerId.isBlank()) {
                baselineAppend = new BaselineAppendCommand(
                        careerId,
                        resolvedTeamId,
                        playerOffId,
                        playerOnId,
                        minute);
            }

            int remaining = engine.substitutionsRemaining(resolvedTeamId);
            log.info("Manual substitution applied: matchId={} teamId={} off={} on={} minute={} substitutionsRemaining={}",
                matchId, resolvedTeamId, playerOffId, playerOnId, minute, remaining);

            return new SubstitutionExecution(SubstitutionResult.ok(minute, remaining), baselineAppend);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // FLAG 1 UX fix: validation failures are NOT thrown to the controller;
            // they're returned as a failure result so the frontend gets a uniform
            // snackbar shape regardless of which validator rejected the request.
            return new SubstitutionExecution(SubstitutionResult.failure(e.getMessage()), null);
        }
    }

    private Mono<Void> appendBaselineSubstitution(UUID matchId, BaselineAppendCommand command) {
        if (command == null) {
            return Mono.empty();
        }
        return baselineStoragePort.findByMatchId(command.careerId(), matchId.toString())
                .flatMap(optionalBaseline -> optionalBaseline
                        .map(baseline -> {
                            BaselineState updated = baseline.withAppendedSub(new AppliedSubstitution(
                                    command.resolvedTeamId(),
                                    command.playerOffId(),
                                    command.playerOnId(),
                                    command.minute()));
                            return baselineStoragePort.save(command.careerId(), updated)
                                    .doOnSuccess(v -> log.info(
                                            "[F6-MATCH-COMPARE] BaselineState updated for matchId={}, sub at minute {} (total subs: {})",
                                            matchId, command.minute(), updated.subs().size()));
                        })
                        .orElseGet(() -> {
                            log.debug("[F6-MATCH-COMPARE] No BaselineState found for matchId={}, sub not appended",
                                    matchId);
                            return Mono.empty();
                        }))
                .onErrorResume(baselineEx -> {
                    log.warn("[F6-MATCH-COMPARE] Failed to update baseline for matchId={}: {}",
                            matchId, baselineEx.getMessage());
                    return Mono.empty();
                });
    }

    private record SubstitutionExecution(
            SubstitutionResult result,
            BaselineAppendCommand baselineAppend
    ) {
    }

    private record BaselineAppendCommand(
            String careerId,
            String resolvedTeamId,
            String playerOffId,
            String playerOnId,
            int minute
    ) {
    }

    /**
     * per-match substitution engine to avoid memory leaks.
     * Wired from the match-finished lifecycle (deferred to Phase 2 integration).
     */
    public void onMatchFinished(UUID matchId) {
        SubstitutionEngine removed = enginesByMatchId.remove(matchId);
        if (removed != null) {
            log.debug("Cleaned up substitution engine for matchId={}", matchId);
        }
    }

    /**
     * starting lineups and bench for the playerOffId.
     * Returns the teamId or throws IllegalArgumentException if not found.
     */
    private String resolveTeamId(LiveSessionContextView context, String playerOffId) {
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

    private boolean containsPlayer(
            List<LiveSessionContextView.PlayerContextView> players,
            String sessionPlayerId) {
        if (players == null) {
            return false;
        }
        for (LiveSessionContextView.PlayerContextView p : players) {
            if (p != null && playerOffId_equals(p, sessionPlayerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean playerOffId_equals(
            LiveSessionContextView.PlayerContextView p,
            String sessionPlayerId) {
        return sessionPlayerId != null && sessionPlayerId.equals(p.sessionPlayerId());
    }

    /**
     * by mapping the SessionPlayer lists to PlayerMatchState.
     *
     * <p>We use the {@link TeamMatchState#create} factory which internally
     * builds the PlayerMatchState objects via {@code PlayerMatchState.fromSessionPlayer}.
     * The bench players are auto-marked as substituteOff in the factory.
     */
    private TeamMatchState buildTeamFromContext(LiveSessionContextView context, String teamId) {
        return TeamMatchState.create(
                context.team(teamId),
                new ArrayList<>(context.startingPlayers(teamId)),
                new ArrayList<>(context.benchPlayers(teamId)));
    }
}

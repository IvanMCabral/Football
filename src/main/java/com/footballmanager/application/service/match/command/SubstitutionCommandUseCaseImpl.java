package com.footballmanager.application.service.match.command;

import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchState;
import com.footballmanager.application.service.simulation.v24.V24SubstitutionEngine;
import com.footballmanager.application.service.simulation.v24.V24TeamMatchState;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.port.in.match.SubstitutionCommandUseCase;
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
 * LIVE-MATCH-F1-POC: implementation of {@link SubstitutionCommandUseCase}.
 *
 * <p>Phase 1 POC (D1=B): manual substitutions are UI-only. The
 * {@link V24SubstitutionEngine} validates and produces the event; this
 * service wires the engine to the live session and persists the event into
 * the session's {@code accumulatedEvents} list so the next SSE snapshot
 * shows the substitution.
 *
 * <p>Critical: homeGoals/awayGoals are NOT recalculated. The result of the
 * match is unchanged. See {@code V24LiveSessionTest#recordManualSubstitution_doesNotAlterResult()}
 * for the regression test that enforces this constraint.
 *
 * <p>Per-match {@link V24SubstitutionEngine} lifecycle: we keep a
 * {@code Map<UUID matchId, V24SubstitutionEngine>} for the duration of the
 * match so each match has its own counter. The map is cleared when the match
 * finishes (see {@link #onMatchFinished(UUID)}).
 */
@Service
public class SubstitutionCommandUseCaseImpl implements SubstitutionCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubstitutionCommandUseCaseImpl.class);

    private final MatchSessionRegistry matchSessionRegistry;
    private final Map<UUID, V24SubstitutionEngine> enginesByMatchId = new ConcurrentHashMap<>();

    public SubstitutionCommandUseCaseImpl(MatchSessionRegistry matchSessionRegistry) {
        this.matchSessionRegistry = matchSessionRegistry;
    }

    @Override
    public Mono<Void> executeSubstitution(UUID userId,
                                         UUID matchId,
                                         String teamId,
                                         String playerOffId,
                                         String playerOnId,
                                         Integer requestedMinute) {
        return Mono.fromCallable(() -> {
            // 1. Resolve the live session for this user/match.
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

            // 2. Validate the teamId by looking up the playerOff in the context.
            //    SessionPlayer IDs are Strings (per V24SubstitutionEngine convention).
            String resolvedTeamId = resolveTeamId(context, playerOffId);
            if (teamId != null && !teamId.isBlank() && !teamId.equals(resolvedTeamId)) {
                throw new IllegalStateException(
                    "playerOffId " + playerOffId + " belongs to team " + resolvedTeamId
                    + ", not " + teamId);
            }
            V24TeamMatchState team = buildTeamFromContext(context, resolvedTeamId);

            // 3. Authoritative minute from the session clock.
            int currentMinute = liveSession.currentMinute();
            int minute = requestedMinute != null ? requestedMinute : currentMinute;

            // 4. Delegate to engine (validates + produces the event).
            V24SubstitutionEngine engine = enginesByMatchId.computeIfAbsent(
                matchId, id -> new V24SubstitutionEngine());
            V24MatchEvent event = engine.manualSubstitute(team, playerOffId, playerOnId, minute);

            // 5. Inject the event into the live session's accumulatedEvents.
            liveSession.recordManualSubstitution(event);

            log.info("[LIVE-MATCH-F1] Manual substitution applied: matchId={} teamId={} off={} on={} minute={} substitutionsRemaining={}",
                matchId, resolvedTeamId, playerOffId, playerOnId, minute,
                engine.substitutionsRemaining(resolvedTeamId));

            return engine.substitutionsRemaining(resolvedTeamId);
        })
            .doOnSuccess(remaining -> log.debug("[LIVE-MATCH-F1] Substitution persisted, {} subs remaining", remaining))
            .doOnError(e -> log.warn("[LIVE-MATCH-F1] Substitution failed for matchId={}: {}", matchId, e.getMessage()))
            .then();
    }

    /**
     * LIVE-MATCH-F1-POC: cleanup hook called when the match finishes. Frees the
     * per-match substitution engine to avoid memory leaks.
     * Wired from the match-finished lifecycle (deferred to Phase 2 integration).
     */
    public void onMatchFinished(UUID matchId) {
        V24SubstitutionEngine removed = enginesByMatchId.remove(matchId);
        if (removed != null) {
            log.debug("[LIVE-MATCH-F1] Cleaned up substitution engine for matchId={}", matchId);
        }
    }

    /**
     * LIVE-MATCH-F1-POC: resolve the teamId by searching the context's
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
     * LIVE-MATCH-F1-POC: build a {@link V24TeamMatchState} from the context
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

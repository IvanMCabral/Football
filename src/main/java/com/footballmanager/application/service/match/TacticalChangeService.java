package com.footballmanager.application.service.match;

import com.footballmanager.adapters.in.web.career.simulation.dto.FormationChangeResultDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.FormationSlotDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.StyleChangeResultDTO;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LIVE-MATCH-F2-LIVE F5 (B3): application service for manager-initiated
 * style/formation changes during a live match.
 *
 * <p>This is the FIRST end-to-end consumer of the F1 replay path
 * ({@link V24LiveSession#mutateContext} + {@link V24LiveSession#replayFromMinute}).
 * Style changes are simple: update the {@code homeStyle} (or {@code awayStyle}
 * for the rival — not exposed in F5) on the effective context. Formation
 * changes are richer: they reassign player positions AND recompute a
 * formation code that the engine uses to pick players.
 *
 * <p><b>Scope of F5 (per the prompt section 2 D-f4, D-shape):</b>
 * <ul>
 *   <li>Only the manager's home team can be changed (away is F4 scope).</li>
 *   <li>The match must be in flight (not finished) — same guard as F1 substitutions.</li>
 *   <li>No rate limit (D-cambios-x-minuto).</li>
 *   <li>The change is persisted as a {@link V24MatchEventType#TACTICAL_CHANGE}
 *       event in the timeline so the F3 UI can render it.</li>
 * </ul>
 *
 * <p><b>How the engine picks up the change:</b>
 * the F1 replay path rebuilds the {@code V24TeamMatchState} on every
 * {@code replayFromMinute} call, reading from
 * {@code context.homeTeam().getFormation()} and
 * {@code context.homeStartingPlayers().get(i).getPosition()}. So a tactical
 * change mutates the {@link SessionTeam} (formation code) and the
 * {@link SessionPlayer} (positions) AND swaps the {@link V24MatchContext}
 * via {@code withNewFormation} / {@code withNewStyle}. The engine's next
 * rebuild picks up all three changes.
 */
@Service
@RequiredArgsConstructor
public class TacticalChangeService {

    private static final Logger log = LoggerFactory.getLogger(TacticalChangeService.class);

    private final MatchSessionRegistry matchSessionRegistry;

    /**
     * LIVE-MATCH-F2-LIVE F5 (B3): change the home team's tactical style mid-match.
     *
     * <p>Flow:
     * <ol>
     *   <li>Resolve the live session for (userId, matchId) — fails with
     *       {@code IllegalStateException} if no session exists (controller
     *       maps to 409).</li>
     *   <li>Validate the live session is in flight (not finished) — fails
     *       with {@code IllegalStateException} if the match has ended.</li>
     *   <li>Apply the style via {@code V24LiveSession.mutateContext(ctx -> ctx.withNewStyle(homeTeamId, newStyle))}
     *       — this triggers {@code replayFromMinute(currentMinute)} automatically.</li>
     *   <li>Record a {@code TACTICAL_CHANGE} event so the F3 UI can render the change.</li>
     *   <li>Return the {@link StyleChangeResultDTO} with the new state.</li>
     * </ol>
     *
     * @param userId   authenticated user (for session lookup)
     * @param matchId  match UUID
     * @param newStyle new tactical style (NOT NULL — caller validates)
     * @return Mono emitting the result DTO; Mono.error on validation failure
     */
    public Mono<StyleChangeResultDTO> changeStyle(UUID userId, UUID matchId, TeamStyle newStyle) {
        return Mono.fromCallable(() -> changeStyleInternal(userId, matchId, newStyle))
            .doOnError(e -> log.warn("[LIVE-MATCH-F2-F5] Style change failed for matchId={} userId={}: {}",
                matchId, userId, e.getMessage()));
    }

    private StyleChangeResultDTO changeStyleInternal(UUID userId, UUID matchId, TeamStyle newStyle) {
        if (newStyle == null) {
            throw new IllegalArgumentException("newStyle must not be null");
        }

        // 1. Resolve live session.
        MatchSession session = matchSessionRegistry.getSession(userId, matchId)
            .orElseThrow(() -> new IllegalStateException(
                "No active match session for userId=" + userId + " matchId=" + matchId));

        V24LiveSession liveSession = session.getV24LiveSession();
        if (liveSession == null) {
            throw new IllegalStateException(
                "Session has no V24LiveSession (not in V24 path?) for matchId=" + matchId);
        }
        if (liveSession.isFinished()) {
            throw new IllegalStateException(
                "Match " + matchId + " has already finished — cannot change style");
        }

        V24MatchContext context = liveSession.context();
        String homeTeamId = context.homeTeamId();

        // 2. Drive mutateContext — F1 replays from currentMinute automatically.
        liveSession.mutateContext(ctx -> ctx.withNewStyle(homeTeamId, newStyle));

        // 3. Record the tactical-change event in the timeline (visible to F3 UI).
        int minute = liveSession.currentMinute();
        V24MatchEvent event = new V24MatchEvent(
            minute,
            V24MatchEventType.TACTICAL_CHANGE,
            homeTeamId,
            null, // no player
            null, // no player name
            null, null, // no related player
            0.0,
            "Style changed to " + newStyle.name()
        );
        liveSession.recordTacticalChange(event);

        log.info("[LIVE-MATCH-F2-F5] Style changed: matchId={} teamId={} newStyle={} minute={}",
            matchId, homeTeamId, newStyle, minute);

        return StyleChangeResultDTO.ok(minute, newStyle);
    }

    /**
     * LIVE-MATCH-F2-LIVE F5 (B3): change the home team's formation mid-match.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate the slots (10-11 entries, exactly 1 GK, unique playerIds) — fails with
     *       {@code IllegalArgumentException} (controller maps to 400).</li>
     *   <li>Validate every {@code playerId} is in the home starting/bench
     *       roster (no free agents) — fails with
     *       {@code IllegalArgumentException} (controller maps to 400).</li>
     *   <li>Resolve the live session — fails with {@code IllegalStateException}
     *       on missing session (controller maps to 409).</li>
     *   <li>Validate the live session is in flight — same as style change.</li>
     *   <li>Mutate the {@link SessionTeam} formation code and each affected
     *       {@link SessionPlayer} position. The engine reads these on the
     *       next replay.</li>
     *   <li>Drive {@code mutateContext(ctx -> ctx.withNewFormation(homeTeamId, code))}
     *       — F1 replays from currentMinute automatically.</li>
     *   <li>Record a {@code TACTICAL_CHANGE} event.</li>
     *   <li>Return the {@link FormationChangeResultDTO} with the new slots.</li>
     * </ol>
     *
     * @param userId       authenticated user
     * @param matchId      match UUID
     * @param newFormation new formation (List of 10-11 slots; controller validates non-null)
     * @return Mono emitting the result DTO; Mono.error on validation failure
     */
    public Mono<FormationChangeResultDTO> changeFormation(UUID userId, UUID matchId, List<FormationSlotDTO> newFormation) {
        return Mono.fromCallable(() -> changeFormationInternal(userId, matchId, newFormation))
            .doOnError(e -> log.warn("[LIVE-MATCH-F2-F5] Formation change failed for matchId={} userId={}: {}",
                matchId, userId, e.getMessage()));
    }

    private FormationChangeResultDTO changeFormationInternal(UUID userId, UUID matchId, List<FormationSlotDTO> newFormation) {
        // 1. Slot-level validation.
        validateFormation(newFormation);

        // 2. Resolve live session.
        MatchSession session = matchSessionRegistry.getSession(userId, matchId)
            .orElseThrow(() -> new IllegalStateException(
                "No active match session for userId=" + userId + " matchId=" + matchId));

        V24LiveSession liveSession = session.getV24LiveSession();
        if (liveSession == null) {
            throw new IllegalStateException(
                "Session has no V24LiveSession (not in V24 path?) for matchId=" + matchId);
        }
        if (liveSession.isFinished()) {
            throw new IllegalStateException(
                "Match " + matchId + " has already finished — cannot change formation");
        }

        V24MatchContext context = liveSession.context();
        String homeTeamId = context.homeTeamId();

        // 3. Roster validation: every playerId must be in homeStartingPlayers or homeBenchPlayers.
        Set<String> rosterIds = new HashSet<>();
        for (SessionPlayer p : context.homeStartingPlayers()) rosterIds.add(p.getSessionPlayerId());
        for (SessionPlayer p : context.homeBenchPlayers()) rosterIds.add(p.getSessionPlayerId());
        for (FormationSlotDTO slot : newFormation) {
            if (!rosterIds.contains(slot.playerId())) {
                throw new IllegalArgumentException(
                    "playerId '" + slot.playerId() + "' is not in the home team's roster");
            }
        }

        // 4. Derive a formation code from the slot positions (X-Y-Z format).
        String newCode = deriveFormationCode(newFormation);

        // 5. Mutate the SessionTeam.formation — the engine reads this on the next replay.
        SessionTeam homeTeam = context.homeTeam();
        String previousCode = homeTeam.getFormation();
        homeTeam.setFormation(newCode);

        // 6. Mutate each affected SessionPlayer.position — engine reads this on the next rebuild.
        for (FormationSlotDTO slot : newFormation) {
            // Find the player in either starting or bench and mutate.
            SessionPlayer p = findPlayer(context, slot.playerId());
            if (p != null && !slot.position().equals(p.getPosition())) {
                p.setPosition(slot.position());
            }
        }

        // 7. Drive mutateContext — F1 replays from currentMinute automatically.
        liveSession.mutateContext(ctx -> ctx.withNewFormation(homeTeamId, newCode));

        // 8. Record the tactical-change event.
        int minute = liveSession.currentMinute();
        V24MatchEvent event = new V24MatchEvent(
            minute,
            V24MatchEventType.TACTICAL_CHANGE,
            homeTeamId,
            null,
            null,
            null, null,
            0.0,
            "Formation changed from " + previousCode + " to " + newCode
        );
        liveSession.recordTacticalChange(event);

        log.info("[LIVE-MATCH-F2-F5] Formation changed: matchId={} teamId={} from={} to={} minute={}",
            matchId, homeTeamId, previousCode, newCode, minute);

        return FormationChangeResultDTO.ok(minute, new ArrayList<>(newFormation));
    }

    // ========== Validation helpers ==========

    /**
     * Validate the incoming formation: 10-11 slots, exactly 1 GK, unique playerIds,
     * non-blank playerId/position on every slot.
     */
    private void validateFormation(List<FormationSlotDTO> formation) {
        if (formation == null) {
            throw new IllegalArgumentException("formation must not be null");
        }
        if (formation.size() < 10 || formation.size() > 11) {
            throw new IllegalArgumentException(
                "formation must contain between 10 and 11 slots, got " + formation.size());
        }
        int gkCount = 0;
        Set<String> seenPlayerIds = new HashSet<>();
        Set<String> seenPositions = new HashSet<>();
        for (FormationSlotDTO slot : formation) {
            if (slot == null || slot.playerId() == null || slot.playerId().isBlank()) {
                throw new IllegalArgumentException("slot.playerId must not be blank");
            }
            if (slot.position() == null || slot.position().isBlank()) {
                throw new IllegalArgumentException(
                    "slot.position must not be blank for playerId=" + slot.playerId());
            }
            if (!seenPlayerIds.add(slot.playerId())) {
                throw new IllegalArgumentException(
                    "duplicate playerId in formation: " + slot.playerId());
            }
            // Positions are NOT unique (a formation has 4 DEFs, etc.) but we DO
            // require each slot's position label to be one of the valid role
            // strings accepted by the engine (GK, DEF, MID, WINGER, ATT).
            String pos = slot.position();
            if (!isValidPosition(pos)) {
                throw new IllegalArgumentException(
                    "invalid position '" + pos + "' — must be one of GK, DEF, MID, WINGER, ATT");
            }
            seenPositions.add(pos);
            if ("GK".equals(pos)) gkCount++;
        }
        if (gkCount != 1) {
            throw new IllegalArgumentException(
                "formation must contain exactly 1 GK, got " + gkCount);
        }
    }

    private boolean isValidPosition(String position) {
        return "GK".equals(position) || "DEF".equals(position) || "MID".equals(position)
            || "WINGER".equals(position) || "ATT".equals(position);
    }

    /**
     * Derive a formation code (X-Y-Z) from the slot positions. Counts DEF, MID, ATT
     * (WINGER counts as MID) and formats accordingly. This is a best-effort mapping
     * so the engine's formation string stays parseable by {@code V24FormationParser}.
     */
    private String deriveFormationCode(List<FormationSlotDTO> formation) {
        int def = 0, mid = 0, fwd = 0;
        for (FormationSlotDTO slot : formation) {
            switch (slot.position()) {
                case "GK" -> { /* ignored — GK is implicit */ }
                case "DEF" -> def++;
                case "MID", "WINGER" -> mid++;
                case "ATT" -> fwd++;
                default -> {
                    /* unreachable: validateFormation already rejected */
                }
            }
        }
        return def + "-" + mid + "-" + fwd;
    }

    private SessionPlayer findPlayer(V24MatchContext context, String playerId) {
        for (SessionPlayer p : context.homeStartingPlayers()) {
            if (playerId.equals(p.getSessionPlayerId())) return p;
        }
        for (SessionPlayer p : context.homeBenchPlayers()) {
            if (playerId.equals(p.getSessionPlayerId())) return p;
        }
        return null;
    }
}

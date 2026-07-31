package com.footballmanager.application.service.match;




import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.LiveSessionContextView;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * style/formation changes during a live match.
 *
 * <p>This is the FIRST end-to-end consumer of the F1 replay path
 * ({@link LiveSession#mutateContext} + {@link LiveSession#replayFromMinute}).
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
 *   <li>The change is persisted as a {@link DetailedMatchEventType#TACTICAL_CHANGE}
 *       event in the timeline so the F3 UI can render it.</li>
 * </ul>
 *
 * <p><b>How the engine picks up the change:</b>
 * the F1 replay path rebuilds the {@code TeamMatchState} on every
 * {@code replayFromMinute} call, reading from
 * {@code context.homeTeam().getFormation()} and
 * {@code context.homeStartingPlayers().get(i).getPosition()}. So a tactical
 * change mutates the {@link SessionTeam} (formation code) and the
 * {@link SessionPlayer} (positions) AND swaps the {@link MatchContext}
 * via {@code withNewFormation} / {@code withNewStyle}. The engine's next
 * rebuild picks up all three changes.
 */
@Service
@RequiredArgsConstructor
public class TacticalChangeService {

    private static final Logger log = LoggerFactory.getLogger(TacticalChangeService.class);

    private final MatchSessionRegistry matchSessionRegistry;

    /**
     *
     * <p>Flow:
     * <ol>
     *   <li>Resolve the live session for (userId, matchId) — fails with
     *       {@code IllegalStateException} if no session exists (controller
     *       maps to 409).</li>
     *   <li>Validate the live session is in flight (not finished) — fails
     *       with {@code IllegalStateException} if the match has ended.</li>
     *   <li>Apply the style via {@code LiveSession.mutateContext(ctx -> ctx.withNewStyle(homeTeamId, newStyle))}
     *       — this triggers {@code replayFromMinute(currentMinute)} automatically.</li>
     *   <li>Record a {@code TACTICAL_CHANGE} event so the F3 UI can render the change.</li>
     *   <li>Return the {@link TacticalStyleChangeResult} with the new state.</li>
     * </ol>
     *
     * @param userId   authenticated user (for session lookup)
     * @param matchId  match UUID
     * @param newStyle new tactical style (NOT NULL — caller validates)
     * @return Mono emitting the result DTO; Mono.error on validation failure
     */
    public Mono<TacticalStyleChangeResult> changeStyle(UUID userId, UUID matchId, TeamStyle newStyle) {
        return Mono.fromCallable(() -> changeStyleInternal(userId, matchId, newStyle))
            .doOnError(e -> log.warn("Style change failed for matchId={} userId={}: {}",
                matchId, userId, e.getMessage()));
    }

    private TacticalStyleChangeResult changeStyleInternal(UUID userId, UUID matchId, TeamStyle newStyle) {
        if (newStyle == null) {
            throw new IllegalArgumentException("newStyle must not be null");
        }

        // 1. Resolve live session.
        MatchSession session = matchSessionRegistry.getSession(userId, matchId)
            .orElseThrow(() -> new IllegalStateException(
                "No active match session for userId=" + userId + " matchId=" + matchId));

        LiveSession liveSession = session.getLiveSession();
        if (liveSession == null) {
            throw new IllegalStateException(
                "Session has no LiveSession (not in detailed match path?) for matchId=" + matchId);
        }
        if (liveSession.isFinished()) {
            throw new IllegalStateException(
                "Match " + matchId + " has already finished — cannot change style");
        }

        LiveSessionContextView context = liveSession.contextView();
        String homeTeamId = context.homeTeamId();

        // 2. Drive mutateContext — F1 replays from currentMinute automatically.
        liveSession.changeTeamStyle(homeTeamId, newStyle);

        // 3. Record the tactical-change event in the timeline (visible to F3 UI).
        int minute = Math.max(1, liveSession.currentMinute());
        DetailedMatchEvent event = new DetailedMatchEvent(
            minute,
            DetailedMatchEventType.TACTICAL_CHANGE,
            homeTeamId,
            null, // no player
            null, // no player name
            null, null, // no related player
            0.0,
            "Style changed to " + newStyle.name()
        );
        liveSession.recordTacticalChange(event);

        log.info("Style changed: matchId={} teamId={} newStyle={} minute={}",
            matchId, homeTeamId, newStyle, minute);

        return TacticalStyleChangeResult.ok(minute, newStyle);
    }

    /**
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
     *   <li>Return the {@link TacticalFormationChangeResult} with the new slots.</li>
     * </ol>
     *
     * @param userId       authenticated user
     * @param matchId      match UUID
     * @param newFormation new formation (List of 10-11 slots; controller validates non-null)
     * @return Mono emitting the result DTO; Mono.error on validation failure
     */
    public Mono<TacticalFormationChangeResult> changeFormation(UUID userId, UUID matchId, List<TacticalFormationSlot> newFormation) {
        return changeFormation(userId, matchId, newFormation, null);
    }

    public Mono<TacticalFormationChangeResult> changeFormation(
            UUID userId,
            UUID matchId,
            List<TacticalFormationSlot> newFormation,
            String requestedFormationCode) {
        return Mono.fromCallable(() -> changeFormationInternal(userId, matchId, newFormation, requestedFormationCode))
            .doOnError(e -> log.warn("Formation change failed for matchId={} userId={}: {}",
                matchId, userId, e.getMessage()));
    }

    private TacticalFormationChangeResult changeFormationInternal(
            UUID userId,
            UUID matchId,
            List<TacticalFormationSlot> newFormation,
            String requestedFormationCode) {
        // 1. Slot-level validation.
        validateFormation(newFormation);

        // 2. Resolve live session.
        MatchSession session = matchSessionRegistry.getSession(userId, matchId)
            .orElseThrow(() -> new IllegalStateException(
                "No active match session for userId=" + userId + " matchId=" + matchId));

        LiveSession liveSession = session.getLiveSession();
        if (liveSession == null) {
            throw new IllegalStateException(
                "Session has no LiveSession (not in detailed match path?) for matchId=" + matchId);
        }
        if (liveSession.isFinished()) {
            throw new IllegalStateException(
                "Match " + matchId + " has already finished — cannot change formation");
        }

        LiveSessionContextView context = liveSession.contextView();
        String managerTeamId = resolveFormationTeamId(context, newFormation);

        // 3. Roster validation: every playerId must be in the manager team's live roster.
        Set<String> rosterIds = rosterIdsForTeam(context, managerTeamId);
        for (TacticalFormationSlot slot : newFormation) {
            if (!rosterIds.contains(slot.playerId())) {
                throw new IllegalArgumentException(
                    "playerId '" + slot.playerId() + "' is not in the manager team's roster");
            }
        }

        // 4. Use the manager-selected formation code when present. Counting
        // slot roles is only a fallback: modern shapes like 4-3-3 carry
        // WINGER roles, and deriving from DEF/MID/ATT can mislabel them.
        String sanitizedCode = sanitizeFormationCode(requestedFormationCode);
        final String newCode = sanitizedCode != null
            ? sanitizedCode
            : deriveFormationCode(newFormation);

        // 5. Mutate the SessionTeam.formation — the engine reads this on the next replay.
        String previousCode = context.team(managerTeamId).formation();

        // 6. Mutate each affected SessionPlayer.position — engine reads this on the next rebuild.
        // 7. Drive mutateContext — F1 replays from currentMinute automatically.
        Map<String, LineupSlot> liveSlots = buildLiveSlots(newFormation);
        liveSession.changeFormation(managerTeamId, newCode, buildTacticalPositions(newFormation), liveSlots);

        // 8. Record the tactical-change event.
        int minute = Math.max(1, liveSession.currentMinute());
        DetailedMatchEvent event = new DetailedMatchEvent(
            minute,
            DetailedMatchEventType.TACTICAL_CHANGE,
            managerTeamId,
            null,
            null,
            null, null,
            0.0,
            buildFormationChangeDescription(previousCode, newCode, newFormation, context, managerTeamId)
        );
        liveSession.recordTacticalChange(event);
        session.refreshDetailedSnapshot();

        log.info("Formation changed: matchId={} teamId={} from={} to={} minute={}",
            matchId, managerTeamId, previousCode, newCode, minute);

        return TacticalFormationChangeResult.ok(minute, new ArrayList<>(newFormation));
    }

    /**
     *
     * <p>The pre-match lineup editor already sends customX/customY through
     * LineupSlot. The live Partido modal uses the same tactical language:
     * when a slot arrives with custom coordinates, the detailed replay gets a
     * slotsByPlayerId map so width/center/vertical movement affects the
     * engine instead of being cosmetic UI-only movement.</p>
     */
    private Map<String, LineupSlot> buildLiveSlots(List<TacticalFormationSlot> formation) {
        Map<String, LineupSlot> slots = new LinkedHashMap<>();
        if (formation == null) {
            return slots;
        }
        for (int i = 0; i < formation.size(); i++) {
            TacticalFormationSlot slot = formation.get(i);
            if (slot == null || slot.playerId() == null || slot.playerId().isBlank()) {
                continue;
            }
            Double x = finiteOrNull(slot.customXPercent());
            Double y = finiteOrNull(slot.customYPercent());
            if (x == null && y == null) {
                continue;
            }
            String subdivisionId = "GK".equalsIgnoreCase(slot.position())
                ? "GK-1"
                : "LIVE-" + (slot.slotIndex() != null ? slot.slotIndex() : i);
            slots.put(slot.playerId(), new LineupSlot(slot.playerId(), subdivisionId, x, y));
        }
        return slots;
    }

    private Double finiteOrNull(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }

    private String buildFormationChangeDescription(
            String previousCode,
            String newCode,
            List<TacticalFormationSlot> formation,
            LiveSessionContextView context,
            String managerTeamId) {
        StringBuilder description = new StringBuilder("Formation changed from ")
            .append(previousCode)
            .append(" to ")
            .append(newCode);

        List<String> moved = new ArrayList<>();
        if (formation != null) {
            for (TacticalFormationSlot slot : formation) {
                if (slot == null || slot.playerId() == null) {
                    continue;
                }
                Double x = finiteOrNull(slot.customXPercent());
                Double y = finiteOrNull(slot.customYPercent());
                if (x == null && y == null) {
                    continue;
                }
                LiveSessionContextView.PlayerContextView player = findPlayer(context, managerTeamId, slot.playerId());
                String name = player != null ? player.name() : slot.playerId();
                moved.add(String.format(Locale.US, "%s %.1f/%.1f",
                    name,
                    x != null ? x : -1.0,
                    y != null ? y : -1.0));
            }
        }
        if (!moved.isEmpty()) {
            description.append(" | pixels: ");
            description.append(String.join(", ", moved.stream().limit(4).toList()));
            if (moved.size() > 4) {
                description.append(" +").append(moved.size() - 4).append(" more");
            }
        }
        return description.toString();
    }

    // ========== Validation helpers ==========

    /**
     * Validate the incoming formation: 10-11 slots, exactly 1 GK, unique playerIds,
     * non-blank playerId/position on every slot.
     */
    private void validateFormation(List<TacticalFormationSlot> formation) {
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
        for (TacticalFormationSlot slot : formation) {
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
     * so the engine's formation string stays parseable by {@code FormationParser}.
     */
    private String deriveFormationCode(List<TacticalFormationSlot> formation) {
        int def = 0, mid = 0, fwd = 0;
        for (TacticalFormationSlot slot : formation) {
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

    private String sanitizeFormationCode(String requestedFormationCode) {
        if (requestedFormationCode == null || requestedFormationCode.isBlank()) {
            return null;
        }
        String code = requestedFormationCode.trim();
        if (!code.matches("\\d(?:-(?:\\d|[A-Z]{2,4})){1,4}")) {
            throw new IllegalArgumentException("formationCode has invalid format: " + requestedFormationCode);
        }
        return code;
    }

    private Map<String, String> buildTacticalPositions(List<TacticalFormationSlot> formation) {
        Map<String, String> positions = new LinkedHashMap<>();
        if (formation == null) {
            return positions;
        }
        for (TacticalFormationSlot slot : formation) {
            if (slot != null && slot.playerId() != null && slot.position() != null) {
                positions.put(slot.playerId(), slot.position());
            }
        }
        return positions;
    }

    private String resolveFormationTeamId(LiveSessionContextView context, List<TacticalFormationSlot> formation) {
        Set<String> requestedIds = new HashSet<>();
        for (TacticalFormationSlot slot : formation) {
            requestedIds.add(slot.playerId());
        }
        Set<String> homeRoster = rosterIdsForTeam(context, context.homeTeamId());
        if (homeRoster.containsAll(requestedIds)) {
            return context.homeTeamId();
        }
        Set<String> awayRoster = rosterIdsForTeam(context, context.awayTeamId());
        if (awayRoster.containsAll(requestedIds)) {
            return context.awayTeamId();
        }
        throw new IllegalArgumentException("formation players do not belong to a single live team roster");
    }

    private Set<String> rosterIdsForTeam(LiveSessionContextView context, String teamId) {
        Set<String> rosterIds = new HashSet<>();
        List<LiveSessionContextView.PlayerContextView> starters = context.startingPlayers(teamId);
        List<LiveSessionContextView.PlayerContextView> bench = context.benchPlayers(teamId);
        for (LiveSessionContextView.PlayerContextView p : starters) rosterIds.add(p.sessionPlayerId());
        for (LiveSessionContextView.PlayerContextView p : bench) rosterIds.add(p.sessionPlayerId());
        return rosterIds;
    }

    private LiveSessionContextView.PlayerContextView findPlayer(
            LiveSessionContextView context,
            String teamId,
            String playerId) {
        return context.findPlayer(teamId, playerId);
    }
}


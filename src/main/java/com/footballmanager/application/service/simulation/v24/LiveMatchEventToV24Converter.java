package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.SessionPlayer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * V24D6M10: Converter from live SSE entity.MatchEvent to V24MatchEvent.
 *
 * <p>Converts entity.MatchEvent (with playerName, no playerId) to V24MatchEvent
 * (with playerId) by resolving playerName to SessionPlayer ID using squad data.
 *
 * <p>MVP rules:
 * <ul>
 *   <li>Do NOT invent xG - use 0.0</li>
 *   <li>Do NOT invent assists - use null</li>
 *   <li>Do NOT invent shot coordinates - use null</li>
 *   <li>Map events that exist in live flow only</li>
 * </ul>
 */
@Slf4j
public final class LiveMatchEventToV24Converter {

    private final CareerSave career;
    private final String homeTeamId;
    private final String awayTeamId;

    public LiveMatchEventToV24Converter(CareerSave career, String homeTeamId, String awayTeamId) {
        this.career = Objects.requireNonNull(career, "career must not be null");
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
    }

    /**
     * Convert a single live entity.MatchEvent to V24MatchEvent.
     */
    public V24MatchEvent convert(MatchEvent liveEvent) {
        if (liveEvent == null) {
            return null;
        }

        String eventPlayerId = liveEvent.getPlayerId();
        String eventTeamId = liveEvent.getTeamId();

        if (eventPlayerId != null && eventTeamId != null) {
            V24MatchEventType v24Type = mapEventType(liveEvent);
            return new V24MatchEvent(
                    liveEvent.getMinute(),
                    v24Type,
                    eventTeamId,
                    eventPlayerId,
                    liveEvent.getPlayerName() != null ? liveEvent.getPlayerName() : "Unknown",
                    null, null,
                    0.0,
                    liveEvent.getDescription(),
                    null
            );
        }

        TeamSide side = resolveTeamSide(liveEvent);
        if (side == null) {
            log.warn("[V24D6M10] Could not resolve teamId for event {} at minute {}, skipping",
                    liveEvent.getEventType(), liveEvent.getMinute());
            return null;
        }

        String playerId = resolvePlayerId(liveEvent, side);
        if (playerId == null) {
            log.warn("[V24D6M10] Could not resolve playerId for playerName '{}' in team {}, skipping event",
                    liveEvent.getPlayerName(), side.teamId);
            return null;
        }

        String resolvedPlayerName = resolvePlayerName(liveEvent, side, playerId);
        V24MatchEventType v24Type = mapEventType(liveEvent);

        return new V24MatchEvent(
                liveEvent.getMinute(),
                v24Type,
                side.teamId,
                playerId,
                resolvedPlayerName,
                null, null,
                0.0,
                liveEvent.getDescription(),
                null
        );
    }

    /**
     * Build V24MatchTimeline from list of live entity.MatchEvent.
     */
    public V24MatchTimeline buildTimeline(List<MatchEvent> liveEvents) {
        V24MatchTimeline timeline = new V24MatchTimeline();
        if (liveEvents == null || liveEvents.isEmpty()) {
            return timeline;
        }

        for (MatchEvent event : liveEvents) {
            V24MatchEvent v24Event = convert(event);
            if (v24Event != null) {
                timeline.addEvent(v24Event);
            }
        }
        return timeline;
    }

    /**
     * Map entity.MatchEvent.EventType to V24MatchEventType.
     * Every domain type maps explicitly.
     */
    private V24MatchEventType mapEventType(MatchEvent liveEvent) {
        MatchEvent.EventType type = liveEvent.getEventType();
        if (type == null) {
            throw new IllegalArgumentException("EventType cannot be null");
        }
        return switch (type) {
            case GOAL -> V24MatchEventType.GOAL;
            case SHOT -> V24MatchEventType.SHOT;
            case SHOT_ON_TARGET -> V24MatchEventType.SHOT_ON_TARGET;
            case SAVE -> V24MatchEventType.SAVE;
            case MISS -> V24MatchEventType.MISS;
            case BLOCK -> V24MatchEventType.BLOCK;
            case CHANCE_CREATED -> V24MatchEventType.CHANCE_CREATED;
            case FOUL -> V24MatchEventType.FOUL;
            case YELLOW_CARD -> V24MatchEventType.YELLOW_CARD;
            case RED_CARD -> V24MatchEventType.RED_CARD;
            case INJURY -> V24MatchEventType.INJURY;
            case CORNER -> V24MatchEventType.CORNER;
            case OFFSIDE -> V24MatchEventType.OFFSIDE;
            case SUBSTITUTION -> V24MatchEventType.SUBSTITUTION;
            case CARD -> {
                String desc = liveEvent.getDescription() != null ? liveEvent.getDescription().toLowerCase() : "";
                if (desc.contains("red") || desc.contains("roja")) {
                    yield V24MatchEventType.RED_CARD;
                }
                yield V24MatchEventType.YELLOW_CARD;
            }
        };
    }

    private enum TeamSide {
        HOME, AWAY;
        String teamId;
    }

    private TeamSide resolveTeamSide(MatchEvent liveEvent) {
        String eventTeamId = liveEvent.getTeamId();
        if (eventTeamId != null && !eventTeamId.isBlank()) {
            TeamSide side = eventTeamId.equals(homeTeamId) ? TeamSide.HOME : TeamSide.AWAY;
            side.teamId = eventTeamId;
            return side;
        }

        String playerName = liveEvent.getPlayerName();
        String desc = liveEvent.getDescription() != null ? liveEvent.getDescription().toLowerCase() : "";

        if (descContainsTeamContext(desc, true)) {
            TeamSide side = TeamSide.HOME;
            side.teamId = homeTeamId;
            return side;
        }
        if (descContainsTeamContext(desc, false)) {
            TeamSide side = TeamSide.AWAY;
            side.teamId = awayTeamId;
            return side;
        }

        String homePlayerId = findPlayerIdInTeam(playerName, homeTeamId);
        String awayPlayerId = findPlayerIdInTeam(playerName, awayTeamId);

        if (homePlayerId != null && awayPlayerId == null) {
            TeamSide side = TeamSide.HOME;
            side.teamId = homeTeamId;
            return side;
        }
        if (awayPlayerId != null && homePlayerId == null) {
            TeamSide side = TeamSide.AWAY;
            side.teamId = awayTeamId;
            return side;
        }
        if (homePlayerId != null && awayPlayerId != null) {
            log.warn("[V24D6M10] Player '{}' found in both squads, ambiguous", playerName);
            return null;
        }

        if (desc.contains("local") || desc.contains("home")) {
            TeamSide side = TeamSide.HOME;
            side.teamId = homeTeamId;
            return side;
        }
        if (desc.contains("visitante") || desc.contains("away")) {
            TeamSide side = TeamSide.AWAY;
            side.teamId = awayTeamId;
            return side;
        }

        log.warn("[V24D6M10] Cannot resolve teamId for event with description '{}'", liveEvent.getDescription());
        return null;
    }

    private boolean descContainsTeamContext(String desc, boolean isHome) {
        if (desc == null) return false;
        if (isHome) {
            return desc.contains("home") || desc.contains("local") || desc.contains("equipo local");
        } else {
            return desc.contains("away") || desc.contains("visitante") || desc.contains("equipo visitante");
        }
    }

    private String resolvePlayerId(MatchEvent liveEvent, TeamSide side) {
        String playerName = liveEvent.getPlayerName();
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        if (isGenericPlayerName(playerName)) {
            SessionPlayer resolved = resolveGenericPlayerName(liveEvent, side);
            return resolved != null ? resolved.getSessionPlayerId() : null;
        }

        String exactId = findPlayerIdInTeam(playerName, side.teamId);
        if (exactId != null) {
            return exactId;
        }

        List<SessionPlayer> squad = career.getTeamSquad(side.teamId);
        if (squad == null || squad.isEmpty()) {
            return null;
        }

        for (SessionPlayer p : squad) {
            if (p.getName().equalsIgnoreCase(playerName)) {
                log.debug("[V24D6M10] Player '{}' resolved via case-insensitive match to '{}'", playerName, p.getSessionPlayerId());
                return p.getSessionPlayerId();
            }
        }

        return null;
    }

    private String resolvePlayerName(MatchEvent liveEvent, TeamSide side, String playerId) {
        String playerName = liveEvent.getPlayerName();
        if (playerName == null || playerName.isBlank()) {
            return "Unknown";
        }

        if (isGenericPlayerName(playerName)) {
            SessionPlayer resolved = resolveGenericPlayerName(liveEvent, side);
            if (resolved != null) {
                return resolved.getName();
            }
            return playerName;
        }

        return playerName;
    }

    private boolean isGenericPlayerName(String playerName) {
        if (playerName == null) return false;
        String lower = playerName.toLowerCase();
        return lower.equals("jugador local") || lower.equals("jugador visitante") || lower.equals("jugador");
    }

    private SessionPlayer resolveGenericPlayerName(MatchEvent liveEvent, TeamSide side) {
        List<SessionPlayer> squad = career.getTeamSquad(side.teamId);
        if (squad == null || squad.isEmpty()) {
            log.warn("[V24D6M10] Cannot resolve generic player '{}': empty squad for team {}",
                    liveEvent.getPlayerName(), side.teamId);
            return null;
        }

        String matchId = liveEvent.getMatchId();
        if (matchId == null || matchId.isBlank()) {
            matchId = homeTeamId + "_" + awayTeamId;
        }

        int minute = liveEvent.getMinute();
        String eventType = liveEvent.getEventType() != null ? liveEvent.getEventType().name() : "";

        int index = Math.floorMod(
                Objects.hash(matchId, minute, eventType, side.teamId),
                squad.size()
        );
        return squad.get(index);
    }

    private String findPlayerIdInTeam(String playerName, String teamId) {
        if (playerName == null || teamId == null) {
            return null;
        }

        List<SessionPlayer> squad = career.getTeamSquad(teamId);
        if (squad == null || squad.isEmpty()) {
            return null;
        }

        for (SessionPlayer p : squad) {
            if (playerName.equals(p.getName())) {
                return p.getSessionPlayerId();
            }
        }
        return null;
    }
}
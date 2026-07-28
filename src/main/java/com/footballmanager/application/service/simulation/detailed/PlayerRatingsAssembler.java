package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * <p>Derives PlayerMatchRatingDto list from CareerSave starting XI + match timeline.
 * Pure function — no mutable state, no Random, no external I/O.
 *
 * <p>Flow:
 * <ol>
 *   <li>Resolve home/away team IDs from fixture</li>
 *   <li>Look up starting XI player IDs from CareerSave.teamStarting11</li>
 *   <li>Convert SessionPlayer → PlayerMatchState via fromSessionPlayer()</li>
 *   <li>Delegate to PlayerMatchStatsModel.computeRatings() for stat derivation</li>
 * </ol>
 */
public final class PlayerRatingsAssembler {

    private final PlayerMatchStatsModel statsModel;

    public PlayerRatingsAssembler() {
        this.statsModel = new PlayerMatchStatsModel();
    }

    /**
     * Build player ratings for a completed match.
     *
     * @param career   the career save (provides starting XI via teamStarting11)
     * @param fixture  the match fixture (provides home/away team IDs)
     * @param result  the detailed match result (provides timeline for stat derivation)
     * @return list of PlayerMatchRatingDto, one per starting player (never null)
     * @throws NullPointerException if any argument is null
     */
    public List<PlayerMatchRatingDto> assemblePlayerRatings(
            CareerSave career,
            MatchFixture fixture,
            DetailedMatchResult result) {
        Objects.requireNonNull(career, "career must not be null");
        Objects.requireNonNull(fixture, "fixture must not be null");
        Objects.requireNonNull(result, "result must not be null");

        String homeTeamId = fixture.getHomeTeamId();
        String awayTeamId = fixture.getAwayTeamId();

        List<PlayerMatchState> homePlayers = resolveStartingPlayers(career, homeTeamId, homeTeamId);
        List<PlayerMatchState> awayPlayers = resolveStartingPlayers(career, awayTeamId, awayTeamId);

        List<PlayerMatchState> allPlayers = new ArrayList<>(homePlayers.size() + awayPlayers.size());
        allPlayers.addAll(homePlayers);
        allPlayers.addAll(awayPlayers);

        if (allPlayers.isEmpty()) {
            return List.of();
        }

        return statsModel.computeRatings(allPlayers, result.timeline());
    }

    private List<PlayerMatchState> resolveStartingPlayers(CareerSave career, String teamId, String teamIdForState) {
        // 1. First try starting XI
        List<String> starterIds = career.getTeamStarting11().get(teamId);
        List<String> playerIdsToUse;

        if (starterIds != null && !starterIds.isEmpty()) {
            // Use starting XI (existing behavior for batch/league flow)
            playerIdsToUse = starterIds;
        } else {
            // 2. Fallback to squad players for live/SSE flow when starting11 is empty
            List<String> squadIds = career.getSquadPlayerIds(teamId);
            if (squadIds == null || squadIds.isEmpty()) {
                return List.of();
            }
            // Take up to 11 players from squad
            playerIdsToUse = squadIds.size() > 11
                    ? squadIds.subList(0, 11)
                    : squadIds;
        }

        List<PlayerMatchState> players = new ArrayList<>(playerIdsToUse.size());
        for (String playerId : playerIdsToUse) {
            SessionPlayer sp = career.getPlayerManager().getSessionPlayer(playerId);
            if (sp != null) {
                players.add(PlayerMatchState.fromSessionPlayer(sp, teamIdForState));
            }
        }
        return players;
    }
}

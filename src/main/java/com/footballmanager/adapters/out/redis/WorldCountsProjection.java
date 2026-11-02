package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.model.entity.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Immutable identities from a validated catalog. Never retains mutable domain objects.
 * Fresh minimal objects let the existing overlay enforce all identity/reference rules.
 */
final class WorldCountsProjection {
    private record League(UUID id) {}
    private record Team(String key, String id, UUID realId, UUID leagueId, WorldTeam.WorldTeamOrigin origin) {}
    private record Player(String key, String id, UUID realId, String teamId, WorldPlayer.WorldPlayerOrigin origin) {}
    private final List<League> leagues;
    private final List<Team> teams;
    private final List<Player> players;

    private WorldCountsProjection(List<League> leagues, List<Team> teams, List<Player> players) {
        this.leagues = List.copyOf(leagues);
        this.teams = List.copyOf(teams);
        this.players = List.copyOf(players);
    }

    static WorldCountsProjection from(WorldSnapshot catalog) {
        List<League> leagues = new ArrayList<>();
        List<Team> teams = new ArrayList<>();
        List<Player> players = new ArrayList<>();
        if (catalog.getLeagues() != null) catalog.getLeagues().forEach(league -> {
            if (league != null) leagues.add(new League(league.getRealLeagueId()));
        });
        if (catalog.getWorldTeams() != null) catalog.getWorldTeams().forEach((key, team) -> {
            if (team != null) teams.add(new Team(key, team.getWorldTeamId(), team.getRealTeamId(),
                    team.getRealLeagueId(), team.getOrigin()));
        });
        if (catalog.getWorldPlayers() != null) catalog.getWorldPlayers().forEach((key, player) -> {
            if (player != null) players.add(new Player(key, player.getWorldPlayerId(), player.getRealPlayerId(),
                    player.getWorldTeamId(), player.getOrigin()));
        });
        return new WorldCountsProjection(leagues, teams, players);
    }

    WorldSnapshot materialize() {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setLeagues(leagues.stream().map(league -> new WorldLeague(league.id(), null, null, null)).toList());
        var teamMap = new LinkedHashMap<String, WorldTeam>();
        for (Team identity : teams) {
            WorldTeam team = new WorldTeam();
            team.setWorldTeamId(identity.id());
            team.setRealTeamId(identity.realId());
            team.setRealLeagueId(identity.leagueId());
            team.setOrigin(identity.origin());
            teamMap.put(identity.key(), team);
        }
        snapshot.setWorldTeams(teamMap);
        var playerMap = new LinkedHashMap<String, WorldPlayer>();
        for (Player identity : players) {
            WorldPlayer player = new WorldPlayer();
            player.setWorldPlayerId(identity.id());
            player.setRealPlayerId(identity.realId());
            player.setWorldTeamId(identity.teamId());
            player.setOrigin(identity.origin());
            playerMap.put(identity.key(), player);
        }
        snapshot.setWorldPlayers(playerMap);
        return snapshot;
    }
}

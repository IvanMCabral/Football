package com.footballmanager.application.service.world.load;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.model.entity.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Carga teams y sus players asociados desde SQL.
 */
@Service
@RequiredArgsConstructor
public class TeamPlayerLoaderService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    /**
     * Resultado de cargar teams y players.
     */
    public record TeamsAndPlayersResult(List<WorldTeam> teams, List<WorldPlayer> players) {}

    /**
     * Carga todos los teams y sus players asociados.
     */
    public Mono<TeamsAndPlayersResult> loadTeamsAndPlayers(UUID userId, Map<UUID, UUID> leagueTeamsMap) {
        return teamRepository.findAllByUserId(userId)
                .flatMap(team -> {
                    UUID leagueId = leagueTeamsMap.get(team.getId().getValue());

                    WorldTeam worldTeam = WorldTeam.fromRealTeam(
                            team.getId().getValue(),
                            leagueId,
                            team.getName(),
                            team.getCountry(),
                            team.getCountry(),
                            team.getBudget(),
                            team.getFormation() != null ? team.getFormation().toString() : "4-3-3"
                    );

                    return playerRepository.findByTeamId(team.getId().getValue())
                            .map(player -> mapPlayerToWorldPlayer(player, worldTeam.getWorldTeamId()))
                            .collectList()
                            .map(players -> new TeamWithPlayers(worldTeam, players));
                })
                .collectList()
                .map(teamWithPlayersList -> {
                    List<WorldTeam> allWorldTeams = new ArrayList<>();
                    List<WorldPlayer> allWorldPlayers = new ArrayList<>();

                    for (TeamWithPlayers twp : teamWithPlayersList) {
                        allWorldTeams.add(twp.worldTeam());
                        allWorldPlayers.addAll(twp.players());
                    }

                    return new TeamsAndPlayersResult(allWorldTeams, allWorldPlayers);
                });
    }

    private record TeamWithPlayers(WorldTeam worldTeam, List<WorldPlayer> players) {}

    private WorldPlayer mapPlayerToWorldPlayer(Player player, String worldTeamId) {
        var attrs = player.getAttributes();
        return WorldPlayer.fromRealPlayer(
                player.getId().getValue(),
                worldTeamId,
                player.getName(),
                player.getAge(),
                player.getPosition().name(),
                attrs.getAttack(),
                attrs.getDefense(),
                attrs.getTechnique(),
                attrs.getSpeed(),
                attrs.getStamina(),
                attrs.getMentality(),
                player.getMarketValue()
        );
    }
}

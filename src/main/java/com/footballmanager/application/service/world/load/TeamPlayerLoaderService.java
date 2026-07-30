package com.footballmanager.application.service.world.load;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
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
                            team.getFormation() != null ? team.getFormation().toString() : "4-3-3",
                            // teams.division (Postgres) through to WorldTeam so the
                            // WorldView carries the tier info for division-aware
                            // queries (phase 4 UI: standings, dropdowns,
                            // promotion/relegation).
                            team.getDivision() != null ? team.getDivision() : Division.defaultDivision()
                    );

                    return playerRepository.findByTeamId(team.getId().getValue())
                            .map(player -> mapPlayerToWorldPlayer(player, worldTeam.getWorldTeamId()))
                            .collectList()
                            .flatMap(this::attachSpecialTraits)
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

    private Mono<List<WorldPlayer>> attachSpecialTraits(List<WorldPlayer> players) {
        List<UUID> playerIds = players.stream()
                .map(WorldPlayer::getRealPlayerId)
                .filter(Objects::nonNull)
                .toList();
        if (playerIds.isEmpty()) {
            return Mono.just(players);
        }

        return playerRepository.findSpecialTraitsByPlayerIds(playerIds)
                .collectMultimap(PlayerSpecialTrait::playerId)
                .map(traitsByPlayerId -> {
                    players.forEach(player -> player.setSpecialTraits(
                            List.copyOf(traitsByPlayerId.getOrDefault(
                                    player.getRealPlayerId(),
                                    List.of()))));
                    return players;
                });
    }

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

package com.footballmanager.application.service.world.load;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import com.footballmanager.application.observability.ReloadWorldTiming;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

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
        // A new manager has no Redis keys yet. Loading each team and its squad
        // separately made the first world request perform hundreds of remote
        // round trips and could outlive the public proxy timeout. Bootstrap
        // both canonical tables in two bounded queries, then attach traits in
        // one bulk query.
        return Mono.zip(
                        teamRepository.findAllFromDatabase().collectList(),
                        playerRepository.findAllByTeamFromDatabase())
                .flatMap(tuple -> {
                    List<Team> teams = tuple.getT1();
                    Map<UUID, List<Player>> playersByTeam = tuple.getT2();
                    Map<UUID, WorldTeam> worldTeamsById = new HashMap<>();
                    List<WorldTeam> worldTeams = teams.stream().map(team -> {
                        UUID leagueId = leagueTeamsMap.get(team.getId().getValue());
                        WorldTeam worldTeam = WorldTeam.fromRealTeam(
                                team.getId().getValue(), leagueId, team.getName(), team.getCountry(),
                                team.getCountry(), team.getBudget(),
                                team.getFormation() != null ? team.getFormation().toString() : "4-3-3",
                                team.getDivision() != null ? team.getDivision() : Division.defaultDivision());
                        worldTeamsById.put(team.getId().getValue(), worldTeam);
                        return worldTeam;
                    }).toList();

                    List<WorldPlayer> worldPlayers = teams.stream()
                            .flatMap(team -> playersByTeam.getOrDefault(team.getId().getValue(), List.of()).stream()
                                    .map(player -> mapPlayerToWorldPlayer(userId, player,
                                            worldTeamsById.get(team.getId().getValue()).getWorldTeamId())))
                            .collect(Collectors.toCollection(ArrayList::new));

                    return attachSpecialTraits(worldPlayers)
                            .map(enriched -> new TeamsAndPlayersResult(worldTeams, enriched));
                });
    }

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

    public Mono<TeamsAndPlayersResult> loadTeamsAndPlayers(UUID userId,
                                                            Map<UUID, UUID> leagueTeamsMap,
                                                            ReloadWorldTiming timing) {
        return loadTeamsAndPlayers(userId, leagueTeamsMap);
    }

    private WorldPlayer mapPlayerToWorldPlayer(UUID ownerId, Player player, String worldTeamId) {
        var attrs = player.getAttributes();
        return WorldPlayer.fromCanonicalPlayer(
                ownerId,
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

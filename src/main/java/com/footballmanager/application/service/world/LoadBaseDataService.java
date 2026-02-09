package com.footballmanager.application.service.world;

import com.footballmanager.application.service.world.load.*;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Orchestrator para carga de datos base.
 * Delega a servicios especializados:
 * - LeagueTeamSyncService: sincronizacion league-team
 * - LeagueLoaderService: carga de leagues
 * - TeamPlayerLoaderService: carga de teams y players
 */
@Service
@RequiredArgsConstructor
public class LoadBaseDataService {

    private final LeagueTeamSyncService leagueTeamSyncService;
    private final LeagueLoaderService leagueLoaderService;
    private final TeamPlayerLoaderService teamPlayerLoaderService;

    /**
     * Carga todos los datos base para un usuario.
     */
    public Mono<BaseDataResult> load(UUID userId) {
        return leagueTeamSyncService.loadLeagueTeamsMap(userId)
                .flatMap(leagueTeamsMap -> Mono.zip(
                        Mono.just(leagueTeamsMap),
                        leagueLoaderService.loadLeagues(userId),
                        teamPlayerLoaderService.loadTeamsAndPlayers(userId, leagueTeamsMap)
                ))
                .flatMap(tuple -> {
                    Map<UUID, UUID> map = tuple.getT1();
                    List<WorldLeague> leagues = tuple.getT2();
                    TeamPlayerLoaderService.TeamsAndPlayersResult teamsAndPlayers = tuple.getT3();

                    return Mono.just(new BaseDataResult(
                            leagues,
                            teamsAndPlayers.teams(),
                            teamsAndPlayers.players(),
                            map
                    ));
                });
    }

    public record BaseDataResult(
            List<WorldLeague> leagues,
            List<WorldTeam> teams,
            List<WorldPlayer> players,
            Map<UUID, UUID> leagueTeamsMap
    ) {}
}

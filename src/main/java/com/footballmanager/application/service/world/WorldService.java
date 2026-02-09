package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.ports.in.player.AssignPlayerUseCase;
import com.footballmanager.domain.ports.in.player.RemovePlayerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorldService {

    private final WorldSnapshotService snapshotService;
    private final WorldQueryService queryService;
    private final WorldTeamCommandService teamCommandService;
    private final WorldPlayerCommandService playerCommandService;
    private final WorldLeagueCommandService leagueCommandService;
    private final AssignPlayerUseCase assignPlayerUseCase;
    private final RemovePlayerUseCase removePlayerUseCase;

    public Mono<Boolean> existsByUserId(UUID userId) {
        return snapshotService.existsByUserId(userId);
    }

    public Mono<WorldSnapshot> initializeWorldSnapshot(UUID userId) {
        return snapshotService.initializeFromDatabase(userId);
    }

    public Mono<WorldSnapshot> getWorldSnapshot(UUID userId) {
        return snapshotService.getSnapshot(userId);
    }

    public Mono<List<WorldLeague>> getLeagues(UUID userId) {
        return queryService.getLeagues(userId);
    }

    public Mono<List<WorldTeam>> getTeamsByLeague(UUID userId, UUID leagueId) {
        return queryService.getTeamsByLeague(userId, leagueId);
    }

    public Mono<List<WorldTeam>> getAllTeams(UUID userId) {
        return queryService.getAllTeams(userId);
    }

    public Mono<WorldTeam> getTeam(UUID userId, String worldTeamId) {
        return queryService.getTeam(userId, worldTeamId);
    }

    public Mono<List<WorldPlayer>> getPlayersByTeam(UUID userId, String worldTeamId) {
        return queryService.getPlayersByTeam(userId, worldTeamId);
    }

    public Mono<List<WorldPlayer>> getAllPlayers(UUID userId) {
        return queryService.getAllPlayers(userId);
    }

    public Mono<List<WorldPlayer>> getFreePlayers(UUID userId) {
        return queryService.getFreePlayers(userId);
    }

    public Mono<List<WorldPlayer>> getAllWorldPlayers(UUID userId) {
        return getAllPlayers(userId);
    }

    public Mono<WorldPlayer> getWorldPlayerById(UUID userId, String playerId) {
        return getWorldSnapshot(userId)
                .map(snapshot -> snapshot.getAllWorldPlayers().stream()
                        .filter(p -> p.getWorldPlayerId().equals(playerId))
                        .findFirst()
                        .orElse(null));
    }

    public Mono<WorldTeam> getWorldTeamById(UUID userId, String teamId) {
        return getTeam(userId, teamId);
    }

    public Mono<List<WorldPlayer>> getPlayersByWorldTeam(UUID userId, String worldTeamId) {
        return getPlayersByTeam(userId, worldTeamId);
    }

    public Mono<WorldSnapshot> assignTeamToLeague(UUID userId, String teamId, UUID leagueId) {
        return addTeamToLeague(userId, leagueId, teamId);
    }

    public Mono<WorldSnapshot> createCustomWorldTeam(
            UUID userId,
            String name,
            String country,
            BigDecimal budget,
            String formation) {
        return teamCommandService.createCustomTeam(userId, name, country, budget, formation);
    }

    public Mono<WorldSnapshot> createRandomWorldTeam(UUID userId) {
        return teamCommandService.createRandomTeam(userId);
    }

    public Mono<WorldSnapshot> createRandomWorldTeams(UUID userId, int count) {
        return teamCommandService.createRandomTeams(userId, count);
    }

    public Mono<WorldSnapshot> createCustomWorldPlayer(
            UUID userId,
            String name,
            Integer age,
            String position,
            Integer attack,
            Integer defense,
            Integer technique,
            Integer speed,
            Integer stamina,
            Integer mentality) {
        return playerCommandService.createCustomPlayer(
                userId, name, age, position, attack, defense, technique, speed, stamina, mentality);
    }

    public Mono<WorldSnapshot> assignPlayerToTeam(UUID userId, String playerId, String teamId) {
        return assignPlayerUseCase.execute(userId, playerId, teamId);
    }

    public Mono<WorldSnapshot> removePlayerFromTeam(UUID userId, String playerId) {
        return removePlayerUseCase.execute(userId, playerId);
    }

    public Mono<WorldSnapshot> addTeamToLeague(UUID userId, UUID leagueId, String teamId) {
        return leagueCommandService.addTeamToLeague(userId, leagueId, teamId);
    }

    public Mono<WorldSnapshot> removeTeamFromLeague(UUID userId, UUID leagueId, String teamId) {
        return leagueCommandService.removeTeamFromLeague(userId, leagueId, teamId);
    }
}

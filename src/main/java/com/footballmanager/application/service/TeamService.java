package com.footballmanager.application.service;

import com.footballmanager.domain.model.*;
import com.footballmanager.domain.ports.out.TeamRepository;
import com.footballmanager.domain.ports.out.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public Mono<Team> createTeam(UserId managerId, String name, String country, BigDecimal initialBudget) {
        TeamId teamId = TeamId.generate();
        Team team = Team.create(teamId, managerId, name, country, initialBudget,
                              Formation.ofDefault());
        return teamRepository.save(team).then(Mono.just(team));
    }

    public Mono<Team> getTeam(TeamId teamId) {
        return teamRepository.findById(teamId);
    }

    public Flux<Team> getUserTeams(UserId managerId) {
        return teamRepository.findByManagerId(managerId);
    }

    public Mono<Team> addPlayerToTeam(TeamId teamId, PlayerId playerId) {
        return teamRepository.findById(teamId)
                .flatMap(team -> {
                    team.addPlayer(playerId);
                    return teamRepository.save(team).then(Mono.just(team));
                });
    }

    public Mono<Team> removePlayerFromTeam(TeamId teamId, PlayerId playerId) {
        return teamRepository.findById(teamId)
                .flatMap(team -> {
                    team.removePlayer(playerId);
                    return teamRepository.save(team).then(Mono.just(team));
                });
    }

    public Mono<Team> updateTeamBudget(TeamId teamId, BigDecimal amount) {
        return teamRepository.findById(teamId)
                .flatMap(team -> {
                    team.updateBudget(amount);
                    return teamRepository.save(team).then(Mono.just(team));
                });
    }

    public Flux<Player> getTeamSquad(TeamId teamId) {
        return teamRepository.findById(teamId)
                .flatMapMany(team -> Flux.fromIterable(team.getSquadPlayerIds())
                    .flatMap(playerRepository::findById));
    }
}

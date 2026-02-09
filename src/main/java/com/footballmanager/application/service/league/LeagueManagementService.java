package com.footballmanager.application.service.league;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.aggregate.League;
import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.LeagueId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.port.in.league.LeagueManagementUseCase;
import com.footballmanager.domain.ports.out.league.LeagueRepository;
import com.footballmanager.domain.ports.out.league.LeagueTeamRepository;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeagueManagementService implements LeagueManagementUseCase {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final WorldService worldService;

    @Override
    public Mono<League> createLeague(UUID userId, League league) {
        return leagueRepository.save(userId, league);
    }

    @Override
    public Mono<League> getLeague(UUID userId, UUID leagueId) {
        return leagueRepository.findById(userId, LeagueId.of(leagueId));
    }

    @Override
    public Flux<League> getAllLeagues(UUID userId) {
        return leagueRepository.findAll(userId)
                .flatMap(league -> enrichLeagueWithTeamIds(userId, league));
    }

    private Mono<League> enrichLeagueWithTeamIds(UUID userId, League league) {
        return leagueTeamRepository.findByLeagueId(userId, league.getId().getValue())
                .map(entity -> TeamId.of(entity.getTeamId()))
                .collectList()
                .map(teamIds -> {
                    return League.reconstruct(
                            league.getId(),
                            league.getName(),
                            league.getCountry(),
                            new HashSet<>(teamIds),
                            league.getCreatedAt(),
                            league.getUpdatedAt(),
                            league.getSeasonId(),
                            league.getWinnerTeamId(),
                            league.getStatus()
                    );
                });
    }

    @Override
    public Mono<League> updateLeague(UUID userId, UUID leagueId, League league) {
        return leagueRepository.findById(userId, LeagueId.of(leagueId))
                .flatMap(existingLeague -> leagueRepository.save(userId, league));
    }

    @Override
    public Mono<Void> deleteLeague(UUID userId, UUID leagueId) {
        return leagueRepository.deleteById(userId, LeagueId.of(leagueId));
    }

    @Override
    public Mono<League> startLeague(UUID userId, UUID leagueId) {
        return leagueRepository.findById(userId, LeagueId.of(leagueId))
                .map(league -> {
                    // league.start();
                    return league;
                })
                .flatMap(league -> leagueRepository.save(userId, league));
    }

    @Override
    public Mono<League> finishLeague(UUID userId, UUID leagueId) {
        return leagueRepository.findById(userId, LeagueId.of(leagueId))
                .map(league -> {
                    // league.finish();
                    return league;
                })
                .flatMap(league -> leagueRepository.save(userId, league));
    }

    @Override
    public Flux<Team> getTeamsInLeague(UUID userId, UUID leagueId) {
        return leagueTeamRepository.findByLeagueId(userId, leagueId)
                .map(entity -> entity.getTeamId())
                .collectList()
                .flatMapMany(teamIds -> {
                    if (teamIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return teamRepository.findAllByUserId(userId)
                            .filter(team -> teamIds.contains(team.getId().getValue()));
                });
    }

    @Override
    public Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId) {
        return Mono.zip(
                leagueRepository.findById(userId, LeagueId.of(leagueId)),
                teamRepository.findById(userId, teamId)
        )
        .flatMap(tuple -> {
            return worldService.assignTeamToLeague(userId, teamId.toString(), leagueId);
        })
        .then();
    }

    @Override
    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId) {
        return Mono.zip(
                leagueRepository.findById(userId, LeagueId.of(leagueId)),
                teamRepository.findById(userId, teamId)
        ).then();
    }
}

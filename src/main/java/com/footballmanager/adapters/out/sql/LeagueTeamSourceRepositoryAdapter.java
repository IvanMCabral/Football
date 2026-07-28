package com.footballmanager.adapters.out.sql;

import com.footballmanager.domain.ports.out.league.LeagueTeamLink;
import com.footballmanager.domain.ports.out.league.LeagueTeamSourceRepository;
import com.footballmanager.infrastructure.persistence.repository.LeagueTeamR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeagueTeamSourceRepositoryAdapter implements LeagueTeamSourceRepository {

    private final LeagueTeamR2dbcRepository repository;

    @Override
    public Flux<LeagueTeamLink> findByLeagueId(UUID leagueId) {
        return repository.findByLeagueId(leagueId)
            .map(entity -> new LeagueTeamLink(entity.getLeagueId(), entity.getTeamId()));
    }
}

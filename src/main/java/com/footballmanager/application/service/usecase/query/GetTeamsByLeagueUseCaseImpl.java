package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.in.query.GetTeamsByLeagueUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de GetTeamsByLeagueUseCase
 */
@Service
@RequiredArgsConstructor
public class GetTeamsByLeagueUseCaseImpl implements GetTeamsByLeagueUseCase {

    private final WorldQueryService queryService;

    @Override
    public Mono<List<WorldTeam>> execute(UUID userId, UUID leagueId) {
        return queryService.getTeamsByLeague(userId, leagueId);
    }
}

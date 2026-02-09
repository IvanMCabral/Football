package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.ports.in.query.GetPlayersByTeamUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de GetPlayersByTeamUseCase
 */
@Service
@RequiredArgsConstructor
public class GetPlayersByTeamUseCaseImpl implements GetPlayersByTeamUseCase {

    private final WorldQueryService queryService;

    @Override
    public Mono<List<WorldPlayer>> execute(UUID userId, String worldTeamId) {
        return queryService.getPlayersByTeam(userId, worldTeamId);
    }
}

package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.in.query.GetAllTeamsUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de GetAllTeamsUseCase
 */
@Service
@RequiredArgsConstructor
public class GetAllTeamsUseCaseImpl implements GetAllTeamsUseCase {

    private final WorldQueryService queryService;

    @Override
    public Mono<List<WorldTeam>> execute(UUID userId) {
        return queryService.getAllTeams(userId);
    }
}

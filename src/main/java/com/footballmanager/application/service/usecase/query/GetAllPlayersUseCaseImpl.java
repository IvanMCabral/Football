package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.ports.in.query.GetAllPlayersUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de GetAllPlayersUseCase
 */
@Service
@RequiredArgsConstructor
public class GetAllPlayersUseCaseImpl implements GetAllPlayersUseCase {

    private final WorldQueryService queryService;

    @Override
    public Mono<List<WorldPlayer>> execute(UUID userId) {
        return queryService.getAllPlayers(userId);
    }
}

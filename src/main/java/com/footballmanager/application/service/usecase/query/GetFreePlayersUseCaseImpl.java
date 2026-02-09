package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.ports.in.query.GetFreePlayersUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de GetFreePlayersUseCase
 */
@Service
@RequiredArgsConstructor
public class GetFreePlayersUseCaseImpl implements GetFreePlayersUseCase {

    private final WorldQueryService queryService;

    @Override
    public Mono<List<WorldPlayer>> execute(UUID userId) {
        return queryService.getFreePlayers(userId);
    }
}

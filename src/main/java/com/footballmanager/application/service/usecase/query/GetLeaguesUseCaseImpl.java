package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.ports.in.query.GetLeaguesUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de GetLeaguesUseCase
 */
@Service
@RequiredArgsConstructor
public class GetLeaguesUseCaseImpl implements GetLeaguesUseCase {

    private final WorldQueryService queryService;

    @Override
    public Mono<List<WorldLeague>> execute(UUID userId) {
        return queryService.getLeagues(userId);
    }
}

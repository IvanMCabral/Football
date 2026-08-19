package com.footballmanager.application.service.usecase.query;

import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.in.query.GetAllTeamsUseCase;
import com.footballmanager.application.service.world.WorldQueryService;
import com.footballmanager.infrastructure.observability.TeamsRequestObservability;
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
    private final TeamsRequestObservability teamsRequestObservability;

    @Override
    public Mono<List<WorldTeam>> execute(UUID userId) {
        return Mono.deferContextual(contextView -> {
            String correlationId = TeamsRequestObservability.correlationId(contextView);
            long startNanos = TeamsRequestObservability.startNanos(contextView);
            teamsRequestObservability.serviceStart(correlationId, startNanos);
            return queryService.getAllTeams(userId)
                    .doOnSuccess(ignored -> teamsRequestObservability.serviceSuccess(correlationId, startNanos))
                    .doOnError(ignored -> teamsRequestObservability.serviceError(correlationId, startNanos));
        });
    }
}

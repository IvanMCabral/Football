package com.footballmanager.application.service.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.port.in.match.GetMatchStateQueryUseCase;
import com.footballmanager.domain.ports.out.match.MatchRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de GetMatchStateQueryUseCase.
 * Obtiene el estado de un partido en vivo.
 */
@Service
@RequiredArgsConstructor
public class GetMatchStateQueryUseCaseImpl implements GetMatchStateQueryUseCase {

    private final MatchRuntimeRepository runtimeRepository;

    @Override
    public Mono<RuntimeMatch> getMatchState(UUID userId, String matchId) {
        return runtimeRepository.findByMatchId(userId, matchId)
            .switchIfEmpty(Mono.error(
                new IllegalArgumentException("Match not found or expired: " + matchId)));
    }
}

package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetMatchStateQueryUseCase {
    Mono<RuntimeMatch> getMatchState(UUID userId, String matchId);
}

package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MatchRuntimeRepository {
    Mono<RuntimeMatch> findByMatchId(UUID userId, String matchId);
    Mono<RuntimeMatch> save(UUID userId, RuntimeMatch match);
    Mono<Void> delete(UUID userId, String matchId);
}

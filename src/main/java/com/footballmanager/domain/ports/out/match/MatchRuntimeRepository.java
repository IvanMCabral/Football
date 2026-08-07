package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import reactor.core.publisher.Mono;

import java.util.UUID;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;

public interface MatchRuntimeRepository {
    Mono<RuntimeMatch> findByMatchId(UUID userId, String matchId);
    Mono<RuntimeMatch> save(UUID userId, RuntimeMatch match);
    default Mono<RuntimeMatch> save(UUID userId, RuntimeMatch match, CareerWriteContext context) {
        return Mono.error(new IllegalStateException(
                "runtime writer requires an explicit lifecycle context"));
    }
    Mono<Void> delete(UUID userId, String matchId);
}

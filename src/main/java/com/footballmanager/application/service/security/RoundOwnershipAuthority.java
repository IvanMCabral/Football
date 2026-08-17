package com.footballmanager.application.service.security;

import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Central authority for in-memory round/runtime ownership. A UUID alone is
 * never sufficient: the engine metadata must match both the current user and
 * the career supplied by the request.
 */
@Component
public final class RoundOwnershipAuthority {

    private final RoundEngineRegistry registry;

    public RoundOwnershipAuthority(RoundEngineRegistry registry) {
        this.registry = registry;
    }

    public Mono<RoundEngine> requireOwned(UUID ownerId, String careerId, UUID roundId) {
        if (ownerId == null || careerId == null || careerId.isBlank() || roundId == null) {
            return Mono.error(new RoundOwnershipDeniedException());
        }
        return Mono.defer(() -> {
            RoundEngine engine = registry.get(roundId);
            return engine != null && engine.belongsTo(ownerId, careerId)
                    ? Mono.just(engine)
                    : Mono.error(new RoundOwnershipDeniedException());
        });
    }

    public Mono<RoundEngine> requireOwnedRound(UUID ownerId, UUID roundId) {
        if (ownerId == null || roundId == null) {
            return Mono.error(new RoundOwnershipDeniedException());
        }
        return Mono.defer(() -> {
            RoundEngine engine = registry.get(roundId);
            return engine != null && engine.belongsTo(ownerId, null)
                    ? Mono.just(engine)
                    : Mono.error(new RoundOwnershipDeniedException());
        });
    }

    public Mono<UUID> requireOwnedRoundIdForMatch(UUID ownerId, UUID matchId) {
        if (ownerId == null || matchId == null) {
            return Mono.error(new RoundOwnershipDeniedException());
        }
        return Mono.defer(() -> {
            UUID roundId = registry.getOwnedRoundIdByMatchId(matchId, ownerId);
            return roundId == null
                    ? Mono.error(new RoundOwnershipDeniedException())
                    : Mono.just(roundId);
        });
    }
}

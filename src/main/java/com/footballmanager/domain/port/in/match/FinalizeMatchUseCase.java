package com.footballmanager.domain.port.in.match;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FinalizeMatchUseCase {
    Mono<Void> finalizeMatch(UUID userId, String matchId);
}

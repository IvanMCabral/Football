package com.footballmanager.domain.port.in.match;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResumeMatchUseCase {
    Mono<Void> execute(UUID userId, UUID matchId);
}

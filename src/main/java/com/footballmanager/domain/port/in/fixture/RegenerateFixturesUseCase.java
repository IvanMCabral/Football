package com.footballmanager.domain.port.in.fixture;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RegenerateFixturesUseCase {
    Mono<Void> regenerate(UUID userId);
}

package com.footballmanager.domain.port.in.fixture;

import reactor.core.publisher.Mono;

public interface MigrateFixturesUseCase {
    Mono<Void> migrate(String userId);
}

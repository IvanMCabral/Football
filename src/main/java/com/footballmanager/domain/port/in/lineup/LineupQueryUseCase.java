package com.footballmanager.domain.port.in.lineup;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LineupQueryUseCase {
    Mono<LineupView> getCurrentLineup(UUID userId);
}

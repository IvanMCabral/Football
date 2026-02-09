package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AdvanceMatchUseCase {
    Mono<RuntimeMatch> advanceMatch(UUID userId, String matchId);
}


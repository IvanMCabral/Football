package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface StartRoundUseCase {
    Mono<List<RuntimeMatch>> startRound(UUID userId, String careerId, int round);
}

package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.MatchCommand;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ExecuteMatchCommandUseCase {
    Mono<Boolean> execute(UUID userId, UUID matchId, MatchCommand command);
}

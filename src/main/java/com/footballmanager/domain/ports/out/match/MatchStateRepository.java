package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.entity.MatchState;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface MatchStateRepository {
    Mono<MatchState> findById(UUID userId, UUID matchId);
    Mono<MatchState> save(UUID userId, MatchState matchState);
    Mono<Void> deleteById(UUID userId, UUID matchId);
}

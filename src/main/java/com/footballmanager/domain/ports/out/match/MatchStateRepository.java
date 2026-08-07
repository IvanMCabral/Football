package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.entity.MatchState;
import reactor.core.publisher.Mono;
import java.util.UUID;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;

public interface MatchStateRepository {
    Mono<MatchState> findById(UUID userId, UUID matchId);
    Mono<MatchState> save(UUID userId, MatchState matchState);
    default Mono<MatchState> save(UUID userId, MatchState matchState, CareerWriteContext context) {
        return save(userId, matchState);
    }
    Mono<Void> deleteById(UUID userId, UUID matchId);
}

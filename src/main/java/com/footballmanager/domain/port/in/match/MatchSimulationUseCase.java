package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MatchSimulationUseCase {
    default Mono<MatchState> createMatchState(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId) {
        return Mono.error(new IllegalStateException(
                "match state creation requires explicit lifecycle context"));
    }
    Mono<MatchState> createMatchState(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId,
                                      CareerWriteContext context);
    Mono<MatchState> advanceMatch(UUID userId, UUID matchId, int toMinute);
    Mono<MatchState> applyCommand(UUID userId, UUID matchId, MatchCommand command);
    Mono<MatchState> getMatchState(UUID userId, UUID matchId);
}

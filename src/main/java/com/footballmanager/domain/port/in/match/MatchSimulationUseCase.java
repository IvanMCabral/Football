package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchCommand;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MatchSimulationUseCase {
    Mono<MatchState> createMatchState(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId);
    Mono<MatchState> advanceMatch(UUID userId, UUID matchId, int toMinute);
    Mono<MatchState> applyCommand(UUID userId, UUID matchId, MatchCommand command);
    Mono<MatchState> getMatchState(UUID userId, UUID matchId);
}

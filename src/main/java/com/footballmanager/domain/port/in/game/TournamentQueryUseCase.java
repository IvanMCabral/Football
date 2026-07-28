package com.footballmanager.domain.port.in.game;

import com.footballmanager.domain.model.entity.TournamentResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TournamentQueryUseCase {
    Flux<TournamentResult> getTournamentHistory(String userId);
    Mono<TournamentStatus> getTournamentStatus(String userId);
    Flux<TournamentStanding> getStandings(String userId);
    Mono<TournamentChampion> getChampion(String userId);
}

package com.footballmanager.domain.port.in.game;

import com.footballmanager.adapters.in.web.game.dto.ChampionDTO;
import com.footballmanager.adapters.in.web.game.dto.StandingDTO;
import com.footballmanager.adapters.in.web.game.dto.TournamentStatusDTO;
import com.footballmanager.domain.model.entity.TournamentResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TournamentQueryUseCase {
    Flux<TournamentResult> getTournamentHistory(String userId);
    Mono<TournamentStatusDTO> getTournamentStatus(String userId);
    Flux<StandingDTO> getStandings(String userId);
    Mono<ChampionDTO> getChampion(String userId);
}

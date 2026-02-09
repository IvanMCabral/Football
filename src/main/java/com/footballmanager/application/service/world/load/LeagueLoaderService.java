package com.footballmanager.application.service.world.load;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.ports.out.league.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Carga las leagues desde SQL y las convierte a WorldLeague.
 */
@Service
@RequiredArgsConstructor
public class LeagueLoaderService {

    private final LeagueRepository leagueRepository;

    /**
     * Carga todas las leagues para un usuario.
     */
    public Mono<java.util.List<WorldLeague>> loadLeagues(UUID userId) {
        return leagueRepository.findAll(userId)
                .flatMap(league -> Mono.just(WorldLeague.fromRealLeague(
                        league.getId().getValue(),
                        league.getName(),
                        league.getCountry(),
                        league.getSeasonId() != 0 ? league.getSeasonId() : 1
                )))
                .collectList();
    }
}

package com.footballmanager.application.service.world;

import com.footballmanager.adapters.in.web.dashboard.dto.WorldStatusResponse;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Query service para estado del mundo del usuario.
 * Lee el WorldSnapshot del usuario (SQL base + Redis customizations).
 */
@Service
public class WorldStatusQueryService {

    private final WorldService worldService;
    private final CareerSessionService careerSessionService;

    public WorldStatusQueryService(WorldService worldService,
                                   CareerSessionService careerSessionService) {
        this.worldService = worldService;
        this.careerSessionService = careerSessionService;
    }

    /**
     * Obtiene el estado del mundo para un usuario especifico.
     * DTO: clubs (teams), players, matches (counted from the user's
     * CareerSave tournament state).
     *
     * <p>C55.7.5 #30: the previous implementation hardcoded {@code 0}
     * for matches with a TODO comment. The dashboard "WORLD STATUS"
     * card was always "0 MATCHES" regardless of career state. Now the
     * matches count is derived from the user's CareerSave — fixtures
     * with a non-null {@code result} are considered played.
     */
    public Mono<WorldStatusResponse> getWorldStatus(UUID userId) {
        Mono<Integer> matchesMono = careerSessionService.getCareerFromCache(userId)
                .map(this::countPlayedFixtures)
                .defaultIfEmpty(0);

        return worldService.getWorldSnapshot(userId)
                .zipWith(matchesMono)
                .map(tuple -> new WorldStatusResponse(
                        tuple.getT1().getAllWorldTeams() != null ? tuple.getT1().getAllWorldTeams().size() : 0,
                        tuple.getT1().getAllWorldPlayers() != null ? tuple.getT1().getAllWorldPlayers().size() : 0,
                        tuple.getT2()
                ))
                .switchIfEmpty(Mono.just(new WorldStatusResponse(0, 0, 0)));
    }

    /**
     * Count fixtures in the user's CareerSave tournament state that have
     * a non-null result. Returns 0 when the career or its tournament
     * state is null.
     */
    private int countPlayedFixtures(CareerSave career) {
        if (career == null || career.getTournamentState() == null
                || career.getTournamentState().getFixtures() == null) {
            return 0;
        }
        int count = 0;
        for (MatchFixture f : career.getTournamentState().getFixtures()) {
            if (f.getResult() != null) {
                count++;
            }
        }
        return count;
    }
}

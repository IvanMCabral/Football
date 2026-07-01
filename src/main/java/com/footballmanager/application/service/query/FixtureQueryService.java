package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.footballmanager.application.service.query.FixtureQueryDtos.*;

/**
 * Fachada para consultas de fixtures.
 * Delega en servicios especializados.
 */
@Service
@RequiredArgsConstructor
public class FixtureQueryService {

    private final UserDivisionFixtureQueryService userDivisionService;
    private final LeagueFixtureQueryService leagueService;

    public Mono<List<MatchInfo>> getUserDivisionFixturesByRound(CareerSave career, int round) {
        return userDivisionService.getByRound(career, round);
    }

    public Mono<AllFixturesResponse> getAllUserDivisionFixtures(CareerSave career) {
        return userDivisionService.getAll(career);
    }

    /**
     * V25D78-C55.7.7 BUG-M4: same payload shape as {@link #getAllUserDivisionFixtures}
     * but filtered to a single round. Used by {@code GET /api/v1/career/fixtures/all?round=N}
     * which previously ignored the {@code round} query param.
     */
    public Mono<AllFixturesResponse> getAllUserDivisionFixturesByRound(CareerSave career, int round) {
        return userDivisionService.getAllByRound(career, round);
    }

    public Mono<CompleteFixturesResponse> getCompleteFixtures(CareerSave career) {
        return leagueService.getCompleteFixtures(career);
    }

    public Mono<List<LeagueDivisionFixtures>> getLeagueFixtures(CareerSave career, Integer round) {
        return leagueService.getLeagueFixtures(career, round);
    }

    // UX-6: BYE indicator
    public Mono<RoundFixturesWithBye> getUserDivisionFixturesByRoundWithBye(CareerSave career, int round) {
        return userDivisionService.getRoundWithBye(career, round);
    }

    public Mono<AllRoundsWithBye> getAllUserDivisionFixturesWithBye(CareerSave career) {
        return userDivisionService.getAllRoundsWithBye(career);
    }
}

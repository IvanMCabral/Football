package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.TeamOverallCalculator;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.service.MatchSimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Simula TODOS los partidos de la liga del usuario.
 * Incluye todas las divisiones de la carrera del usuario.
 */
@Service
@RequiredArgsConstructor
public class LeagueSimulator {

    private final MatchSimulator matchSimulator;

    /**
     * Simula todos los partidos de la fecha en la liga del usuario.
     * Esto incluye TODAS las divisiones de su carrera.
     */
    public void simulateLeagueRound(CareerSave career, int round) {
        TournamentState tournamentState = career.getTournamentState();
        List<MatchFixture> allFixtures = tournamentState.getFixtures();

        for (MatchFixture fixture : allFixtures) {
            if (fixture.getRound() != round) continue;
            if (!fixture.canBeSimulated()) continue;

            // Simular TODOS los partidos (incluye division del usuario)
            int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId());
            int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());

            MatchSimulator.MatchResult result = matchSimulator.simulateQuick(
                    fixture.getHomeTeamId(),
                    fixture.getAwayTeamId(),
                    homeOvr,
                    awayOvr
            );

            MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
                    result.homeGoals(), result.awayGoals(), 50, 50, 5, 5
            );

            tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
        }
    }

    private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
        // Preserve legacy empty-squad behavior: old LeagueSimulator returned 50
        List<String> squadPlayerIds = career.getTeamManager().getSquadPlayerIds(sessionTeamId);
        if (squadPlayerIds == null || squadPlayerIds.isEmpty()) {
            return 50;
        }
        // Delegate to TeamOverallCalculator for non-empty squads
        return TeamOverallCalculator.calculateFromSessionTeam(
                sessionTeamId,
                career.getTeamManager(),
                career.getPlayerManager()
        );
    }
}

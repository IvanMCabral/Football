package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentResult;
import com.footballmanager.domain.model.entity.TournamentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Procesador de resultados de partidos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResultProcessor {

    public record MatchResultInfo(String matchId, int homeGoals, int awayGoals, List<com.footballmanager.domain.model.entity.MatchEvent> events) {
        public MatchResultInfo(String matchId, int homeGoals, int awayGoals) {
            this(matchId, homeGoals, awayGoals, List.of());
        }
    }

    /**
     * Procesa los resultados individuales de partidos.
     */
    public int process(CareerSave career, List<MatchResultInfo> results) {
        int nuevosProcesados = 0;
        TournamentState tournamentState = career.getTournamentState();

        for (MatchResultInfo result : results) {
            var fixtureOpt = tournamentState.getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(result.matchId))
                    .findFirst();

            if (fixtureOpt.isEmpty()) {
                continue;
            }

            var fixture = fixtureOpt.get();

            if (fixture.getStatus() != com.footballmanager.domain.model.valueobject.MatchStatus.PENDING) {
                continue;
            }

            try {
                // V24D6T: NOTE — `result.events()` carries live path events but
                // TournamentState.processMatchResult expects
                // `domain.model.valueobject.MatchEvent` (a different type) and
                // currently does not consume the list. Forwarding the events
                // would require an entity→VO converter, which is a refactor
                // deferred to V24D6T+ui. The events remain persisted via the
                // V24 detail store (LeagueSimulator.persistV24DetailForLiveMatch)
                // and re-emitted in RoundController.handleMatchFinished.
                // Mutations are applied BEFORE this point by
                // applyLiveMatchCareerMutations — no duplication risk.
                tournamentState.processMatchResult(result.matchId, result.homeGoals, result.awayGoals, List.of());
                nuevosProcesados++;
            } catch (Exception e) {
            }
        }

        return nuevosProcesados;
    }

    /**
     * Determina el campeón de cada división.
     */
    public List<TournamentResult> determineDivisionChampions(CareerSave career) {
        List<TournamentResult> allResults = new ArrayList<>();

        for (Division division : career.getSeasonManager().getDivisions()) {
            Set<String> divisionTeamIds = new HashSet<>(division.getTeamIds());

            List<TeamStandings> divStandings = career.getTournamentState()
                    .getSortedStandings().stream()
                    .filter(s -> divisionTeamIds.contains(s.getTeamId()))
                    .toList();

            // GUARDAR CLASIFICACIONES FINALES para promociones/descensos
            career.getTournamentState().setDivisionFinalStandings(division.getDivisionId(), divStandings);

            if (!divStandings.isEmpty()) {
                TeamStandings championStanding = divStandings.get(0);
                var championTeam = career.getSessionTeam(championStanding.getTeamId());
                String championName = championTeam != null ? championTeam.getName() : championStanding.getTeamName();
                String championCoach = championTeam != null ? championTeam.getCoachName() : "Unknown";

                TournamentResult divResult = new TournamentResult(
                        career.getCurrentSeason(), division.getDivisionId(), division.getDisplayName(),
                        championStanding.getTeamId(), championName, championCoach
                );

                allResults.add(divResult);
                log.debug("[PROMOTIONS] Division {} final standings: champion={}",
                    division.getDivisionId(), championName);
            }
        }

        return allResults;
    }
}

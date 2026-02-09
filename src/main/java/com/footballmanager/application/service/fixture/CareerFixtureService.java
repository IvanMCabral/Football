package com.footballmanager.application.service.fixture;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.service.FixtureGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio compartido para gestión de fixtures en Career.
 *
 * Responsabilidad: Generar fixtures por división, inicializar standings,
 * y configurar TournamentState.
 *
 * Usado por:
 * - StartCareerUseCaseImpl (inicio de carrera)
 * - ContinueSeasonUseCaseImpl (nueva temporada)
 * - MigrateFixturesUseCaseImpl (regeneración de fixtures)
 */
@Service
@RequiredArgsConstructor
public class CareerFixtureService {

    private final FixtureGenerator fixtureGenerator;

    /**
     * Configura fixtures y standings para una carrera nueva.
     *
     * @param career Career a configurar
     * @param isNewCareer Si es carrera nueva (true) o continuación (false)
     */
    public void setupCareerFixtures(CareerSave career, boolean isNewCareer) {
        TournamentState state = career.getTournamentState();

        // Limpiar fixtures existentes
        state.getFixtures().clear();

        int maxTotalRounds = generateFixturesForAllDivisions(career, state);

        // Configurar tournamentState
        state.setTotalRounds(maxTotalRounds);
        state.setCurrentRound(1);
        state.setFinished(false);
        state.setCareerPhase(CareerPhase.PRE_MATCH);
        state.setChampionTeamId(null);

        // Inicializar standings
        initializeStandings(career, state);
    }

    /**
     * Genera fixtures para todas las divisiones.
     *
     * @return Total de rondas máximo
     */
    private int generateFixturesForAllDivisions(CareerSave career, TournamentState state) {
        int maxTotalRounds = 0;

        for (Division division : career.getSeasonManager().getDivisions()) {
            List<MatchFixture> divFixtures = generateFixturesForDivision(division, career);
            state.getFixtures().addAll(divFixtures);

            int divTotalRounds = calculateTotalRounds(division.getTeamCount());
            maxTotalRounds = Math.max(maxTotalRounds, divTotalRounds);
        }

        return maxTotalRounds;
    }

    /**
     * Genera fixtures para una división específica.
     */
    public List<MatchFixture> generateFixturesForDivision(Division division, CareerSave career) {
        // Crear copia mutable de teamIds
        List<String> divisionTeamIds = new ArrayList<>(division.getTeamIds());

        // Shuffle con seed determinístico (basado en season + division)
        long seed = System.currentTimeMillis() + career.getCurrentSeason() + division.getDivisionId().hashCode();
        Collections.shuffle(divisionTeamIds, new Random(seed));

        // Convertir a TeamId
        List<TeamId> teamIds = divisionTeamIds.stream()
            .map(teamId -> TeamId.of(UUID.fromString(teamId)))
            .collect(Collectors.toList());

        // Generar fixture round-robin
        List<FixtureGenerator.FixtureRound> rounds = fixtureGenerator.generate(teamIds, true);

        // Convertir a MatchFixture
        List<MatchFixture> fixtures = new ArrayList<>();
        for (FixtureGenerator.FixtureRound round : rounds) {
            for (FixtureGenerator.FixtureSlot slot : round.matches()) {
                fixtures.add(new MatchFixture(
                    UUID.randomUUID().toString(),
                    slot.home().getValue().toString(),
                    slot.away().getValue().toString(),
                    round.roundNumber()
                ));
            }
        }

        return fixtures;
    }

    /**
     * Inicializa standings para todos los equipos de todas las divisiones.
     */
    private void initializeStandings(CareerSave career, TournamentState state) {
        state.getStandings().clear();

        for (Division division : career.getSeasonManager().getDivisions()) {
            for (String teamId : division.getTeamIds()) {
                SessionTeam team = career.getSessionTeam(teamId);
                if (team != null && !state.getStandings().containsKey(teamId)) {
                    TeamStandings standing = new TeamStandings(
                        team.getSessionTeamId(),
                        team.getName()
                    );
                    state.getStandings().put(teamId, standing);
                }
            }
        }
    }

    /**
     * Calcula total de rondas para round-robin doble vuelta.
     */
    public int calculateTotalRounds(int teamCount) {
        if (teamCount % 2 == 0) {
            return 2 * (teamCount - 1);
        } else {
            return 2 * teamCount;
        }
    }
}

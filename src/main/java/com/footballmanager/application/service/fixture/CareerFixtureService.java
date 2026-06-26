package com.footballmanager.application.service.fixture;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.service.FixtureGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
     *
     * <p>V25D36-F3: defensa en profundidad contra el bug "X vs X" en el
     * fixture (donde el mismo teamId aparece como home y away del mismo
     * partido). Tres guards:
     * <ol>
     *   <li>Deduplicar {@code division.getTeamIds()} preservando orden — si el
     *       upstream inyecta duplicados (e.g. un WorldTeam doble-creado por
     *       LaLigaSeed o un re-seed mal idempotentado), el round-robin NO
     *       debería emparejar al equipo consigo mismo.</li>
     *   <li>Skipear MatchSlots donde home == away (en caso de que la dedup
     *       falle o el upstream haya mutado la lista entre la lectura y el
     *       uso). Log WARN con contexto.</li>
     *   <li>Loggear un warning si el conteo de fixtures generados difiere
     *       del esperado por round-robin matemático (n-1 por pierna con
     *       n par, n por pierna con n impar).</li>
     * </ol>
     *
     * <p>La causa raíz exacta del bug original (Real Madrid vs Real Madrid)
     * no se pudo reproducir sin levantar el stack — el guard previene la
     * clase del bug independientemente del origen.
     */
    public List<MatchFixture> generateFixturesForDivision(Division division, CareerSave career) {
        // V25D36-F3 #1: deduplicar preservando orden (LinkedHashSet).
        // Si upstream inyecta duplicados, el round-robin podría emparejar
        // un equipo consigo mismo (ver generateIda en FixtureGenerator).
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        int duplicatesSkipped = 0;
        for (String teamId : division.getTeamIds()) {
            if (teamId == null || teamId.isBlank()) {
                duplicatesSkipped++;
                continue;
            }
            if (!seen.add(teamId)) {
                duplicatesSkipped++;
            }
        }
        List<String> divisionTeamIds = new ArrayList<>(seen);
        if (duplicatesSkipped > 0) {
            log.warn("[CAREER-FIXTURE] V25D36-F3: deduplicated {} teamId entries (null/blank/duplicate) for division {}",
                duplicatesSkipped, division.getDivisionId());
        }

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
        int selfPairingsSkipped = 0;
        for (FixtureGenerator.FixtureRound round : rounds) {
            for (FixtureGenerator.FixtureSlot slot : round.matches()) {
                String homeId = slot.home().getValue().toString();
                String awayId = slot.away().getValue().toString();
                // V25D36-F3 #2: assert home != away. Si la dedup falló (e.g.
                // n=1 post-filter), skip + warn. Nunca debería pasar post-dedup
                // pero es defense-in-depth.
                if (homeId.equals(awayId)) {
                    selfPairingsSkipped++;
                    log.warn("[CAREER-FIXTURE] V25D36-F3: skipped self-pairing fixture homeId=awayId={} round={} division={}",
                        homeId, round.roundNumber(), division.getDivisionId());
                    continue;
                }
                fixtures.add(new MatchFixture(
                    UUID.randomUUID().toString(),
                    homeId,
                    awayId,
                    round.roundNumber()
                ));
            }
        }

        // V25D36-F3 #3: sanity check de count vs round-robin matemático.
        int n = teamIds.size();
        int expectedMatchesPerLeg = n / 2; // matches per round
        int expectedRoundsPerLeg = (n % 2 == 0) ? (n - 1) : n;
        int expectedTotalMatches = expectedMatchesPerLeg * expectedRoundsPerLeg * 2; // ida + vuelta
        if (fixtures.size() != expectedTotalMatches) {
            log.warn("[CAREER-FIXTURE] V25D36-F3: division {} generated {} fixtures, expected {} (n={}, selfPairingsSkipped={})",
                division.getDivisionId(), fixtures.size(), expectedTotalMatches, n, selfPairingsSkipped);
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

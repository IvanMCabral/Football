package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de consultas para standings (solo lectura).
 *
 * Responsabilidades:
 * - Consultar standings de la división del usuario
 * - Consultar standings de todas las divisiones
 *
 * NO modifica estado (solo operaciones de lectura).
 */
@Service
public class StandingQueryService {

    /**
     * DTO para un equipo en la tabla de posiciones.
     */
    public record StandingEntry(
            String teamId,
            String teamName,
            Integer played,
            Integer won,
            Integer drawn,
            Integer lost,
            Integer goalsFor,
            Integer goalsAgainst,
            Integer goalDifference,
            Integer points
    ) {}

    /**
     * DTO para standings de una división específica.
     */
    public record DivisionStandings(
            String divisionId,
            String divisionName,
            Boolean isUserDivision,
            List<StandingEntry> standings
    ) {}

    /**
     * DTO para respuesta de todos los standings.
     */
    public record AllStandingsResponse(
            List<DivisionStandings> divisions
    ) {}

    /**
     * Obtiene los standings de la división del usuario.
     *
     * @param career Carrera a consultar
     * @param userDivision División del usuario (null si no existe)
     * @return Lista de standings filtrados por división del usuario
     */
    public Mono<List<StandingEntry>> getUserDivisionStandings(CareerSave career, Division userDivision) {
        return Mono.fromCallable(() -> {
            Set<String> userDivisionTeamIds = new HashSet<>(
                    userDivision != null ? userDivision.getTeamIds() : List.of()
            );

            List<TeamStandings> allStandings =
                    career.getTournamentState().getSortedStandings();

            // Filtrar standings de la división del usuario
            List<TeamStandings> filteredStandings = allStandings.stream()
                    .filter(s -> userDivisionTeamIds.contains(s.getTeamId()))
                    .toList();

            return filteredStandings.stream()
                    .map(s -> new StandingEntry(
                            s.getTeamId(),
                            s.getTeamName(),
                            s.getPlayed(),
                            s.getWon(),
                            s.getDrawn(),
                            s.getLost(),
                            s.getGoalsFor(),
                            s.getGoalsAgainst(),
                            s.getGoalDifference(),
                            s.getPoints()
                    ))
                    .collect(Collectors.toList());
        });
    }

    /**
     * Obtiene los standings de todas las divisiones.
     *
     * @param career Carrera a consultar
     * @param divisions Lista de divisiones
     * @param userDivision División del usuario
     * @return Respuesta con standings de cada división
     */
    public Mono<AllStandingsResponse> getAllStandings(CareerSave career, List<Division> divisions, Division userDivision) {
        return Mono.fromCallable(() -> {
            TournamentState mainState = career.getTournamentState();
            List<TeamStandings> allStandings = mainState.getSortedStandings();

            List<DivisionStandings> resultDivisions = new ArrayList<>();

            for (Division division : divisions) {
                Set<String> divisionTeamIds = new HashSet<>(division.getTeamIds());

                List<StandingEntry> divStandings = allStandings.stream()
                        .filter(s -> divisionTeamIds.contains(s.getTeamId()))
                        .map(s -> new StandingEntry(
                                s.getTeamId(),
                                s.getTeamName(),
                                s.getPlayed(),
                                s.getWon(),
                                s.getDrawn(),
                                s.getLost(),
                                s.getGoalsFor(),
                                s.getGoalsAgainst(),
                                s.getGoalDifference(),
                                s.getPoints()
                        ))
                        .collect(Collectors.toList());

                resultDivisions.add(new DivisionStandings(
                        division.getDivisionId().toString(),
                        division.getDisplayName(),
                        userDivision != null &&
                                division.getDivisionId().equals(userDivision.getDivisionId()),
                        divStandings
                ));
            }

            return new AllStandingsResponse(resultDivisions);
        });
    }
}

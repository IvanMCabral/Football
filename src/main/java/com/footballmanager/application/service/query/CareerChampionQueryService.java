package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Servicio de consultas para campeón del torneo.
 */
@Service
public class CareerChampionQueryService {

    public record ChampionInfo(
            String teamId,
            String teamName,
            Integer points,
            Integer wins,
            Integer goalDifference
    ) {}

    /**
     * Obtiene el campeón del torneo.
     *
     * @param career Carrera a consultar
     * @return Información del campeón
     * @throws IllegalStateException si el torneo no ha terminado o no hay campeón
     */
    public Mono<ChampionInfo> getChampion(CareerSave career) {
        return Mono.fromCallable(() -> {
            TournamentState state = career.getTournamentState();

            if (!state.getFinished()) {
                throw new IllegalStateException("El torneo no ha terminado");
            }

            String championTeamId = state.getChampionTeamId();
            if (championTeamId == null) {
                List<TeamStandings> standings = state.getSortedStandings();
                if (standings.isEmpty()) {
                    throw new IllegalStateException("No hay equipos en el torneo");
                }
                TeamStandings champion = standings.get(0);
                return new ChampionInfo(
                        champion.getTeamId(),
                        champion.getTeamName(),
                        champion.getPoints(),
                        champion.getWon(),
                        champion.getGoalDifference()
                );
            }

            TeamStandings championStanding = state.getStandings().get(championTeamId);
            if (championStanding == null) {
                throw new IllegalStateException("Campeón no encontrado en standings: " + championTeamId);
            }

            return new ChampionInfo(
                    championStanding.getTeamId(),
                    championStanding.getTeamName(),
                    championStanding.getPoints(),
                    championStanding.getWon(),
                    championStanding.getGoalDifference()
            );
        });
    }
}

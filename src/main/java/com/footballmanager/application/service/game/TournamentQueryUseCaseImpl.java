package com.footballmanager.application.service.game;

import com.footballmanager.adapters.in.web.game.dto.ChampionDTO;
import com.footballmanager.adapters.in.web.game.dto.StandingDTO;
import com.footballmanager.adapters.in.web.game.dto.TournamentStatusDTO;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentResult;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.game.TournamentQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementación de UseCase para consultas del torneo.
 *
 * Lee directamente de TournamentState en Redis (no de PostgreSQL).
 */
@Service
@RequiredArgsConstructor
public class TournamentQueryUseCaseImpl implements TournamentQueryUseCase {

    private final CareerRepository careerRepository;

    @Override
    public Flux<TournamentResult> getTournamentHistory(String userId) {
        return careerRepository.findById(userId)
            .flatMapMany(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Flux.empty();
                }
                CareerSave career = optionalCareer.get();

                if (career.getSeasonManager().getPalmares() == null || career.getSeasonManager().getPalmares().isEmpty()) {
                    return Flux.empty();
                }

                return Flux.fromIterable(career.getSeasonManager().getPalmares());
            })
            .switchIfEmpty(Flux.empty());
    }

    @Override
    public Mono<TournamentStatusDTO> getTournamentStatus(String userId) {
        return careerRepository.findById(userId)
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.just(new TournamentStatusDTO(0, 0, false, true, null));
                }

                CareerSave career = optionalCareer.get();
                TournamentState state = career.getTournamentState();

                ChampionDTO champion = null;
                if (state.getChampionTeamId() != null) {
                    champion = new ChampionDTO(
                        UUID.fromString(state.getChampionTeamId()),
                        getTeamName(state, state.getChampionTeamId()),
                        0, 0, 0
                    );
                }

                return Mono.just(new TournamentStatusDTO(
                    state.getCurrentRound(),
                    state.getTotalRounds(),
                    state.canAdvanceToNextRound(),
                    state.getFinished(),
                    champion
                ));
            })
            .switchIfEmpty(Mono.just(new TournamentStatusDTO(0, 0, false, true, null)));
    }

    @Override
    public Flux<StandingDTO> getStandings(String userId) {
        return careerRepository.findById(userId)
            .flatMapMany(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Flux.empty();
                }

                CareerSave career = optionalCareer.get();
                return buildStandingsDTOs(career);
            })
            .switchIfEmpty(Flux.empty());
    }

    @Override
    public Mono<ChampionDTO> getChampion(String userId) {
        return getStandings(userId)
            .collectList()
            .flatMap(standings -> {
                // C55.7.5 #29: previous contract returned Mono.empty() when
                // the top team had 0 points. That made the
                // /api/v1/games/{id}/champion endpoint 404 in
                // end-of-tournament views, surfacing as "Error al cargar
                // el campeón" in the frontend. The correct contract is
                // to always return the top-ranked team — points=0 is a
                // valid (if undesired) state that should still surface
                // a valid DTO with the top teamId, letting the frontend
                // render "0 PTS" gracefully instead of an error.
                if (standings.isEmpty()) {
                    return Mono.empty();
                }
                StandingDTO champion = standings.get(0);
                return Mono.just(new ChampionDTO(
                    champion.teamId(),
                    champion.teamName(),
                    champion.points(),
                    champion.wins(),
                    champion.goalDifference()
                ));
            });
    }

    private Flux<StandingDTO> buildStandingsDTOs(CareerSave career) {
        TournamentState state = career.getTournamentState();

        return Flux.fromIterable(state.getSortedStandings())
            .map(standing -> new StandingDTO(
                UUID.fromString(standing.getTeamId()),
                standing.getTeamName(),
                standing.getPlayed(),
                standing.getWon(),
                standing.getDrawn(),
                standing.getLost(),
                standing.getGoalsFor(),
                standing.getGoalsAgainst(),
                standing.getGoalDifference(),
                standing.getPoints()
            ));
    }

    private String getTeamName(TournamentState state, String teamId) {
        TeamStandings standing = state.getStandings().get(teamId);
        return standing != null ? standing.getTeamName() : teamId;
    }
}

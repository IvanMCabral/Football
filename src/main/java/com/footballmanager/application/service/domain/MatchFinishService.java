package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.entity.Match;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.Standing;
import com.footballmanager.domain.model.valueobject.MatchId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.ports.out.match.MatchRepository;
import com.footballmanager.domain.ports.out.standing.StandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Servicio para persistir resultados finales de partidos.
 * Actualiza Match y Standing cuando un partido termina.
 */
@Service
@RequiredArgsConstructor
public class MatchFinishService {
    private final MatchRepository matchRepository;
    private final StandingRepository standingRepository;

    /**
     * Procesa el resultado final de un partido.
     * Actualiza la entidad Match y el Standing en Redis.
     */
    public Mono<Void> finishMatch(UUID userId, MatchState finalState) {
        return matchRepository.findById(userId, MatchId.of(finalState.getMatchId()))
            .flatMap(match -> {
                int homeGoals = finalState.getScore().home();
                int awayGoals = finalState.getScore().away();

                // Crear el MatchResult con todos los datos
                MatchResult result = MatchResult.of(
                    homeGoals,
                    awayGoals,
                    50, // homePossession (default 50-50)
                    50, // awayPossession
                    homeGoals * 3, // homeShots (3 shots per goal estimate)
                    awayGoals * 3, // awayShots
                    finalState.getEvents() != null ? finalState.getEvents() : new ArrayList<>(),
                    null // summary (auto-generated)
                );

                // Usar el método de dominio para simular el partido
                match.simulate(result);

                // Persistir Match actualizado y actualizar Standing
                return matchRepository.save(userId, match)
                    .then(updateStandings(userId, match, homeGoals, awayGoals));
            })
            .onErrorResume(error -> Mono.empty());
    }

    /**
     * Actualiza los standings de ambos equipos en Redis.
     */
    private Mono<Void> updateStandings(UUID userId, Match match, int homeGoals, int awayGoals) {
        // Por ahora usamos seasonKey = "1" como default
        // TODO: Obtener seasonKey desde Match o Game
        String seasonKey = "1";

        TeamId homeTeamId = match.getHomeTeamId();
        TeamId awayTeamId = match.getAwayTeamId();

        // Actualizar standing del equipo local
        Mono<Void> homeStandingMono = updateTeamStanding(userId, seasonKey, homeTeamId, homeGoals, awayGoals, true);

        // Actualizar standing del equipo visitante
        Mono<Void> awayStandingMono = updateTeamStanding(userId, seasonKey, awayTeamId, awayGoals, homeGoals, false);

        return Mono.when(homeStandingMono, awayStandingMono);
    }

    /**
     * Actualiza el standing de un equipo específico.
     */
    private Mono<Void> updateTeamStanding(UUID userId, String seasonKey, TeamId teamId, int goalsFor, int goalsAgainst, boolean isHomeTeam) {
        return standingRepository.findBySeasonKeyAndTeamId(userId, seasonKey, teamId.getValue())
            .flatMap(standing -> {
                standing.updateResult(goalsFor, goalsAgainst, isHomeTeam);
                return standingRepository.save(userId, standing).then();
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Crear nuevo standing si no existe
                Standing newStanding = new Standing(seasonKey, teamId);
                newStanding.updateResult(goalsFor, goalsAgainst, isHomeTeam);
                return standingRepository.save(userId, newStanding).then();
            }))
            .onErrorResume(error -> Mono.empty());
    }
}

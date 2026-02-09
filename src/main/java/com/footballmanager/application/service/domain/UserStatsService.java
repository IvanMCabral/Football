package com.footballmanager.application.service.domain;

import com.footballmanager.adapters.in.web.dashboard.dto.UserStatsResponse;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.infrastructure.persistence.repository.UserR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * UserStatsService - Servicio de estadísticas de usuario.
 *
 * Responsabilidad: Consultar estadísticas desde Redis (CareerSave).
 * Los stats vienen de TournamentState.standings, NO de SQL.
 */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserR2dbcRepository userRepository;
    private final CareerSessionService careerSessionService;

    public Mono<UserStatsResponse> getUserStats(UUID userId) {
        // Obtener nombre del usuario
        Mono<String> userNameMono = userRepository.findById(userId)
            .map(userEntity -> userEntity.getUsername())
            .defaultIfEmpty("Unknown");

        // Obtener stats desde CareerSave (Redis)
        Mono<TeamStandings> standingsMono = careerSessionService.continueCareer(userId)
            .map(career -> {
                String userTeamId = career.getUserSessionTeamId();
                if (userTeamId == null) {
                    return null;
                }
                TournamentState state = career.getTournamentState();
                if (state == null || state.getStandings() == null) {
                    return null;
                }
                return state.getStandings().get(userTeamId);
            })
            .switchIfEmpty(Mono.empty());

        return Mono.zip(userNameMono, standingsMono)
            .map(tuple -> {
                String userName = tuple.getT1();
                TeamStandings standing = tuple.getT2();

                if (standing == null) {
                    return new UserStatsResponse(userName, 0, 0, 0, 0.0);
                }

                int played = standing.getPlayed() != null ? standing.getPlayed() : 0;
                int wins = standing.getWon() != null ? standing.getWon() : 0;
                int losses = standing.getLost() != null ? standing.getLost() : 0;

                double winPercentage = played > 0
                    ? Math.round((wins * 100.0 / played) * 100.0) / 100.0
                    : 0.0;

                return new UserStatsResponse(
                    userName,
                    played,
                    wins,
                    losses,
                    winPercentage
                );
            });
    }
}

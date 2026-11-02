package com.footballmanager.application.service.domain;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.ports.out.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.footballmanager.application.observability.RuntimeOperationMetrics;

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

    private final UserRepository userRepository;
    private final CareerSessionService careerSessionService;

    /** Derived from the same loaded career used by entry status, without another profile query. */
    public static UserStatsSummary summarizeCareer(String userName, com.footballmanager.domain.model.entity.CareerSave career) {
        TeamStandings standing = career == null || career.getUserSessionTeamId() == null
                || career.getTournamentState() == null || career.getTournamentState().getStandings() == null
                ? null : career.getTournamentState().getStandings().get(career.getUserSessionTeamId());
        return summarizeStanding(userName, standing);
    }

    private static UserStatsSummary summarizeStanding(String userName, TeamStandings standing) {
        if (standing == null) return new UserStatsSummary(userName, 0, 0, 0, 0.0);
        int played = standing.getPlayed() == null ? 0 : standing.getPlayed();
        int wins = standing.getWon() == null ? 0 : standing.getWon();
        int losses = standing.getLost() == null ? 0 : standing.getLost();
        double percentage = played > 0 ? Math.round((wins * 100.0 / played) * 100.0) / 100.0 : 0.0;
        return new UserStatsSummary(userName, played, wins, losses, percentage);
    }

    public Mono<UserStatsSummary> getUserStats(UUID userId) {
        Mono<String> userNameMono = RuntimeOperationMetrics.measure("dashboard.userStats.userName",
                userRepository.findById(userId)
                    .map(userEntity -> userEntity.getUsername())
                    .defaultIfEmpty("Unknown"));

        // Obtener stats desde CareerSave (Redis)
        Mono<TeamStandings> standingsMono = RuntimeOperationMetrics.measure(
            "dashboard.userStats.loadCareer",
            careerSessionService.getCareerFromCache(userId))
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

        return RuntimeOperationMetrics.measure("dashboard.userStats.total",
            Mono.zip(userNameMono, standingsMono))
            .map(tuple -> {
                String userName = tuple.getT1();
                TeamStandings standing = tuple.getT2();

                return summarizeStanding(userName, standing);
            });
    }
}

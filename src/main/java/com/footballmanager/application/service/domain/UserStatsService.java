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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Duration;

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
    private final ConcurrentMap<UUID, Mono<String>> userNameCache = new ConcurrentHashMap<>();

    public Mono<UserStatsSummary> getUserStats(UUID userId) {
        Mono<String> userNameMono = userNameCache.computeIfAbsent(userId, id ->
            RuntimeOperationMetrics.measure("dashboard.userStats.userName",
                userRepository.findById(id)
                    .map(userEntity -> userEntity.getUsername())
                    .defaultIfEmpty("Unknown"))
                .cache(Duration.ofMinutes(10)));

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

                if (standing == null) {
                    return new UserStatsSummary(userName, 0, 0, 0, 0.0);
                }

                int played = standing.getPlayed() != null ? standing.getPlayed() : 0;
                int wins = standing.getWon() != null ? standing.getWon() : 0;
                int losses = standing.getLost() != null ? standing.getLost() : 0;

                double winPercentage = played > 0
                    ? Math.round((wins * 100.0 / played) * 100.0) / 100.0
                    : 0.0;

                return new UserStatsSummary(
                    userName,
                    played,
                    wins,
                    losses,
                    winPercentage
                );
            });
    }
}

package com.footballmanager.application.service.world;

import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.out.league.LeagueTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Servicio para operaciones de Liga-Equipo.
 * Maneja tanto Redis Sets como la actualizacion del WorldSnapshot.
 */
@Service
@RequiredArgsConstructor
public class LeagueTeamCommandService {

    private final LeagueTeamRepository leagueTeamRepository;
    private final RedisWorldRepository worldRepository;

    /**
     * Agrega un equipo a una liga.
     * Actualiza tanto Redis Sets como el WorldSnapshot.
     */
    public Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId) {
        String worldTeamId = teamId.toString(); // worldTeamId = realTeamId para equipos reales

        return leagueTeamRepository.addTeamToLeague(userId, leagueId, teamId)
                .then(updateSnapshotRealLeagueId(userId, worldTeamId, leagueId))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Remueve un equipo de una liga.
     * Actualiza tanto Redis Sets como el WorldSnapshot.
     */
    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId) {
        String worldTeamId = teamId.toString();

        return leagueTeamRepository.removeTeamFromLeague(userId, leagueId, teamId)
                .then(updateSnapshotRealLeagueId(userId, worldTeamId, null))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Actualiza el realLeagueId del WorldTeam en el snapshot
     */
    private Mono<Void> updateSnapshotRealLeagueId(UUID userId, String worldTeamId, UUID leagueId) {
        return worldRepository.findByUserId(userId)
                .flatMap(snapshot -> {
                    var team = snapshot.getWorldTeam(worldTeamId);
                    if (team != null) {
                        team.setRealLeagueId(leagueId);
                        return worldRepository.save(snapshot).then();
                    } else {
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> Mono.empty());
    }
}

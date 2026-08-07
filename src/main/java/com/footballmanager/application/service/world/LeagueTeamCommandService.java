package com.footballmanager.application.service.world;

import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.out.league.LeagueTeamRepository;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
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
    private final WorldSnapshotRepository worldRepository;

    /**
     * Agrega un equipo a una liga.
     * Actualiza tanto Redis Sets como el WorldSnapshot.
     */
    public Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId) {
        String worldTeamId = teamId.toString(); // worldTeamId = realTeamId para equipos reales

        return leagueTeamRepository.addTeamToLeague(userId, leagueId, teamId)
                .then(updateSnapshotRealLeagueId(userId, worldTeamId, leagueId));
    }

    /** Career-scoped variant; all world writes use the captured lifecycle context. */
    public Mono<Void> addTeamToLeague(UUID userId, UUID leagueId, UUID teamId,
                                      CareerWriteContext context) {
        String worldTeamId = teamId.toString();
        return leagueTeamRepository.addTeamToLeague(userId, leagueId, teamId)
                .then(updateSnapshotRealLeagueId(userId, worldTeamId, leagueId, context));
    }

    /**
     * Remueve un equipo de una liga.
     * Actualiza tanto Redis Sets como el WorldSnapshot.
     */
    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId) {
        String worldTeamId = teamId.toString();

        return leagueTeamRepository.removeTeamFromLeague(userId, leagueId, teamId)
                .then(updateSnapshotRealLeagueId(userId, worldTeamId, null));
    }

    public Mono<Void> removeTeamFromLeague(UUID userId, UUID leagueId, UUID teamId,
                                           CareerWriteContext context) {
        String worldTeamId = teamId.toString();
        return leagueTeamRepository.removeTeamFromLeague(userId, leagueId, teamId)
                .then(updateSnapshotRealLeagueId(userId, worldTeamId, null, context));
    }

    /**
     * Actualiza el realLeagueId del WorldTeam en el snapshot
     */
    private Mono<Void> updateSnapshotRealLeagueId(UUID userId, String worldTeamId, UUID leagueId) {
        return updateSnapshotRealLeagueId(userId, worldTeamId, leagueId, null);
    }

    private Mono<Void> updateSnapshotRealLeagueId(UUID userId, String worldTeamId, UUID leagueId,
                                                  CareerWriteContext context) {
        return worldRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("world snapshot not found")))
                .flatMap(snapshot -> {
                    var team = snapshot.getWorldTeam(worldTeamId);
                    if (team != null) {
                        team.setRealLeagueId(leagueId);
                        Mono<WorldSnapshot> save = context == null
                                ? worldRepository.save(snapshot)
                                : worldRepository.saveWithContext(context, snapshot);
                        return save.then();
                    } else {
                        return Mono.error(new IllegalArgumentException("world team not found"));
                    }
                });
    }
}


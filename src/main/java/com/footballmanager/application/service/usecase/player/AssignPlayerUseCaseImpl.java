package com.footballmanager.application.service.usecase.player;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.in.player.AssignPlayerUseCase;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.application.service.world.WorldSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de AssignPlayerUseCase
 */
@Service
@RequiredArgsConstructor
public class AssignPlayerUseCaseImpl implements AssignPlayerUseCase {

    private final WorldSnapshotService snapshotService;
    private final TeamRepository teamRepository;

    @Override
    public Mono<WorldSnapshot> execute(UUID userId, String worldPlayerId, String worldTeamId) {
        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    WorldPlayer player = snapshot.getWorldPlayer(worldPlayerId);
                    if (player == null) {
                        return Mono.error(new IllegalArgumentException(
                                "WorldPlayer no encontrado: " + worldPlayerId));
                    }

                    // Buscar equipo primero en snapshot, luego en SQL
                    WorldTeam team = snapshot.getWorldTeam(worldTeamId);
                    if (team == null) {
                        // Buscar en SQL (equipos base)
                        return teamRepository.findById(userId, UUID.fromString(worldTeamId))
                                .flatMap(realTeam -> {
                                    // Crear WorldTeam desde el equipo real
                                    // V25D78-C55.6: propagate division tier from
                                    // Team aggregate (Postgres) through to WorldTeam.
                                    WorldTeam newTeam = WorldTeam.fromRealTeam(
                                            realTeam.getId().getValue(),
                                            null, // leagueId desconocido
                                            realTeam.getName(),
                                            realTeam.getCountry(),
                                            realTeam.getCountry(),
                                            realTeam.getBudget(),
                                            realTeam.getFormation() != null ? realTeam.getFormation().toString() : "4-3-3",
                                            realTeam.getDivision() != null ? realTeam.getDivision() : com.footballmanager.domain.model.valueobject.Division.defaultDivision()
                                    );
                                    // Agregar al snapshot
                                    snapshot.addWorldTeam(newTeam);

                                    // Asignar jugador
                                    player.setWorldTeamId(worldTeamId);

                                    return snapshotService.saveSnapshot(snapshot);
                                })
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                        "WorldTeam no encontrado: " + worldTeamId + " (ni en snapshot ni en SQL)")));
                    }

                    player.setWorldTeamId(worldTeamId);

                    return snapshotService.saveSnapshot(snapshot);
                });
    }
}

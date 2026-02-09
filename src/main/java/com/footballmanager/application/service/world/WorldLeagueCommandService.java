package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.out.league.LeagueTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WorldLeagueCommandService - Responsable de operaciones sobre relaciones liga-equipo.
 * Principio de Responsabilidad Unica: gestiona asignaciones de equipos a ligas.
 */
@Service
@RequiredArgsConstructor
public class WorldLeagueCommandService {

    private final WorldSnapshotService snapshotService;
    private final LeagueTeamRepository leagueTeamRepository;

    /**
     * Agrega un equipo a una liga
     * Actualiza tanto el WorldSnapshot como Redis
     */
    public Mono<WorldSnapshot> addTeamToLeague(UUID userId, UUID realLeagueId, String worldTeamId) {


        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    // Validar que la liga existe
                    var league = snapshot.getLeagues().stream()
                            .filter(l -> l.getRealLeagueId().equals(realLeagueId))
                            .findFirst()
                            .orElse(null);

                    if (league == null) {
                        return Mono.error(new IllegalArgumentException(
                                "Liga no encontrada: " + realLeagueId));
                    }

                    // Validar que el equipo existe
                    WorldTeam team = snapshot.getWorldTeam(worldTeamId);
                    if (team == null) {
                        return Mono.error(new IllegalArgumentException(
                                "Equipo no encontrado: " + worldTeamId));
                    }

                    // Asignar equipo a liga en WorldSnapshot
                    team.setRealLeagueId(realLeagueId);

                    // Guardar relacion en Redis
                    return leagueTeamRepository.addTeamToLeague(userId, realLeagueId, UUID.fromString(worldTeamId))
                            .then(snapshotService.saveSnapshot(snapshot));
                });
    }

    /**
     * Remueve un equipo de una liga
     * Actualiza tanto el WorldSnapshot como Redis
     */
    public Mono<WorldSnapshot> removeTeamFromLeague(UUID userId, UUID realLeagueId, String worldTeamId) {


        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    // Validar que el equipo existe
                    WorldTeam team = snapshot.getWorldTeam(worldTeamId);
                    if (team == null) {
                        return Mono.error(new IllegalArgumentException(
                                "Equipo no encontrado: " + worldTeamId));
                    }

                    // Validar que el equipo esta en la liga especificada
                    if (team.getRealLeagueId() == null || !team.getRealLeagueId().equals(realLeagueId)) {
                        return Mono.error(new IllegalArgumentException(
                                "El equipo no pertenece a la liga especificada"));
                    }

                    // Remover equipo de liga en WorldSnapshot
                    team.setRealLeagueId(null);

                    // Remover relacion de Redis
                    return leagueTeamRepository.removeTeamFromLeague(userId, realLeagueId, UUID.fromString(worldTeamId))
                            .then(snapshotService.saveSnapshot(snapshot));
                });
    }
}

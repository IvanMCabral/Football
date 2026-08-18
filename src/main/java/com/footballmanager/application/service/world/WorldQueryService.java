package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * WorldQueryService - Consultas sobre el mundo.
 *
 * Usa BuildWorldViewUseCase para construir la vista on-demand.
 * Principio de Responsabilidad Unica: solo consultas, no modificaciones.
 *
 * Nota: Los datos vienen de SQL (base) + Redis (custom del usuario).
 */
@Service
@RequiredArgsConstructor
public class WorldQueryService {

    private final BuildWorldViewUseCase buildWorldViewUseCase;
    private final WorldSnapshotRepository worldSnapshotRepository;
    private final LoadBaseDataService loadBaseDataService;

    /**
     * Obtiene todas las ligas
     */
    public Mono<List<WorldLeague>> getLeagues(UUID userId) {
        return worldViewForQuery(userId)
                .map(WorldView::leagues);
    }

    /**
     * Obtiene todos los equipos de una liga
     */
    public Mono<List<WorldTeam>> getTeamsByLeague(UUID userId, UUID leagueId) {
        return loadTeamsForQuery(userId)
                .map(teams -> teams.stream()
                        .filter(team -> leagueId.equals(team.getRealLeagueId()))
                .toList());
    }

    /** Canonical-only projection for callers that already established no snapshot exists. */
    public Mono<List<WorldTeam>> getCanonicalTeamsByLeague(UUID leagueId) {
        return loadCanonicalDataTeamsByLeague(leagueId);
    }

    private Mono<List<WorldTeam>> loadCanonicalDataTeamsByLeague(UUID leagueId) {
        return loadBaseDataService.loadCanonicalTeams()
                .map(teams -> teams.stream()
                        .filter(team -> leagueId.equals(team.getRealLeagueId()))
                        .toList());
    }

    /**
     * Obtiene todos los WorldTeams
     */
    public Mono<List<WorldTeam>> getAllTeams(UUID userId) {
        return loadTeamsForQuery(userId);
    }

    /**
     * Obtiene todos los equipos para el editor (incluye equipos sin liga).
     * Util para mostrar equipos en "Manage Players and Teams".
     */
    public Mono<List<WorldTeam>> getAllTeamsForEditor(UUID userId) {
        return worldViewForQuery(userId)
                .map(worldView -> {
                    List<WorldTeam> allTeams = worldView.teams();
                    return allTeams;
                });
    }

    /**
     * Obtiene un WorldTeam especifico
     */
    public Mono<WorldTeam> getTeam(UUID userId, String worldTeamId) {
        return worldViewForQuery(userId)
                .map(worldView -> worldView.getTeamById(worldTeamId))
                .filter(team -> team != null)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "WorldTeam no encontrado: " + worldTeamId)));
    }

    /**
     * Obtiene todos los jugadores
     */
    public Mono<List<WorldPlayer>> getAllPlayers(UUID userId) {
        return worldViewForQuery(userId)
                .map(WorldView::players);
    }

    /**
     * Obtiene un jugador especifico
     */
    public Mono<WorldPlayer> getPlayer(UUID userId, String worldPlayerId) {
        return worldViewForQuery(userId)
                .map(worldView -> worldView.players().stream()
                        .filter(p -> p.getWorldPlayerId().equals(worldPlayerId))
                        .findFirst()
                        .orElse(null))
                .filter(player -> player != null)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "WorldPlayer no encontrado: " + worldPlayerId)));
    }

    /**
     * Obtiene todos los jugadores de un equipo
     */
    public Mono<List<WorldPlayer>> getPlayersByTeam(UUID userId, String worldTeamId) {
        return worldViewForQuery(userId)
                .map(worldView -> worldView.getPlayersByTeam(worldTeamId));
    }

    /**
     * Obtiene jugadores libres (sin equipo)
     */
    public Mono<List<WorldPlayer>> getFreePlayers(UUID userId) {
        return worldViewForQuery(userId)
                .map(worldView -> {
                    if (worldView.players() == null) {
                        return Collections.<WorldPlayer>emptyList();
                    }
                    return worldView.players().stream()
                            .filter(p -> p.getWorldTeamId() == null || p.getWorldTeamId().isEmpty())
                            .toList();
        });
    }

    /**
     * Builds the read model used by catalog/query endpoints without creating
     * an owner snapshot for a newly registered manager.  Query projections
     * must not turn a harmless catalog read into a large Redis write; the
     * snapshot is created only by a command that actually needs owner state.
     * Existing owners retain the overlay-aware world-view path.
     */
    public Mono<WorldView> worldViewForQuery(UUID userId) {
        return worldSnapshotRepository.existsByUserId(userId)
                .flatMap(exists -> exists
                        ? buildWorldViewUseCase.build(userId)
                : loadBaseDataService.loadCanonical(userId).map(base -> new WorldView(
                                userId,
                                base.leagues(),
                                base.teams(),
                                base.players(),
                                new java.util.HashMap<>())));
    }

    /**
     * Catalog reads retain the owner snapshot's custom-team semantics when a
     * snapshot already exists, but never create one as a side effect.  New
     * owners use the narrow canonical teams projection instead of hydrating
     * the player-inclusive world view.
     */
    private Mono<List<WorldTeam>> loadTeamsForQuery(UUID userId) {
        return worldSnapshotRepository.findByUserId(userId)
                .filter(snapshot -> snapshot.getWorldTeams() != null
                        && !snapshot.getWorldTeams().isEmpty())
                .map(WorldSnapshot::getAllWorldTeams)
                .switchIfEmpty(Mono.defer(loadBaseDataService::loadCanonicalTeams));
    }
}

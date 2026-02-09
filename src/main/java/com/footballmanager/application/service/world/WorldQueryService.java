package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
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

    /**
     * Obtiene todas las ligas
     */
    public Mono<List<WorldLeague>> getLeagues(UUID userId) {
        return buildWorldViewUseCase.build(userId)
                .map(WorldView::leagues);
    }

    /**
     * Obtiene todos los equipos de una liga
     */
    public Mono<List<WorldTeam>> getTeamsByLeague(UUID userId, UUID leagueId) {
        return buildWorldViewUseCase.build(userId)
                .map(worldView -> worldView.getTeamsByLeague(leagueId));
    }

    /**
     * Obtiene todos los WorldTeams
     */
    public Mono<List<WorldTeam>> getAllTeams(UUID userId) {
        return buildWorldViewUseCase.build(userId)
                .map(WorldView::teams);
    }

    /**
     * Obtiene todos los equipos para el editor (incluye equipos sin liga).
     * Util para mostrar equipos en "Manage Players and Teams".
     */
    public Mono<List<WorldTeam>> getAllTeamsForEditor(UUID userId) {
        return buildWorldViewUseCase.build(userId)
                .map(worldView -> {
                    List<WorldTeam> allTeams = worldView.teams();
                    return allTeams;
                });
    }

    /**
     * Obtiene un WorldTeam especifico
     */
    public Mono<WorldTeam> getTeam(UUID userId, String worldTeamId) {
        return buildWorldViewUseCase.build(userId)
                .map(worldView -> worldView.getTeamById(worldTeamId))
                .filter(team -> team != null)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "WorldTeam no encontrado: " + worldTeamId)));
    }

    /**
     * Obtiene todos los jugadores
     */
    public Mono<List<WorldPlayer>> getAllPlayers(UUID userId) {
        return buildWorldViewUseCase.build(userId)
                .map(WorldView::players);
    }

    /**
     * Obtiene un jugador especifico
     */
    public Mono<WorldPlayer> getPlayer(UUID userId, String worldPlayerId) {
        return buildWorldViewUseCase.build(userId)
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
        return buildWorldViewUseCase.build(userId)
                .map(worldView -> worldView.getPlayersByTeam(worldTeamId));
    }

    /**
     * Obtiene jugadores libres (sin equipo)
     */
    public Mono<List<WorldPlayer>> getFreePlayers(UUID userId) {
        return buildWorldViewUseCase.build(userId)
                .map(worldView -> {
                    if (worldView.players() == null) {
                        return Collections.<WorldPlayer>emptyList();
                    }
                    return worldView.players().stream()
                            .filter(p -> p.getWorldTeamId() == null || p.getWorldTeamId().isEmpty())
                            .toList();
                });
    }
}

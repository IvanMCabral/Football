package com.footballmanager.domain.model.view;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WorldView - Vista en memoria del mundo para un usuario.
 *
 * Esta clase representa una FOTO del mundo en un momento específico.
 * Se construye on-demand desde SQL (datos base) + Redis (datos custom del usuario).
 *
 * NO se persiste - es un POJO que vive en memoria durante la request.
 * NO es un snapshot de carrera - para eso existe CareerSnapshot.
 *
 * Principios:
 * - Inmutable (record)
 * - Sin lógica de negocio (solo datos)
 * - Construido por BuildWorldViewUseCase
 */
public record WorldView(
    UUID userId,
    List<WorldLeague> leagues,
    List<WorldTeam> teams,
    List<WorldPlayer> players,
    Map<UUID, List<UUID>> leagueTeams
) {
    /**
     * Factory method para crear WorldView vacío.
     */
    public static WorldView empty(UUID userId) {
        return new WorldView(userId, List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Obtiene equipos de una liga específica.
     * Filtra por realLeagueId directamente (más confiable que el mapa).
     */
    public List<WorldTeam> getTeamsByLeague(UUID leagueId) {
        return teams.stream()
                .filter(team -> leagueId.equals(team.getRealLeagueId()))
                .toList();
    }

    /**
     * Obtiene jugadores de un equipo específico.
     */
    public List<WorldPlayer> getPlayersByTeam(String worldTeamId) {
        return players.stream()
                .filter(player -> worldTeamId.equals(player.getWorldTeamId()))
                .toList();
    }

    /**
     * Obtiene un equipo por su ID.
     */
    public WorldTeam getTeamById(String worldTeamId) {
        return teams.stream()
                .filter(team -> worldTeamId.equals(team.getWorldTeamId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtiene una liga por su ID.
     */
    public WorldLeague getLeagueById(UUID leagueId) {
        return leagues.stream()
                .filter(league -> leagueId.equals(league.getRealLeagueId()))
                .findFirst()
                .orElse(null);
    }
}

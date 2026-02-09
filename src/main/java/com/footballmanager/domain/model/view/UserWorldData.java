package com.footballmanager.domain.model.view;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UserWorldData - Datos custom del usuario en Redis.
 *
 * Representa TODOS los datos que el usuario ha creado o modificado:
 * - Equipos custom
 * - Jugadores custom
 * - Relaciones custom (league-team agregadas por el usuario)
 *
 * Esta clase es un DTO que se serializa a Redis.
 * NO es un WorldView - es la persistencia de cambios del usuario.
 */
public record UserWorldData(
    Map<String, WorldTeam> customTeams,
    Map<String, WorldPlayer> customPlayers,
    List<CustomRelation> customRelations
) {
    /**
     * Factory method para crear UserWorldData vacío.
     */
    public static UserWorldData empty() {
        return new UserWorldData(
            new HashMap<>(),
            new HashMap<>(),
            new ArrayList<>()
        );
    }

    /**
     * Agrega un equipo custom.
     */
    public UserWorldData withAddedTeam(WorldTeam team) {
        Map<String, WorldTeam> newTeams = new HashMap<>(customTeams);
        newTeams.put(team.getWorldTeamId(), team);
        return new UserWorldData(newTeams, customPlayers, customRelations);
    }

    /**
     * Agrega un jugador custom.
     */
    public UserWorldData withAddedPlayer(WorldPlayer player) {
        Map<String, WorldPlayer> newPlayers = new HashMap<>(customPlayers);
        newPlayers.put(player.getWorldPlayerId(), player);
        return new UserWorldData(customTeams, newPlayers, customRelations);
    }

    /**
     * Agrega una relación custom.
     */
    public UserWorldData withAddedRelation(CustomRelation relation) {
        List<CustomRelation> newRelations = new ArrayList<>(customRelations);
        newRelations.add(relation);
        return new UserWorldData(customTeams, customPlayers, newRelations);
    }

    /**
     * Obtiene el conteo total de entidades custom.
     */
    public int getTotalCustomCount() {
        return customTeams.size() + customPlayers.size();
    }

    /**
     * Verifica si tiene datos custom.
     */
    public boolean isEmpty() {
        return customTeams.isEmpty() && customPlayers.isEmpty() && customRelations.isEmpty();
    }

    /**
     * CustomRelation - Representa una relación league-team creada por el usuario.
     *
     * @param leagueId ID de la liga
     * @param teamId ID del equipo
     * @param addedAt Timestamp de cuando se agregó
     */
    public record CustomRelation(
        UUID leagueId,
        UUID teamId,
        long addedAt
    ) {
        /**
         * Factory method para crear una nueva relación.
         */
        public static CustomRelation create(UUID leagueId, UUID teamId) {
            return new CustomRelation(leagueId, teamId, System.currentTimeMillis());
        }
    }
}

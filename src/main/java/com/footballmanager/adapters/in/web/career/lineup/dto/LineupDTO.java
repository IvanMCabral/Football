package com.footballmanager.adapters.in.web.career.lineup.dto;

import java.util.List;

/**
 * DTO para el Starting XI completo
 */
public record LineupDTO(
    String formation,
    List<PlayerLineupDTO> players,
    boolean confirmed
) {

    public LineupDTO {
        // Solo validar si hay jugadores seleccionados
        if (players != null && !players.isEmpty() && players.size() != 11) {
            throw new IllegalArgumentException("Lineup must have exactly 11 players, found: " + players.size());
        }
    }
}

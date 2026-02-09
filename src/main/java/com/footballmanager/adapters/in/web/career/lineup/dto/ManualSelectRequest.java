package com.footballmanager.adapters.in.web.career.lineup.dto;

import java.util.List;

/**
 * Request para seleccionar manualmente el Starting XI
 */
public record ManualSelectRequest(
    String formation,
    List<String> playerIds
) {

    public ManualSelectRequest {
        if (formation == null || formation.isBlank()) {
            throw new IllegalArgumentException("Formation is required");
        }
        if (playerIds == null || playerIds.isEmpty()) {
            throw new IllegalArgumentException("Player IDs are required");
        }
        if (playerIds.size() != 11) {
            throw new IllegalArgumentException("Must select exactly 11 players");
        }
    }
}

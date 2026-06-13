package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.application.service.lineup.LineupRules;

import java.util.List;

/**
 * DTO para el Starting XI completo.
 *
 * <p>V24D6U2: size is now in {@code [MIN_AVAILABLE_PLAYERS, MAX_LINEUP_PLAYERS]}.
 * An empty list is still permitted (used as the empty state when no lineup
 * has been saved yet). When the lineup is below
 * {@code TARGET_LINEUP_PLAYERS}, the response carries a non-empty
 * {@code warnings} list.
 */
public record LineupDTO(
    String formation,
    List<PlayerLineupDTO> players,
    boolean confirmed,
    List<LineupWarningDTO> warnings
) {

    public LineupDTO(String formation, List<PlayerLineupDTO> players, boolean confirmed) {
        this(formation, players, confirmed, List.of());
    }

    public LineupDTO {
        if (players == null) {
            players = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
        // Only validate size when non-empty. Empty list is the "no lineup saved" state.
        if (!players.isEmpty()) {
            int size = players.size();
            if (size < LineupRules.MIN_AVAILABLE_PLAYERS) {
                throw new IllegalArgumentException(
                    "Lineup must have at least " + LineupRules.MIN_AVAILABLE_PLAYERS
                    + " players, found: " + size);
            }
            if (size > LineupRules.MAX_LINEUP_PLAYERS) {
                throw new IllegalArgumentException(
                    "Lineup must have at most " + LineupRules.MAX_LINEUP_PLAYERS
                    + " players, found: " + size);
            }
        }
        // Defensive copy of mutable lists to preserve immutability of the record
        players = List.copyOf(players);
        warnings = List.copyOf(warnings);
    }
}

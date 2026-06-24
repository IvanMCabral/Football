package com.footballmanager.adapters.in.web.career.lineup.dto;

import java.util.List;

/**
 * Request para seleccionar manualmente el Starting XI.
 *
 * <p>Body backward compat:
 * <ul>
 *   <li>Forma legacy: {@code { formation, playerIds: ["p1", ...] }} — el back
 *       acepta solo playerIds (la subdivision se infiere on-the-fly del role).</li>
 *   <li>Forma nueva (MVP1-lineup-cancha-1): {@code { formation, playerIds, slots: [...] }} —
 *       el back persiste la subdivisionId por jugador.</li>
 * </ul>
 *
 * <p>{@code slots} es opcional; si está presente y tiene subdivisionId válida,
 * se persiste. Si está vacío o ausente, se usa backward compat.
 */
public record ManualSelectRequest(
    String formation,
    List<String> playerIds,
    List<LineupSlotDTO> slots
) {

    /** Compact constructor para callers que vienen del flow legacy (sin slots). */
    public ManualSelectRequest(String formation, List<String> playerIds) {
        this(formation, playerIds, null);
    }

    public ManualSelectRequest {
        if (formation == null || formation.isBlank()) {
            throw new IllegalArgumentException("Formation is required");
        }
        if (playerIds == null || playerIds.isEmpty()) {
            throw new IllegalArgumentException("Player IDs are required");
        }
        if (playerIds.size() < 7) {
            throw new IllegalArgumentException("Must select at least 7 players");
        }
        if (playerIds.size() > 11) {
            throw new IllegalArgumentException("Must select at most 11 players");
        }
        // slots es opcional — null o vacío = backward compat
        if (slots == null) {
            slots = List.of();
        }
    }
}

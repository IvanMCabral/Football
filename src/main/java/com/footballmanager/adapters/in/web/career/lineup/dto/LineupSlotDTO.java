package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * Slot persistido en Lineup: player + subdivision (slot) del campo.
 *
 * <p>Nueva shape introducida por MVP1-lineup-cancha-1.
 * Antes solo se persistía {@code List<String>} de player IDs.
 *
 * <p>Si {@code subdivisionId} es null/blank, el back infiere el slot del
 * role del jugador según la formación (backward compat con lineups viejos).
 */
public record LineupSlotDTO(
    String playerId,
    String subdivisionId
) {
}
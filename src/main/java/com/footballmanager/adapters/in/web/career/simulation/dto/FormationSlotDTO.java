package com.footballmanager.adapters.in.web.career.simulation.dto;

/**
 * LIVE-MATCH-F2-LIVE F5 (B4): a single slot in a tactical formation.
 *
 * <p>Each slot pairs a player with a position. The combination of slots
 * constitutes a tactical formation (10 outfield + 1 GK = 11 players).
 * Used by {@link FormationChangeRequestDTO} to describe the manager's
 * desired formation when changing tactics mid-match.
 *
 * <p>The position is a free-form string (e.g. "GK", "DEF", "MID", "WINGER",
 * "ATT") — it matches the {@code V24PlayerMatchState.position} convention
 * (String, not enum) used by the engine.
 */
public record FormationSlotDTO(
    String playerId,
    String position
) {}

package com.footballmanager.adapters.in.web.dashboard.dto;

/**
 * Estado del mundo del usuario logueado.
 * DTO compatible con frontend: clubs, players, matches.
 */
public record WorldStatusResponse(
        int clubs,
        int players,
        int matches
) {}

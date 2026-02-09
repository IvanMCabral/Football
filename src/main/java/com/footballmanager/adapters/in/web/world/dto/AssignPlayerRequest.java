package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request para asignar jugador a equipo
 */
public record AssignPlayerRequest(UUID userId, String playerId, String teamId) {}

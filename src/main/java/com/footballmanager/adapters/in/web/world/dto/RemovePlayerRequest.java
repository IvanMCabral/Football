package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request para remover jugador
 */
public record RemovePlayerRequest(UUID userId, String playerId) {}

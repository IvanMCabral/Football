package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request para batch de creación de jugadores
 */
public record BatchPlayerCreationRequest(UUID userId, Integer count) {}

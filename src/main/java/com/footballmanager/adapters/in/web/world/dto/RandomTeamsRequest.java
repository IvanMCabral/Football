package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request para obtener equipos random
 */
public record RandomTeamsRequest(UUID userId, Integer count) {}

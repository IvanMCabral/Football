package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request para inicializar WorldSnapshot
 */
public record InitializeWorldRequest(UUID userId) {}

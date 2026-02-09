package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request con solo UUID (para endpoints que solo necesitan userId)
 */
public record UUIDRequest(UUID userId) {}

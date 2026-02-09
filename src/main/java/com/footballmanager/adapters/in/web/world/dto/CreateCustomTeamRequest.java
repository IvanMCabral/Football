package com.footballmanager.adapters.in.web.world.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request para crear un equipo custom
 */
public record CreateCustomTeamRequest(
    UUID userId,
    String name,
    String country,
    BigDecimal budget,
    String formation
) {}

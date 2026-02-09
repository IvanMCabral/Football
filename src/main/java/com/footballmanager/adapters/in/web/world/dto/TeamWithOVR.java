package com.footballmanager.adapters.in.web.world.dto;

import java.math.BigDecimal;

/**
 * Team con OVR promedio para preview de divisiones
 */
public record TeamWithOVR(
    String worldTeamId,
    String name,
    String country,
    String formation,
    int ovr,
    int playerCount,
    BigDecimal budget
) {}

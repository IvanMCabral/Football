package com.footballmanager.adapters.in.web.world.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request para crear un jugador custom
 */
public record CreateCustomPlayerRequest(
    UUID userId,
    String name,
    Integer age,
    String position,
    Integer attack,
    Integer defense,
    Integer technique,
    Integer speed,
    Integer stamina,
    Integer mentality,
    BigDecimal marketValue
) {}

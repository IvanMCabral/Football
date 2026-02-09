package com.footballmanager.adapters.in.web.career.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for session team data.
 */
public record SessionTeamDTO(
        String sessionTeamId,
        UUID baseTeamId,
        String name,
        String country,
        BigDecimal budget,
        String formation,
        Integer morale,
        Integer reputation,
        String origin
) {}

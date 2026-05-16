package com.footballmanager.adapters.in.web.career.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for session player data.
 */
public record SessionPlayerDTO(
        String sessionPlayerId,
        UUID basePlayerId,
        String name,
        Integer age,
        String position,
        Integer attack,
        Integer defense,
        Integer technique,
        Integer speed,
        Integer stamina,
        Integer mentality,
        BigDecimal marketValue,
        Integer energy,
        Integer form,
        Boolean injured,
        String injuryType,
        Integer injuryRemainingMatches,
        String origin,
        Integer overall,
        Integer yellowCards,
        Integer redCards,
        Boolean suspended,
        Integer suspensionRemainingMatches
) {}
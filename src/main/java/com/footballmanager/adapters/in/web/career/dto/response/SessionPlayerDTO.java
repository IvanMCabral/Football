package com.footballmanager.adapters.in.web.career.dto.response;

import java.math.BigDecimal;
import java.util.List;
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
        Integer suspensionRemainingMatches,
        List<SpecialTraitDTO> specialTraits
) {
    public SessionPlayerDTO(
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
    ) {
        this(
                sessionPlayerId,
                basePlayerId,
                name,
                age,
                position,
                attack,
                defense,
                technique,
                speed,
                stamina,
                mentality,
                marketValue,
                energy,
                form,
                injured,
                injuryType,
                injuryRemainingMatches,
                origin,
                overall,
                yellowCards,
                redCards,
                suspended,
                suspensionRemainingMatches,
                List.of());
    }

    public record SpecialTraitDTO(
            String code,
            String name,
            String description
    ) {
    }
}

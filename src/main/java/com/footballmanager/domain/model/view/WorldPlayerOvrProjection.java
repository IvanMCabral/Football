package com.footballmanager.domain.model.view;

import com.footballmanager.domain.service.WorldPlayerOvrCalculator;

import java.util.UUID;

/**
 * Narrow canonical player projection required by the WorldPlayer OVR read.
 * It deliberately excludes traits, height, age, career state, and full world
 * entity state because none of those fields participate in WorldPlayer OVR.
 */
public record WorldPlayerOvrProjection(
        UUID teamId,
        String position,
        Integer baseAttack,
        Integer baseDefense,
        Integer baseTechnique,
        Integer baseSpeed,
        Integer baseStamina,
        Integer baseMentality
) {
    public int calculateOverall() {
        return WorldPlayerOvrCalculator.calculate(
                baseAttack,
                baseDefense,
                baseTechnique,
                baseSpeed,
                baseStamina,
                baseMentality,
                position);
    }
}

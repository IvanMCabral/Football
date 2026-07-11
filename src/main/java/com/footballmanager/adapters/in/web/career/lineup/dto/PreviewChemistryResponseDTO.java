package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.valueobject.ChemistryDetail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backward-compatible response for /preview-chemistry.
 *
 * <p>Preserves the old ChemistryDetail JSON shape and adds tactical data
 * inside {@code breakdown.tacticalChemistry} when slots are supplied.
 */
public record PreviewChemistryResponseDTO(
        int score,
        ChemistryBreakdownDTO breakdown,
        Map<String, Integer> maxSkillByType,
        int coveragePercentage
) {
    public static PreviewChemistryResponseDTO from(
            ChemistryDetail detail,
            ChemistryBreakdownDTO breakdown
    ) {
        Map<String, Integer> maxSkillByType = new LinkedHashMap<>();
        detail.maxSkillByType().forEach((skill, value) -> maxSkillByType.put(skill.name(), value));
        return new PreviewChemistryResponseDTO(
                detail.score(),
                breakdown,
                maxSkillByType,
                detail.coveragePercentage());
    }
}

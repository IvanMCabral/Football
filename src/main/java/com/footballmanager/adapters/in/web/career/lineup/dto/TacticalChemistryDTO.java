package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.valueobject.TacticalChemistry;

import java.util.List;
import java.util.Map;

/**
 * Wire DTO for spatial/player-link chemistry.
 */
public record TacticalChemistryDTO(
        int score,
        Map<String, Integer> lineScores,
        Map<String, Integer> channelScores,
        List<LinkDTO> links,
        List<String> warnings
) {
    public record LinkDTO(
            String fromPlayerId,
            String toPlayerId,
            String type,
            int score,
            double distance,
            String note
    ) {}

    public static TacticalChemistryDTO from(TacticalChemistry domain) {
        if (domain == null) {
            return null;
        }
        return new TacticalChemistryDTO(
                domain.score(),
                domain.lineScores(),
                domain.channelScores(),
                domain.links().stream()
                        .map(l -> new LinkDTO(
                                l.fromPlayerId(),
                                l.toPlayerId(),
                                l.type(),
                                l.score(),
                                l.distance(),
                                l.note()))
                        .toList(),
                domain.warnings());
    }
}

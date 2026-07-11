package com.footballmanager.domain.model.valueobject;

import java.util.List;
import java.util.Map;

/**
 * V25D99.20.13-BACK: spatial/link chemistry for the squad screen.
 *
 * <p>The classic {@link TeamChemistryCalculator} answers "how talented and
 * skill-covered is this XI?". This record answers the more football-specific
 * question: "how well are the players connected in the current tactical
 * drawing?". It is intentionally read-only and preview-friendly.
 */
public record TacticalChemistry(
        int score,
        Map<String, Integer> lineScores,
        Map<String, Integer> channelScores,
        List<Link> links,
        List<String> warnings
) {
    public record Link(
            String fromPlayerId,
            String toPlayerId,
            String type,
            int score,
            double distance,
            String note
    ) {}
}

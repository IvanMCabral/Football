package com.footballmanager.domain.model.valueobject;

import java.io.Serializable;

/**
 * Tactical placement of a player in a persisted lineup.
 *
 * <p>The player id identifies who occupies the slot. The subdivision id keeps
 * compatibility with grid-based formations, while custom coordinates preserve
 * free pixel-level movement from the tactical editor.</p>
 */
public record LineupSlot(
    String playerId,
    String subdivisionId,
    Double customXPercent,
    Double customYPercent
) implements Serializable {

    public LineupSlot(String playerId, String subdivisionId) {
        this(playerId, subdivisionId, null, null);
    }
}

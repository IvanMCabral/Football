package com.footballmanager.domain.model.valueobject;

import java.io.Serializable;

/**
 * Tactical live slot used by match state snapshots.
 */
public record FormationSlot(
    String playerId,
    String position,
    Integer slotIndex,
    Double customXPercent,
    Double customYPercent
) implements Serializable {

    public FormationSlot(String playerId, String position) {
        this(playerId, position, null, null, null);
    }
}

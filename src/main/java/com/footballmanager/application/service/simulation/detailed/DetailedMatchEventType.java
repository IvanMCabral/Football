package com.footballmanager.application.service.simulation.detailed;

/**
 * Event types for detailed match detailed match timeline.
 * Internal to detailed match engine — not persisted.
 */
public enum DetailedMatchEventType {
    GOAL,
    SHOT,
    SHOT_ON_TARGET,
    SAVE,
    MISS,
    BLOCK,
    CHANCE_CREATED,
    FOUL,
    YELLOW_CARD,
    RED_CARD,
    INJURY,
    CORNER,
    OFFSIDE,
    SUBSTITUTION,
    // mid-match. The event is appended to the timeline (visible to F3 UI) but
    // does NOT carry goals/xG — those are recomputed by LiveSession.replayFromMinute.
    TACTICAL_CHANGE
}

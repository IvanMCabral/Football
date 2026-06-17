package com.footballmanager.application.service.simulation.v24;

/**
 * Event types for V24 detailed match timeline.
 * Internal to V24 engine — not persisted.
 */
public enum V24MatchEventType {
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
    // LIVE-MATCH-F2-LIVE F5: emitted when the manager changes style/formation
    // mid-match. The event is appended to the timeline (visible to F3 UI) but
    // does NOT carry goals/xG — those are recomputed by V24LiveSession.replayFromMinute.
    TACTICAL_CHANGE
}
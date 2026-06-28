package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.application.service.lineup.LineupRules;

/**
 * V24D6U2: Non-fatal warning attached to a {@link LineupDTO}.
 *
 * <p>Warnings inform the UI that the lineup is valid but degraded
 * (e.g. short-handed, no goalkeeper). The match is still playable.
 * For fatal errors (too few players, duplicates, unavailable players)
 * the use case returns 422 instead — warnings are NOT errors.
 */
public record LineupWarningDTO(
    String code,
    String message,
    String severity,
    Integer available,
    Integer minimumRequired,
    Integer target
) {

    public static final String CODE_SHORT_HANDED = "LINEUP_SHORT_HANDED";
    public static final String CODE_NO_GOALKEEPER = "LINEUP_NO_GOALKEEPER";
    // V25D59-C19 P0: warning when auto-select fills a formation row (DEF/MID/ATT)
    // with off-position players because the squad doesn't have enough players
    // for that exact position group. The lineup is still valid (11 slots) but
    // tactically degraded — the engine computes the effectiveness penalty via
    // FormationEffectiveness (sprint C11a) and the UI surfaces it.
    public static final String CODE_OFF_POSITION_FILL = "LINEUP_OFF_POSITION_FILL";

    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_INFO = "INFO";

    public static LineupWarningDTO shortHanded(int available) {
        return new LineupWarningDTO(
            CODE_SHORT_HANDED,
            "Only " + available + " available players. Team will play short-handed.",
            SEVERITY_WARNING,
            available,
            LineupRules.MIN_AVAILABLE_PLAYERS,
            LineupRules.TARGET_LINEUP_PLAYERS
        );
    }

    public static LineupWarningDTO noGoalkeeper(int available) {
        return new LineupWarningDTO(
            CODE_NO_GOALKEEPER,
            "No available goalkeeper. Team will play without a GK.",
            SEVERITY_WARNING,
            available,
            LineupRules.MIN_AVAILABLE_PLAYERS,
            LineupRules.TARGET_LINEUP_PLAYERS
        );
    }

    /**
     * V25D59-C19 P0: off-position fill warning. {@code positionGroup} is one
     * of {@code "GK" | "DEF" | "MID" | "ATT"} — the row of the formation
     * that ended up with off-position players. {@code offPositionCount} is the
     * number of slots in that row filled by players whose natural position
     * doesn't match (e.g., a CM filling a CB slot). The penalty surfaces in
     * {@code formationEffectiveness} (sprint C11a) per-player.
     */
    public static LineupWarningDTO offPositionFill(String positionGroup, int offPositionCount) {
        return new LineupWarningDTO(
            CODE_OFF_POSITION_FILL,
            offPositionCount + " " + positionGroup + " slot" + (offPositionCount == 1 ? "" : "s")
                + " filled by off-position players (effectiveness penalty applied).",
            SEVERITY_WARNING,
            offPositionCount,
            0,
            LineupRules.TARGET_LINEUP_PLAYERS
        );
    }
}

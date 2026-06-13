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
}

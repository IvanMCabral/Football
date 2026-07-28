package com.footballmanager.domain.port.in.lineup;

import com.footballmanager.application.service.lineup.LineupRules;

public record LineupWarning(
    String code,
    String message,
    String severity,
    Integer available,
    Integer minimumRequired,
    Integer target
) {
    public static final String CODE_SHORT_HANDED = "LINEUP_SHORT_HANDED";
    public static final String CODE_NO_GOALKEEPER = "LINEUP_NO_GOALKEEPER";
    public static final String CODE_OFF_POSITION_FILL = "LINEUP_OFF_POSITION_FILL";

    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_INFO = "INFO";

    public static LineupWarning shortHanded(int available) {
        return new LineupWarning(
            CODE_SHORT_HANDED,
            "Only " + available + " available players. Team will play short-handed.",
            SEVERITY_WARNING,
            available,
            LineupRules.MIN_AVAILABLE_PLAYERS,
            LineupRules.TARGET_LINEUP_PLAYERS);
    }

    public static LineupWarning noGoalkeeper(int available) {
        return new LineupWarning(
            CODE_NO_GOALKEEPER,
            "No goalkeeper available. Team will play without a natural GK.",
            SEVERITY_WARNING,
            available,
            LineupRules.MIN_AVAILABLE_PLAYERS,
            LineupRules.TARGET_LINEUP_PLAYERS);
    }

    public static LineupWarning offPositionFill(String positionGroup, int count) {
        return new LineupWarning(
            CODE_OFF_POSITION_FILL,
            count + " " + positionGroup + " slot(s) filled by off-position players.",
            SEVERITY_INFO,
            count,
            null,
            null);
    }
}

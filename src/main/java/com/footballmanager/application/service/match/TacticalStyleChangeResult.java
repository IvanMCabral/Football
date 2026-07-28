package com.footballmanager.application.service.match;

import com.footballmanager.application.service.domain.TeamStyle;

public record TacticalStyleChangeResult(
    boolean success,
    int minuteApplied,
    TeamStyle currentStyle,
    String error
) {
    public static TacticalStyleChangeResult ok(int minuteApplied, TeamStyle currentStyle) {
        return new TacticalStyleChangeResult(true, minuteApplied, currentStyle, null);
    }

    public static TacticalStyleChangeResult error(String errorMessage) {
        return new TacticalStyleChangeResult(false, 0, null, errorMessage);
    }
}

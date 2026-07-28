package com.footballmanager.application.service.match;

import java.util.List;

public record TacticalFormationChangeResult(
    boolean success,
    int minuteApplied,
    List<TacticalFormationSlot> currentFormation,
    String error
) {
    public static TacticalFormationChangeResult ok(int minuteApplied, List<TacticalFormationSlot> currentFormation) {
        return new TacticalFormationChangeResult(true, minuteApplied, currentFormation, null);
    }

    public static TacticalFormationChangeResult error(String errorMessage) {
        return new TacticalFormationChangeResult(false, 0, null, errorMessage);
    }
}

package com.footballmanager.domain.service;

import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Domain ordering for competitive session teams.
 */
public final class SessionTeamRankingPolicy {

    private SessionTeamRankingPolicy() {
    }

    public static Comparator<SessionTeam> byStrengthBudgetAndName(
            Function<String, Integer> ovrProvider) {
        return (a, b) -> {
            int ovrA = ovrProvider.apply(a.getSessionTeamId());
            int ovrB = ovrProvider.apply(b.getSessionTeamId());
            if (ovrA != ovrB) {
                return Integer.compare(ovrB, ovrA);
            }
            int budgetCompare = b.getBudget().compareTo(a.getBudget());
            if (budgetCompare != 0) {
                return budgetCompare;
            }
            return a.getName().compareTo(b.getName());
        };
    }
}

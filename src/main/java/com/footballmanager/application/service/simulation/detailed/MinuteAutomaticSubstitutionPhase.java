package com.footballmanager.application.service.simulation.detailed;

final class MinuteAutomaticSubstitutionPhase {
    private final MinuteSubstitutionPolicies substitutionPolicies;

    MinuteAutomaticSubstitutionPhase(MinuteSubstitutionPolicies substitutionPolicies) {
        this.substitutionPolicies = substitutionPolicies;
    }

    void apply(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        int minute = minuteContext.minute();
        if (minute >= 60
                && !minuteContext.homeState().startingPlayers().isEmpty()
                && substitutionPolicies.automaticSubstitutionEngine()
                .hasSubstitutionsRemaining(minuteContext.matchContext().homeTeamId())
                && !possession.homeHasPossession()) {
            substitutionPolicies.automaticSubstitutionEngine().attemptSubstitution(minuteContext.homeState(), minute)
                    .ifPresent(e -> minuteContext.timeline().addEvent(e));
        }
        if (minute >= 60
                && !minuteContext.awayState().startingPlayers().isEmpty()
                && substitutionPolicies.automaticSubstitutionEngine()
                .hasSubstitutionsRemaining(minuteContext.matchContext().awayTeamId())
                && possession.homeHasPossession()) {
            substitutionPolicies.automaticSubstitutionEngine().attemptSubstitution(minuteContext.awayState(), minute)
                    .ifPresent(e -> minuteContext.timeline().addEvent(e));
        }
    }
}

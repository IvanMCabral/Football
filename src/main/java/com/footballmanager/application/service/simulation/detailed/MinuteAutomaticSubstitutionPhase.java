package com.footballmanager.application.service.simulation.detailed;

final class MinuteAutomaticSubstitutionPhase {
    private final DetailedMatchMinuteSupport support;

    MinuteAutomaticSubstitutionPhase(DetailedMatchMinuteSupport support) {
        this.support = support;
    }

    void apply(MinuteSimulationContext minuteContext, MinutePossessionState possession) {
        int minute = minuteContext.minute();
        if (minute >= 60
                && !minuteContext.homeState().startingPlayers().isEmpty()
                && support.substitutionEngine.hasSubstitutionsRemaining(minuteContext.matchContext().homeTeamId())
                && !possession.homeHasPossession()) {
            support.substitutionEngine.attemptSubstitution(minuteContext.homeState(), minute)
                    .ifPresent(e -> minuteContext.timeline().addEvent(e));
        }
        if (minute >= 60
                && !minuteContext.awayState().startingPlayers().isEmpty()
                && support.substitutionEngine.hasSubstitutionsRemaining(minuteContext.matchContext().awayTeamId())
                && possession.homeHasPossession()) {
            support.substitutionEngine.attemptSubstitution(minuteContext.awayState(), minute)
                    .ifPresent(e -> minuteContext.timeline().addEvent(e));
        }
    }
}

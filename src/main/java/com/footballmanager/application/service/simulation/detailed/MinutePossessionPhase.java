package com.footballmanager.application.service.simulation.detailed;

final class MinutePossessionPhase {
    private final MinutePlayerStatePolicies playerStatePolicies;

    MinutePossessionPhase(MinutePlayerStatePolicies playerStatePolicies) {
        this.playerStatePolicies = playerStatePolicies;
    }

    MinutePossessionState apply(MinuteSimulationContext minuteContext, MinuteTacticalState tacticalState) {
        boolean homeHasPossession = minuteContext.random().nextDouble() < tacticalState.homeShare();
        TeamMatchState possessor = homeHasPossession ? minuteContext.homeState() : minuteContext.awayState();
        TeamMatchState opponent = homeHasPossession ? minuteContext.awayState() : minuteContext.homeState();
        PlayerSelector selector = homeHasPossession ? minuteContext.homeSelector() : minuteContext.awaySelector();
        String teamRole = homeHasPossession ? minuteContext.matchContext().homeTeamId() : minuteContext.matchContext().awayTeamId();
        String formation = homeHasPossession ? minuteContext.matchContext().homeFormation() : minuteContext.matchContext().awayFormation();
        String opponentFormation = homeHasPossession ? minuteContext.matchContext().awayFormation() : minuteContext.matchContext().homeFormation();
        possessor.addPossessionTick();
        applyMinuteDrain(minuteContext.homeState(), minuteContext.matchContext().homeStyle());
        applyMinuteDrain(minuteContext.awayState(), minuteContext.matchContext().awayStyle());
        return new MinutePossessionState(
                homeHasPossession,
                possessor,
                opponent,
                selector,
                teamRole,
                formation,
                opponentFormation,
                homeHasPossession ? tacticalState.homeShape() : tacticalState.awayShape(),
                homeHasPossession ? tacticalState.awayShape() : tacticalState.homeShape(),
                homeHasPossession ? tacticalState.homeEffectiveSlots() : tacticalState.awayEffectiveSlots(),
                homeHasPossession ? tacticalState.awayEffectiveSlots() : tacticalState.homeEffectiveSlots());
    }

    private void applyMinuteDrain(TeamMatchState team, com.footballmanager.domain.model.valueobject.TeamStyle style) {
        int baseDrain = playerStatePolicies.fatigueModel().baseDrainPerMinute(style);
        for (PlayerMatchState p : team.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard()) {
                playerStatePolicies.fatigueModel().applyDrain(p, baseDrain);
            }
        }
    }
}

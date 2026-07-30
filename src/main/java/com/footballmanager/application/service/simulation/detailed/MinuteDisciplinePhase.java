package com.footballmanager.application.service.simulation.detailed;

final class MinuteDisciplinePhase {
    private final MinutePlayerStatePolicies playerStatePolicies;
    private final MinuteEventPolicies eventPolicies;

    MinuteDisciplinePhase(MinutePlayerStatePolicies playerStatePolicies, MinuteEventPolicies eventPolicies) {
        this.playerStatePolicies = playerStatePolicies;
        this.eventPolicies = eventPolicies;
    }

    void apply(MinuteSimulationContext minuteContext, MinutePossessionState possession) {
        var potentialFouler = possession.selector().selectShooter(
                possession.possessor().startingPlayers(), possession.formation());
        if (potentialFouler.isEmpty()) {
            return;
        }
        PlayerMatchState f = potentialFouler.get();
        boolean defending = !possession.homeHasPossession();
        if (playerStatePolicies.disciplineModel().shouldCommitFoul(
                f, possession.possessor().style(), defending, minuteContext.random())) {
            minuteContext.timeline().addEvent(new DetailedMatchEvent(
                    minuteContext.minute(),
                    DetailedMatchEventType.FOUL,
                    possession.teamRole(),
                    f.sessionPlayerId(),
                    f.name(),
                    null, null,
                    0.0,
                    f.name() + " committed a foul"
            ));
            playerStatePolicies.fatigueModel().applyDrain(f, 5);
            if (playerStatePolicies.disciplineModel().shouldReceiveYellow(
                    f, possession.possessor().style(), minuteContext.random())
                    && !f.redCard()) {
                eventPolicies.cardEventService().applyYellowCardAndMaybeSecondYellowRed(
                        f, minuteContext.timeline(), minuteContext.minute(), possession.teamRole());
            }
        }
    }

    void applyYellowCardAndMaybeSecondYellowRed(
            PlayerMatchState player,
            MatchTimeline timeline,
            int minute,
            String teamRole) {
        eventPolicies.cardEventService().applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
    }
}

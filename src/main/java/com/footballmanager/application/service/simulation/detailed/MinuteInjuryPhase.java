package com.footballmanager.application.service.simulation.detailed;

final class MinuteInjuryPhase {
    private final InjuryModel injuryModel;

    MinuteInjuryPhase(InjuryModel injuryModel) {
        this.injuryModel = java.util.Objects.requireNonNull(injuryModel, "injuryModel must not be null");
    }

    void apply(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        var potentialInjured = possession.selector().selectShooter(
                possession.possessor().startingPlayers(), possession.formation());
        if (potentialInjured.isEmpty()) {
            return;
        }

        PlayerMatchState player = potentialInjured.get();
        if (!injuryModel.shouldInjure(
                player, possession.possessor().style(), false, minuteContext.random())) {
            return;
        }

        player.injure();
        minuteContext.timeline().addEvent(new DetailedMatchEvent(
                minuteContext.minute(),
                DetailedMatchEventType.INJURY,
                possession.teamRole(),
                player.sessionPlayerId(),
                player.name(),
                null, null,
                0.0,
                player.name() + " was injured"
        ));
    }
}

package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;

final class MinutePhysicalStatePhase {
    private final MinutePlayerStatePolicies playerStatePolicies;

    MinutePhysicalStatePhase(MinutePlayerStatePolicies playerStatePolicies) {
        this.playerStatePolicies = playerStatePolicies;
    }

    void apply(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        var potentialInjured = possession.selector().selectShooter(
                possession.possessor().startingPlayers(), possession.formation());
        if (potentialInjured.isPresent()) {
            PlayerMatchState p = potentialInjured.get();
            if (playerStatePolicies.injuryModel().shouldInjure(
                    p, possession.possessor().style(), false, minuteContext.random())) {
                p.injure();
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minuteContext.minute(),
                        DetailedMatchEventType.INJURY,
                        possession.teamRole(),
                        p.sessionPlayerId(),
                        p.name(),
                        null, null,
                        0.0,
                        p.name() + " was injured"
                ));
            }
        }
        if (minuteContext.random().nextDouble() < 0.035) {
            var player = possession.selector().selectShooter(
                    possession.possessor().startingPlayers(), possession.formation());
            if (player.isPresent()) {
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minuteContext.minute(),
                        DetailedMatchEventType.CORNER,
                        possession.teamRole(),
                        player.get().sessionPlayerId(),
                        player.get().name(),
                        null, null,
                        0.0,
                        "Corner for " + possession.possessor().name()
                ));
            }
        }
        if (minuteContext.random().nextDouble() < 0.04 && possession.possessor().style() != TeamStyle.DEFENSIVE) {
            var player = possession.selector().selectShooter(
                    possession.possessor().startingPlayers(), possession.formation());
            if (player.isPresent()) {
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minuteContext.minute(),
                        DetailedMatchEventType.OFFSIDE,
                        possession.teamRole(),
                        player.get().sessionPlayerId(),
                        player.get().name(),
                        null, null,
                        0.0,
                        "Offside"
                ));
            }
        }
    }
}

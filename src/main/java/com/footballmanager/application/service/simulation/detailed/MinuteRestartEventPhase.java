package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;

final class MinuteRestartEventPhase {
    void apply(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        maybeAddCorner(minuteContext, possession);
        maybeAddOffside(minuteContext, possession);
    }

    private void maybeAddCorner(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        if (minuteContext.random().nextDouble() >= 0.035) {
            return;
        }
        var player = possession.selector().selectShooter(
                possession.possessor().startingPlayers(), possession.formation());
        player.ifPresent(p -> minuteContext.timeline().addEvent(new DetailedMatchEvent(
                minuteContext.minute(),
                DetailedMatchEventType.CORNER,
                possession.teamRole(),
                p.sessionPlayerId(),
                p.name(),
                null, null,
                0.0,
                "Corner for " + possession.possessor().name()
        )));
    }

    private void maybeAddOffside(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        if (minuteContext.random().nextDouble() >= 0.04 || possession.possessor().style() == TeamStyle.DEFENSIVE) {
            return;
        }
        var player = possession.selector().selectShooter(
                possession.possessor().startingPlayers(), possession.formation());
        player.ifPresent(p -> minuteContext.timeline().addEvent(new DetailedMatchEvent(
                minuteContext.minute(),
                DetailedMatchEventType.OFFSIDE,
                possession.teamRole(),
                p.sessionPlayerId(),
                p.name(),
                null, null,
                0.0,
                "Offside"
        )));
    }
}

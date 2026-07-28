package com.footballmanager.application.service.simulation.detailed;

final class CardEventService {

    void applyYellowCardAndMaybeSecondYellowRed(
            PlayerMatchState player,
            MatchTimeline timeline,
            int minute,
            String teamRole) {
        boolean wasRedBefore = player.redCard();
        player.addYellowCard();
        timeline.addEvent(new DetailedMatchEvent(
            minute,
            DetailedMatchEventType.YELLOW_CARD,
            teamRole,
            player.sessionPlayerId(),
            player.name(),
            null, null,
            0.0,
            player.name() + " received a yellow card"
        ));
        if (player.yellowCards() >= 2 && !wasRedBefore) {
            timeline.addEvent(new DetailedMatchEvent(
                minute,
                DetailedMatchEventType.RED_CARD,
                teamRole,
                player.sessionPlayerId(),
                player.name(),
                null, null,
                0.0,
                player.name() + " received a red card (second yellow)"
            ));
        }
    }
}

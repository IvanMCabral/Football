package com.footballmanager.application.service.simulation.v24;

final class V24CardEventService {

    void applyYellowCardAndMaybeSecondYellowRed(
            V24PlayerMatchState player,
            V24MatchTimeline timeline,
            int minute,
            String teamRole) {
        boolean wasRedBefore = player.redCard();
        player.addYellowCard();
        timeline.addEvent(new V24MatchEvent(
            minute,
            V24MatchEventType.YELLOW_CARD,
            teamRole,
            player.sessionPlayerId(),
            player.name(),
            null, null,
            0.0,
            player.name() + " received a yellow card"
        ));
        if (player.yellowCards() >= 2 && !wasRedBefore) {
            timeline.addEvent(new V24MatchEvent(
                minute,
                V24MatchEventType.RED_CARD,
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

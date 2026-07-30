package com.footballmanager.application.service.simulation.detailed;

import java.util.Random;
import java.util.Set;

record MinuteSimulationContext(
        MatchContext matchContext,
        Random random,
        TeamMatchState homeState,
        TeamMatchState awayState,
        MatchTimeline timeline,
        PlayerSelector homeSelector,
        PlayerSelector awaySelector,
        Set<String> appliedScheduledSubs,
        SubstitutionEngine scheduledSubEngine,
        double homePossBase,
        double awayPossBase,
        double matchIntensity,
        int minute) {
}

package com.footballmanager.application.service.simulation.detailed;

import java.util.Random;
import java.util.Set;

record MinuteSimulationInput(
        MinuteSimulationConfig config,
        MinuteMatchState matchState,
        int minute) {

    MinuteSimulationInput {
        java.util.Objects.requireNonNull(config, "config must not be null");
        java.util.Objects.requireNonNull(matchState, "matchState must not be null");
        if (minute < 1 || minute > 90) {
            throw new IllegalArgumentException("minute must be in [1, 90], got " + minute);
        }
    }

    MatchContext matchContext() {
        return config.matchContext();
    }

    Random random() {
        return matchState.random();
    }

    TeamMatchState homeState() {
        return matchState.homeState();
    }

    TeamMatchState awayState() {
        return matchState.awayState();
    }

    MatchTimeline timeline() {
        return matchState.timeline();
    }

    PlayerSelector homeSelector() {
        return matchState.homeSelector();
    }

    PlayerSelector awaySelector() {
        return matchState.awaySelector();
    }

    Set<String> appliedScheduledSubs() {
        return matchState.appliedScheduledSubs();
    }

    SubstitutionEngine scheduledSubstitutionEngine() {
        return matchState.scheduledSubstitutionEngine();
    }

    double homePossBase() {
        return config.homePossessionBase();
    }

    double awayPossBase() {
        return config.awayPossessionBase();
    }

    double matchIntensity() {
        return config.matchIntensity();
    }
}

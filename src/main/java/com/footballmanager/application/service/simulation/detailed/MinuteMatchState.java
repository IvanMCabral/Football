package com.footballmanager.application.service.simulation.detailed;

import java.util.Objects;
import java.util.Random;
import java.util.Set;

record MinuteMatchState(
        Random random,
        TeamMatchState homeState,
        TeamMatchState awayState,
        MatchTimeline timeline,
        PlayerSelector homeSelector,
        PlayerSelector awaySelector,
        Set<String> appliedScheduledSubs) {

    MinuteMatchState {
        Objects.requireNonNull(random, "random must not be null");
        Objects.requireNonNull(homeState, "homeState must not be null");
        Objects.requireNonNull(awayState, "awayState must not be null");
        Objects.requireNonNull(timeline, "timeline must not be null");
        Objects.requireNonNull(homeSelector, "homeSelector must not be null");
        Objects.requireNonNull(awaySelector, "awaySelector must not be null");
        Objects.requireNonNull(appliedScheduledSubs, "appliedScheduledSubs must not be null");
        if (homeState == awayState) {
            throw new IllegalArgumentException("homeState and awayState must be distinct");
        }
        if (homeSelector == awaySelector) {
            throw new IllegalArgumentException("homeSelector and awaySelector must be distinct");
        }
    }
}

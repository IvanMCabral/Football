package com.footballmanager.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MatchResult {
    private final int homeGoals;
    private final int awayGoals;
    private final int homePossession;
    private final int awayPossession;
    private final int homeShots;
    private final int awayShots;
    private final List<MatchEvent> events;
    private final String summary;

    private MatchResult(int homeGoals, int awayGoals, int homePossession, int awayPossession,
                       int homeShots, int awayShots, List<MatchEvent> events, String summary) {
        validateGoals(homeGoals, "Home goals");
        validateGoals(awayGoals, "Away goals");
        validatePossession(homePossession, awayPossession);
        validateShots(homeShots, "Home shots");
        validateShots(awayShots, "Away shots");
        
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homePossession = homePossession;
        this.awayPossession = awayPossession;
        this.homeShots = homeShots;
        this.awayShots = awayShots;
        this.events = events != null ? new ArrayList<>(events) : new ArrayList<>();
        this.summary = summary != null ? summary : generateSummary();
    }

    public static MatchResult of(int homeGoals, int awayGoals, int homePossession, int awayPossession,
                                int homeShots, int awayShots, List<MatchEvent> events, String summary) {
        return new MatchResult(homeGoals, awayGoals, homePossession, awayPossession,
                             homeShots, awayShots, events, summary);
    }

    private void validateGoals(int goals, String fieldName) {
        if (goals < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    private void validatePossession(int homePossession, int awayPossession) {
        if (homePossession < 0 || homePossession > 100) {
            throw new IllegalArgumentException("Home possession must be between 0 and 100");
        }
        if (awayPossession < 0 || awayPossession > 100) {
            throw new IllegalArgumentException("Away possession must be between 0 and 100");
        }
        if (homePossession + awayPossession != 100) {
            throw new IllegalArgumentException("Total possession must equal 100%");
        }
    }

    private void validateShots(int shots, String fieldName) {
        if (shots < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    private String generateSummary() {
        String result;
        if (homeGoals > awayGoals) {
            result = "Home team wins!";
        } else if (awayGoals > homeGoals) {
            result = "Away team wins!";
        } else {
            result = "It's a draw!";
        }
        return String.format("%s Final score: %d-%d", result, homeGoals, awayGoals);
    }

    public boolean isHomeWin() {
        return homeGoals > awayGoals;
    }

    public boolean isAwayWin() {
        return awayGoals > homeGoals;
    }

    public boolean isDraw() {
        return homeGoals == awayGoals;
    }

    // Getters
    public int getHomeGoals() {
        return homeGoals;
    }

    public int getAwayGoals() {
        return awayGoals;
    }

    public int getHomePossession() {
        return homePossession;
    }

    public int getAwayPossession() {
        return awayPossession;
    }

    public int getHomeShots() {
        return homeShots;
    }

    public int getAwayShots() {
        return awayShots;
    }

    public List<MatchEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public String getSummary() {
        return summary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchResult that = (MatchResult) o;
        return homeGoals == that.homeGoals &&
                awayGoals == that.awayGoals &&
                homePossession == that.homePossession &&
                awayPossession == that.awayPossession &&
                homeShots == that.homeShots &&
                awayShots == that.awayShots;
    }

    @Override
    public int hashCode() {
        return Objects.hash(homeGoals, awayGoals, homePossession, awayPossession, homeShots, awayShots);
    }

    @Override
    public String toString() {
        return String.format("MatchResult{%d-%d, possession: %d%%-%d%%, shots: %d-%d, events: %d}",
                homeGoals, awayGoals, homePossession, awayPossession, homeShots, awayShots, events.size());
    }
}

package com.footballmanager.domain.simulation.v32;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable output from a simulation tick.
 */
public final class TickOutput {

    private final List<MatchEvent> events;
    private boolean matchEnded;
    private int homeGoals;
    private int awayGoals;
    private String endReason;

    public TickOutput() {
        this.events = new ArrayList<MatchEvent>();
        this.matchEnded = false;
        this.homeGoals = -1;
        this.awayGoals = -1;
        this.endReason = "";
    }

    public void addEvent(MatchEvent event) {
        events.add(event);
    }

    public List<MatchEvent> getEvents() {
        return events;
    }

    public boolean hasEvents() {
        return !events.isEmpty();
    }

    public void setMatchEnded(int homeGoals, int awayGoals, String reason) {
        this.matchEnded = true;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.endReason = reason;
    }

    public boolean isMatchEnded() { return matchEnded; }
    public int getHomeGoals() { return homeGoals; }
    public int getAwayGoals() { return awayGoals; }
    public String getEndReason() { return endReason; }

    public void reset() {
        events.clear();
        matchEnded = false;
        homeGoals = -1;
        awayGoals = -1;
        endReason = "";
    }
}

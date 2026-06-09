package com.footballmanager.application.service.simulation.v24;

import java.util.ArrayList;
import java.util.List;

/**
 * V24D6M11: Per-tick snapshot DTO for live SSE stream.
 *
 * <p>Created by V24LiveSession.tick() and sent to the SSE stream
 * so the frontend gets minute-by-minute match state updates.
 *
 * <p>Contains only the fields the frontend needs for the live match UI.
 * For final persistence, use V24LiveSession.finalResult() which carries
 * the full V24DetailedMatchResult with complete timeline.
 */
public final class V24LiveSnapshot {

    private final String matchId;
    private final int minute;
    private final int homeGoals;
    private final int awayGoals;
    private final String homeTeamId;
    private final String awayTeamId;
    private final boolean finished;
    private final List<V24MatchEvent> allEvents;
    private final int homePossession;
    private final int awayPossession;

    public V24LiveSnapshot(
            String matchId,
            int minute,
            int homeGoals,
            int awayGoals,
            String homeTeamId,
            String awayTeamId,
            boolean finished,
            List<V24MatchEvent> allEvents,
            int homePossession,
            int awayPossession) {
        this.matchId = matchId;
        this.minute = minute;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.finished = finished;
        this.allEvents = allEvents != null ? List.copyOf(allEvents) : List.of();
        this.homePossession = homePossession;
        this.awayPossession = awayPossession;
    }

    public String matchId() { return matchId; }
    public int minute() { return minute; }
    public int homeGoals() { return homeGoals; }
    public int awayGoals() { return awayGoals; }
    public String homeTeamId() { return homeTeamId; }
    public String awayTeamId() { return awayTeamId; }
    public boolean isFinished() { return finished; }
    public List<V24MatchEvent> allEvents() { return allEvents; }
    public int homePossession() { return homePossession; }
    public int awayPossession() { return awayPossession; }
}
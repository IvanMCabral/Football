package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import com.footballmanager.domain.model.valueobject.FormationSlot;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * <p>Created by V24LiveSession.tick() and sent to the SSE stream
 * so the frontend gets minute-by-minute match state updates.
 *
 * <p>Contains only the fields the frontend needs for the live match UI.
 * For final persistence, use V24LiveSession.finalResult() which carries
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
    // can render them in real time. Values come from effectiveContext.homeStyle()
    // (TeamStyle.name() = BALANCED/ATTACKING/DEFENSIVE/COUNTER/POSSESSION) and
    // effectiveContext.homeFormation() (String like "4-4-2"). Nullable for the
    // initial tick before the first style/formation is established — defaults
    // to "BALANCED" / "4-4-2" so the UI never breaks.
    private final String homeStyle;
    private final String awayStyle;
    private final String homeFormation;
    private final String awayFormation;
    private final List<FormationSlot> homeSlots;
    private final List<FormationSlot> awaySlots;

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
        this(matchId, minute, homeGoals, awayGoals, homeTeamId, awayTeamId,
             finished, allEvents, homePossession, awayPossession,
             "BALANCED", "BALANCED", "4-4-2", "4-4-2",
             List.of(), List.of());
    }

    /**
     * formation per team. The new fields default to "BALANCED" / "4-4-2" via
     * the legacy constructor so existing callers (e.g. tests) keep working
     * unchanged.
     *
     * @param homeStyle      effective style of the home team (TeamStyle.name()).
     *                       Nullable — falls back to "BALANCED" if null/blank.
     * @param awayStyle      effective style of the away team. Nullable.
     * @param homeFormation  effective formation of the home team (e.g. "4-4-2").
     *                       Nullable — falls back to "4-4-2" if null/blank.
     * @param awayFormation  effective formation of the away team. Nullable.
     */
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
            int awayPossession,
            String homeStyle,
            String awayStyle,
            String homeFormation,
            String awayFormation) {
        this(matchId, minute, homeGoals, awayGoals, homeTeamId, awayTeamId,
             finished, allEvents, homePossession, awayPossession,
             homeStyle, awayStyle, homeFormation, awayFormation,
             List.of(), List.of());
    }

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
            int awayPossession,
            String homeStyle,
            String awayStyle,
            String homeFormation,
            String awayFormation,
            List<FormationSlot> homeSlots,
            List<FormationSlot> awaySlots) {
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
        this.homeStyle = (homeStyle != null && !homeStyle.isBlank()) ? homeStyle : "BALANCED";
        this.awayStyle = (awayStyle != null && !awayStyle.isBlank()) ? awayStyle : "BALANCED";
        this.homeFormation = (homeFormation != null && !homeFormation.isBlank()) ? homeFormation : "4-4-2";
        this.awayFormation = (awayFormation != null && !awayFormation.isBlank()) ? awayFormation : "4-4-2";
        this.homeSlots = homeSlots != null ? List.copyOf(homeSlots) : List.of();
        this.awaySlots = awaySlots != null ? List.copyOf(awaySlots) : List.of();
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
    public String homeStyle() { return homeStyle; }
    public String awayStyle() { return awayStyle; }
    public String homeFormation() { return homeFormation; }
    public String awayFormation() { return awayFormation; }
    public List<FormationSlot> homeSlots() { return homeSlots; }
    public List<FormationSlot> awaySlots() { return awaySlots; }
}

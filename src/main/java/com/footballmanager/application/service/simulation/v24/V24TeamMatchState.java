package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Mutable team-level match state aggregate.
 */
public class V24TeamMatchState {

    private final String teamId;
    private final String name;
    private final String formation;
    private final TeamStyle style;
    private final List<V24PlayerMatchState> startingPlayers;
    private final List<V24PlayerMatchState> benchPlayers;

    private int goals;
    private double xg;
    private int shots;
    private int shotsOnTarget;
    private int possessionTicks;

    private V24TeamMatchState(
            String teamId, String name, String formation, TeamStyle style,
            List<V24PlayerMatchState> startingPlayers,
            List<V24PlayerMatchState> benchPlayers,
            int goals, double xg, int shots, int shotsOnTarget, int possessionTicks) {
        this.teamId = teamId;
        this.name = name;
        this.formation = formation;
        this.style = style;
        this.startingPlayers = new ArrayList<>(startingPlayers);
        this.benchPlayers = new ArrayList<>(benchPlayers);
        this.goals = goals;
        this.xg = xg;
        this.shots = shots;
        this.shotsOnTarget = shotsOnTarget;
        this.possessionTicks = possessionTicks;
    }

    // Package-private constructor for test subclassing
    V24TeamMatchState(
            String teamId, String name, String formation, TeamStyle style,
            List<V24PlayerMatchState> startingPlayers,
            List<V24PlayerMatchState> benchPlayers) {
        this(teamId, name, formation, style, startingPlayers, benchPlayers,
                0, 0.0, 0, 0, 0);
    }

    public static V24TeamMatchState create(
            SessionTeam team,
            List<SessionPlayer> starting,
            List<SessionPlayer> bench,
            TeamStyle style) {
        Objects.requireNonNull(team, "team must not be null");
        Objects.requireNonNull(starting, "starting list must not be null");
        Objects.requireNonNull(bench, "bench list must not be null");
        if (starting.size() != 11) {
            throw new IllegalArgumentException("starting must contain exactly 11 players, got " + starting.size());
        }
        TeamStyle effectiveStyle = (style != null) ? style : TeamStyle.BALANCED;

        String tid = team.getSessionTeamId();
        List<V24PlayerMatchState> startState = new ArrayList<>();
        for (SessionPlayer p : starting) {
            startState.add(V24PlayerMatchState.fromSessionPlayer(p, tid));
        }

        List<V24PlayerMatchState> benchState = new ArrayList<>();
        for (SessionPlayer p : bench) {
            V24PlayerMatchState bp = V24PlayerMatchState.fromSessionPlayer(p, tid);
            bp.substituteOff(); // bench players start off pitch
            benchState.add(bp);
        }

        return new V24TeamMatchState(
                tid,
                team.getName(),
                team.getFormation(),
                effectiveStyle,
                startState,
                benchState,
                0, 0.0, 0, 0, 0
        );
    }

    public String teamId() { return teamId; }
    public String name() { return name; }
    public String formation() { return formation; }
    public TeamStyle style() { return style; }
    public List<V24PlayerMatchState> startingPlayers() { return Collections.unmodifiableList(startingPlayers); }
    public List<V24PlayerMatchState> benchPlayers() { return Collections.unmodifiableList(benchPlayers); }
    public int goals() { return goals; }
    public double xg() { return xg; }
    public int shots() { return shots; }
    public int shotsOnTarget() { return shotsOnTarget; }
    public int possessionTicks() { return possessionTicks; }

    public void addGoal() { goals++; }
    public void addXg(double amount) { xg += amount; }
    public void addShot(boolean onTarget) { shots++; if (onTarget) shotsOnTarget++; }
    public void addPossessionTick() { possessionTicks++; }
}
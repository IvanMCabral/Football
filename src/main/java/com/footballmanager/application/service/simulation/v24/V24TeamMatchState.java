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
    // LIVE-MATCH-F2-LIVE F5 (B1): 'formation' and 'style' are NO LONGER final.
    // They are mutable so a manager can change formation/style mid-match.
    // Setters validate (NOT NULL for style; for formation, the new value
    // must parse via V24FormationParser into 10 outfield players).
    // Other fields stay final — only these two are touched by tactical changes.
    private String formation;
    private TeamStyle style;
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
        // V24D6U2: accept short-handed starting lineups in [MIN, 11]
        int min = com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS;
        if (starting.size() < min || starting.size() > 11) {
            throw new IllegalArgumentException(
                "starting must contain between " + min + " and 11 players, got " + starting.size());
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

    // ========== LIVE-MATCH-F2-LIVE F5 (B1): validated mutators ==========

    /**
     * LIVE-MATCH-F2-LIVE F5 (B1): replace the team's tactical style.
     * Validates non-null. After mutation, the teamState is in an
     * "in-flight" state until {@link V24LiveSession#replayFromMinute(int)}
     * recomputes the engine. The setter itself does NOT trigger a replay —
     * callers (e.g. {@code TacticalChangeService}) drive the replay through
     * the live session.
     *
     * @param style new tactical style (NOT NULL)
     * @throws IllegalArgumentException if style is null
     */
    public void setStyle(TeamStyle style) {
        if (style == null) {
            throw new IllegalArgumentException("style must not be null");
        }
        this.style = style;
    }

    /**
     * LIVE-MATCH-F2-LIVE F5 (B1): replace the team's formation string.
     * Validates that the formation parses via {@link V24FormationParser}
     * into 10 outfield players. Null and blank are rejected.
     *
     * <p>Per the F5 spec (section 2 D-formation): the rule is 10-11 players
     * in valid positions. Since formation is a code (e.g. "4-4-2") and the
     * engine always pairs it with 1 GK, a parseable code that yields
     * {@code outfieldPlayers() == 10} is the proxy for "11 total". A 10-player
     * starting XI (e.g. an expulsion) is handled by the engine upstream and
     * is not enforced here.
     *
     * @param formation new formation code (NOT NULL, NOT BLANK, parseable)
     * @throws IllegalArgumentException if formation is null/blank/unparseable
     */
    public void setFormation(String formation) {
        if (formation == null || formation.isBlank()) {
            throw new IllegalArgumentException("formation must not be null or blank");
        }
        // Match V24FormationParser normalization: trim + collapse whitespace + em-dash to hyphen.
        // If the parser falls back to the BALANCED_DEFAULT ("4-4-2") for unparseable input,
        // the normalized input will not match the parsed raw — that's our rejection signal.
        String normalized = formation.trim().replaceAll("\\s+", "").replace('\u2013', '-');
        V24FormationParser parser = new V24FormationParser();
        V24FormationParser.V24Formation parsed = parser.parse(formation);
        if (!normalized.equals(parsed.raw())) {
            throw new IllegalArgumentException(
                "formation must parse into a valid tactical code, got '" + formation + "'");
        }
        if (parsed.outfieldPlayers() != 10) {
            throw new IllegalArgumentException(
                "formation must have 10 outfield players (1 GK is implicit), got "
                + parsed.outfieldPlayers() + " for '" + formation + "'");
        }
        this.formation = parsed.raw();
    }

    public void addGoal() { goals++; }
    public void addXg(double amount) { xg += amount; }
    public void addShot(boolean onTarget) { shots++; if (onTarget) shotsOnTarget++; }
    public void addPossessionTick() { possessionTicks++; }
}
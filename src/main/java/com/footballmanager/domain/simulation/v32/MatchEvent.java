package com.footballmanager.domain.simulation.v32;

/**
 * Represents a match event in V32 simulation.
 */
public final class MatchEvent {

    /** Event type */
    public enum Type {
        GOAL,
        SHOT,
        SAVE,
        FOUL,
        CARD,
        CORNER,
        FREE_KICK,
        THROW_IN,
        OFFSIDE,
        SUBSTITUTION,
        INJURY,
        POSSESSION_CHANGE,
        PENALTY,
        KICKOFF,
        HALF_TIME,
        FULL_TIME,
        WHISTLE,
        PLAYER_CONTROL,
        PLAYER_RECEIVE
    }

    public final Type type;
    public final int tick;
    public final int minute;
    public final int team; // 0 = home, 1 = away
    public final int playerIdx; // -1 if N/A
    public final String description;
    public final float x; // Optional position
    public final float y;
    public final float xg; // Expected goal value
    public final boolean isHome;

    private MatchEvent(Type type, int tick, int minute, int team, int playerIdx,
                       String description, float x, float y, float xg, boolean isHome) {
        this.type = type;
        this.tick = tick;
        this.minute = minute;
        this.team = team;
        this.playerIdx = playerIdx;
        this.description = description;
        this.x = x;
        this.y = y;
        this.xg = xg;
        this.isHome = isHome;
    }

    public static MatchEvent goal(int tick, int minute, int team, int playerIdx,
                                   String description, float xg) {
        return new MatchEvent(Type.GOAL, tick, minute, team, playerIdx, description,
                              0, 0, xg, team == 0);
    }

    public static MatchEvent shot(int tick, int minute, int team, int playerIdx,
                                   float x, float y, float xg) {
        return new MatchEvent(Type.SHOT, tick, minute, team, playerIdx, "Shot",
                              x, y, xg, team == 0);
    }

    public static MatchEvent save(int tick, int minute, int team, int playerIdx) {
        return new MatchEvent(Type.SAVE, tick, minute, team, playerIdx, "Save",
                              0, 0, 0, team == 0);
    }

    public static MatchEvent foul(int tick, int minute, int team, int playerIdx) {
        return new MatchEvent(Type.FOUL, tick, minute, team, playerIdx, "Foul",
                              0, 0, 0, team == 0);
    }

    public static MatchEvent card(int tick, int minute, int team, int playerIdx,
                                  String cardType) {
        return new MatchEvent(Type.CARD, tick, minute, team, playerIdx, cardType,
                              0, 0, 0, team == 0);
    }

    public static MatchEvent possessionChange(int tick, int minute, int newTeam) {
        return new MatchEvent(Type.POSSESSION_CHANGE, tick, minute, newTeam, -1,
                              newTeam == 0 ? "Home" : "Away", 0, 0, 0, newTeam == 0);
    }

    public static MatchEvent kickoff(int tick, int minute) {
        return new MatchEvent(Type.KICKOFF, tick, minute, -1, -1, "Kickoff", 0, 0, 0, true);
    }

    public static MatchEvent halfTime(int tick) {
        return new MatchEvent(Type.HALF_TIME, tick, 45, -1, -1, "Half Time", 0, 0, 0, true);
    }

    public static MatchEvent fullTime(int tick) {
        return new MatchEvent(Type.FULL_TIME, tick, 90, -1, -1, "Full Time", 0, 0, 0, true);
    }

    public static MatchEvent whistle(int tick, int minute, String reason) {
        return new MatchEvent(Type.WHISTLE, tick, minute, -1, -1, reason, 0, 0, 0, true);
    }

    public static MatchEvent playerControl(int tick, int minute, int team, int playerIdx) {
        return new MatchEvent(Type.PLAYER_CONTROL, tick, minute, team, playerIdx,
                              "Player " + playerIdx + " takes control", 0, 0, 0, team == 0);
    }

    @Override
    public String toString() {
        return String.format("[%d'] %s: %s (p%d)", minute, type, description, playerIdx);
    }
}

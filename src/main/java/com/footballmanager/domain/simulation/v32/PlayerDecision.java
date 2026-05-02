package com.footballmanager.domain.simulation.v32;

/**
 * Player decision output from AI for a single tick.
 */
public final class PlayerDecision {

    /** Decision type */
    public enum Decision {
        IDLE,
        MOVE_TO,
        CHASE_BALL,
        PASS,
        SHOOT,
        DRIBBLE,
        TACKLE,
        CLEAR,
        HOLD,
        COVER,
        MARK,
        SUPPORT,
        PRESS,
        GO_TO_POSITION,
        CROSS,
        THROW_IN,
        GOALKEEPER_ADVANCE,
        GOALKEEPER_DIVE
    }

    public final int playerIdx;
    public final Decision decision;
    public final float targetX;
    public final float targetY;
    public final int targetPlayer; // For passes
    public final float urgency; // 0-1
    public final float power; // For shots/kicks
    public final long committedUntil; // Tick until which this decision is locked

    private PlayerDecision(int playerIdx, Decision decision, float targetX, float targetY,
                           int targetPlayer, float urgency, float power, long committedUntil) {
        this.playerIdx = playerIdx;
        this.decision = decision;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetPlayer = targetPlayer;
        this.urgency = urgency;
        this.power = power;
        this.committedUntil = committedUntil;
    }

    public static PlayerDecision idle(int playerIdx) {
        return new PlayerDecision(playerIdx, Decision.IDLE, 0, 0, -1, 0, 0, 0);
    }

    public static PlayerDecision moveTo(int playerIdx, float x, float y, float urgency) {
        return new PlayerDecision(playerIdx, Decision.MOVE_TO, x, y, -1, urgency, 0,
                                   System.currentTimeMillis() + 250);
    }

    public static PlayerDecision chaseBall(int playerIdx, float urgency) {
        return new PlayerDecision(playerIdx, Decision.CHASE_BALL, 0, 0, -1, urgency, 0,
                                   System.currentTimeMillis() + 250);
    }

    public static PlayerDecision pass(int playerIdx, int targetPlayer, float power) {
        return new PlayerDecision(playerIdx, Decision.PASS, 0, 0, targetPlayer, 0.8f, power,
                                   System.currentTimeMillis() + 100);
    }

    public static PlayerDecision shoot(int playerIdx, float targetX, float targetY, float power) {
        return new PlayerDecision(playerIdx, Decision.SHOOT, targetX, targetY, -1, 1.0f, power,
                                   System.currentTimeMillis() + 50);
    }

    public static PlayerDecision tackle(int playerIdx, int opponentIdx) {
        return new PlayerDecision(playerIdx, Decision.TACKLE, 0, 0, opponentIdx, 1.0f, 0,
                                   System.currentTimeMillis() + 100);
    }

    public static PlayerDecision clear(int playerIdx) {
        return new PlayerDecision(playerIdx, Decision.CLEAR, 0, 0, -1, 0.9f, 1.0f,
                                   System.currentTimeMillis() + 50);
    }

    public static PlayerDecision press(int playerIdx, int opponentIdx) {
        return new PlayerDecision(playerIdx, Decision.PRESS, 0, 0, opponentIdx, 0.7f, 0,
                                   System.currentTimeMillis() + 200);
    }

    public static PlayerDecision cover(int playerIdx, int coverIdx) {
        return new PlayerDecision(playerIdx, Decision.COVER, 0, 0, coverIdx, 0.5f, 0,
                                   System.currentTimeMillis() + 500);
    }

    public static PlayerDecision mark(int playerIdx, int markIdx) {
        return new PlayerDecision(playerIdx, Decision.MARK, 0, 0, markIdx, 0.6f, 0,
                                   System.currentTimeMillis() + 300);
    }

    public static PlayerDecision support(int playerIdx, int supportTargetIdx) {
        return new PlayerDecision(playerIdx, Decision.SUPPORT, 0, 0, supportTargetIdx, 0.5f, 0,
                                   System.currentTimeMillis() + 400);
    }

    public static PlayerDecision hold(int playerIdx) {
        return new PlayerDecision(playerIdx, Decision.HOLD, 0, 0, -1, 0.3f, 0,
                                   System.currentTimeMillis() + 500);
    }

    public static PlayerDecision goToPosition(int playerIdx, float x, float y) {
        return new PlayerDecision(playerIdx, Decision.GO_TO_POSITION, x, y, -1, 0.4f, 0,
                                   System.currentTimeMillis() + 600);
    }

    public boolean isCommitted(long currentTick) {
        return committedUntil > System.currentTimeMillis();
    }

    public boolean isExpired() {
        return committedUntil < System.currentTimeMillis();
    }
}

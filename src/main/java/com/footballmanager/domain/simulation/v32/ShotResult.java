package com.footballmanager.domain.simulation.v32;

import com.footballmanager.domain.simulation.v32.enums.ShotType;

/**
 * Result of a shot attempt.
 */
public final class ShotResult {

    public enum Outcome {
        GOAL,
        SAVED,
        MISSED,
        BLOCKED,
        POST,
        CROSSBAR
    }

    public final Outcome outcome;
    public final int shooterIdx;
    public final int gkIdx;
    public final float x;
    public final float y;
    public final float xg;
    public final ShotType shotType;
    public final boolean wasOnTarget;

    private ShotResult(Outcome outcome, int shooterIdx, int gkIdx,
                      float x, float y, float xg, ShotType shotType,
                      boolean wasOnTarget) {
        this.outcome = outcome;
        this.shooterIdx = shooterIdx;
        this.gkIdx = gkIdx;
        this.x = x;
        this.y = y;
        this.xg = xg;
        this.shotType = shotType;
        this.wasOnTarget = wasOnTarget;
    }

    public static ShotResult goal(int shooterIdx, int gkIdx, float x, float y, float xg, ShotType shotType) {
        return new ShotResult(Outcome.GOAL, shooterIdx, gkIdx, x, y, xg, shotType, true);
    }

    public static ShotResult saved(int shooterIdx, int gkIdx, float x, float y, float xg, ShotType shotType) {
        return new ShotResult(Outcome.SAVED, shooterIdx, gkIdx, x, y, xg, shotType, true);
    }

    public static ShotResult missed(int shooterIdx, float x, float y, float xg, ShotType shotType) {
        return new ShotResult(Outcome.MISSED, shooterIdx, -1, x, y, xg, shotType, false);
    }

    public static ShotResult blocked(int shooterIdx, int blockerIdx, float x, float y, float xg, ShotType shotType) {
        return new ShotResult(Outcome.BLOCKED, shooterIdx, blockerIdx, x, y, xg, shotType, false);
    }

    public static ShotResult post(int shooterIdx, float x, float y, float xg, ShotType shotType) {
        return new ShotResult(Outcome.POST, shooterIdx, -1, x, y, xg, shotType, true);
    }

    public static ShotResult crossbar(int shooterIdx, float x, float y, float xg, ShotType shotType) {
        return new ShotResult(Outcome.CROSSBAR, shooterIdx, -1, x, y, xg, shotType, true);
    }

    public boolean isGoal() { return outcome == Outcome.GOAL; }
    public boolean isSaved() { return outcome == Outcome.SAVED; }
    public boolean isMissed() { return outcome == Outcome.MISSED; }
    public boolean isBlocked() { return outcome == Outcome.BLOCKED; }

    @Override
    public String toString() {
        return String.format("ShotResult: %s by p%d (xG=%.2f) at (%.1f, %.1f)",
            outcome, shooterIdx, xg, x, y);
    }
}

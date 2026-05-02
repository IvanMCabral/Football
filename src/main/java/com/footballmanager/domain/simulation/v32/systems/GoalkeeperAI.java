package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.PlayerDecision;
import com.footballmanager.domain.simulation.v32.math.Vector2D;
import com.footballmanager.domain.simulation.v32.math.Vector3D;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;

/**
 * Goalkeeper AI for V32.
 * Handles GK positioning, diving, and shot saving.
 */
public final class GoalkeeperAI {

    private static final float GK_Y_SPEED = 5.0f;
    private static final float GK_DIVE_SPEED = 6.0f;

    public GoalkeeperAI() {}

    /**
     * Decides GK action for a tick.
     * @param state Match state
     * @param gkIdx GK player index (0 for home, 11 for away)
     * @param rng RNG
     * @return PlayerDecision for GK
     */
    public PlayerDecision decide(MatchStateSoA state, int gkIdx, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(gkIdx);
        boolean isHome = teamSide == 0;

        // Check if shot is incoming
        float shotThreat = calculateShotThreat(state, gkIdx);

        if (shotThreat > 0.7f) {
            // Diving save
            return decideDive(state, gkIdx, rng);
        } else if (shotThreat > 0.3f) {
            // Positioning adjustment
            return adjustPosition(state, gkIdx, rng);
        } else if (state.ballX < 35 || state.ballX > 35) {
            // Ball in final third - be more alert
            return trackBall(state, gkIdx, rng);
        }

        // Stay alert near goal
        return holdPosition(state, gkIdx);
    }

    private float calculateShotThreat(MatchStateSoA state, int gkIdx) {
        int teamSide = state.getPlayerTeamSide(gkIdx);

        // Check if ball is heading toward goal
        float ballX = state.ballX;
        float ballY = state.ballY;
        float ballVX = state.ballVX;
        float ballVY = state.ballVY;

        // Home GK defends against shots from positive X direction
        // Away GK defends against shots from negative X direction
        boolean towardGoal = (teamSide == 0 && ballVX < -2) || (teamSide == 1 && ballVX > 2);

        if (!towardGoal) return 0.0f;

        // Calculate time to goal line
        float goalX = teamSide == 0 ? PhysicsConstants.HOME_GOAL_X : PhysicsConstants.AWAY_GOAL_X;
        float timeToGoal = Math.abs((goalX - ballX) / ballVX);

        if (timeToGoal > 3.0f) return 0.0f;

        // Distance from ball to goal center
        float distToGoal = Math.abs(ballY);

        // Threat based on distance and speed
        float speed = (float) Math.sqrt(ballVX * ballVX + ballVY * ballVY);
        float threat = (1.0f - timeToGoal / 3.0f) * (1.0f - distToGoal / 20.0f) * (speed / 30.0f);

        return Math.min(1.0f, threat);
    }

    private PlayerDecision decideDive(MatchStateSoA state, int gkIdx, ScopedRNG rng) {
        // Predict where ball will be
        float ballX = state.ballX;
        float ballY = state.ballY;
        float ballVX = state.ballVX;
        float ballVY = state.ballVY;

        int teamSide = state.getPlayerTeamSide(gkIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.HOME_GOAL_X : PhysicsConstants.AWAY_GOAL_X;

        // Time to reach goal line
        float timeToGoal = Math.abs((goalX - ballX) / ballVX);
        if (timeToGoal <= 0) timeToGoal = 0.1f;

        // Predicted Y position
        float predictedY = ballY + ballVY * timeToGoal;

        // GK current position
        float gkY = state.getPlayerY(gkIdx);

        // Decide dive direction
        float diveDirection = predictedY - gkY;

        if (Math.abs(diveDirection) > 2.0f) {
            // Dive
            float diveTargetY = predictedY;
            diveTargetY = Math.max(-15, Math.min(15, diveTargetY));
            return PlayerDecision.moveTo(gkIdx, goalX + 2, diveTargetY, 1.0f);
        }

        // Stay and react
        return holdPosition(state, gkIdx);
    }

    private PlayerDecision adjustPosition(MatchStateSoA state, int gkIdx, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(gkIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.HOME_GOAL_X : PhysicsConstants.AWAY_GOAL_X;

        // Move toward ball's Y position
        float targetY = state.ballY * 0.3f; // Follow partially
        targetY = Math.max(-10, Math.min(10, targetY));

        return PlayerDecision.moveTo(gkIdx, goalX, targetY, 0.5f);
    }

    private PlayerDecision trackBall(MatchStateSoA state, int gkIdx, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(gkIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.HOME_GOAL_X : PhysicsConstants.AWAY_GOAL_X;

        // Slightly off the line to be ready
        float readyX = goalX + (teamSide == 0 ? 1 : -1) * 2;
        float readyY = state.ballY * 0.2f;

        return PlayerDecision.moveTo(gkIdx, readyX, readyY, 0.3f);
    }

    private PlayerDecision holdPosition(MatchStateSoA state, int gkIdx) {
        int teamSide = state.getPlayerTeamSide(gkIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.HOME_GOAL_X : PhysicsConstants.AWAY_GOAL_X;

        return PlayerDecision.goToPosition(gkIdx, goalX, 0);
    }

    /**
     * Calculates save probability for a shot.
     */
    public float calculateSaveProbability(MatchStateSoA state, int gkIdx, float shotX, float shotY) {
        float gkY = state.getPlayerY(gkIdx);
        float gkReach = SimulationConstants.GK_REACH;

        float distance = Math.abs(shotY - gkY);

        if (distance < gkReach) {
            return 0.8f; // Close to center, good chance
        } else if (distance < gkReach * 2) {
            return 0.4f; // Extended reach
        } else if (distance < gkReach * 3) {
            return 0.15f; // Far corner, hard to reach
        }

        return 0.05f; // Very far, essentially unsaveable
    }
}

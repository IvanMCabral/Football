package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;

/**
 * Shot System for V32.
 * Handles xG calculations and shot outcomes.
 */
public final class ShotSystem {

    public ShotSystem() {}

    /**
     * Calculates xG (Expected Goals) for a shot.
     */
    public double calculateXG(MatchStateSoA state, int shooterIdx, float targetX, float targetY) {
        int teamSide = state.getPlayerTeamSide(shooterIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
        float goalY = 0;

        // Distance to goal
        float dx = targetX - goalX;
        float dy = targetY - goalY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Angle to goal
        double angle = Math.atan2(Math.abs(dy), Math.abs(dx));

        // Base xG
        double xg = SimulationConstants.XG_BASE;

        // Distance penalty (further = less likely)
        xg -= distance * SimulationConstants.XG_DISTANCE_COEFFICIENT;

        // Angle bonus (wider angle = more likely)
        xg += angle * SimulationConstants.XG_ANGLE_COEFFICIENT;

        // Header penalty
        if (state.ballZ > 1.0f) {
            xg += SimulationConstants.XG_HEADER_BONUS;
        }

        // One-on-one bonus (keeper out of position)
        int gkIdx = teamSide == 0 ? 11 : 0;
        float gkY = state.getPlayerY(gkIdx);
        float gkDistFromCenter = Math.abs(gkY);
        if (gkDistFromCenter > 3.0f) {
            xg += SimulationConstants.XG_ONE_ON_ONE;
        }

        // Foot shot vs header
        if (state.ballZ > 1.5f) {
            // High ball, harder to score
            xg -= 0.15;
        }

        // Player skill factor
        float shootingSkill = state.playerOvr[shooterIdx] / 99.0f;
        xg *= (0.8 + shootingSkill * 0.4);

        // Momentum factor
        float momentum = teamSide == 0 ? state.homeMomentum : state.awayMomentum;
        xg *= (0.9 + momentum * 0.2);

        // Clamp to valid range
        return Math.max(0.01, Math.min(0.95, xg));
    }

    /**
     * Determines if shot is on target.
     */
    public boolean isOnTarget(MatchStateSoA state, int shooterIdx, float targetX, float targetY,
                              ScopedRNG rng) {
        float baseProbability = SimulationConstants.SHOT_ON_TARGET_BASE;

        // Distance affects accuracy
        int teamSide = state.getPlayerTeamSide(shooterIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
        float distance = Math.abs(targetX - goalX);

        float accuracy = baseProbability - (distance * 0.005f);

        // Skill improves accuracy
        float skill = state.playerOvr[shooterIdx] / 99.0f;
        accuracy += skill * 0.15f;

        // Low energy reduces accuracy
        if (state.getPlayerEnergy(shooterIdx) < 0.3f) {
            accuracy -= 0.1f;
        }

        return rng.nextFloat() < accuracy;
    }

    /**
     * Determines shot outcome.
     */
    public ShotOutcome determineOutcome(MatchStateSoA state, int shooterIdx,
                                        float targetX, float targetY, ScopedRNG rng) {
        double xg = calculateXG(state, shooterIdx, targetX, targetY);

        // Check if shot is on target
        if (!isOnTarget(state, shooterIdx, targetX, targetY, rng)) {
            return ShotOutcome.MISSED;
        }

        // GK save attempt
        int gkIdx = state.getPlayerTeamSide(shooterIdx) == 0 ? 11 : 0;
        GoalkeeperAI gkAI = new GoalkeeperAI();
        float saveProb = gkAI.calculateSaveProbability(state, gkIdx, targetX, targetY);

        // Home advantage slight boost
        int teamSide = state.getPlayerTeamSide(shooterIdx);
        if (teamSide == 0) {
            saveProb -= 0.05f;
        }

        if (rng.nextFloat() < saveProb) {
            return ShotOutcome.SAVED;
        }

        // Goal!
        if (rng.nextFloat() < (float) xg) {
            return ShotOutcome.GOAL;
        }

        return ShotOutcome.SAVED;
    }

    /**
     * Gets best shooting target for a player.
     */
    public float[] getBestShotTarget(MatchStateSoA state, int shooterIdx, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(shooterIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;

        // Aim for corners with some randomness
        float targetY = rng.nextFloatRange(-3, 3);
        float targetX = goalX - 1; // Just in front of goal line

        return new float[] { targetX, targetY };
    }

    /**
     * Shot outcome enum.
     */
    public enum ShotOutcome {
        GOAL,
        SAVED,
        MISSED,
        BLOCKED
    }
}

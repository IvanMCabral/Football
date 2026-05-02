package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.TickInput;
import com.footballmanager.domain.simulation.v32.PlayerDecision;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.enums.MatchPhase;
import com.footballmanager.domain.simulation.v32.enums.PlayerRole;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;

/**
 * Off-Ball AI for V32 - CONTROLLED STOCHASTIC VERSION.
 * Shot decisions are controlled by match-level shot budget system.
 */
public final class OffBallAI {

    /** Maximum possession time before forced decision (ticks) */
    private static final int MAX_POSSESSION_TICKS = 6;

    public OffBallAI() {}

    /**
     * Makes AI decision for a player.
     */
    public PlayerDecision decide(MatchStateSoA state, int playerIdx,
                                TickInput input, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        boolean hasBall = state.ballController == playerIdx;

        if (hasBall) {
            return decideWithBall(state, playerIdx, input, rng);
        } else {
            return decideWithoutBall(state, playerIdx, input, rng);
        }
    }

    /**
     * Calculate urgency factor based on shot budget remaining.
     * Higher urgency when running out of time or shots.
     */
    private float calculateShotUrgency(MatchStateSoA state, int teamSide) {
        int budget = (teamSide == 0) ? state.homeShotBudget : state.awayShotBudget;
        int taken = (teamSide == 0) ? state.homeShotsTaken : state.awayShotsTaken;
        int remaining = budget - taken;

        float timeProgress = (float) state.totalTicks / PhysicsConstants.TICKS_PER_MATCH;
        float timeRemaining = 1.0f - timeProgress;

        if (timeRemaining < 0.1f) {
            timeRemaining = 0.1f; // Minimum 10% time factor
        }

        // Urgency = shots needed / time remaining
        float urgency = remaining / timeRemaining;
        return Math.min(urgency, 3.0f); // Cap at 3x normal rate
    }

    private PlayerDecision decideWithBall(MatchStateSoA state, int playerIdx,
                                          TickInput input, ScopedRNG rng) {
        float ballX = state.ballX;
        float ballY = state.ballY;
        int teamSide = state.getPlayerTeamSide(playerIdx);
        PlayerRole role = state.getPlayerRole(playerIdx);

        // GK ALWAYS clears the ball
        if (role == PlayerRole.GK) {
            return PlayerDecision.clear(playerIdx);
        }

        // DEFENSIVE: If defender has ball in own half, clear it forward
        boolean inOwnHalf = (teamSide == 0 && ballX < -20) || (teamSide == 1 && ballX > 20);
        if (inOwnHalf && (role == PlayerRole.CB || role == PlayerRole.LB || role == PlayerRole.RB)) {
            return PlayerDecision.clear(playerIdx);
        }

        // Calculate possession time for this player
        long possessionTicks = state.totalTicks - state.lastBallAcquireTick[playerIdx];

        // Get shot budget info
        int budget = (teamSide == 0) ? state.homeShotBudget : state.awayShotBudget;
        int taken = (teamSide == 0) ? state.homeShotsTaken : state.awayShotsTaken;
        int remaining = budget - taken;
        boolean hasBudgetRemaining = remaining > 0;

        // Calculate distance to goal
        float shotDistance = getDistanceToGoal(state, playerIdx);

        // CONTROLLED SHOT DECISION
        if (shotDistance > 3 && hasBudgetRemaining) {
            // Base shot probability
            float baseProb;
            if (shotDistance < 10) {
                baseProb = 0.70f;
            } else if (shotDistance < 20) {
                baseProb = 0.55f;
            } else if (shotDistance < 35) {
                baseProb = 0.40f;
            } else {
                baseProb = 0.25f;
            }

            // Dynamic urgency boost based on shot budget
            float urgency = calculateShotUrgency(state, teamSide);
            float boostedProb = baseProb + urgency * 0.25f;
            boostedProb = Math.min(boostedProb, 0.95f); // Cap at 95%

            // FORCE SHOT after 6 ticks of possession (no infinite dribbling)
            if (possessionTicks >= MAX_POSSESSION_TICKS) {
                boostedProb = 0.90f; // Force shot
            }

            if (rng.nextFloat() < boostedProb) {
                float targetX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
                float targetY = rng.nextFloatRange(-1, 1);
                return PlayerDecision.shoot(playerIdx, targetX, targetY, 0.88f);
            }
        }

        // FORCED DECISION after max possession time
        if (possessionTicks >= MAX_POSSESSION_TICKS) {
            // If near goal, shoot
            if (shotDistance < 15) {
                float targetX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
                float targetY = rng.nextFloatRange(-1, 1);
                return PlayerDecision.shoot(playerIdx, targetX, targetY, 0.88f);
            }
            // If can pass forward, do so
            int passTarget = findForwardPassTarget(state, playerIdx);
            if (passTarget != -1) {
                return PlayerDecision.pass(playerIdx, passTarget, 0.7f);
            }
            // Otherwise clear the ball
            return PlayerDecision.clear(playerIdx);
        }

        // Normal play: pass if good option ahead
        int passTarget = findPassTarget(state, playerIdx, rng);
        if (passTarget != -1) {
            float selfX = state.getPlayerX(playerIdx);
            float targetX = state.getPlayerX(passTarget);
            float forwardProgress = (teamSide == 0) ? (targetX - selfX) : (selfX - targetX);
            if (forwardProgress > 5) {
                return PlayerDecision.pass(playerIdx, passTarget, 0.7f);
            }
        }

        // dribble forward toward goal
        float forwardDir = (teamSide == 0) ? 1 : -1;
        float dribbleX = ballX + forwardDir * 5;
        float dribbleY = ballY + rng.nextFloatRange(-3, 3);
        return PlayerDecision.moveTo(playerIdx, dribbleX, dribbleY, 0.7f);
    }

    private PlayerDecision decideWithoutBall(MatchStateSoA state, int playerIdx,
                                             TickInput input, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        int ballController = state.ballController;
        MatchPhase phase = state.phase;

        // If teammate has ball
        if (ballController != -1 && state.getPlayerTeamSide(ballController) == teamSide) {
            return supportTeammate(state, playerIdx, ballController, rng);
        }

        // Opposition has ball
        if (ballController != -1) {
            return pressOrDefend(state, playerIdx, ballController, rng);
        }

        // Loose ball
        return chaseLooseBall(state, playerIdx, rng);
    }

    private PlayerDecision supportTeammate(MatchStateSoA state, int playerIdx,
                                          int ballCarrier, ScopedRNG rng) {
        float carrierX = state.getPlayerX(ballCarrier);
        float carrierY = state.getPlayerY(ballCarrier);
        int teamSide = state.getPlayerTeamSide(playerIdx);

        // Find good support position
        float supportX = carrierX + (teamSide == 0 ? -8 : 8);
        float supportY = carrierY + rng.nextFloatRange(-12, 12);

        // Clamp to field
        supportX = Math.max(-50, Math.min(50, supportX));
        supportY = Math.max(-30, Math.min(30, supportY));

        return PlayerDecision.goToPosition(playerIdx, supportX, supportY);
    }

    private PlayerDecision pressOrDefend(MatchStateSoA state, int playerIdx,
                                         int opponentIdx, ScopedRNG rng) {
        float oppX = state.getPlayerX(opponentIdx);
        float oppY = state.getPlayerY(opponentIdx);
        int teamSide = state.getPlayerTeamSide(playerIdx);

        // Distance to opponent
        float selfX = state.getPlayerX(playerIdx);
        float selfY = state.getPlayerY(playerIdx);
        float dist = (float) Math.sqrt((oppX - selfX) * (oppX - selfX) + (oppY - selfY) * (oppY - selfY));

        // Pressing distance
        if (dist < SimulationConstants.PRESSING_TRIGGER_DISTANCE) {
            return PlayerDecision.press(playerIdx, opponentIdx);
        }

        // Defensive shape
        float defX = oppX + (teamSide == 0 ? 5 : -5);
        float defY = oppY;
        return PlayerDecision.goToPosition(playerIdx, defX, defY);
    }

    private PlayerDecision chaseLooseBall(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        // If player just shot the ball, don't chase for 30 ticks (allow rebounds)
        if (state.lastShooterId == playerIdx && state.totalTicks - state.lastShotTick < 30) {
            return PlayerDecision.hold(playerIdx);
        }
        // If player just cleared the ball, they should NOT chase it
        if (state.lastClearPlayerId == playerIdx && state.totalTicks - state.lastClearTick < 80) {
            return PlayerDecision.hold(playerIdx);
        }
        // Chase the ball
        return PlayerDecision.chaseBall(playerIdx, 0.9f);
    }

    /**
     * Find a pass target that moves ball forward.
     */
    private int findForwardPassTarget(MatchStateSoA state, int playerIdx) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        int bestTarget = -1;
        float bestProgress = 0;

        for (int i = 0; i < 22; i++) {
            if (i == playerIdx) continue;
            if (state.getPlayerTeamSide(i) != teamSide) continue;

            float selfX = state.getPlayerX(playerIdx);
            float targetX = state.getPlayerX(i);
            float forwardProgress = (teamSide == 0) ? (targetX - selfX) : (selfX - targetX);

            if (forwardProgress > bestProgress) {
                bestProgress = forwardProgress;
                bestTarget = i;
            }
        }

        return (bestProgress > 5) ? bestTarget : -1;
    }

    private int findPassTarget(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        int bestTarget = -1;
        float bestScore = 0;

        for (int i = 0; i < 22; i++) {
            if (i == playerIdx) continue;
            if (state.getPlayerTeamSide(i) != teamSide) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);

            // Skip if too close
            float selfX = state.getPlayerX(playerIdx);
            float selfY = state.getPlayerY(playerIdx);
            float dist = (float) Math.sqrt((px - selfX) * (px - selfX) + (py - selfY) * (py - selfY));
            if (dist < 5) continue;

            // Forward progress bonus
            float forwardBonus = (teamSide == 0 && px > selfX) ? 2.0f : (teamSide == 1 && px < selfX) ? 2.0f : 0;

            // Open space bonus
            float spaceScore = 1.0f + calculateSpace(state, i);

            float score = spaceScore + forwardBonus;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = i;
            }
        }

        return bestTarget;
    }

    private float calculateSpace(MatchStateSoA state, int playerIdx) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        float space = 0;

        for (int i = 0; i < 22; i++) {
            if (i == playerIdx) continue;
            if (state.getPlayerTeamSide(i) == teamSide) {
                float px = state.getPlayerX(playerIdx);
                float py = state.getPlayerY(playerIdx);
                float ix = state.getPlayerX(i);
                float iy = state.getPlayerY(i);
                float dist = (float) Math.sqrt((px - ix) * (px - ix) + (py - iy) * (py - iy));
                space += Math.min(dist, 10.0f);
            }
        }

        return space / 10.0f;
    }

    private float getDistanceToGoal(MatchStateSoA state, int playerIdx) {
        float px = state.getPlayerX(playerIdx);
        int teamSide = state.getPlayerTeamSide(playerIdx);
        float goalX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
        return Math.abs(px - goalX);
    }
}
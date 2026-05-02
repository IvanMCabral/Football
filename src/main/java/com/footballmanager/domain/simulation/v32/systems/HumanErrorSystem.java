package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.constants.PlayerConstants;

/**
 * Human Error System for V32.
 * Models first touch errors, misplaced passes, slips, heavy touches.
 */
public final class HumanErrorSystem {

    public HumanErrorSystem() {}

    /**
     * Gets error magnitude for a pass.
     */
    public float getPassError(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        float baseError = SimulationConstants.MISPLACED_PASS_BASE;

        // Reduce error for high skill
        float skillFactor = 1.0f - (state.playerOvr[playerIdx] / 99.0f) * 0.5f;

        // Increase error under pressure
        float pressureFactor = 1.0f;
        float distanceToOpponent = findClosestOpponentDistance(state, playerIdx);
        if (distanceToOpponent < 3.0f) {
            pressureFactor = 1.5f;
        } else if (distanceToOpponent < 5.0f) {
            pressureFactor = 1.2f;
        }

        // Low stamina increases error
        float staminaFactor = state.getPlayerEnergy(playerIdx) < 0.3f ? 1.3f : 1.0f;

        // Weather conditions
        float weatherFactor = 1.0f;
        if (state.windSpeed > 5.0f) {
            weatherFactor = 1.2f;
        }

        float error = baseError * skillFactor * pressureFactor * staminaFactor * weatherFactor;
        return error * SimulationConstants.ERROR_MAGNITUDE;
    }

    /**
     * Gets error magnitude for first touch.
     */
    public float getFirstTouchError(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        float baseError = SimulationConstants.FIRST_TOUCH_ERROR_BASE;

        // Reduce for technique
        float techFactor = 1.0f - (state.playerOvr[playerIdx] / 99.0f) * 0.4f;

        // Ball speed affects error
        float ballSpeed = (float) Math.sqrt(
            state.ballVX * state.ballVX + state.ballVY * state.ballVY
        );
        float speedFactor = 1.0f + ballSpeed * 0.02f;

        // Fatigue factor
        float fatigueFactor = state.getPlayerEnergy(playerIdx) < 0.4f ? 1.4f : 1.0f;

        return baseError * techFactor * speedFactor * fatigueFactor * SimulationConstants.ERROR_MAGNITUDE;
    }

    /**
     * Checks if a player slips.
     */
    public boolean checkSlip(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        float slipProb = SimulationConstants.SLIP_PROBABILITY;

        // Wet conditions
        if (state.humidity > 80 || state.rain > 0) {
            slipProb *= 2.0f;
        }

        // Low energy increases slip risk
        if (state.getPlayerEnergy(playerIdx) < 0.3f) {
            slipProb *= 1.5f;
        }

        // Sprinting increases slip chance
        float speed = (float) Math.sqrt(
            state.getPlayerVX(playerIdx) * state.getPlayerVX(playerIdx) +
            state.getPlayerVY(playerIdx) * state.getPlayerVY(playerIdx)
        );
        if (speed > 6.0f) {
            slipProb *= 1.5f;
        }

        return rng.nextFloat() < slipProb;
    }

    /**
     * Gets heavy touch probability.
     */
    public boolean checkHeavyTouch(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        float prob = SimulationConstants.HEAVY_TOUCH_PROBABILITY;

        // Low stamina
        if (state.getPlayerEnergy(playerIdx) < 0.25f) {
            prob *= 1.8f;
        }

        // High ball speed
        float ballSpeed = (float) Math.sqrt(
            state.ballVX * state.ballVX + state.ballVY * state.ballVY
        );
        if (ballSpeed > 20.0f) {
            prob *= 1.5f;
        }

        return rng.nextFloat() < prob;
    }

    /**
     * Gets shooting error (miss the target).
     */
    public float getShootingError(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        float baseError = 0.3f; // 30% of shots miss target on average

        // Reduce for high shooting skill
        float skill = state.playerOvr[playerIdx] / 99.0f;
        float skillFactor = 1.0f - skill * 0.5f;

        // Distance increases error
        float shotDistance = (float) Math.sqrt(
            (state.getPlayerX(playerIdx) - state.ballX) * (state.getPlayerX(playerIdx) - state.ballX) +
            (state.getPlayerY(playerIdx) - state.ballY) * (state.getPlayerY(playerIdx) - state.ballY)
        );
        float distanceFactor = 1.0f + shotDistance * 0.005f;

        // Low energy affects accuracy
        float fatigueFactor = state.getPlayerEnergy(playerIdx) < 0.35f ? 1.4f : 1.0f;

        return baseError * skillFactor * distanceFactor * fatigueFactor;
    }

    private float findClosestOpponentDistance(MatchStateSoA state, int playerIdx) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        float minDist = Float.MAX_VALUE;

        for (int i = 0; i < 22; i++) {
            if (state.getPlayerTeamSide(i) != teamSide) {
                float dx = state.getPlayerX(i) - state.getPlayerX(playerIdx);
                float dy = state.getPlayerY(i) - state.getPlayerY(playerIdx);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < minDist) minDist = dist;
            }
        }

        return minDist;
    }
}

package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.TickInput;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;

/**
 * Momentum system for V32.
 * Tracks team momentum which affects performance.
 */
public final class MomentumSystem {

    public MomentumSystem() {}

    /**
     * Updates momentum based on match events.
     */
    public void update(MatchStateSoA state, TickInput input, ScopedRNG rng) {
        float homeMomentum = state.homeMomentum;
        float awayMomentum = state.awayMomentum;

        // Momentum naturally decays toward 0.5 (neutral)
        float decay = SimulationConstants.MOMENTUM_DECAY;
        if (homeMomentum > 0.5f) {
            homeMomentum = Math.max(0.5f, homeMomentum - decay);
        } else if (homeMomentum < 0.5f) {
            homeMomentum = Math.min(0.5f, homeMomentum + decay);
        }

        if (awayMomentum > 0.5f) {
            awayMomentum = Math.max(0.5f, awayMomentum - decay);
        } else if (awayMomentum < 0.5f) {
            awayMomentum = Math.min(0.5f, awayMomentum + decay);
        }

        // Possession affects momentum
        if (state.possessionTeam == 0) {
            homeMomentum = Math.min(1.0f, homeMomentum + 0.0005f);
            awayMomentum = Math.max(0.0f, awayMomentum - 0.0005f);
        } else if (state.possessionTeam == 1) {
            awayMomentum = Math.min(1.0f, awayMomentum + 0.0005f);
            homeMomentum = Math.max(0.0f, homeMomentum - 0.0005f);
        }

        // Attacking in final third slightly increases momentum
        if (state.ballX > 30 && state.possessionTeam == 0) {
            homeMomentum = Math.min(1.0f, homeMomentum + 0.0002f);
        }
        if (state.ballX < -30 && state.possessionTeam == 1) {
            awayMomentum = Math.min(1.0f, awayMomentum + 0.0002f);
        }

        state.homeMomentum = homeMomentum;
        state.awayMomentum = awayMomentum;
    }

    /**
     * Adds momentum swing after goal.
     */
    public void onGoal(MatchStateSoA state, int scoringTeam, ScopedRNG rng) {
        float swing = SimulationConstants.GOAL_MOMENTUM_SWING;
        if (scoringTeam == 0) {
            state.homeMomentum = Math.min(1.0f, state.homeMomentum + swing);
            state.awayMomentum = Math.max(0.0f, state.awayMomentum - swing * 0.5f);
        } else {
            state.awayMomentum = Math.min(1.0f, state.awayMomentum + swing);
            state.homeMomentum = Math.max(0.0f, state.homeMomentum - swing * 0.5f);
        }
    }

    /**
     * Applies momentum modifier to a value.
     * @param baseValue Base value (e.g., 0.5 for 50% probability)
     * @param teamMomentum Team momentum (0-1)
     * @return Modified value
     */
    public float applyMomentum(float baseValue, float teamMomentum) {
        // Momentum ranges from 0.5 to 1.0, so it adds up to +50% to base
        float momentumBonus = (teamMomentum - 0.5f) * baseValue;
        return Math.max(0.0f, Math.min(1.0f, baseValue + momentumBonus));
    }

    /**
     * Gets performance multiplier from momentum.
     */
    public float getPerformanceMultiplier(float momentum) {
        // Momentum 0.5 = 1.0x, momentum 1.0 = 1.2x, momentum 0 = 0.8x
        return 0.8f + momentum * 0.4f;
    }
}

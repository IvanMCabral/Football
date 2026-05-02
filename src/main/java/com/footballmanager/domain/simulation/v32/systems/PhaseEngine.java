package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.TickInput;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.enums.MatchPhase;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;
import com.footballmanager.domain.simulation.v32.math.Vector2D;

/**
 * Phase Engine for V32 - determines match phase and transitions.
 */
public final class PhaseEngine {

    public PhaseEngine() {}

    /**
     * Updates match phase based on current state.
     */
    public void update(MatchStateSoA state, TickInput input, ScopedRNG rng) {
        MatchPhase currentPhase = state.phase;

        // Determine new phase based on conditions
        MatchPhase newPhase = determinePhase(state);

        if (newPhase != currentPhase) {
            state.phase = newPhase;
        }
    }

    private MatchPhase determinePhase(MatchStateSoA state) {
        // Ball in air - IN_AIR zone
        if (state.ballZ > 0.5f) {
            return MatchPhase.CHANCE; // Likely a cross or shot
        }

        // Ball loose
        if (state.ballController == -1) {
            return MatchPhase.LOOSE_BALL; // or could be DEFENSIVE_TRANSITION
        }

        int possessingTeam = state.possessionTeam;
        float ballX = state.ballX;

        // Determine zone
        boolean inHomeHalf = ballX < -10;
        boolean inAwayHalf = ballX > 10;
        boolean inFinalThird = ballX > 30 || ballX < -30;

        if (state.possessionTeam == -1) {
            // Loose ball - transition phase
            return MatchPhase.DEFENSIVE_TRANSITION;
        }

        if (possessingTeam == 0) {
            // Home team has ball
            if (inFinalThird) {
                if (inAwayHalf) {
                    return MatchPhase.CHANCE;
                }
                return MatchPhase.ATTACKING;
            } else if (inHomeHalf) {
                return MatchPhase.BUILD_UP;
            } else {
                return MatchPhase.ATTACKING_TRANSITION;
            }
        } else {
            // Away team has ball
            if (inFinalThird) {
                if (inHomeHalf) {
                    return MatchPhase.CHANCE;
                }
                return MatchPhase.ATTACKING;
            } else if (inAwayHalf) {
                return MatchPhase.BUILD_UP;
            } else {
                return MatchPhase.ATTACKING_TRANSITION;
            }
        }
    }

    /**
     * Checks if phase transition should occur.
     */
    public boolean shouldTransition(MatchStateSoA state, MatchPhase newPhase) {
        // Minimum duration in current phase before transition
        // Could add hysteresis logic here
        return true;
    }
}

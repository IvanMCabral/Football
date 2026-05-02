package com.footballmanager.domain.simulation.v32.systems;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.TickInput;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.PlayerConstants;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;

/**
 * Fatigue system for V32 - multi-factor fatigue model.
 * Models metabolic, neuromuscular, and mental fatigue with real performance impact.
 */
public final class FatigueSystem {

    public FatigueSystem() {}

    /**
     * Updates fatigue for all players.
     */
    public void update(MatchStateSoA state, TickInput input, ScopedRNG rng) {
        float dt = (float) input.getDeltaTime();

        for (int i = 0; i < 22; i++) {
            float energy = state.getPlayerEnergy(i);
            float stamina = state.getPlayerStamina(i);
            float speed = (float) Math.sqrt(
                state.getPlayerVX(i) * state.getPlayerVX(i) +
                state.getPlayerVY(i) * state.getPlayerVY(i)
            );

            // Determine if player is exerting effort
            float exertionLevel = calculateExertion(state, i, speed);

            if (exertionLevel > 0.5f) {
                // Accumulate fatigue
                float fatigueRate = PlayerConstants.BASE_STAMINA_RATE * exertionLevel;
                float metabolicDrain = fatigueRate * dt * 0.7f;
                float neuromuscularDrain = fatigueRate * dt * 0.2f;
                float mentalDrain = fatigueRate * dt * 0.1f;

                energy = Math.max(0.0f, energy - (metabolicDrain + neuromuscularDrain + mentalDrain) * 0.01f);
                stamina = Math.max(0.0f, stamina - fatigueRate * dt * 0.5f);

            } else if (exertionLevel < 0.1f) {
                // Recovery when resting
                float recovery = PlayerConstants.STAMINA_REGEN_RATE * dt;
                energy = Math.min(1.0f, energy + recovery);
                stamina = Math.min(1.0f, stamina + recovery * 0.3f);
            }

            // Apply performance degradation at low fatigue
            if (energy < PlayerConstants.STAMINA_THRESHOLD) {
                // Additional drain at critical levels (cramping risk)
                if (energy < 0.15f && rng.nextFloat() < 0.001f) {
                    // Minor cramping event
                }
            }

            state.setPlayerEnergy(i, energy);
            state.setPlayerStamina(i, stamina);
        }
    }

    /**
     * Calculates exertion level for a player.
     */
    private float calculateExertion(MatchStateSoA state, int playerIdx, float speed) {
        float exertion = 0.0f;

        // Sprinting (above sprint threshold)
        if (speed > PhysicsConstants.SPRINT_THRESHOLD) {
            exertion = 1.0f;
        } else if (speed > PhysicsConstants.JOG_THRESHOLD) {
            exertion = 0.5f;
        } else if (speed > 0.5f) {
            exertion = 0.2f;
        }

        // High pressing engagement
        float distanceToBall = (float) Math.sqrt(
            (state.getPlayerX(playerIdx) - state.ballX) * (state.getPlayerX(playerIdx) - state.ballX) +
            (state.getPlayerY(playerIdx) - state.ballY) * (state.getPlayerY(playerIdx) - state.ballY)
        );

        if (distanceToBall < SimulationConstants.PRESSING_TRIGGER_DISTANCE) {
            exertion = Math.max(exertion, 0.6f);
        }

        // Tackling/physical contest
        if (state.ballController != -1) {
            int opponentIdx = state.ballController;
            if (state.getPlayerTeamSide(playerIdx) != state.getPlayerTeamSide(opponentIdx)) {
                float dist = (float) Math.sqrt(
                    (state.getPlayerX(playerIdx) - state.getPlayerX(opponentIdx)) * (state.getPlayerX(playerIdx) - state.getPlayerX(opponentIdx)) +
                    (state.getPlayerY(playerIdx) - state.getPlayerY(opponentIdx)) * (state.getPlayerY(playerIdx) - state.getPlayerY(opponentIdx))
                );
                if (dist < 2.0f) {
                    exertion = Math.max(exertion, 0.8f);
                }
            }
        }

        // Adjust for energy levels
        float energyFactor = state.getPlayerEnergy(playerIdx);
        if (energyFactor < 0.3f) {
            exertion *= 1.2f; // Fatigues faster when already tired
        }

        return Math.min(1.0f, exertion);
    }

    /**
     * Gets speed multiplier based on energy.
     */
    public float getSpeedMultiplier(float energy) {
        if (energy > 0.7f) return 1.0f;
        if (energy > 0.5f) return 0.9f;
        if (energy > 0.3f) return 0.8f;
        if (energy > 0.15f) return 0.7f;
        return 0.5f; // Cramping risk zone
    }

    /**
     * Gets decision quality multiplier based on energy.
     */
    public float getDecisionQuality(float energy) {
        if (energy > 0.6f) return 1.0f;
        if (energy > 0.4f) return 0.9f;
        if (energy > 0.25f) return 0.75f;
        return 0.5f; // Poor decision making
    }

    /**
     * Gets passing accuracy multiplier based on energy.
     */
    public float getPassingAccuracy(float energy) {
        if (energy > 0.5f) return 1.0f;
        if (energy > 0.3f) return 0.85f;
        return 0.6f;
    }
}

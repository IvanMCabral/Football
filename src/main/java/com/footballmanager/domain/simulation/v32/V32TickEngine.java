package com.footballmanager.domain.simulation.v32;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.enums.MatchPhase;
import com.footballmanager.domain.simulation.v32.math.Vector2D;
import com.footballmanager.domain.simulation.v32.math.Vector3D;
import com.footballmanager.domain.simulation.v32.systems.FatigueSystem;
import com.footballmanager.domain.simulation.v32.systems.MomentumSystem;
import com.footballmanager.domain.simulation.v32.systems.HumanErrorSystem;
import com.footballmanager.domain.simulation.v32.systems.PhaseEngine;
import com.footballmanager.domain.simulation.v32.systems.OffBallAI;

/**
 * V32 Tick Engine - executes one simulation tick.
 * Called 60 times per second of match time.
 */
public final class V32TickEngine {

    private static final FatigueSystem fatigueSystem = new FatigueSystem();
    private static final MomentumSystem momentumSystem = new MomentumSystem();
    private static final HumanErrorSystem errorSystem = new HumanErrorSystem();
    private static final PhaseEngine phaseEngine = new PhaseEngine();
    private static final OffBallAI offBallAI = new OffBallAI();

    public V32TickEngine() {}

    /**
     * Executes one simulation tick.
     * @param state Match state (mutated in place)
     * @param input Tick input
     * @param output Tick output (events accumulated here)
     * @param rng RNG provider
     * @param userTeamSide -1 for no user, 0 for home, 1 for away
     */
    public void execute(MatchStateSoA state, TickInput input, TickOutput output,
                        ScopedRNG rng, int userTeamSide) {

        // 1. Check for half-time / full-time transitions
        handlePhaseTransitions(state, input, output);

        if (state.matchOver) return;

        // 2. Update phase engine
        phaseEngine.update(state, input, rng);

        // 3. Update momentum
        momentumSystem.update(state, input, rng);

        // 4. Process player decisions (AI or human)
        processPlayerDecisions(state, input, output, rng, userTeamSide);

        // 5. Update player physics (movement)
        updatePlayerPhysics(state, input, rng);

        // 6. Update ball physics
        updateBallPhysics(state, input, rng);

        // 7. Handle collisions
        handleCollisions(state, input, rng);

        // 8. Update fatigue
        fatigueSystem.update(state, input, rng);

        // 9. Check for goals
        checkGoals(state, input, output, rng);

        // 10. Update possession statistics
        updatePossession(state);

        state.totalTicks++;
    }

    private void handlePhaseTransitions(MatchStateSoA state, TickInput input, TickOutput output) {
        long tick = input.getTickNumber();

        // Half-time transition
        if (tick == PhysicsConstants.TICKS_PER_HALF && !state.firstHalfOver) {
            state.firstHalfOver = true;
            state.phase = MatchPhase.HALF_TIME;
            output.addEvent(MatchEvent.halfTime((int) tick));
            // Reset positions would happen here
        }

        // Full-time
        if (tick >= PhysicsConstants.TICKS_PER_MATCH) {
            state.matchOver = true;
            state.phase = MatchPhase.FULL_TIME;
            output.addEvent(MatchEvent.fullTime((int) tick));
            output.setMatchEnded(state.homeScore, state.awayScore, "Full Time");
        }
    }

    private void processPlayerDecisions(MatchStateSoA state, TickInput input,
                                         TickOutput output, ScopedRNG rng, int userTeamSide) {
        // For each player, determine action
        for (int i = 0; i < 22; i++) {
            int teamSide = state.getPlayerTeamSide(i);

            // Skip if this is user's team (would be controlled externally)
            if (userTeamSide >= 0 && teamSide == userTeamSide) {
                // User controls this team - decisions come from external input
                continue;
            }

            // AI decision for this player
            PlayerDecision decision = offBallAI.decide(state, i, input, rng);

            // Store decision in state if needed for later processing
            // (In full implementation, would cache decisions)
            applyDecision(state, i, decision, rng);
        }
    }

    private void applyDecision(MatchStateSoA state, int playerIdx,
                                PlayerDecision decision, ScopedRNG rng) {
        switch (decision.decision) {
            case MOVE_TO:
            case GO_TO_POSITION:
                moveToward(state, playerIdx, decision.targetX, decision.targetY,
                           decision.urgency, rng);
                break;

            case CHASE_BALL:
                chaseBall(state, playerIdx, decision.urgency, rng);
                break;

            case PRESS:
                pressOpponent(state, playerIdx, decision.targetPlayer, rng);
                break;

            case PASS:
                executePass(state, playerIdx, decision.targetPlayer, decision.power, rng);
                break;

            case SHOOT:
                executeShot(state, playerIdx, decision.targetX, decision.targetY,
                           decision.power, rng);
                break;

            case TACKLE:
                attemptTackle(state, playerIdx, decision.targetPlayer, rng);
                break;

            case CLEAR:
                clearBall(state, playerIdx, rng);
                break;

            case IDLE:
            case HOLD:
            case COVER:
            case MARK:
            case SUPPORT:
            default:
                // Slight drift toward formation position
                break;
        }
    }

    private void moveToward(MatchStateSoA state, int playerIdx,
                            float tx, float ty, float urgency, ScopedRNG rng) {
        float px = state.getPlayerX(playerIdx);
        float py = state.getPlayerY(playerIdx);
        float speed = PhysicsConstants.MAX_ACCELERATION * urgency;

        float dx = tx - px;
        float dy = ty - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > 0.1f) {
            float vx = (dx / dist) * speed;
            float vy = (dy / dist) * speed;
            state.setPlayerVX(playerIdx, vx);
            state.setPlayerVY(playerIdx, vy);
        } else {
            state.setPlayerVX(playerIdx, 0);
            state.setPlayerVY(playerIdx, 0);
        }
    }

    private void chaseBall(MatchStateSoA state, int playerIdx,
                           float urgency, ScopedRNG rng) {
        float targetX = state.ballX;
        float targetY = state.ballY;
        moveToward(state, playerIdx, targetX, targetY, urgency, rng);
    }

    private void pressOpponent(MatchStateSoA state, int playerIdx,
                               int opponentIdx, ScopedRNG rng) {
        if (opponentIdx < 0 || opponentIdx >= 22) return;
        float ox = state.getPlayerX(opponentIdx);
        float oy = state.getPlayerY(opponentIdx);
        moveToward(state, playerIdx, ox, oy, 0.9f, rng);
    }

    private void executePass(MatchStateSoA state, int playerIdx,
                             int targetIdx, float power, ScopedRNG rng) {
        if (targetIdx < 0 || targetIdx >= 22) return;
        if (state.getPlayerTeamSide(playerIdx) != state.getPlayerTeamSide(targetIdx)) return;

        float tx = state.getPlayerX(targetIdx);
        float ty = state.getPlayerY(targetIdx);

        // Apply error
        float errorMagnitude = errorSystem.getPassError(state, playerIdx, rng);
        tx += rng.nextFloatRange(-errorMagnitude, errorMagnitude);
        ty += rng.nextFloatRange(-errorMagnitude, errorMagnitude);

        BallPhysicsEngine.applyKick(state, tx, ty, power, 0.2, rng);
    }

    private void executeShot(MatchStateSoA state, int playerIdx,
                              float tx, float ty, float power, ScopedRNG rng) {
        // Get shooting direction
        float px = state.getPlayerX(playerIdx);
        float py = state.getPlayerY(playerIdx);
        float dx = tx - px;
        float dy = ty - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > 0.1f) {
            float kickX = tx + rng.nextFloatRange(-1.0f, 1.0f); // Slight inaccuracy
            float kickY = ty + rng.nextFloatRange(-1.0f, 1.0f);
            BallPhysicsEngine.applyKick(state, kickX, kickY, power, 0.1f, rng);

            state.getPlayerTeamSide(playerIdx) == 0 ?
                state.homeShots++ : state.awayShots++;
        }
    }

    private void attemptTackle(MatchStateSoA state, int playerIdx,
                                int opponentIdx, ScopedRNG rng) {
        if (opponentIdx < 0 || opponentIdx >= 22) return;

        // Check if opponent has ball
        if (state.ballController == opponentIdx) {
            // Tackle attempt - success based on attributes
            float tacklerStrength = state.getPlayerStrength(playerIdx);
            float dribblerSkill = state.playerOvr[opponentIdx] / 99.0f;

            if (rng.nextFloat() < tacklerStrength - dribblerSkill * 0.5f) {
                // Tackle successful - ball becomes loose
                state.ballController = -1;
                state.possessionPlayer = -1;
                state.possessionTeam = -1;
            }
        }
    }

    private void clearBall(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        // Kick ball up field
        float clearX = state.getPlayerX(playerIdx) > 0 ? -50 : 50;
        float clearY = rng.nextFloatRange(-20, 20);
        BallPhysicsEngine.applyKick(state, clearX, clearY, 0.9f, 0.5f, rng);
    }

    private void updatePlayerPhysics(MatchStateSoA state, TickInput input, ScopedRNG rng) {
        float dt = (float) input.getDeltaTime();

        for (int i = 0; i < 22; i++) {
            float vx = state.getPlayerVX(i);
            float vy = state.getPlayerVY(i);

            // Apply velocity limits
            float speed = (float) Math.sqrt(vx * vx + vy * vy);
            float maxSpeed = PhysicsConstants.MAX_PLAYER_SPEED * state.getPlayerEnergy(i);

            if (speed > maxSpeed) {
                vx = (vx / speed) * maxSpeed;
                vy = (vy / speed) * maxSpeed;
                state.setPlayerVX(i, vx);
                state.setPlayerVY(i, vy);
            }

            // Update position
            float nx = state.getPlayerX(i) + vx * dt;
            float ny = state.getPlayerY(i) + vy * dt;

            // Clamp to field
            nx = Math.max(-PhysicsConstants.FIELD_LENGTH / 2, Math.min(PhysicsConstants.FIELD_LENGTH / 2, nx));
            ny = Math.max(-PhysicsConstants.FIELD_WIDTH / 2, Math.min(PhysicsConstants.FIELD_WIDTH / 2, ny));

            state.setPlayerX(i, nx);
            state.setPlayerY(i, ny);

            // Update rotation to face movement direction
            if (speed > 0.1f) {
                state.setPlayerRotation(i, (float) Math.atan2(vy, vx));
            }

            // Decay velocity slightly (friction)
            state.setPlayerVX(i, vx * 0.95f);
            state.setPlayerVY(i, vy * 0.95f);
        }
    }

    private void updateBallPhysics(MatchStateSoA state, TickInput input, ScopedRNG rng) {
        BallPhysicsEngine.updateBall(state, input, rng);
    }

    private void handleCollisions(MatchStateSoA state, TickInput input, ScopedRNG rng) {
        // Player-ball collisions
        for (int i = 0; i < 22; i++) {
            if (CollisionResolver.canControlBall(state, i)) {
                if (state.ballController == -1 || state.ballController == i) {
                    state.ballController = i;
                    state.possessionTeam = state.getPlayerTeamSide(i);
                    state.possessionPlayer = i;
                }
            }

            CollisionResolver.resolvePlayerBallCollision(state, i, rng);
        }

        // Player-player collisions
        for (int i = 0; i < 22; i++) {
            for (int j = i + 1; j < 22; j++) {
                CollisionResolver.resolvePlayerPlayerCollision(state, i, j, rng);
            }
        }
    }

    private void checkGoals(MatchStateSoA state, TickInput input,
                            TickOutput output, ScopedRNG rng) {
        int goal = BallPhysicsEngine.checkGoal(state);

        if (goal != 0) {
            int scoringTeam = (goal == 1) ? 0 : 1; // 1 = home, -1 = away
            int scoringPlayer = state.ballController;

            // Update score
            if (goal == 1) {
                state.homeScore++;
            } else {
                state.awayScore++;
            }

            // Log goal event
            float xg = calculateXG(state);
            MatchEvent goalEvent = MatchEvent.goal(
                (int) input.getTickNumber(),
                input.getMinute(),
                scoringTeam,
                scoringPlayer,
                scoringTeam == 0 ? "Home Goal!" : "Away Goal!",
                xg
            );
            output.addEvent(goalEvent);

            // Momentum swing
            if (scoringTeam == 0) {
                state.homeMomentum = Math.min(1.0f, state.homeMomentum + SimulationConstants.GOAL_MOMENTUM_SWING);
                state.awayMomentum = Math.max(0.0f, state.awayMomentum - SimulationConstants.GOAL_MOMENTUM_SWING);
            } else {
                state.awayMomentum = Math.min(1.0f, state.awayMomentum + SimulationConstants.GOAL_MOMENTUM_SWING);
                state.homeMomentum = Math.max(0.0f, state.homeMomentum - SimulationConstants.GOAL_MOMENTUM_SWING);
            }

            // Reset ball to center
            state.ballX = 0;
            state.ballY = 0;
            state.ballZ = 0;
            state.ballVX = 0;
            state.ballVY = 0;
            state.ballVZ = 0;
            state.ballController = -1;
            state.possessionTeam = -1;
            state.possessionPlayer = -1;
        }
    }

    private float calculateXG(MatchStateSoA state) {
        // Simple xG calculation
        float dx = PhysicsConstants.AWAY_GOAL_X - state.ballX;
        float dy = state.ballY; // Y distance from center
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        double xg = SimulationConstants.XG_BASE;
        xg -= distance * 0.01; // Distance penalty

        return (float) Math.max(0.01, Math.min(0.95, xg));
    }

    private void updatePossession(MatchStateSoA state) {
        // Decay possession stats
        float decay = 0.001f;
        if (state.possessionTeam == 0) {
            state.homePossession += 0.001f;
        } else if (state.possessionTeam == 1) {
            state.awayPossession += 0.001f;
        }

        // Normalize
        float total = state.homePossession + state.awayPossession;
        if (total > 0) {
            state.homePossession /= total;
            state.awayPossession /= total;
        }
    }
}

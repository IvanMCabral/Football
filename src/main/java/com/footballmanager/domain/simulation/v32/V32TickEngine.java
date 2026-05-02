package com.footballmanager.domain.simulation.v32;

import com.footballmanager.domain.simulation.v32.state.MatchStateSoA;
import com.footballmanager.domain.simulation.v32.rng.ScopedRNG;
import com.footballmanager.domain.simulation.v32.constants.PhysicsConstants;
import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;
import com.footballmanager.domain.simulation.v32.enums.MatchPhase;
import com.footballmanager.domain.simulation.v32.systems.FatigueSystem;
import com.footballmanager.domain.simulation.v32.systems.MomentumSystem;
import com.footballmanager.domain.simulation.v32.systems.HumanErrorSystem;
import com.footballmanager.domain.simulation.v32.systems.PhaseEngine;
import com.footballmanager.domain.simulation.v32.systems.OffBallAI;
import com.footballmanager.domain.simulation.v32.enums.PlayerRole;

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

    /** Shot decision lock — player cannot generate new SHOOT approvals for this many ticks.
     *  Prevents tick-level reevaluation from producing repeated SHOOT approvals per possession.
     *  600 ticks ≈ 10 seconds of match time. Enough to prevent per-tick spam,
     *  but short enough that a player can shoot again after repositioning or passing. */
    private static final int DECISION_COOLDOWN = 600;

    public V32TickEngine() {}

    /**
     * Executes one simulation tick.
     * Pipeline order (P1→P2→P3→P4→P5→P6) as specified in SDD.
     * @param state Match state (mutated in place)
     * @param input Tick input
     * @param output Tick output (events accumulated here)
     * @param rng RNG provider
     * @param userTeamSide -1 for no user, 0 for home, 1 for away
     */
    public void execute(MatchStateSoA state, TickInput input, TickOutput output,
                        ScopedRNG rng, int userTeamSide) {

        // ═══════════════════════════════════════════════════════════════
        // PHASE 1: SYNCHRONIZATION (Read state from previous tick)
        // ═══════════════════════════════════════════════════════════════
        state.totalTicks++;
        state.shotTakenThisTick = false;
        state.pipelineApprovedShot = false;

        // Save previous ball position for goal detection
        state.prevBallX = state.ballX;
        state.prevBallY = state.ballY;

        // ═══════════════════════════════════════════════════════════════
        // PHASE 2: POSSESSION TRACKING (Before AI decisions)
        // ═══════════════════════════════════════════════════════════════
        // Track possession at PLAYER level — a new possession starts whenever
        // a player acquires the ball after it was loose, regardless of team.
        //
        // PossessionSeq increments when:
        //   1. ballController changes from -1 to a player (new acquisition after loose)
        //   2. ballController changes to a different player after loose ball phase
        //   3. possession changes team (existing behavior — preserved)
        //
        // PossessionSeq does NOT increment on same-team passes if ball was never loose.
        //
        // NEW FIELDS:
        //   looseBallStartTick: tick when ball became loose (-1 if controlled)
        //   prevBallController: ballController from previous tick

        int prevController = state.prevBallController;
        int currController = state.ballController;
        boolean wasLoose = (prevController == -1);
        boolean isLoose = (currController == -1);

        // Track loose ball start time
        if (isLoose) {
            if (prevController != -1 && state.looseBallStartTick == -1) {
                // Ball just became loose — record the tick
                state.looseBallStartTick = state.totalTicks;
            }
        } else {
            // Ball is controlled — clear loose ball marker
            state.looseBallStartTick = -1;
        }

        // Update prevBallController for next tick
        state.prevBallController = currController;

        if (currController != -1) {
            int currentTeam = state.getPlayerTeamSide(currController);

            if (state.possessionSeq == 0) {
                // First possession of match — initialize once
                state.possessionSeq = 1;
                state.lastPossessionChangeTick = state.totalTicks;
                state.teamSideOfLastPossession = currentTeam;
                state.possessionTeamSide = currentTeam;
                state.totalPossessions = 1;
                state.possessionStartTick = state.totalTicks;
                state.shotsInCurrentPossession = 0;
                state.lastShootApprovalPlayer = -1;
                state.lastShootApprovalTick = 0;
            } else {
                // Check for new possession conditions
                boolean newPossession = false;

                if (wasLoose && prevController != -1) {
                    // Ball was loose → now acquired by a player
                    // This is a new possession (attack/possession sequence)
                    newPossession = true;
                } else if (currentTeam != state.teamSideOfLastPossession) {
                    // Team possession changed
                    newPossession = true;
                }

                if (newPossession) {
                    // END previous possession stats
                    if (state.shotsInCurrentPossession > 0) {
                        state.possessionsWithShootApproval++;
                        state.totalPossessionDuration += (state.totalTicks - state.possessionStartTick);
                    }
                    state.possessionSeq++;
                    state.totalPossessions++;
                    state.lastPossessionChangeTick = state.totalTicks;
                    state.teamSideOfLastPossession = currentTeam;
                    state.possessionTeamSide = currentTeam;
                    state.possessionStartTick = state.totalTicks;
                    state.shotsInCurrentPossession = 0;
                    state.lastShootApprovalPlayer = -1;
                    state.lastShootApprovalTick = 0;
                }
                // When same team keeps ball (no loose phase): NO increment — possession continues
            }
        }
        // When ballController == -1: no action — loose ball, possession seq holds

        // Handle half-time / full-time transitions
        if (!state.firstHalfOver && state.totalTicks == PhysicsConstants.TICKS_PER_HALF) {
            state.firstHalfOver = true;
            state.phase = MatchPhase.HALF_TIME;
            output.addEvent(MatchEvent.halfTime((int) state.totalTicks));
        }

        if (state.totalTicks >= PhysicsConstants.TICKS_PER_MATCH) {
            state.matchOver = true;
            state.phase = MatchPhase.FULL_TIME;
            output.addEvent(MatchEvent.fullTime((int) state.totalTicks));
            output.setMatchEnded(state.homeScore, state.awayScore, "Full Time");
            if (playerId == 12 && state.totalTicks >= 216005 && state.totalTicks <= 216010 && state.progressionTransfers == 8010) {
                        System.out.printf("[DEBUG] executePass: playerId=%d targetIdx=%d power=%.2f skipVelocityDamping=%s justKicked(BEFORE)=%s%n",
                            playerId, targetIdx, power, state.skipVelocityDamping, state.justKicked);
                    }
                    return;
        }

        if (state.matchOver) return;

        // ═══════════════════════════════════════════════════════════════
        // PHASE 3: AI DECISIONS (OffBallAI + Shot Selection)
        // ═══════════════════════════════════════════════════════════════
        // OffBallAI decision for non-ballController players only.
        // ballController is EXCLUDED — only the shot pipeline may authorize SHOOT.
        // Invariant A: OffBallAI CANNOT generate executeShot calls for the ballController.
        for (int i = 0; i < 22; i++) {
            if (i == state.ballController) continue; // INVARIANT A

            int teamSide = state.getPlayerTeamSide(i);

            // Skip if this is user's team (would be controlled externally)
            if (userTeamSide >= 0 && teamSide == userTeamSide) {
                continue;
            }

            // AI decision for this player
            PlayerDecision decision = offBallAI.decide(state, i, input, rng);
            applyDecision(state, i, decision, output, rng);
        }

        // Shot Selection — pipeline overrides OffBallAI for ballController
        if (state.ballController != -1) {
            // Reset shot flag before pipeline override
            state.shotTakenThisTick = false;
            state.pipelineApprovedShot = false;

            int controller = state.ballController;
            int currentTick = (int) state.totalTicks;
            int ticksSinceLastShotDecision = currentTick - state.lastShotDecisionTick[controller];

            if (ticksSinceLastShotDecision < DECISION_COOLDOWN) {
                // Player is shot-locked: cooldown is handled as a viability penalty at STEP5.
                // The pipeline runs normally — if the player has a high-quality shot despite
                // the cooldown penalty, it should be approved, not discarded.
                // DRIBBLE/PASS decisions are applied normally.
                // NOTE: We do NOT run the pipeline twice anymore (no saved/restore pattern).
                int savedShotDecisionTick = state.lastShotDecisionTick[controller];
                PlayerDecision lockedDecision = executeShotSelectionPipeline(state, controller, rng);
                if (lockedDecision != null && lockedDecision.decision == PlayerDecision.Decision.SHOOT) {
                    // Pipeline returned SHOOT for a locked player.
                    // This means the shot passed the cooldown-adjusted STEP5 viability threshold.
                    // The cooldown penalty is the anti-spam mechanism — don't discard it here.
                    // Apply the SHOOT and let executeShot() handle its own 5-tick cooldown.
                    state.shotTakenThisTick = true;
                    state.pipelineApprovedShot = true;
                    applyDecision(state, controller, lockedDecision, output, rng);
                } else if (lockedDecision != null) {
                    applyDecision(state, controller, lockedDecision, output, rng);
                }
                // If pipeline returns null, do nothing — physics moves ball
            } else {
                // No recent shot — run the pipeline normally
                PlayerDecision shotDecision = executeShotSelectionPipeline(state, controller, rng);
                if (shotDecision != null) {
                    // Mark as pipeline-approved ONLY when pipeline explicitly returns SHOOT
                    // This ensures only pipeline-approved shots (not OffBallAI direct shots) are counted
                    if (shotDecision.decision == PlayerDecision.Decision.SHOOT) {
                        state.pipelineApprovedShot = true;
                        // AUDIT: approved SHOOT reached applyDecision SHOOT case
                        state.approvalReachedApplyDecision++;
                    }
                    // Pipeline returned decision (SHOOT, DRIBBLE, PASS, etc.) → override OffBallAI
                    applyDecision(state, controller, shotDecision, output, rng);
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // PHASE 4: APPLY DECISIONS (already done in Phase 3 via applyDecision)
        // ═══════════════════════════════════════════════════════════════
        // Decisions are applied immediately in Phase 3

        // ═══════════════════════════════════════════════════════════════
        // PHASE 5: PHYSICS AND OUTCOMES
        // ═══════════════════════════════════════════════════════════════

        // Update phase engine
        phaseEngine.update(state, input, rng);

        // Update momentum
        momentumSystem.update(state, input, rng);

        // Update player physics (movement)
        updatePlayerPhysics(state, input, rng);

        // Update ball physics
        updateBallPhysics(state, input, rng);

        // Handle collisions
        handleCollisions(state, input, rng);

        // Update fatigue
        fatigueSystem.update(state, input, rng);

        // Check for goals
        checkGoals(state, input, output, rng);

        // Update GK confidence
        updateGKConfidence(state);

        // ═══════════════════════════════════════════════════════════════
        // PHASE 6: CLEANUP
        // ═══════════════════════════════════════════════════════════════
        if ((state.totalTicks - state.lastKickTick) > 30) {
            for (int i = 0; i < 22; i++) {
                state.justRebounded[i] = false;
            }
        }

        // Decrement dribble lead state — allows ball to lead player during dribble runs
        if (state.dribbleLeadTicksRemaining > 0) {
            state.dribbleLeadTicksRemaining--;
        }

        // Decrement finalization pass receiver protection timer
        for (int i = 0; i < 22; i++) {
            if (state.justReceivedFinalizationPassTicks[i] > 0) {
                state.justReceivedFinalizationPassTicks[i]--;
            }
            if (state.attackingAnchorTicks[i] > 0) {
                state.attackingAnchorTicks[i]--;
            }
        }
    }

    private void updateGKConfidence(MatchStateSoA state) {
        // FIX #3: Goal dampening decay
        if (state.goalDampeningTicks > 0) {
            state.goalDampeningTicks--;
        }

        // Slowly recover GK confidence toward neutral (0.6)
        // Only recover if not in crisis (below 0.4) or doing well (above 0.8)
        float neutralConfidence = 0.6f;
        float recoveryRate = 0.002f; // Slightly faster recovery per tick

        // Home GK recovery
        if (state.homeGKConfidence < neutralConfidence) {
            state.homeGKConfidence = Math.min(neutralConfidence,
                state.homeGKConfidence + recoveryRate);
        } else if (state.homeGKConfidence > neutralConfidence) {
            state.homeGKConfidence = Math.max(neutralConfidence,
                state.homeGKConfidence - recoveryRate);
        }

        // Away GK recovery
        if (state.awayGKConfidence < neutralConfidence) {
            state.awayGKConfidence = Math.min(neutralConfidence,
                state.awayGKConfidence + recoveryRate);
        } else if (state.awayGKConfidence > neutralConfidence) {
            state.awayGKConfidence = Math.max(neutralConfidence,
                state.awayGKConfidence - recoveryRate);
        }
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
            applyDecision(state, i, decision, output, rng);
        }
    }

    private void applyDecision(MatchStateSoA state, int playerIdx,
                                PlayerDecision decision, TickOutput output, ScopedRNG rng) {
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
                state.shotTakenThisTick = true;
                executeShot(state, playerIdx, decision.targetX, decision.targetY,
                           decision.power, output, rng);
                break;

            case TACKLE:
                attemptTackle(state, playerIdx, decision.targetPlayer, rng);
                break;

            case CLEAR:
                clearBall(state, playerIdx, rng);
                break;

            case DRIBBLE:
                executeDribble(state, playerIdx, rng);
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

        // FIX 1: Track last kick (pass counts as a kick)
        state.lastKickTick = state.totalTicks;

        // FAST BALL RECOVERY: Mark that ball was just kicked
        // Skip if this was a DESPERATION pass (skipVelocityDamping flag set by pipeline)
        // to avoid 60% velocity damping that would make the pass only ~5m/tick
        // and allow defenders to close the gap and re-attach to the original carrier.
        if (!state.skipVelocityDamping) {
            state.justKicked = true;
        }
        state.skipVelocityDamping = false;

        // FINALIZATION PASS RECEIVER PROTECTION:
        // If this pass was a finalization transfer (playerIdx was non-finisher in attacking third),
        // mark the target as having just received a finalization pass.
        // This protects the receiver from OffBallAI backward repositioning for the next N ticks,
        // giving the shot pipeline a chance to evaluate before movement pulls them deep.
        PlayerRole receiverRole = state.getPlayerRole(targetIdx);
        boolean isFinisher = (receiverRole == PlayerRole.ST || receiverRole == PlayerRole.RW ||
                             receiverRole == PlayerRole.LW || receiverRole == PlayerRole.AM);
        if (isFinisher) {
            state.justReceivedFinalizationPassTicks[targetIdx] = 15;
        }
    }

    private void executeShot(MatchStateSoA state, int playerIdx,
                              float tx, float ty, float power, TickOutput output, ScopedRNG rng) {
        int currentTick = (int) state.totalTicks;
        int teamSide = state.getPlayerTeamSide(playerIdx);

        // Per-player cooldown to prevent spam
        if (currentTick - state.lastShotAttemptTick[playerIdx] < 5) {
            state.executeShotBlockedByCooldown++;
            state.approvalBlockedByCooldown++;
            return;
        }

        // Pipeline SHOOT reached executeShot — passed cooldown
        state.executeShotCalled++;
        state.pipelineToExecuteShotConversions++;
        state.approvalPassedCooldown++;

        // CONSUME segment budget here (not at approval time) so blocked shots don't waste budget
        int allowance = state.segmentAllowanceRemaining[teamSide];
        int carryOver = state.segmentCarryOverRemaining[teamSide];
        if (carryOver > 0) {
            state.segmentCarryOverRemaining[teamSide]--;
        } else {
            state.segmentAllowanceRemaining[teamSide]--;
        }

        float px = state.getPlayerX(playerIdx);
        float py = state.getPlayerY(playerIdx);
        float dx = tx - px;
        float dy = ty - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > 0.1f) {
            float ballSpeedBefore = (float) Math.sqrt(
                state.ballVX * state.ballVX + state.ballVY * state.ballVY);

            // Calculate xG for this shot
            float shotXg = calculateShotXG(state, px, py, teamSide);

            // === xG ACCUMULATION: Track expected goals per team ===
            if (teamSide == 0) {
                state.homeExpectedGoals += shotXg;
            } else {
                state.awayExpectedGoals += shotXg;
            }

            // === RECORD SHOT IN SHOT RECORD ===
            int roleIdx = roleToIdx(state.getPlayerRole(playerIdx));
            if (state.shotRecordCount < MatchStateSoA.MAX_SHOT_RECORD) {
                int idx = state.shotRecordCount;
                state.shotRecordXg[idx] = shotXg;
                state.shotRecordRole[idx] = roleIdx;
                state.shotRecordTeam[idx] = teamSide;
                state.shotRecordBallX[idx] = state.ballX;
                state.shotRecordBallY[idx] = state.ballY;
                state.shotRecordTick[idx] = (int) state.totalTicks;
                // distToGoal from shooter position
                float goalX = (teamSide == 0) ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
                float dxGoal = goalX - px;
                float dyGoal = -py;
                state.shotRecordDistToGoal[idx] = (float) Math.sqrt(dxGoal * dxGoal + dyGoal * dyGoal);
                // Accumulate xG by role
                if (teamSide == 0) {
                    state.homeXgByRole[roleIdx] += shotXg;
                } else {
                    state.awayXgByRole[roleIdx] += shotXg;
                }
                state.shotRecordCount++;
            }

            // ========================================================
            // SINGLE-AUTHORITY GOAL RESOLUTION
            // xG is the probability of scoring. One roll per shot.
            // Physics only animates misses/saves — never creates goals.
            // ========================================================
            // Reset pendingGoal - it should only be true for ONE shot at a time
            // If previous shot missed, pendingGoal stays true and blocks rebounds
            state.pendingGoal = false;

            // SINGLE-AUTHORITY GOAL RESOLUTION
            // xG is the probability of scoring. One roll per shot.
            // Physics only animates misses/saves — never creates goals.
            float goalProbability = Math.min(shotXg, 0.45f);

            boolean suppressed = false;
            int homeGoals = state.homeScore;
            int awayGoals = state.awayScore;
            if (Math.abs(homeGoals - awayGoals) >= 3) {
                boolean teamIsWinning = (teamSide == 0) ? (homeGoals > awayGoals) : (awayGoals > homeGoals);
                if (teamIsWinning) suppressed = true;
            }

            boolean finalGoalRoll = false;
            if (!suppressed && homeGoals + awayGoals < 7) {
                finalGoalRoll = rng.nextFloat() < goalProbability;
            }
            if (finalGoalRoll && (homeGoals + awayGoals) >= 5) {
                finalGoalRoll = rng.nextFloat() < (goalProbability * 0.5f);
            }

            // Power for realistic travel time
            float powerVar = power * (0.85f + shotXg * 0.10f);

            // INACCURACY: varies kick placement but does NOT affect goal outcome
            float distanceFactor = Math.min(dist / 25.0f, 1.3f);
            float baseInaccuracy = 0.5f * distanceFactor;
            float inaccuracy = (shotXg > 0.3f) ? baseInaccuracy * 0.6f : baseInaccuracy;
            float kickX = tx + rng.nextFloatRange(-inaccuracy, inaccuracy);
            float kickY = ty + rng.nextFloatRange(-inaccuracy, inaccuracy);

            if (finalGoalRoll) {
                // GOAL: immediate registration, no physics needed
                state.legacyGoals++;
                state.approvalResultedInGoal++;
                if (teamSide == 0) {
                    state.homeScore++;
                    state.homeGoalsByRole[roleIdx]++;
                } else {
                    state.awayScore++;
                    state.awayGoalsByRole[roleIdx]++;
                }

                if (state.shotRecordCount < MatchStateSoA.MAX_SHOT_RECORD) {
                    int shotIdx = state.shotRecordCount - 1;
                    if (shotIdx >= 0 && state.shotRecordTeam[shotIdx] == teamSide &&
                        state.shotRecordTick[shotIdx] == (int) state.totalTicks) {
                        state.shotRecordOutcome[shotIdx] = 1;
                    }
                }

                state.goalJustScored = true;
                state.goalCooldownTicks = 300;

                MatchEvent goalEvent = MatchEvent.goal(
                    (int) state.totalTicks,
                    (int) (state.totalTicks * PhysicsConstants.TICK_DURATION) / 60,
                    teamSide, playerIdx,
                    teamSide == 0 ? "Home Goal!" : "Away Goal!",
                    shotXg
                );
                output.addEvent(goalEvent);

                // Place ball off goal line — visual only
                state.ballX = 0;
                state.ballY = 0;
                state.ballZ = 0.1f;
                state.ballVX = 0;
                state.ballVY = 0;
                state.ballVZ = 0;
                state.ballController = -1;
                state.possessionTeam = -1;
                state.possessionPlayer = -1;
                state.justKicked = false;
            } else {
                // MISS/SAVE: animate only — NO second goal roll, NO physics scoring
                state.justKicked = true;
                state.preKickBallSpeed = ballSpeedBefore;
            }

            // Track last shooter to prevent self-recover
            state.lastShooterId = playerIdx;
            state.lastShotTick = state.totalTicks;
            state.lastKickTick = state.totalTicks;
            state.lastShotAttemptTick[playerIdx] = currentTick;

            // FIX 1: Count ALL pipeline-approved shots, not just shouldScore ones
            // A real shot = any SHOOT decision from the shot selection pipeline
            // Both shouldScore=true and shouldScore=false shots count toward the 12-16 target
            if (state.pipelineApprovedShot) {
                // DEBUG: Print every shot increment
                // System.out.println("[DEBUG] Shot at tick " + currentTick + " by team " + teamSide + " total: " + (teamSide == 0 ? state.homeShots : state.awayShots));
                if (teamSide == 0) {
                    state.homeShots++;
                    state.homeShotsTaken++;
                    state.lastTeamShotTick[0] = currentTick;
                } else {
                    state.awayShots++;
                    state.awayShotsTaken++;
                    state.lastTeamShotTick[1] = currentTick;
                }
            }

            // Clear ball controller
            int oldController = state.ballController;
            state.ballController = -1;
            state.possessionTeam = -1;
            state.possessionPlayer = -1;
            // Reset dribble tracking
            if (oldController != -1) {
                state.lastBallAcquireTick[oldController] = state.totalTicks;
            }
            // Reset controlled outcome since this kick may be a rebound
            // The next shot will determine its own outcome
            state.currentShotShouldScore = false;
        }
    }

    /**
     * CONTROLLED OUTCOME SYSTEM - Determines if shot should score BEFORE physics.
     * TARGET: ~85% of shots should score to achieve goal targets.
     * Only ~15% are "misses" for variance.
     */
    private boolean determineShotOutcome(MatchStateSoA state, int playerIdx, int teamSide, float shotXg, ScopedRNG rng) {
        // Anti-outlier: No scoring after 7 goals total
        if (state.homeScore + state.awayScore >= 7) {
            return false;
        }

        // FIX 2: BLOWOUT SUPPRESSION - Only suppress the LEADING team when up by 2+
        // Previous logic incorrectly blocked the trailing team (preventing comebacks)
        // New logic: suppress ONLY the winning team to freeze blowouts at 2-goal leads
        // At 2-0: leading team suppressed (can get 3-0 but not 4-0)
        // At 3-0: leading team suppressed (can get 4-0 but not 5-0) — wait, this allows 4+?
        // Actually: at 2-0, goalDiff=2, trailing team suppressed, leading team proceeds to 3-0
        // At 3-0, goalDiff=3, leading team suppressed, score frozen
        // But 4-0 can still form: 0-0 -> 1-0 -> 2-0 (no suppression, leading team scores)
        //                              -> 3-0 (now suppression on leading team)
        // So 3-0 is max, 4-0 is blocked. Good!
        int homeGoals = state.homeScore;
        int awayGoals = state.awayScore;
        int goalDiff = Math.abs(homeGoals - awayGoals);
        if (goalDiff >= 3) {
            // Determine which team is winning and whether THIS team is the leader
            boolean homeIsWinning = homeGoals > awayGoals;
            boolean teamIsWinning = (teamSide == 0) ? homeIsWinning : !homeIsWinning;
            if (teamIsWinning) {
                // Suppress the leading team - prevents 4+ goal blowouts
                return false;
            }
            // Trailing team can still score - allows comebacks like 2-1, 2-2, 3-2
        }

        // Scoring probability: scale xG down significantly to achieve target goal rate
        // Target: ~2.5 goals per match from ~13 shots = ~19% conversion
        // xG * 0.13 cap at 0.13 gives: 0.10 xG → 1.3%, 0.20 xG → 2.6%, 0.50 xG → 6.5%
        float scoreProb = Math.min(shotXg * 0.25f, 0.15f);

        // Ensure minimum chance for any shot (even 0.01 xG can be a fluke)
        scoreProb = Math.max(scoreProb, 0.02f);

        return rng.nextFloat() < scoreProb;
    }

    /**
     * PATH B2: Physics-based goal probability calculation.
     * Computes the probability that a shot results in a goal based on:
     * - Distance to goal (closer = higher)
     * - Shot quality (xG-based)
     * - GK save probability (reduces goal probability)
     * - Shot angle (more central = higher)
     *
     * TARGET: ~20-25% of shots should score (for ~2.5 goals from ~12 shots)
     */
    private float calculatePhysicsGoalProb(MatchStateSoA state, int playerIdx, int teamSide,
                                          float distToGoal, float shotXg) {
        // Base goal probability from xG - scale xG to get goal probability
        // Target: ~18% of all shots score (for ~2.2 goals from ~12 shots)
        float baseProb = shotXg * 0.26f; // xG * 0.26 = goal probability
        baseProb = Math.min(baseProb, 0.45f); // Cap at 45%

        // GK save probability reduces goal probability
        float gkConfidence = (teamSide == 0) ? state.awayGKConfidence : state.homeGKConfidence;
        float saveProb = calculateNormalizedSaveProb(shotXg, gkConfidence);

        // GK saves reduce goal probability
        float goalProb = baseProb * (1.0f - saveProb * 0.50f);

        // Distance modifier: very close shots have higher conversion
        if (distToGoal < 15.0f) {
            goalProb = Math.min(goalProb * 1.20f, 0.45f);
        } else if (distToGoal > 35.0f) {
            goalProb *= 0.75f;
        }

        // Match context: DESPERATION shots have lower conversion
        int context = detectContext(state, playerIdx);
        if (context == 2) { // DESPERATION
            goalProb *= 0.65f;
        }

        return Math.max(goalProb, 0.04f); // Minimum 4% chance for any shot
    }

    /**
     * NORMALIZED GK SAVE PROBABILITY.
     * Ties GK performance to shot quality, not pure RNG.
     */
    private float calculateNormalizedSaveProb(float shotXg, float gkConfidence) {
        // Base save probability tied to xG (better shots = harder to save)
        float baseSave = 0.55f - shotXg * 0.35f; // 0.20 to 0.55 range

        // GK confidence modifier (subtle)
        float confidenceEffect = 0.85f + gkConfidence * 0.25f; // 0.85 to 1.10

        float saveProb = baseSave * confidenceEffect;

        // Clamp to reasonable range
        return Math.max(0.20f, Math.min(0.70f, saveProb));
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
                int oldController = state.ballController;
                state.ballController = -1;
                state.possessionPlayer = -1;
                state.possessionTeam = -1;
                // Reset dribble tracking for tackled player
                if (oldController != -1) {
                    state.lastBallAcquireTick[oldController] = state.totalTicks - 20;
                }
            }
        }
    }

    private void clearBall(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        // Kick ball toward OPPONENT's goal, not own goal.
        // Kicking to x=-50 (own goal) meant HOME clears toward HOME goal,
        // and HOME CBs (closest at x=45) recover before AWAY (at x=52).
        // Kicking to x=52 (opponent goal) creates contested loose ball:
        // - Ball travels forward 40-50m through center
        // - Both teams have players in that zone, creating real contests
        float clearX = state.getPlayerX(playerIdx) > 0 ? 52 : -52;
        float clearY = rng.nextFloatRange(-20, 20);
        BallPhysicsEngine.applyKick(state, clearX, clearY, 0.95f, 0.6f, rng);

        // Track clear
        state.lastClearPlayerId = playerIdx;
        state.lastClearTick = state.totalTicks;
        state.lastKickTick = state.totalTicks;

        // Reset dribble tracking - player just cleared so they shouldn't re-acquire immediately
        state.lastBallAcquireTick[playerIdx] = state.totalTicks;

        // Clear ball controller
        state.ballController = -1;
        state.possessionTeam = -1;
        state.possessionPlayer = -1;

        // FAST BALL RECOVERY: Mark that ball was just kicked
        state.justKicked = true;
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
        // Decay rebound window
        if (state.reboundTicks > 0) {
            state.reboundTicks--;
        }

        // Maintain possession for ball controller
        // BUT: If ball was just kicked (justKicked=true), the ball is in flight to a target
        // and should NOT be pulled back to the sender. Clear ballController and let ball travel.
        // This mirrors executeShot() which clears ballController before physics runs.
        if (state.ballController != -1 && state.justKicked) {
            state.ballController = -1;
            state.possessionTeam = -1;
            state.possessionPlayer = -1;
        }

        if (state.ballController != -1) {
            int controller = state.ballController;
            float px = state.getPlayerX(controller);
            float py = state.getPlayerY(controller);
            float pvx = state.getPlayerVX(controller);
            float pvy = state.getPlayerVY(controller);
            float rotation = state.getPlayerRotation(controller);

            float aheadDist = 0.8f;
            float ballTargetX = px + (float) Math.cos(rotation) * aheadDist;
            float ballTargetY = py + (float) Math.sin(rotation) * aheadDist;

            // Ball-player distance for lead clamp
            float dxBall = state.ballX - px;
            float dyBall = state.ballY - py;
            float ballLeadDist = (float) Math.sqrt(dxBall * dxBall + dyBall * dyBall);

            // Dribble-aware follow speed with max lead clamp.
            // If dribbleLeadTicksRemaining > 0, ball was just dribbled ahead of player.
            // REDUCED followSpeed to let dribble advance: dribble targetX = selfX + 5m,
            // so ball is 5m ahead. With 0.10 followSpeed, pullback per tick = 0.5m,
            // net dribble advance = 5 - 0.5 = 4.5m/tick (vs previous 3 - 2.2 = 0.8m/tick).
            // This helps ball progress from X~10 toward attacking third.
            float followSpeed = (state.dribbleLeadTicksRemaining > 0) ? 0.10f : 0.20f;
            state.ballX = state.ballX + (ballTargetX - state.ballX) * followSpeed;
            state.ballY = state.ballY + (ballTargetY - state.ballY) * followSpeed;
            state.ballZ = 0.1f;
            state.ballVX = pvx * 0.5f;
            state.ballVY = pvy * 0.5f;
            state.ballVZ = 0;
        }

        // Track GK save state
        boolean gkSavedThisTick = false;
        int gkWhoSaved = -1;
        int attackingSide = -1;

        // Player-ball collisions
        for (int i = 0; i < 22; i++) {
            if (state.ballController == i) continue;

            float ballSpeed = (float) Math.sqrt(
                state.ballVX * state.ballVX + state.ballVY * state.ballVY);

            // Check if player just cleared the ball (can't re-acquire for 100 ticks)
            boolean justCleared = (state.lastClearPlayerId == i &&
                                   state.totalTicks - state.lastClearTick < 100);

            if (justCleared) {
                // Player just cleared - don't give them the ball back
                CollisionResolver.resolvePlayerBallCollision(state, i, rng);
                continue;
            }

            if (CollisionResolver.canControlBall(state, i)) {
                // CRITICAL: If ball is in flight toward goal (pendingGoal=true),
                // do NOT allow any player to take possession. Let physics play out.
                if (state.pendingGoal) {
                    // Ball is in flight - skip this player but still resolve collision
                    CollisionResolver.resolvePlayerBallCollision(state, i, rng);
                    continue;
                }

                boolean isGK = state.getPlayerRole(i) == PlayerRole.GK;
                boolean isFastBall = ballSpeed > 5.0f;

                // GK SAVE - Use normalized save probability based on xG
                if (isGK && isFastBall && !justCleared) {
                    // Calculate xG-based save probability
                    float gkConfidence = (i == 0) ? state.homeGKConfidence : state.awayGKConfidence;
                    // Estimate shot xG from current shot
                    float shotXg = state.currentShotTaker == (i == 0 ? 1 : 0)
                        ? 0.5f : 0.4f; // Simplified xG estimate

                    float saveProb = calculateNormalizedSaveProb(shotXg, gkConfidence);

                    // Super-save reduction
                    if (state.totalTicks - state.lastGKSaveTick < 60) {
                        saveProb *= 0.7f;
                    }

                    if (rng.nextFloat() < saveProb) {
                        // GK SAVES - parry for rebound
                        state.reboundTicks = 15;
                        state.lastGKSaveTick = state.totalTicks;
                        state.reboundAttackerSide = 1 - state.getPlayerTeamSide(i);
                        gkSavedThisTick = true;
                        gkWhoSaved = i;
                        attackingSide = 1 - state.getPlayerTeamSide(i);
                        continue;
                    }
                }

                // Take possession if ball is loose
                // BUT NOT if ball is in flight toward goal (pendingGoal=true)
                // pendingGoal=true means a shot was taken and ball is traveling toward goal
                // We must let physics play out - ball will either cross goal or be saved
                if (state.ballController == -1 && !state.pendingGoal) {
                    // FIX SELF RE-GATHER: Prevent shooter from re-acquiring within 25 ticks
                    boolean isRecentShooter = (i == state.lastShooterId && state.totalTicks - state.lastShotTick < 25);
                    if (!isRecentShooter) {
                        state.ballController = i;
                        state.possessionTeam = state.getPlayerTeamSide(i);
                        state.possessionPlayer = i;
                        state.lastBallAcquireTick[i] = state.totalTicks;
                        // RESET velocity so player stops drifting in chase direction.
                        // Previously, player acquired ball while chasing (pvx < 0 for team 0),
                        // causing ball to be dragged toward own goal via ballTargetX offset.
                        state.setPlayerVX(i, 0);
                        state.setPlayerVY(i, 0);
                        // Reset rotation to face attacking direction:
                        // Team 0 (+X toward AWAY_GOAL_X=52.5) → rotation=0 (cos=1)
                        // Team 1 (-X toward HOME_GOAL_X=-52.5) → rotation=π (cos=-1)
                        int teamSide = state.getPlayerTeamSide(i);
                        state.setPlayerRotation(i, teamSide == 0 ? 0.0f : (float) Math.PI);
                    }
                }
            }

            CollisionResolver.resolvePlayerBallCollision(state, i, rng);
        }

        // Rebound system - create second-chance shots after GK save
        if (state.reboundTicks > 0 && attackingSide != -1) {
            float nearestDist = Float.MAX_VALUE;
            int nearestAttacker = -1;
            for (int i = 0; i < 22; i++) {
                if (state.getPlayerTeamSide(i) == attackingSide) {
                    float dx = state.getPlayerX(i) - state.ballX;
                    float dy = state.getPlayerY(i) - state.ballY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < nearestDist && dist < 16.0f) {
                        nearestDist = dist;
                        nearestAttacker = i;
                    }
                }
            }

            if (nearestAttacker != -1 && nearestDist < 12.0f) {
                if (rng.nextFloat() < 0.60f) {
                    float goalX = (attackingSide == 0) ?
                        PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;

                    BallPhysicsEngine.applyKick(state, goalX, rng.nextFloatRange(-2, 2), 0.80f, 0.12f, rng);

                    state.ballController = -1;
                    state.possessionTeam = -1;
                    state.possessionPlayer = -1;

                    state.lastKickTick = state.totalTicks;
                    state.lastShooterId = nearestAttacker;
                    state.lastShotTick = state.totalTicks;
                    // NOTE: Rebounds are NOT counted as shots - they're second-chance attempts
                    // The shot counter tracks deliberate shot decisions, not rebounds
                }
            }

            if (state.reboundTicks < 3) {
                state.reboundTicks = 10 + (int)(rng.nextFloat() * 6);
            }
        }

        // Player-player collisions
        for (int i = 0; i < 22; i++) {
            for (int j = i + 1; j < 22; j++) {
                CollisionResolver.resolvePlayerPlayerCollision(state, i, j, rng);
            }
        }

        // ============================================================
        // FAST BALL RECOVERY SYSTEM - ELIMINATE DEAD TIME
        // ============================================================

        float currentBallSpeed = (float) Math.sqrt(
            state.ballVX * state.ballVX + state.ballVY * state.ballVY);

        // POST-KICK VELOCITY DAMPING - After a kick, reduce velocity to prevent long rebounds
        // BUT: Do NOT dampen if pendingGoal=true (ball is in flight toward goal - must preserve momentum)
        if (state.justKicked && !state.pendingGoal) {
            // Only dampen when ball is NOT in flight toward goal
            if (currentBallSpeed > 4.0f) {
                // Reduce velocity by 60% - keeps ball moving but much slower
                state.ballVX *= 0.40f;
                state.ballVY *= 0.40f;
            } else if (currentBallSpeed <= 4.0f) {
                // Ball is slow enough - mark kick as resolved
                state.justKicked = false;
            }
        } else if (!state.justKicked) {
            // not kicked recently
        } else {
            // justKicked && pendingGoal - ball in flight toward goal, don't dampen
        }

        // TRACK BALL FREE TIME
        if (state.ballController == -1) {
            // Ball just became free
            if (state.ballFreeTick < 0) {
                state.ballFreeTick = state.totalTicks;
            }

            long ballFreeDuration = state.totalTicks - state.ballFreeTick;

            // IMMEDIATE RECOVERY: If ball speed < 4.0 and no controller, force pickup by NON-SHOOTER
            // BUT NOT if ball is in flight toward goal (pendingGoal=true) - give the shot a chance to result in goal
            // pendingGoal=true is the authoritative flag that a shot is in flight toward goal
            if (currentBallSpeed < 4.0f && ballFreeDuration > 4 && state.pendingGoal) {
                // Ball is in flight toward goal - DO NOT PICK UP - let it continue to goal/save
                state.ballFreeTick = state.totalTicks; // reset so we don't keep checking this condition
            } else if (currentBallSpeed < 4.0f && ballFreeDuration > 4 && !state.pendingGoal) {
                int shooterId = state.lastShooterId;
                int lastTeam = state.possessionTeam;

                float nearestDist = Float.MAX_VALUE;
                float nearestOppDist = Float.MAX_VALUE;
                int nearestPlayer = -1;
                int nearestOpp = -1;

                for (int i = 0; i < 22; i++) {
                    // Skip the shooter to prevent self re-gather
                    if (i == shooterId) continue;

                    float dx = state.getPlayerX(i) - state.ballX;
                    float dy = state.getPlayerY(i) - state.ballY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestPlayer = i;
                    }
                    // Track nearest opponent separately for turnover bias
                    if (state.getPlayerTeamSide(i) != lastTeam && dist < nearestOppDist) {
                        nearestOppDist = dist;
                        nearestOpp = i;
                    }
                }

                // TURNOVER REALISM: bias toward opponent when ball has been loose
                // After 8 ticks: 50% opponent preference. After 12 ticks: 70%.
                int pickupPlayer = nearestPlayer;
                if (nearestOpp != -1) {
                    float turnoverChance = (ballFreeDuration > 12) ? 0.70f : 0.50f;
                    if (ballFreeDuration > 8 && rng.nextFloat() < turnoverChance) {
                        pickupPlayer = nearestOpp;
                    }
                }

                if (pickupPlayer != -1 && nearestDist < 12.0f) {
                    state.ballController = pickupPlayer;
                    state.possessionTeam = state.getPlayerTeamSide(pickupPlayer);
                    state.possessionPlayer = pickupPlayer;
                    state.lastBallAcquireTick[pickupPlayer] = state.totalTicks;
                    state.ballFreeTick = -100000;
                    state.justKicked = false;
                    // Set ball right at player's feet with tiny velocity
                    state.ballX = state.getPlayerX(pickupPlayer);
                    state.ballY = state.getPlayerY(pickupPlayer);
                    state.ballVX = 0;
                    state.ballVY = 0;
                    state.ballVZ = 0;
                    // Reset player velocity and rotation so they don't carry chase momentum
                    // into possession (which would drag ball backward via ballTarget offset)
                    state.setPlayerVX(pickupPlayer, 0);
                    state.setPlayerVY(pickupPlayer, 0);
                    int teamSide = state.getPlayerTeamSide(pickupPlayer);
                    state.setPlayerRotation(pickupPlayer, teamSide == 0 ? 0.0f : (float) Math.PI);
                }
            }

            // SLOW BALL RECOVERY: If ball free > 15 ticks, force pickup by anyone
            // BUT skip shooter if within 25 ticks of their shot
            if (ballFreeDuration > 15) {
                int lastTeam = state.possessionTeam;
                float nearestDist = Float.MAX_VALUE;
                float nearestOppDist = Float.MAX_VALUE;
                int nearestPlayer = -1;
                int nearestOpp = -1;
                for (int i = 0; i < 22; i++) {
                    // FIX SELF RE-GATHER: Skip shooter within 25 ticks of shot
                    if (i == state.lastShooterId && state.totalTicks - state.lastShotTick < 25) {
                        continue;
                    }
                    float dx = state.getPlayerX(i) - state.ballX;
                    float dy = state.getPlayerY(i) - state.ballY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestPlayer = i;
                    }
                    if (state.getPlayerTeamSide(i) != lastTeam && dist < nearestOppDist) {
                        nearestOppDist = dist;
                        nearestOpp = i;
                    }
                }

                // TURNOVER REALISM: strong opponent bias for slow recovery
                // After 25 ticks: 60% opponent. After 40 ticks: 80%.
                int pickupPlayer = nearestPlayer;
                if (nearestOpp != -1) {
                    float turnoverChance = (ballFreeDuration > 40) ? 0.80f : 0.60f;
                    if (rng.nextFloat() < turnoverChance) {
                        pickupPlayer = nearestOpp;
                    }
                }

                if (pickupPlayer != -1 && nearestDist < 20.0f) {
                    state.ballController = pickupPlayer;
                    state.possessionTeam = state.getPlayerTeamSide(pickupPlayer);
                    state.possessionPlayer = pickupPlayer;
                    state.lastBallAcquireTick[pickupPlayer] = state.totalTicks;
                    state.ballFreeTick = -100000;
                    state.justKicked = false;
                    state.ballX = state.getPlayerX(pickupPlayer);
                    state.ballY = state.getPlayerY(pickupPlayer);
                    state.ballVX = 0;
                    state.ballVY = 0;
                    state.ballVZ = 0;
                    // Reset player velocity and rotation so they don't carry chase momentum
                    // into possession (which would drag ball backward via ballTarget offset)
                    state.setPlayerVX(pickupPlayer, 0);
                    state.setPlayerVY(pickupPlayer, 0);
                    int teamSide = state.getPlayerTeamSide(pickupPlayer);
                    state.setPlayerRotation(pickupPlayer, teamSide == 0 ? 0.0f : (float) Math.PI);
                }
            }
        } else {
            // Ball is controlled
            state.ballFreeTick = -100000;
            state.justKicked = false;
        }
    }

    private void checkGoals(MatchStateSoA state, TickInput input,
                            TickOutput output, ScopedRNG rng) {
        // Handle goal cooldown
        if (state.goalCooldownTicks > 0) {
            state.goalCooldownTicks--;
            if (state.goalCooldownTicks == 0) {
                state.goalJustScored = false;
            }
            return;
        }

        int goal = BallPhysicsEngine.checkGoal(state);

        // Decrement pendingGoalTicks if > 0 (ball is in flight toward goal)
        if (state.pendingGoalTicks > 0) {
            state.pendingGoalTicks--;
            // Keep pendingGoal true until the counter expires
            state.pendingGoal = true;
        } else if (state.pendingGoalTicks == 0 && state.pendingGoal) {
            // pendingGoalTicks expired without ball crossing goal - clear pendingGoal
            // This happens when ball was slowed/stopped before reaching goal line
            state.pendingGoal = false;
        }

        if (goal != 0 && !state.goalJustScored) {
            // ANTI-PHANTOM GOAL: Only count goals from shots that were pre-determined
            // to score (pendingGoalTicks > 0). Non-shot ball movements (dribble/clear)
            // should never result in goals.
            if (state.pendingGoalTicks <= 0 && !state.pendingGoal) {
                // Ball crossed goal line without being a pre-determined shot goal
                // Reset ball to center as if goal happened (prevent phantom)
                state.ballX = 0;
                state.ballY = 0;
                state.ballZ = 0;
                state.ballVX = 0;
                state.ballVY = 0;
                state.ballVZ = 0;
                state.ballController = -1;
                return;
            }

            // ALL valid goal crossings count - no filtering by ball controller
            // This maximizes goal detection from shots

            // Determine scoring team
            int scoringTeam = (goal == 1) ? 0 : 1;
            int scoringPlayer = state.ballController;

            // Anti-outlier: Max 7 goals per match
            if (state.homeScore + state.awayScore >= 7) {
                return;
            }

            // Goal dampening after scoring (reduced to allow more goals)
            state.goalDampeningTicks = 50;

            // Update score
            if (goal == 1) {
                state.homeScore++;
            } else {
                state.awayScore++;
            }

            // GRADUAL PHYSICS TAKEOVER: Handle pending goal resolution
            if (state.pendingGoal) {
                // This goal came from a shouldScore-triggered shot
                // Count as legacy goal for instrumentation
                state.legacyGoals++;
                // Clear pending goal flag and timer
                state.pendingGoal = false;
                state.pendingGoalTicks = 0;
            } else {
                // This goal came from a normal physics shot
                state.physicsGoals++;
            }

            // Update GK confidence
            if (goal == 1) {
                state.awayGKConfidence = Math.max(0.15f, state.awayGKConfidence - 0.15f);
                state.awayGKRecentGoals++;
            } else {
                state.homeGKConfidence = Math.max(0.15f, state.homeGKConfidence - 0.15f);
                state.homeGKRecentGoals++;
            }

            // Track goal streaks
            if (goal == 1) {
                if (state.totalTicks - state.lastHomeGoalTick < 60) {
                    state.homeGoalStreak++;
                } else {
                    state.homeGoalStreak = 1;
                }
                state.lastHomeGoalTick = state.totalTicks;
            } else {
                if (state.totalTicks - state.lastAwayGoalTick < 60) {
                    state.awayGoalStreak++;
                } else {
                    state.awayGoalStreak = 1;
                }
                state.lastAwayGoalTick = state.totalTicks;
            }

            // Blowout prevention
            int scoreDiff = Math.abs(state.homeScore - state.awayScore);
            int winningStreak = (state.homeScore > state.awayScore) ? state.homeGoalStreak : state.awayGoalStreak;
            if (scoreDiff >= 2 && winningStreak >= 2) {
                if (state.homeScore > state.awayScore) {
                    state.awayGKConfidence = Math.min(0.95f, state.awayGKConfidence + 0.20f);
                } else {
                    state.homeGKConfidence = Math.min(0.95f, state.homeGKConfidence + 0.20f);
                }
            }

            // Mark goal
            state.goalJustScored = true;
            state.goalCooldownTicks = 300;
            state.lastGoalTick = state.totalTicks;

            // Log goal event
            float xg = calculateShotXG(state, state.ballX, state.ballY, scoringTeam);
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

            // Reset ball
            state.ballX = 0;
            state.ballY = 0;
            state.ballZ = 0;
            state.ballVX = 0;
            state.ballVY = 0;
            state.ballVZ = 0;
            state.ballController = -1;
            state.possessionTeam = -1;
            state.possessionPlayer = -1;
            state.currentShotShouldScore = false;
            state.currentShotTaker = -1;

            state.phase = MatchPhase.KICKOFF;
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

    /**
     * Calculate xG for a shot based on position.
     * Uses distance to goal and vertical position.
     */
    private float calculateShotXG(MatchStateSoA state, float shooterX, float shooterY, int teamSide) {
        float goalX = (teamSide == 0) ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
        float dx = goalX - shooterX;
        float dy = shooterY; // Y distance from center (goal is at y=0)
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Realistic xG model: drops sharply with distance
        // Benchmarks: 5m=0.95, 10m=0.75, 15m=0.55, 20m=0.38, 30m=0.18, 40m=0.08
        double xg = 0.95 * Math.exp(-distance * 0.045);
        // Small bonus for shots toward center (y=0)
        double yBonus = 1.0 + Math.abs(dy) / PhysicsConstants.FIELD_WIDTH * 0.05;
        xg *= yBonus;

        return (float) Math.max(0.02, Math.min(0.95, xg));
    }

    /**
     * Calculate xG multiplier based on actual vs expected goals ratio.
     * Only PENALIZES overperforming teams - multiplier < 1.0
     * Does NOT reward underperformers - keeps multiplier at 1.0
     * Activates when actual > expected * 1.2
     * Range: 0.70 - 1.0
     */
    private float calculateXgMultiplier(MatchStateSoA state, int teamSide, float shotXg) {
        float actualGoals = (teamSide == 0) ? state.homeScore : state.awayScore;
        float expectedGoals = (teamSide == 0) ? state.homeExpectedGoals : state.awayExpectedGoals;

        // Avoid division by zero or very small xG
        if (expectedGoals < 0.1f) {
            return 1.0f; // Neutral if no xG accumulated yet
        }

        // FIX B: Only suppress when match is clearly unbalanced (goalDiff >= 2)
        // Do NOT suppress normal match flow (1-goal differences)
        // The suppression must target the WINNING team only
        int goalDiff = state.homeScore - state.awayScore;
        boolean teamIsWinningByTwoOrMore = (teamSide == 0) ? (goalDiff >= 2) : (goalDiff <= -2);
        float ratio = actualGoals / expectedGoals;

        // Only penalize overperformance when ratio > 1.2 AND this team leads by 2+
        if (ratio > 1.2f && teamIsWinningByTwoOrMore) {
            // Penalty: ratio=1.5 → 0.83, ratio=2.0 → 0.71
            float excessRatio = ratio - 1.2f;
            float penalty = 1.0f / (1.0f + excessRatio * 0.7f);
            return Math.max(0.70f, penalty);
        }
        return 1.0f;
    }

    // ═══════════════════════════════════════════════════════════════
    // SHOT SELECTION PIPELINE (SDD v2.1 Section 10.2)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Executes the shot selection pipeline for a player with the ball.
     * Returns the decision to apply in the main loop.
     * - Returns PASS(targetPlayer): shoot was blocked, pass instead
     * - Returns DRIBBLE: fallback dribble
     * - Returns SHOOT: all checks passed, shoot approved
     */
    private PlayerDecision executeShotSelectionPipeline(MatchStateSoA state, int playerId, ScopedRNG rng) {
        int teamSide = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);
        PlayerRole role = state.getPlayerRole(playerId);

        float goalX = (teamSide == 0) ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
        float dxGoal = goalX - selfX;
        float dyGoal = 0.0f - selfY;
        float distToGoal = (float) Math.sqrt(dxGoal * dxGoal + dyGoal * dyGoal);

        // ═══════════════════════════════════════════════════════════════
        // PASS-BEFORE-SHOT: Defenders in attacking third must pass, not shoot.
        // When a non-finisher (CB/LB/RB/DM) has the ball in the attacking third
        // but is positioned far from goal (distToGoal > 60m), they should pass
        // to an attacker instead of evaluating the shot pipeline.
        // This prevents invalid STEP1 rejections where distToGoal is computed
        // from the defender's deep position while the ball is in the attack.
        // ═══════════════════════════════════════════════════════════════
        boolean isNonFinisher = (role == PlayerRole.CB || role == PlayerRole.LB ||
                                 role == PlayerRole.RB || role == PlayerRole.DM);
        if (isNonFinisherFinalThird) {
            float absBallX = Math.abs(state.ballX);
            if (absBallX >= 35.0f && distToGoal > 60.0f) {
                // Force forward pass — defender in attacking third but far from goal.
                // Pass to highest-priority forward player.
                int passTarget = findForwardAttackerTarget(state, playerId);
                if (passTarget != -1) {
                    state.forcedForwardPasses++;
                    // DEBUG: trace progression pass
                        if (playerId == 12 && state.totalTicks == 216006 && state.progressionTransfers == 8010) {
                            System.out.printf("[PROG] tick=%d playerId=%d passTarget=%d urgent=%s skipVelDamp=%s justKicked=%s%n",
                                state.totalTicks, playerId, passTarget, urgentSituation, state.skipVelocityDamping, state.justKicked);
                        }
                        return PlayerDecision.pass(playerId, passTarget, 0.95f);
                }
                // No attacker found — still pass to nearest forward teammate
                int fallbackTarget = findNearestForwardPlayer(state, playerId);
                if (fallbackTarget != -1) {
                    state.forcedForwardPasses++;
                    return PlayerDecision.pass(playerId, fallbackTarget, 0.85f);
                }
                // No forward player available — dribble is not acceptable fallback here.
                // Clear the ball instead.
                return PlayerDecision.clear(playerId);
            }
        }

        // GK PASS-BEFORE-SHOT: GK with ball at field center is not a shot candidate.
        // 269405 GK STEP1 rejections at avg distToGoal=110m confirm GK is never close enough.
        // Pass immediately to avoid 269k wasted pipeline invocations per match.
        if (role == PlayerRole.GK) {
            int passTarget = findPassTarget(state, playerId, rng);
            if (passTarget != -1) {
                return PlayerDecision.pass(playerId, passTarget, 0.85f);
            }
            return PlayerDecision.clear(playerId);
        }

        // PIPELINE INSTRUMENTATION
        state.pipelineInvocations++;

        // Per-role pipeline tracking
        int roleIdx = roleToIdx(state.getPlayerRole(playerId));
        state.pipelineEvalsByRole[roleIdx]++;

        // BUDGET DEFICIT COMPUTATION: computed once, used in STEP1, STEP4, STEP5
        int budgetTeamSide = state.getPlayerTeamSide(playerId);
        int budget = (budgetTeamSide == 0) ? state.homeShotBudget : state.awayShotBudget;
        int taken = (budgetTeamSide == 0) ? state.homeShotsTaken : state.awayShotsTaken;
        float timeProgress = (float) state.totalTicks / PhysicsConstants.TICKS_PER_MATCH;
        float expectedShots = budget * timeProgress;
        float deficit = expectedShots - (float) taken;
        // Handle uninitialized budget (0) by using default budget of 14
        boolean budgetInitialized = state.shotBudgetInitialized;
        float effectiveBudget = (budgetInitialized && budget > 0) ? budget : 14.0f;
        float effectiveExpected = effectiveBudget * timeProgress;
        float effectiveDeficit = (budgetInitialized && budget > 0) ? deficit : (effectiveExpected - (float) taken);

        // STEP 1: Hard Constraints - Distance cap
        // distToGoal already computed at line 1476 — reuse it here (no redeclaration)
        int context = detectContext(state, playerId);
        switch (context) {
            case 2: state.contextDesperation++; break;
            case 1: state.contextTransition++; break;
            default: state.contextBuildUp++; break;
        }
        float maxDistance;
        switch (context) {
            case 2: maxDistance = 45.0f; break;  // DESPERATION (was 40)
            case 1: maxDistance = 50.0f; break;  // TRANSITION (was 45)
            default: maxDistance = 55.0f; break; // BUILD_UP (was 50)
        }

        // BUDGET-DRIVEN STEP1 RELAXATION: When under shot target, extend max distance
        // Removed ownHalf requirement - in Phase B (DESPERATION), the team needs shots
        // regardless of field position. Ball carrier at x=4.4 (past center) still needs
        // extra range to reach 60m cap when under budget pressure.
        float adjustedMaxDistance = maxDistance;
        if (effectiveDeficit > 0) {
            PlayerRole evalRole = state.getPlayerRole(playerId);
            boolean isEligibleRole = (evalRole == PlayerRole.ST || evalRole == PlayerRole.AM ||
                                     evalRole == PlayerRole.RW || evalRole == PlayerRole.LW ||
                                     evalRole == PlayerRole.CM || evalRole == PlayerRole.DM ||
                                     evalRole == PlayerRole.CB || evalRole == PlayerRole.RB || evalRole == PlayerRole.LB);
            if (isEligibleRole) {
                float budgetExtraRange = Math.min(12.0f, effectiveDeficit * 1.0f);
                adjustedMaxDistance = maxDistance + budgetExtraRange;
            }
        }

        // ATTACK-ZONE STEP1 RELAXATION: When attacker is already in attacking third,
        // allow shots from longer distance since they're in position.
        // Only for ST/LW/RW/AM/CM, only when ball is in attacking third (absBallX >= 35).
        PlayerRole evalRole = state.getPlayerRole(playerId);
        boolean isAttackerRole = (evalRole == PlayerRole.ST || evalRole == PlayerRole.AM ||
                                  evalRole == PlayerRole.RW || evalRole == PlayerRole.LW ||
                                  evalRole == PlayerRole.CM);
        if (isAttackerRole) {
            if (absBallX >= 35.0f) {
                // Attacker in attacking third — relax STEP1 by +20m
                adjustedMaxDistance = Math.max(adjustedMaxDistance, maxDistance + 20.0f);
            }
        }

        // FINISHER-IN-ZONE MICRO-RELAXATION: For ST/RW/LW in attacking third,
        // reduce the zone boost slightly and add targeted help for borderline cases.
        // distToGoal uses PLAYER X (formation position), not ball X.
        // ST at x=27.5 → distToGoal=25m. ST at x=30 → distToGoal=22.5m.
        // These are already within 55m cap — no micro-relaxation needed.
        // The issue is when zone boost (+20m) creates too generous a cap,
        // causing non-shooting situations to be classified as shots.
        // REPLACED: removed +5m stack which over-inflated volume.
        // The zone boost alone (+20m) is sufficient.

        if (distToGoal > adjustedMaxDistance) {
            state.step1DistanceRejections++;
            state.step1RejByRole[roleIdx]++;
            // STEP1 DIAGNOSTICS
            state.step1DistSumByRole[roleIdx] += distToGoal;
            state.step1PlayerXSumByRole[roleIdx] += selfX;
            state.step1BallXSumByRole[roleIdx] += state.ballX;
            // Zone: 0=ownHalf, 1=mid third (|x|<35), 2=attacking third (|x|>=35)
            int zone = (Math.abs(selfX) >= 35) ? 2 : (Math.abs(selfX) < 20) ? 0 : 1;
            state.step1CountByRoleZone[roleIdx][zone]++;
            state.step1CountByRoleContext[roleIdx][context]++;
            // Near-miss: cap = maxDistance (BUILD_UP=55, TRANSITION=50, DESPERATION=45)
            float overCap = distToGoal - maxDistance;
            if (overCap <= 5.0f) state.step1NearMiss5ByRole[roleIdx]++;
            if (overCap <= 10.0f) state.step1NearMiss10ByRole[roleIdx]++;
            if (overCap <= 15.0f) state.step1NearMiss15ByRole[roleIdx]++;
            // In DESPERATION (player deep in own half), dribble is ineffective:
            // - Player surrounded by pressure, ball carrier has pvx~0, rotation reset
            // - Possession handler pulls ball back to player each tick
            // - Net advance per tick ≈ 0.5m, so distToGoal stays ≈100m → permanent deadlock
            // Use PASS instead: ball travels 20-50m through air, skips possession-pullback issue.
            if (context == 2) {  // DESPERATION
                int passTarget = findPassTarget(state, playerId, rng);
                if (passTarget != -1) {
                    // Skip velocity damping so ball travels at FULL SPEED (28 m/s for power=0.8)
                    // instead of ~5m/tick. Target reaches ball in 1-2 ticks before defenders close gap.
                    // Also mark justKicked so possession handler doesn't pull ball back.
                    state.skipVelocityDamping = true;
                    state.justKicked = true;
                    return PlayerDecision.pass(playerId, passTarget, 0.8f);
                }
                // No valid pass target found (player is most advanced teammate in DESPERATION).
                // Use CLEAR: kick ball hard toward opponent goal to create contested loose ball.
                // The ball travels far (high power), skipping the possession-pullback issue,
                // and creates a real contested situation where either team can recover.
                return PlayerDecision.clear(playerId);
            }
            return PlayerDecision.dribble(playerId);
        }

        // Pressure veto: if closest opponent < 2.0m — was 0.5f which was too tight (99.9% rejection)
        float closestOppDist = findClosestOpponentDistance(state, playerId);

        // === PRESSURE DIAGNOSTICS ===
        state.pressureDistSum += closestOppDist;
        state.pressureDistCount++;
        if (closestOppDist < state.pressureDistMin) state.pressureDistMin = closestOppDist;
        if (closestOppDist > state.pressureDistMax) state.pressureDistMax = closestOppDist;
        int bucket = (int) Math.min(closestOppDist, 20.0f);
        if (bucket >= 0 && bucket < state.pressureDistHistogram.length) {
            state.pressureDistHistogram[bucket]++;
        }

        if (closestOppDist < 2.0f) {
            state.step2PressureRejections++;
            state.step2RejByRole[roleIdx]++;
            // STEP2 per-role diagnostics
            state.step2DistSumByRole[roleIdx] += closestOppDist;
            state.step2PlayerXSumByRole[roleIdx] += selfX;
            state.step2BallXSumByRole[roleIdx] += state.ballX;
            int rejectBucket = (int) Math.min(closestOppDist, 20.0f);
            if (rejectBucket >= 0 && rejectBucket < state.step2RejectHistogram.length) {
                state.step2RejectHistogram[rejectBucket]++;
            }
            return PlayerDecision.dribble(playerId);
        } else {
            // STEP2 accepted — record for avg distance comparison
            state.step2AcceptDistSumByRole[roleIdx] += closestOppDist;
            state.step2AcceptCountByRole[roleIdx]++;
        }

        // STEP 3 (STEP 2 in user doc): Memory Check - Has this player shot recently?
        int ticksSinceShot = (int) (state.totalTicks - state.lastShotAttemptTick[playerId]);
        if (ticksSinceShot < 150 && ticksSinceShot >= 0) {
            float memoryPenalty = 0.60f;
            if (!passesViabilityThreshold(state, playerId, memoryPenalty)) {
                state.step3MemoryRejections++;
                return PlayerDecision.dribble(playerId);
            }
        }

        // STEP 4 (STEP 3 in user doc): Viability Scoring
        // URGENCY MODE: When DESPERATION, use BUILD_UP parameters for consistency with threshold.
        int urgencyContext = (context == 2) ? 0 : context;
        float viabilityScore = calculateViabilityScore(state, playerId, urgencyContext, rng);
        // URGENCY MODE: When DESPERATION, use BUILD_UP threshold.
        // DESPERATION raises thresholds (making shots harder), but Phase B is late-game
        // where shots should be based on quality, not defensive pressure.
        // Phase A burst (17+ shots) makes deficit negative at Phase B start, preventing
        // urgency mode from engaging when needed. Remove deficit check to ensure
        // BUILD_UP thresholds apply in all DESPERATION situations.
        float threshold = getRoleThreshold(state.getPlayerRole(playerId), urgencyContext);

        if (viabilityScore < 0.25f) {
            state.step4ViabilityRejections++;
            return PlayerDecision.dribble(playerId);
        }

        // STEP 5 (STEP 4 in user doc): Threshold Check by Role
        // Lower thresholds for BUILD_UP/TRANSITION contexts to allow more shots
        float roleThreshold = threshold;
        if (context != 2 && viabilityScore >= 0.10f && viabilityScore < threshold) {
            // Allow borderline shots if viability is above minimum floor
            roleThreshold = Math.min(threshold, 0.30f);
        }
        if (viabilityScore < roleThreshold) {
            state.step5RoleRejections++;
            return PlayerDecision.dribble(playerId);
        }

        // LOCK SHORT-CIRCUIT: Do NOT generate throwaway SHOOT approvals for locked players.
        // Pipeline has cleared all gating steps (distance, pressure, memory, viability, role).
        // If the player is still inside DECISION_COOLDOWN, return DRIBBLE immediately.
        // This eliminates the "approve then discard" pattern that was spamming 568 lock-discards per match.
        // The lock semantics remain intact — no player can shoot twice within DECISION_COOLDOWN ticks.
        int ticksSinceDecision = (int) (state.totalTicks - state.lastShotDecisionTick[playerId]);
        if (ticksSinceDecision < DECISION_COOLDOWN) {
            state.lostLockShortCircuit++;
            return PlayerDecision.dribble(playerId);
        }

        // FINAL-THIRD TRANSFER: Non-finishers (DM/CB/LB/RB) in the final third
        // must pass to an attacker if one is ahead. Prevents DM/CB shooting from box edges.
        boolean isNonFinisherFinalThird = (role == PlayerRole.DM || role == PlayerRole.CB ||
                                role == PlayerRole.LB || role == PlayerRole.RB);
        if (isNonFinisherFinalThird) {
            if (absBallX >= 35.0f) {
                int attackerTarget = findForwardAttackerTarget(state, playerId);
                if (attackerTarget != -1) {
                    state.lostFinalThirdPass++;
                    return PlayerDecision.pass(playerId, attackerTarget, 0.8f);
                }
                // No attacker target found. Let the non-finisher shoot if they pass viability.
                // Do NOT return DRIBBLE here — let them flow into pipeline approval.
                // They cleared S1-S4, and STEP5 only checks threshold (which they pass if viabilityScore >= threshold).
                // This converts "final-third no target" losses into actual shots.
            }
        }

        // PIPELINE APPROVED SHOOT
        state.pipelineShootApprovals++;
        state.approvalsByRole[roleIdx]++;
        if (deficit > 0) state.shotsUnderBudgetPressure++;

        // SHOT DECISION LOCK: Record this shot decision so player can't shoot again for DECISION_COOLDOWN ticks
        state.lastShotDecisionTick[playerId] = (int) state.totalTicks;

        // Track SHOOT within current possession
        state.shotsInCurrentPossession++;
        state.totalShootApprovalsInPossessions++;

        // Repeated decision: same player SHOOTing twice in same possession
        if (state.lastShootApprovalPlayer == playerId) {
            state.repeatedShootApprovals++;
            long ticksBetween = state.totalTicks - state.lastShootApprovalTick;
            state.sumTicksBetweenRepeatedShootApprovals += ticksBetween;
            state.repeatedShootApprovalPairs++;
        }
        state.lastShootApprovalPlayer = playerId;
        state.lastShootApprovalTick = state.totalTicks;

        // TEAM SHOT REFRACTORY: Check at execution time, before pipelineApprovedShot is set.
        // 2500 ticks ≈ 42 seconds. Prevents burst: 41 shots in 9s → ~2 shots in first 42s.
        // If blocked, pipelineApprovedShot stays false so homeShots/awayShots counters don't increment.
        int currentTick = (int) state.totalTicks;
        long lastTeamShotTick = (teamSide == 0) ? state.lastHomeShotTick : state.lastAwayShotTick;
        long ticksSinceTeamShot = currentTick - lastTeamShotTick;
        // TEAM SHOT REFRACTORY: Disabled — real issue is STEP1-5 rejection after min 15
        // The pipeline runs throughout the match (~54k ticks/segment) but shots only occur in 0-15.
        // Refractory was masking the real problem. Revert and diagnose STEP1-5 blocking.
        // REMOVE the refractory check — it causes too few shots.
        /*
        if (ticksSinceTeamShot < 100) {
            state.lostTeamRefractoryBypass++;
            return PlayerDecision.dribble(playerId);
        }
        */

        // Team gap OK — this shot can proceed. Update last shot tick.
        if (teamSide == 0) {
            state.lastHomeShotTick = currentTick;
        } else {
            state.lastAwayShotTick = currentTick;
        }

        float targetY = rng.nextFloatRange(-1, 1);
        return PlayerDecision.shoot(playerId, goalX, targetY, 0.75f);
    }

    /**
     * Team-level shot pacing: per-segment budget.
     * Returns SHOOT if segment budget allows, otherwise PASS/DRIBBLE.
     * Budget is NOT consumed here — only at executeShot time.
     * This prevents phantom budget consumption when executeShot blocks due to cooldown.
     */
    private PlayerDecision teamRefractoryCheck(MatchStateSoA state, int playerId, int teamSide,
                                               float goalX, ScopedRNG rng) {
        // Update segment if we've moved to a new 15-min block
        updateSegmentBudget(state);

        // Check if team has any allowance in current segment
        int allowance = state.segmentAllowanceRemaining[teamSide];
        int carryOver = state.segmentCarryOverRemaining[teamSide];

        if (allowance <= 0 && carryOver <= 0) {
            // No allowance — block this shot
            state.shotsBlockedBySegmentBudget[teamSide]++;
            state.teamPacingBlocksTotal++;

            // Try forward pass to finisher instead
            int passTarget = findForwardAttackerTarget(state, playerId);
            if (passTarget != -1) {
                return PlayerDecision.pass(playerId, passTarget, 0.85f);
            }
            return PlayerDecision.dribble(playerId);
        }

        // Allowance available — budget will be consumed at executeShot, not here.
        // Record instrumentation
        state.shotsAllowedBySegment[teamSide]++;

        float targetY = rng.nextFloatRange(-1, 1);
        return PlayerDecision.shoot(playerId, goalX, targetY, 0.75f);
    }

    /**
     * Update segment budget at segment boundaries.
     * Carry over unused allowance from previous segment (with cap of 2).
     */
    private void updateSegmentBudget(MatchStateSoA state) {
        int newSegment = (int) ((state.totalTicks * 90L) / PhysicsConstants.TICKS_PER_MATCH) / 15;
        newSegment = Math.min(5, Math.max(0, newSegment));

        if (newSegment != state.currentSegment && newSegment < 6) {
            // Segment changed — carry over unused allowance
            for (int t = 0; t < 2; t++) {
                int unused = state.segmentAllowanceRemaining[t];
                if (unused > 0) {
                    // Cap carry-over at 6 extra shots
                    state.segmentCarryOverRemaining[t] = Math.min(6, unused);
                }
                // Reset base allowance for new segment
                state.segmentAllowanceRemaining[t] = state.segmentBudgetPerTeam[t];
            }
            state.currentSegment = newSegment;
        }
    }

    /**
     * Detect match context for distance cap selection.
     * Returns: 0=BUILD_UP, 1=TRANSITION, 2=DESPERATION
     */
    private int detectContext(MatchStateSoA state, int playerId) {
        long ticksSinceChange = state.totalTicks - state.lastPossessionChangeTick;

        // TRANSITION: Recent possession change (< 400 ticks ≈ 6.7 seconds)
        if (ticksSinceChange < 400) {
            return 1;  // TRANSITION
        }

        // DESPERATION: When possession is very old (> 90000 ticks ≈ 2.5+ min sustained)
        // regardless of ball carrier position. Old possession = team can't break through,
        // needs to take whatever shot is available. Removed ownHalf requirement because
        // in Phase B (under shot budget pressure), urgency applies even when ball carrier
        // is in attacking territory - the team needs shots, not more build-up.
        if (ticksSinceChange > 90000) {
            return 2;  // DESPERATION
        }

        // BUILD_UP: Default — normal build-up play, including settled attacking possession
        return 0;
    }

    /**
     * Detect ball progression phase based on current ball X position.
     * 0 = BUILD_UP: ball in own half or midfield (|x| < 20)
     * 1 = ADVANCE: ball in middle third (20 <= |x| < 35)
     * 2 = FINALIZATION: ball in final third (|x| >= 35)
     */
    private int detectProgressionPhase(MatchStateSoA state, int playerId) {
        float ballX = state.ballX;
        float absX = Math.abs(ballX);

        if (absX < 20) {
            return 0;  // BUILD_UP: own half or early midfield
        } else if (absX < 35) {
            return 1;  // ADVANCE: middle third
        } else {
            return 2;  // FINALIZATION: final third
        }
    }

    /**
     * Calculate shot angle in degrees from distance.
     * Formula: 2 * atan2(GOAL_HALF_WIDTH, shotDistance) * (180/PI)
     */
    private float calculateShotAngle(float distanceToGoal) {
        return (float) (2.0 * Math.atan2(PhysicsConstants.GOAL_HALF_WIDTH, distanceToGoal) * (180.0 / Math.PI));
    }

    private int roleToIdx(PlayerRole role) {
        return switch (role) {
            case GK -> 0; case CB -> 1; case LB -> 2; case RB -> 3;
            case DM -> 4; case CM -> 5; case AM -> 6;
            case LW -> 7; case RW -> 8; case ST -> 9;
            default -> 0;
        };
    }

    /**
     * Find distance to closest opponent.
     */
    private float findClosestOpponentDistance(MatchStateSoA state, int playerId) {
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);
        int teamSide = state.getPlayerTeamSide(playerId);
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < 22; i++) {
            if (state.getPlayerTeamSide(i) == teamSide) continue;
            if (state.getPlayerRole(i) == PlayerRole.GK) continue;

            float dx = state.getPlayerX(i) - selfX;
            float dy = state.getPlayerY(i) - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < closestDist) {
                closestDist = dist;
            }
        }
        return closestDist;
    }

    /**
     * Check if passes viability threshold with memory penalty applied.
     */
    private boolean passesViabilityThreshold(MatchStateSoA state, int playerId, float memoryMultiplier) {
        int context = detectContext(state, playerId);
        float viabilityScore = calculateViabilityScore(state, playerId, context, null);
        float threshold = getRoleThreshold(state.getPlayerRole(playerId), context);
        return (viabilityScore * memoryMultiplier) >= threshold;
    }

    /**
     * Calculate shot viability score.
     * Weighted: distanceScore×0.4 + angleScore×0.3 + pressureScore×0.3
     */
    private float calculateViabilityScore(MatchStateSoA state, int playerId, int context, ScopedRNG rng) {
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);
        int teamSide = state.getPlayerTeamSide(playerId);

        // Distance score (0-1, higher is better)
        float goalX = (teamSide == 0) ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
        float dxGoal = goalX - selfX;
        float dyGoal = 0.0f - selfY;
        float distance = (float) Math.sqrt(dxGoal * dxGoal + dyGoal * dyGoal);
        // maxDist increased to 45m to match STEP1 cap (was 35m which created dead zone)
        float maxDist = (context == 2) ? 25.0f : (context == 1) ? 30.0f : 45.0f;
        float distanceScore = Math.max(0.0f, 1.0f - (distance / maxDist));

        // Angle score (0-1, higher is better)
        float angle = calculateShotAngle(distance);
        float angleScore = Math.min(1.0f, angle / 45.0f);

        // Pressure score (0-1, higher is better = fewer nearby opponents)
        float closestOppDist = findClosestOpponentDistance(state, playerId);
        float pressureScore = Math.min(1.0f, closestOppDist / 10.0f);

        return (distanceScore * 0.4f) + (angleScore * 0.3f) + (pressureScore * 0.3f);
    }

    /**
     * Get shot threshold based on player role and context.
     */
    private float getRoleThreshold(PlayerRole role, int context) {
        // TARGET: ~12-16 shots/match. Aim for ~35% STEP5 passage.
        // Band between 0.27-0.37 (24.84 shots) and 0.30-0.40 (0 shots, collapsed).
        // Try midpoint: 0.29-0.39.
        switch (role) {
            case ST:
                return (context == 2) ? 0.28f : 0.12f;  // ST: primary finisher — more permissive (was 0.15)
            case AM:
                return (context == 2) ? 0.30f : 0.28f;  // AM: second-most permissive
            case RW:
            case LW:
                return (context == 2) ? 0.30f : 0.28f;  // RW/LW: tied second-most permissive
            case CM:
            case RM:
            case LM:
                return (context == 2) ? 0.30f : 0.25f;  // CM: moderate — secondary shooter, lower than DM
            case DM:
                return (context == 2) ? 0.32f : 0.30f;  // DM: moderate — can shoot if positioned
            case CB:
                return (context == 2) ? 0.33f : 0.30f;  // CB: lowered from 0.50 to 0.30 to pass STEP5 with viability ~0.417
            case RB:
            case LB:
                return (context == 2) ? 0.33f : 0.50f;  // Fullbacks: high threshold
            case GK:
                return (context == 2) ? 0.35f : 0.60f;  // GK: most restrictive
            default:
                return 0.33f;
        }
    }

    /**
     * Find best pass target using deterministic algorithm.
     * Returns playerId or -1 if no valid target.
     */
    private int findPassTarget(MatchStateSoA state, int playerId, ScopedRNG rng) {
        float bestScore = 0.0f;
        int bestTarget = -1;
        int team = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);
        float bestDistance = Float.MAX_VALUE;

        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;

            // Distance filter
            float dx = state.getPlayerX(i) - selfX;
            float dy = state.getPlayerY(i) - selfY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < PhysicsConstants.MIN_PASS_DISTANCE) continue;

            PlayerRole role = state.getPlayerRole(i);

            // Forward progress (meters)
            float forwardProgress;
            if (team == 0) {  // Home attacks right (+X)
                forwardProgress = state.getPlayerX(i) - selfX;
            } else {          // Away attacks left (-X)
                forwardProgress = selfX - state.getPlayerX(i);
            }
            // Reject targets with zero or negative forward progress
            if (forwardProgress <= 0.0f) continue;

            // Space score: count opponents within 10m of target
            int nearbyOpponents = 0;
            for (int j = 0; j < 22; j++) {
                if (state.getPlayerTeamSide(j) == team) continue;
                if (state.getPlayerRole(j) == PlayerRole.GK) continue;
                float odx = state.getPlayerX(j) - state.getPlayerX(i);
                float oday = state.getPlayerY(j) - state.getPlayerY(i);
                float oppDist = (float) Math.sqrt(odx * odx + oday * oday);
                if (oppDist < PhysicsConstants.SPACE_SEARCH_RADIUS) {
                    nearbyOpponents++;
                }
            }
            float spaceScore = (float) nearbyOpponents;  // Lower is better

            // Primary finisher bonus: strongly redirect ball to attackers in advanced positions
            float finisherBonus = (role == PlayerRole.ST || role == PlayerRole.AM ||
                                   role == PlayerRole.RW || role == PlayerRole.LW) ? 30.0f : 0.0f;

            // Combined score: finisher bonus + forward progress + mild space preference
            float progressBonus = forwardProgress * 2.0f;
            float score = finisherBonus + progressBonus + (spaceScore * 2.0f);

            // Tie-breaking: prefer closer player if score equal
            if (score > bestScore) {
                bestScore = score;
                bestTarget = i;
                bestDistance = distance;
            } else if (score == bestScore && score > 0.0f) {
                // Tie-break: choose player with less distance
                if (distance < bestDistance) {
                    bestTarget = i;
                    bestDistance = distance;
                }
            }
        }

        return bestTarget;
    }

    /**
     * Find an attacker (ST/RW/LW/AM) ahead of the ball carrier who can receive a pass.
     * Used by FINAL-THIRD TRANSFER to redirect shots from DM/CB/LB/RB to attackers.
     * Returns playerId or -1 if no valid attacker is ahead.
     */
    private int findForwardAttackerTarget(MatchStateSoA state, int playerId) {
        int team = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);

        int bestTarget = -1;
        float bestScore = -1.0f;

        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;

            PlayerRole role = state.getPlayerRole(i);
            if (role != PlayerRole.ST && role != PlayerRole.RW &&
                role != PlayerRole.LW && role != PlayerRole.AM) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);

            float forwardProg = (team == 0) ? (px - selfX) : (selfX - px);
            if (forwardProg < 5.0f) continue;

            float absX = Math.abs(px);
            if (absX < 35.0f) continue;

            float dx = px - selfX;
            float dy = py - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 5.0f) continue;

            float goalX = (team == 0) ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
            float distToGoal = (float) Math.sqrt((goalX - px) * (goalX - px) + py * py);
            float score = forwardProg + (40.0f - distToGoal);

            if (score > bestScore) {
                bestScore = score;
                bestTarget = i;
            }
        }

        return bestTarget;
    }

    /**
     * Find the nearest forward player (ST/RW/LW/AM/CM) to pass to when no
     * attacker is found in the attacking third.
     */
    private int findNearestForwardPlayer(MatchStateSoA state, int playerId) {
        int team = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);

        int bestTarget = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;

            PlayerRole role = state.getPlayerRole(i);
            // Forward players: ST, RW, LW, AM, CM (not DM, not defenders)
            if (role == PlayerRole.GK || role == PlayerRole.CB ||
                role == PlayerRole.LB || role == PlayerRole.RB || role == PlayerRole.DM) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);

            float dx = px - selfX;
            float dy = py - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            // Must be ahead of the player (forward progress)
            float forwardProg = (team == 0) ? (px - selfX) : (selfX - px);
            if (forwardProg < 3.0f) continue;

            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = i;
            }
        }

        return bestTarget;
    }

    /**
     * Execute dribble action - soft kick that maintains possession.
     */
    private void executeDribble(MatchStateSoA state, int playerId, ScopedRNG rng) {
        int team = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);

        // Direction: toward opponent goal
        // Home (team=0): attacks AWAY_GOAL_X=52.5 → +1 (positive X)
        // Away (team=1): attacks HOME_GOAL_X=-52.5 → -1 (negative X)
        float forwardDir = (team == 0) ? 1.0f : -1.0f;

        // Target: 3-5m forward depending on match phase.
        // Late match (minute >= 60): increase to 5m to help ball reach attacking third.
        // This partially offsets possession-pullback which otherwise cancels 40-60% of dribble advance.
        int minute = (int)((state.totalTicks * 90L) / PhysicsConstants.TICKS_PER_MATCH);
        float dribbleDistance = (minute >= 60) ? 5.0f : 3.0f;
        float goalLineX = (team == 0)
            ? PhysicsConstants.AWAY_GOAL_X - 5.0f
            : PhysicsConstants.HOME_GOAL_X + 5.0f;
        float targetX = selfX + (forwardDir * dribbleDistance);
        targetX = forwardDir > 0 ? Math.min(targetX, goalLineX) : Math.max(targetX, goalLineX);
        float targetY = selfY + rng.nextFloatRange(-3.0f, 3.0f);

        // Boundary clamp - keep ball on field (X stays within field, Y stays in bounds)
        targetX = Math.max(PhysicsConstants.FIELD_BOUNDARY_X_MIN + 5,
                           Math.min(PhysicsConstants.FIELD_BOUNDARY_X_MAX - 5, targetX));
        targetY = Math.max(PhysicsConstants.FIELD_BOUNDARY_Y_MIN,
                           Math.min(PhysicsConstants.FIELD_BOUNDARY_Y_MAX, targetY));

        // Dribble = soft kick (low power, low height)
        BallPhysicsEngine.applyKick(state, targetX, targetY,
                                    PhysicsConstants.DRIBBLE_POWER,
                                    PhysicsConstants.DRIBBLE_HEIGHT, rng);

        // State mutations
        // Use dribbleLeadTicksRemaining to allow ball to lead player for ~15 ticks.
        // This is a moderate duration that allows stable 1-3m separation.
        // State is checked in possession handler to use low followSpeed.
        state.dribbleLeadTicksRemaining = 4;
        state.lastKickTick = state.totalTicks;
    }

    /**
     * Classify shot outcome based on physics result.
     * Returns: 0=BLOCKED, 1=MISSED, 2=SAVED, 3=GOAL, 4=ACTIVE
     */
    private int classifyShotOutcome(MatchStateSoA state) {
        // Check ballEntersGoal
        boolean ballEntersGoal = checkBallEntersGoal(state);
        boolean gkSaveOccurred = checkGKSaveOccurred(state);
        boolean ballHitDefender = checkBallHitDefender(state);
        boolean ballMissedGoalFrame = checkBallMissedGoalFrame(state);

        if (ballEntersGoal) {
            return 3;  // GOAL
        } else if (gkSaveOccurred) {
            return 2;  // SAVED
        } else if (ballHitDefender) {
            return 0;  // BLOCKED
        } else if (ballMissedGoalFrame) {
            return 1;  // MISSED
        } else {
            return 4;  // ACTIVE
        }
    }

    private boolean checkBallEntersGoal(MatchStateSoA state) {
        // Ball enters goal if ballX is within goal line and ballY within goal width
        float goalX = (state.ballX < 0) ? PhysicsConstants.HOME_GOAL_X : PhysicsConstants.AWAY_GOAL_X;
        float goalLineX = (state.ballX < 0) ? -52.5f : 52.5f;
        float absX = Math.abs(state.ballX);

        // Check if ball crossed goal line
        boolean crossedGoalLine = (state.prevBallX < goalLineX && state.ballX >= goalLineX) ||
                                  (state.prevBallX > goalLineX && state.ballX <= goalLineX);

        if (crossedGoalLine) {
            // Check if within goal width (y between -3.66 and +3.66)
            float goalHalfWidth = PhysicsConstants.GOAL_HALF_WIDTH;
            return Math.abs(state.ballY) <= goalHalfWidth;
        }
        return false;
    }

    private boolean checkGKSaveOccurred(MatchStateSoA state) {
        // GK save occurs if ball was deflected by GK
        // Simplified: check if ball is near GK position and was saved
        return state.reboundTicks > 0 && state.totalTicks - state.lastGKSaveTick < 5;
    }

    private boolean checkBallHitDefender(MatchStateSoA state) {
        // Ball hit defender if it was blocked
        return state.totalTicks - state.lastBlockTick < 3;
    }

    private boolean checkBallMissedGoalFrame(MatchStateSoA state) {
        // Ball missed goal frame if it went wide or above
        float goalLineX = (state.ballX < 0) ? -52.5f : 52.5f;
        boolean crossedLine = (state.prevBallX < goalLineX && state.ballX >= goalLineX) ||
                              (state.prevBallX > goalLineX && state.ballX <= goalLineX);
        if (crossedLine) {
            // Outside goal width
            return Math.abs(state.ballY) > PhysicsConstants.GOAL_HALF_WIDTH;
        }
        return false;
    }

    /**
     * Find a finisher target (ST → RW/LW → AM → CM) for finalization transfer.
     * ST is prioritized as central finisher even if slightly behind the ball.
     */
    private int findFinisherTarget(MatchStateSoA state, int playerId) {
        int team = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);

        // === ST PRIORITY: central finisher, may be behind ball ===
        // ST is the primary target. ST can be within [-15m, +45m] forwardProg.
        // ST must be in/near attacking third (absX >= 15, lowered from 25).
        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;
            if (state.getPlayerRole(i) != PlayerRole.ST) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);
            float absX = Math.abs(px);

            state.stConsideredAsFinisher++;

            // Position requirement: ST must be in/near attacking third
            // Lowered from 25 to 15 to allow progression transfer to select ST
            // who is positioned at x=35 (min attacking X) even when ball is at center.
            // Further lowered to 5 for segments 4-5 where ST avg X is ~2 and no
            // finisher can be found to push ball into attacking third.
            if (absX < 5.0f) continue;

            float forwardProg = (team == 0) ? (px - selfX) : (selfX - px);

            // ST allowed if: -15m <= forwardProg <= 65m
            // Increased from 45m to 65m to allow progression transfer when ball is deep.
            // When ball is at x=-30 (own half), ST at x=35 has forwardProg=65 — valid.
            if (forwardProg < -15.0f) {
                state.stRejectedTooFarBehind++;
                continue;
            }
            if (forwardProg > 65.0f) continue; // Too far ahead

            // Lateral distance check — ST must be reachable
            // INCREASED from 25 to 40 to allow passes to wide attacker positions
            // At X=-31, ST at Y=22 has lateral ~30m which should be valid for progression
            float dy = py - selfY;
            float lateralDist = Math.abs(dy);
            if (lateralDist > 40.0f) continue; // Too wide

            // Distance from ball carrier to ST
            float dx = px - selfX;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 3.0f) continue; // Too close (same position)

            state.stSelectedAsFinisher++;
            return i;
        }

        // === RW/LW FALLBACK ===
        // Wingers must be ahead of ball carrier (forwardProg >= 0)
        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;
            PlayerRole r = state.getPlayerRole(i);
            if (r != PlayerRole.RW && r != PlayerRole.LW) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);
            float absX = Math.abs(px);
            float forwardProg = (team == 0) ? (px - selfX) : (selfX - px);

            if (absX < 5.0f) continue;
            if (forwardProg < 0.0f) continue; // Must be ahead
            if (Math.abs(py - selfY) > 25.0f) continue;

            float dx = px - selfX;
            float dy = py - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 3.0f) continue;

            state.wingerSelectedAsFinisher++;
            return i;
        }

        // === AM FALLBACK ===
        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;
            if (state.getPlayerRole(i) != PlayerRole.AM) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);
            float absX = Math.abs(px);
            if (absX < 5.0f) continue;

            float forwardProg = (team == 0) ? (px - selfX) : (selfX - px);
            if (forwardProg < 0.0f) continue;
            if (Math.abs(py - selfY) > 25.0f) continue;

            float dx = px - selfX;
            float dy = py - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 3.0f) continue;

            return i;
        }

        // === CM RECYCLE (last resort, backward pass) ===
        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;
            if (state.getPlayerRole(i) != PlayerRole.CM) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);

            // CM must be clearly behind (recycle, not advance)
            float forwardProg = (team == 0) ? (px - selfX) : (selfX - px);
            if (forwardProg > -5.0f) continue; // Not a backward pass

            float dx = px - selfX;
            float dy = py - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 3.0f || dist > 30.0f) continue;

            return i;
        }

        return -1;
    }

    /**
     * Find a recycle pass target when no finisher is available.
     * Returns nearest teammate with no forward progress requirement.
     */
    private int findRecyclePassTarget(MatchStateSoA state, int playerId) {
        int team = state.getPlayerTeamSide(playerId);
        float selfX = state.getPlayerX(playerId);
        float selfY = state.getPlayerY(playerId);

        int bestTarget = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < 22; i++) {
            if (i == playerId) continue;
            if (state.getPlayerTeamSide(i) != team) continue;

            float px = state.getPlayerX(i);
            float py = state.getPlayerY(i);
            float dx = px - selfX;
            float dy = py - selfY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 3.0f || dist > 30.0f) continue;

            // Prefer backward or lateral passes (negative forward progress = recycle)
            float fwdProg = (team == 0) ? (px - selfX) : (selfX - px);
            if (fwdProg > 5.0f) continue; // Skip very forward passes

            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = i;
            }
        }
        return bestTarget;
    }
}

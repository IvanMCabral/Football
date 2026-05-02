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
 * Off-Ball AI for V32.
 * Handles player movement, positioning, and off-ball decisions.
 */
public final class OffBallAI {

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

    private PlayerDecision decideWithBall(MatchStateSoA state, int playerIdx,
                                          TickInput input, ScopedRNG rng) {
        // Player has the ball - decide action
        float ballX = state.ballX;
        float ballY = state.ballY;
        int teamSide = state.getPlayerTeamSide(playerIdx);
        PlayerRole role = state.getPlayerRole(playerIdx);

        // DEFENSIVE: GK ALWAYS clears the ball (never dribbles in field)
        if (role == PlayerRole.GK) {
            return PlayerDecision.clear(playerIdx);
        }

        // DEFENSIVE: If player has ball in own third, clear it forward
        boolean inOwnThird = (teamSide == 0 && ballX < -33) || (teamSide == 1 && ballX > 33);
        if (inOwnThird) {
            return PlayerDecision.clear(playerIdx);
        }

        // Check for shot opportunity
        float shotDistance = getDistanceToGoal(state, playerIdx);

        // PROBABILISTIC SHOT DECISION
        if (shotDistance > 3) {
            float baseProb;
            if (shotDistance < 10) {
                baseProb = 0.99f;
            } else if (shotDistance < 20) {
                baseProb = 0.95f;
            } else if (shotDistance < 35) {
                baseProb = 0.88f;
            } else if (shotDistance < 60) {
                baseProb = 0.75f;
            } else {
                baseProb = 0.50f;
            }

            if (rng.nextFloat() < baseProb) {
                float targetX = teamSide == 0 ? PhysicsConstants.AWAY_GOAL_X : PhysicsConstants.HOME_GOAL_X;
                float targetY = rng.nextFloatRange(-1, 1);
                return PlayerDecision.shoot(playerIdx, targetX, targetY, 0.88f);
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
        int teamSide = state.getPlayerTeamSide(playerIdx);
        PlayerRole role = state.getPlayerRole(playerIdx);
        boolean isPresser = (role == PlayerRole.CB || role == PlayerRole.LB ||
                             role == PlayerRole.RB || role == PlayerRole.DM);
        float forwardDir = (teamSide == 0) ? 1.0f : -1.0f;

        // FIX: Only defenders/dms (pressers) track the ball carrier.
        // All other roles maintain their structural formation Y position
        // regardless of where the ball carrier is. This prevents the blob.
        if (isPresser) {
            // Pressers: track ball carrier, position ahead of them
            float carrierX = state.getPlayerX(ballCarrier);
            float carrierY = state.getPlayerY(ballCarrier);
            float supportX = carrierX + forwardDir * 8;
            float supportY = carrierY + rng.nextFloatRange(-5, 5);
            supportX = Math.max(-50, Math.min(50, supportX));
            supportY = Math.max(-30, Math.min(30, supportY));
            return PlayerDecision.goToPosition(playerIdx, supportX, supportY);
        }

        // NON-PRESSERS: PROGRESSIVE ATTACKER POSITIONING
        // Position attackers based on BALL POSITION, not carrier position.
        // Attackers stay ahead of the ball but not excessively isolated.
        // Ball in own half = intermediate positions. Ball in attacking third = near goal.
        float formationY = state.getPlayerY(playerIdx);
        float ballX = state.ballX;
        float newX;

        // === LATE BUILDUP CORRECTION (minute >= 60) ===
        // When minute >= 60 and team has possession and ballController is a non-finisher
        // (CB/LB/RB/DM/CM), position attackers RELATIVE TO THE CARRIER, not the ball.
        // This creates forward passing targets so progression transfer can fire.
        // Without this, attackers stay at ball-relative positions (x=27-34) which are
        // BEHIND the carrier when carrier is at x=9-25, making forwardProg negative.
        int lateMinute = (int)((state.totalTicks * 90L) / PhysicsConstants.TICKS_PER_MATCH);
        boolean inLateBuildup = (lateMinute >= 60);
        PlayerRole carrierRole = (state.ballController >= 0) ? state.getPlayerRole(state.ballController) : null;
        boolean carrierIsNonFinisher = (carrierRole == PlayerRole.CB || carrierRole == PlayerRole.LB ||
                                        carrierRole == PlayerRole.RB || carrierRole == PlayerRole.DM ||
                                        carrierRole == PlayerRole.CM);
        boolean teamHasBall = (state.ballController >= 0 && state.getPlayerTeamSide(state.ballController) == teamSide);
        float ballCarrierX = (state.ballController >= 0) ? state.getPlayerX(state.ballController) : ballX;

        if (role == PlayerRole.ST) {
            // ST: Progressive based on ball X position
            // DEEP FIX: When ball is in attacking third, ST MUST stay at maximum
            // forward position. The previous logic allowed x=27 when |ballX|<20,
            // but this creates S1 distance rejections when ball is turned over
            // and ST is still at x=27 while ball goes to x=-10 (center).
            // With ball in attacking third, hard-clamp ST to x=40 to maximize
            // shot pipeline pass rate and prevent midfield ball-carrying.
            float absBallX = Math.abs(ballX);
            if (absBallX >= 35) {
                // Ball in attacking third: ST stays at maximum forward depth
                newX = forwardDir * 40.0f;
            } else if (absBallX >= 20) {
                // Ball in middle third: advance to x=34
                newX = forwardDir * 34.0f;
            } else {
                // Ball in own half: intermediate at x=27
                newX = forwardDir * 27.0f;
            }

            // LATE BUILDUP CORRECTION: position ahead of carrier, not ball
            // When minute >= 60 and team has possession and ballController is a non-finisher,
            // attackers must position ahead of the carrier to create valid forward passing options.
            // This fixes the issue where ST at x=2 can't be found by findFinisherTarget
            // because forwardProg = 2 - carrierX < 0 (ST behind carrier).
            if (inLateBuildup && teamHasBall && carrierIsNonFinisher) {
                // ST should be ballCarrierX + 15m toward goal — USE DIRECT ASSIGNMENT
                // Not Math.max: we want to position ST exactly 15m ahead, not use this as a minimum
                float carrierRelativeX = ballCarrierX + forwardDir * 15.0f;
                newX = carrierRelativeX;  // Direct: overrides ball-relative position
            }
        } else if (role == PlayerRole.LW || role == PlayerRole.RW) {
            // Wingers: slightly behind striker, wide channel
            float ballDepth = Math.abs(ballX);
            if (ballDepth < 20) {
                newX = forwardDir * 24.0f;
            } else if (ballDepth < 35) {
                newX = forwardDir * 30.0f;
            } else {
                newX = forwardDir * 36.0f;
            }

            // LATE BUILDUP CORRECTION: position ahead of carrier
            // USE DIRECT ASSIGNMENT so winger is positioned exactly 10m ahead of carrier
            if (inLateBuildup && teamHasBall && carrierIsNonFinisher) {
                float carrierRelativeX = ballCarrierX + forwardDir * 10.0f;
                newX = carrierRelativeX;  // Direct: overrides ball-relative position
            }
        } else if (role == PlayerRole.AM) {
            // AM: between midfield and striker
            float ballDepth = Math.abs(ballX);
            if (ballDepth < 20) {
                newX = forwardDir * 22.0f;
            } else if (ballDepth < 35) {
                newX = forwardDir * 28.0f;
            } else {
                newX = forwardDir * 33.0f;
            }

            // LATE BUILDUP CORRECTION: position ahead of carrier
            // USE DIRECT ASSIGNMENT so AM is positioned exactly 8m ahead of carrier
            if (inLateBuildup && teamHasBall && carrierIsNonFinisher) {
                float carrierRelativeX = ballCarrierX + forwardDir * 8.0f;
                newX = carrierRelativeX;  // Direct: overrides ball-relative position
            }
        } else if (role == PlayerRole.CM) {
            // CM: maintain half-space, relative to carrier
            float carrierXFromState = state.getPlayerX(ballCarrier);
            newX = carrierXFromState + forwardDir * 10;
        } else {
            // Fallback: stay near carrier
            float carrierX = state.getPlayerX(ballCarrier);
            newX = carrierX + forwardDir * 5;
        }

        // MINIMUM ATTACKING X: When team has possession, always enforce forward positioning.
        // Attackers must stay high to provide forward passing options during buildup,
        // regardless of where the ball is on the pitch.
        // This prevents the "wait until ball reaches midfield" problem.
        float minAttackingX;
        if (role == PlayerRole.ST) {
            minAttackingX = 35.0f;
        } else if (role == PlayerRole.LW || role == PlayerRole.RW) {
            minAttackingX = 30.0f;
        } else if (role == PlayerRole.AM) {
            minAttackingX = 28.0f;
        } else {
            minAttackingX = -1; // No minimum for non-attackers
        }

        if (minAttackingX > 0) {
            float minX = minAttackingX * forwardDir;
            if (teamSide == 0) {
                // Home (positive X): enforce minimum (x must be at least minX)
                newX = Math.max(newX, minX);
            } else {
                // Away (negative X): enforce maximum (x must be at most minX, which is negative)
                newX = Math.min(newX, minX);
            }
        }

        // PERSISTENT ATTACKING ANCHOR: If finisher has active anchor timer,
        // OR if minute >= 60 and team has possession and role is finisher,
        // force position to the full anchor value (not just a minimum).
        // This overrides ball-based recalculation and allows the player to
        // physically reach and hold the advanced position.

        // Phase-based anchor: activate when minute >= 60 and team has possession
        int currentAnchor = state.attackingAnchorTicks[playerIdx];
        int minute = (int)((state.totalTicks * 90L) / PhysicsConstants.TICKS_PER_MATCH);
        boolean isFinisher = (role == PlayerRole.ST || role == PlayerRole.LW ||
                             role == PlayerRole.RW || role == PlayerRole.AM);
        boolean inLatePhase = (minute >= 60);

        int effectiveAnchor = currentAnchor;
        if (inLatePhase && isFinisher) {
            // In late phase, set anchor to 600 ticks (~10 seconds).
            // Use Math.max to avoid resetting if currentAnchor > 600 (allows anchors to persist).
            effectiveAnchor = Math.max(currentAnchor, 600);
        }
        if (effectiveAnchor > 0) {
            // Calculate the anchor X for this role
            float anchorX;
            if (role == PlayerRole.ST) {
                anchorX = forwardDir * 35.0f;
            } else if (role == PlayerRole.LW || role == PlayerRole.RW) {
                anchorX = forwardDir * 30.0f;
            } else if (role == PlayerRole.AM) {
                anchorX = forwardDir * 28.0f;
            } else {
                anchorX = newX; // No anchor for non-finishers
            }

            // Force direct position to anchor — overrides ball-based recalculation
            newX = anchorX;
        }

        newX = Math.max(-52, Math.min(52, newX));

        // Formation Y is fixed — no carrier Y tracking
        float newY = formationY;
        newY = Math.max(-30, Math.min(30, newY));

        return PlayerDecision.goToPosition(playerIdx, newX, newY);
    }

    private PlayerDecision pressOrDefend(MatchStateSoA state, int playerIdx,
                                         int opponentIdx, ScopedRNG rng) {
        float oppX = state.getPlayerX(opponentIdx);
        float oppY = state.getPlayerY(opponentIdx);
        int teamSide = state.getPlayerTeamSide(playerIdx);
        float forwardDir = (teamSide == 0) ? 1.0f : -1.0f;

        // Distance to opponent
        float selfX = state.getPlayerX(playerIdx);
        float selfY = state.getPlayerY(playerIdx);
        float dist = (float) Math.sqrt((oppX - selfX) * (oppX - selfX) + (oppY - selfY) * (oppY - selfY));

        // Role check: ONLY defenders and defensive midfielders press
        PlayerRole role = state.getPlayerRole(playerIdx);
        boolean isPresser = (role == PlayerRole.CB || role == PlayerRole.LB ||
                             role == PlayerRole.RB || role == PlayerRole.DM);

        // Pressing: only defenders/defensive mids, and only within a tight radius
        // Limit to 3 pressers maximum converging on the ball carrier
        if (isPresser && dist < SimulationConstants.PRESSING_TRIGGER_DISTANCE) {
            return PlayerDecision.press(playerIdx, opponentIdx);
        }

        // NON-PRESSERS (ST, LW, RW, AM, CM):
        // Maintain FORMATION-BASED defensive shape, NOT a swarm behind the ball carrier.
        // Each role has a structurally-defined slot relative to the ball carrier.
        // This eliminates the dense clustering that creates near-zero closestOppDist.

        float defX;
        float defY;

        if (role == PlayerRole.ST) {
            // Striker: hold the defensive line — stay 2-4m ahead of own defenders
            // Not near the ball carrier — maintain offside line
            defX = oppX + (teamSide == 0 ? -8.0f : 8.0f);
            defY = oppY + rng.nextFloatRange(-4, 4);
        } else if (role == PlayerRole.LW || role == PlayerRole.RW) {
            // Winger: maintain wide channel, NOT converging on ball carrier
            // Stay at y=±22 (wide), x near halfway line
            defX = oppX + (teamSide == 0 ? 5.0f : -5.0f);
            defY = (role == PlayerRole.LW) ? -22.0f : 22.0f;
        } else if (role == PlayerRole.AM) {
            // Attacking mid: sit in front of defense, not near ball carrier
            defX = oppX + (teamSide == 0 ? -12.0f : 12.0f);
            defY = oppY + rng.nextFloatRange(-6, 6);
        } else {
            // CM: midfield anchor — maintain half-space, not swarm
            defX = oppX + (teamSide == 0 ? -10.0f : 10.0f);
            defY = (selfY > 0) ? 14.0f : -14.0f;
        }

        // HARD MINIMUM DISTANCE: if non-presser's target is within 8m of ball carrier,
        // redirect to formation position instead. Prevents all 7 non-pressers
        // from collapsing into a tight blob around the ball.
        float carrierX = state.getPlayerX(opponentIdx);
        float carrierY = state.getPlayerY(opponentIdx);
        float targetDistToCarrier = (float) Math.sqrt(
            (defX - carrierX) * (defX - carrierX) +
            (defY - carrierY) * (defY - carrierY));
        if (targetDistToCarrier < 8.0f) {
            // Redirect to base formation position instead of clustering
            defX = selfX;  // stay where you are — don't converge
            defY = selfY;
        }

        // MINIMUM ATTACKING X OVERRIDE: Even when opponent has ball, enforce the
        // minimum attacking X. Attackers must stay forward to provide a passing
        // outlet when team regains possession. This anchor applies to BOTH
        // supportTeammate AND pressOrDefend paths.
        float minAttackingX;
        if (role == PlayerRole.ST) {
            minAttackingX = 35.0f;
        } else if (role == PlayerRole.LW || role == PlayerRole.RW) {
            minAttackingX = 30.0f;
        } else if (role == PlayerRole.AM) {
            minAttackingX = 28.0f;
        } else {
            minAttackingX = -1;
        }

        if (minAttackingX > 0) {
            float minX = minAttackingX * forwardDir;
            if (teamSide == 0) {
                // Home: enforce minimum forward X
                defX = Math.max(defX, minX);
            } else {
                // Away: enforce maximum (negative) X
                defX = Math.min(defX, minX);
            }
        }

        // PERSISTENT ATTACKING ANCHOR: If finisher has active anchor timer,
        // force position to the full anchor value (not just a minimum).
        int anchorTicks = state.attackingAnchorTicks[playerIdx];
        if (effectiveAnchor > 0) {
            float anchorX;
            if (role == PlayerRole.ST) {
                anchorX = forwardDir * 35.0f;
            } else if (role == PlayerRole.LW || role == PlayerRole.RW) {
                anchorX = forwardDir * 30.0f;
            } else if (role == PlayerRole.AM) {
                anchorX = forwardDir * 28.0f;
            } else {
                anchorX = defX;
            }

            if (teamSide == 0) {
                defX = Math.max(defX, anchorX);
            } else {
                defX = Math.min(defX, anchorX);
            }
        }

        defX = Math.max(-52, Math.min(52, defX));
        defY = Math.max(-30, Math.min(30, defY));

        return PlayerDecision.goToPosition(playerIdx, defX, defY);
    }

    private PlayerDecision chaseLooseBall(MatchStateSoA state, int playerIdx, ScopedRNG rng) {
        // If player just shot the ball, don't chase for 30 ticks (allow rebounds)
        if (state.lastShooterId == playerIdx && state.totalTicks - state.lastShotTick < 30) {
            return PlayerDecision.hold(playerIdx);
        }
        // If player just cleared the ball, they should NOT chase it
        // This prevents defenders/GKs from recovering their own clears
        if (state.lastClearPlayerId == playerIdx && state.totalTicks - state.lastClearTick < 80) {
            // Stay in defensive position - don't chase
            return PlayerDecision.hold(playerIdx);
        }
        // Chase the ball
        return PlayerDecision.chaseBall(playerIdx, 0.9f);
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

    /**
     * Check if any teammate is close enough to potentially contest a rebound.
     * This prevents shots from going unreachable and ensures balanced play.
     */
    private boolean hasCloseTeammate(MatchStateSoA state, int playerIdx, float maxDistance) {
        int teamSide = state.getPlayerTeamSide(playerIdx);
        float px = state.getPlayerX(playerIdx);
        float py = state.getPlayerY(playerIdx);

        for (int i = 0; i < 22; i++) {
            if (i == playerIdx) continue;
            if (state.getPlayerTeamSide(i) != teamSide) continue;

            float ix = state.getPlayerX(i);
            float iy = state.getPlayerY(i);
            float dist = (float) Math.sqrt((px - ix) * (px - ix) + (py - iy) * (py - iy));

            if (dist < maxDistance) {
                return true;
            }
        }
        return false;
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

    private boolean canShoot(MatchStateSoA state, int playerIdx) {
        // Simple version - can always shoot if in range
        // A teammate being ahead is not a reason to block
        return true;
    }
}

package com.footballmanager.domain.simulation.v32.state;

import com.footballmanager.domain.simulation.v32.enums.BallZone;
import com.footballmanager.domain.simulation.v32.enums.MatchPhase;
import com.footballmanager.domain.simulation.v32.enums.PlayerRole;

/**
 * Match state using Struct-of-Arrays (SoA) layout for cache efficiency.
 * All 22 players' data stored in parallel arrays for SIMD-friendly access.
 */
public final class MatchStateSoA {

    // ==================== BALL STATE ====================
    public float ballX;
    public float ballY;
    public float ballZ;
    public float ballVX;
    public float ballVY;
    public float ballVZ;

    /** Previous ball position for goal detection (cruce) */
    public float prevBallX;
    public float prevBallY;

    /** Ball zone for quick lookup */
    public BallZone ballZone;

    /** Player index controlling ball (-1 if none) */
    public int ballController; // -1 = loose

    /** Goal scoring state */
    public boolean goalJustScored;
    public int goalCooldownTicks;

    // ==================== SHOT/KICK TRACKING ====================
    /** Tick of last kick (shot or pass) */
    public long lastKickTick;

    /** Last player who shot */
    public int lastShooterId;
    /** Tick of last shot */
    public long lastShotTick;

    /** Per-team shot tracking for cooldowns — index by teamSide (0=home, 1=away) */
    public final int[] lastTeamShotTick = new int[2];

    /** Per-segment shot budget pacing: shots allowed per segment per team (hard cap) */
    public final int[] segmentBudgetPerTeam = new int[2];
    /** Remaining shot allowance per segment per team */
    public final int[] segmentAllowanceRemaining = new int[2];
    /** Carry-over shots from previous segment per team */
    public final int[] segmentCarryOverRemaining = new int[2];
    /** Current segment index (0-5) */
    public int currentSegment;
    /** Shots allowed per segment per team (instrumentation) */
    public final int[] shotsAllowedBySegment = new int[2];
    /** Shots blocked by segment budget per team (instrumentation) */
    public final int[] shotsBlockedBySegmentBudget = new int[2];

    /** Team-level pacing blocks: count per 15-min segment (0-14, 15-29, ..., 75-89) */
    public final int[] teamPacingBlocksBySegment = new int[6];
    /** Total team-level pacing blocks this match */
    public int teamPacingBlocksTotal;

    /** Per-player shot attempt tracking */
    public final int[] lastShotAttemptTick = new int[22];
    /** Tick of last finalization transfer (per player, 15-tick cooldown) */
    public final int[] lastFinalizationTransferTick = new int[22];
    /** Timer: player who just received finalization pass gets brief protection from OffBallAI backward pull */
    public final int[] justReceivedFinalizationPassTicks = new int[22];
    /** Ticks remaining for persistent attacking anchor (finishers held forward in late match) */
    public final int[] attackingAnchorTicks = new int[22];

    /** Last player who cleared the ball (defensive clear, not shot) */
    public int lastClearPlayerId;
    /** Tick of last clear */
    public long lastClearTick;

    // ==================== CONTROLLED SHOT BUDGET SYSTEM ====================
    /** Target shots for home team (set at match start) */
    public int homeShotBudget;
    /** Target shots for away team (set at match start) */
    public int awayShotBudget;
    /** Actual shots taken by home team */
    public int homeShotsTaken;
    /** Actual shots taken by away team */
    public int awayShotsTaken;
    /** Whether shot budget has been initialized */
    public boolean shotBudgetInitialized;

    /** Current shot being processed - should this shot score? (controlled outcome) */
    public boolean currentShotShouldScore;
    /** Team taking current shot (0=home, 1=away) */
    public int currentShotTaker;
    /** Whether the current shot was approved by the shot selection pipeline (not OffBallAI direct) */
    public boolean pipelineApprovedShot;

    // ==================== GOAL AUTHORITY INSTRUMENTATION ====================
    /** Goals from legacy direct scoring (shouldScore path in executeShot) */
    public int legacyGoals;
    /** Goals from physics engine (checkGoals path) */
    public int physicsGoals;
    /** Shots that went through legacy shouldScore path */
    public int legacyShots;

    // ==================== PENDING GOAL SYSTEM ====================
    /** Pending goal pending resolution by physics engine */
    public boolean pendingGoal;
    /** Ticks remaining for pendingGoal to survive — allows ball flight time to goal */
    public int pendingGoalTicks;
    /** Player ID of pending goal shooter */
    public int pendingGoalPlayerId;
    /** Team side of pending goal (0=home, 1=away) */
    public int pendingGoalTeamSide;
    /** xG value of pending goal shot */
    public float pendingGoalXg;
    /** Ball X position at time of shot */
    public float pendingGoalBallX;
    /** Ball Y position at time of shot */
    public float pendingGoalBallY;
    /** Ball Z position at time of shot */
    public float pendingGoalBallZ;

    // ==================== SHOT RECORD (per match, for xG validation) ====================
    /** Max shots to record per match (safety cap) */
    public static final int MAX_SHOT_RECORD = 256;
    /** xG value for each shot taken this match */
    public final float[] shotRecordXg = new float[MAX_SHOT_RECORD];
    /** Role index for each shot (0=GK,1=CB,...,9=ST) */
    public final int[] shotRecordRole = new int[MAX_SHOT_RECORD];
    /** Team side for each shot (0=home, 1=away) */
    public final int[] shotRecordTeam = new int[MAX_SHOT_RECORD];
    /** Ball X position at time of shot */
    public final float[] shotRecordBallX = new float[MAX_SHOT_RECORD];
    /** Ball Y position at time of shot */
    public final float[] shotRecordBallY = new float[MAX_SHOT_RECORD];
    /** Distance to goal at time of shot */
    public final float[] shotRecordDistToGoal = new float[MAX_SHOT_RECORD];
    /** Outcome: 0=miss/save/block, 1=goal */
    public final int[] shotRecordOutcome = new int[MAX_SHOT_RECORD];
    /** Tick when shot was taken */
    public final int[] shotRecordTick = new int[MAX_SHOT_RECORD];
    /** Number of shots recorded this match */
    public int shotRecordCount;

    // ==================== PER-ROLE xG AND GOALS ====================
    /** Goals scored by role (0=GK,1=CB,...,9=ST) for home team */
    public final int[] homeGoalsByRole = new int[10];
    /** Goals scored by role for away team */
    public final int[] awayGoalsByRole = new int[10];
    /** xG accumulated by role for home team */
    public final float[] homeXgByRole = new float[10];
    /** xG accumulated by role for away team */
    public final float[] awayXgByRole = new float[10];

    // ==================== GK STATE ====================
    /** GK confidence: 0.5 = neutral, 1.0 = unbeatable, 0.0 = hopeless */
    public float homeGKConfidence;
    public float awayGKConfidence;
    /** GK recent saves/Goals against for confidence calculation */
    public int homeGKRecentSaves;
    public int homeGKRecentGoals;
    public int awayGKRecentSaves;
    public int awayGKRecentGoals;

    // ==================== REBOUND SYSTEM ====================
    /** Rebound window in ticks (after GK save, attackers get second chance) */
    public int reboundTicks;
    /** Side that should benefit from rebound (attacking team) */
    public int reboundAttackerSide;
    /** Tick of last GK save (for super-save limiting) */
    public long lastGKSaveTick;

    // ==================== MATCH MOMENTUM ====================
    /** Track consecutive goals by each team for streak detection */
    public int homeGoalStreak;
    public int awayGoalStreak;
    /** Tick of last goal for each team */
    public long lastHomeGoalTick;
    public long lastAwayGoalTick;

    // ==================== xG TRACKING (Variance Reduction) ====================
    /** Accumulated Expected Goals for home team (sum of xG from shots) */
    public float homeExpectedGoals;
    /** Accumulated Expected Goals for away team */
    public float awayExpectedGoals;
    /** Tick of last goal for anti-streak cooldown */
    public long lastGoalTick;
    /** Anti-streak cooldown: after a goal, reduce conversion for ticks */
    public int goalAntiStreakCooldown;
    /** Goal dampening: after a goal, reduce scoring for 300 ticks (Fix #3) */
    public int goalDampeningTicks;

    // ==================== FAST BALL RECOVERY SYSTEM ====================
    /** Tick when ball became free (no controller) */
    public long ballFreeTick;
    /** Tracks if ball was just kicked (shot/pass) - for post-kick velocity dampening */
    public boolean justKicked;
    /** Shot speed before kick - for velocity dampening calculation */
    public float preKickBallSpeed;

    // ==================== MATCH STATE ====================
    public int homeTeamId;
    public int awayTeamId;
    public int homeScore;
    public int awayScore;
    public int currentMinute;
    public int currentSecond; // Milliseconds within minute
    public boolean matchOver;

    /** Current match phase */
    public MatchPhase phase;

    /** Tick count since match start */
    public long totalTicks;

    // ==================== CLEAN POSSESSION MODEL ====================
    /** Possession sequence counter — incremented each time ballController changes to a different player */
    public int possessionSeq;
    /** Tick when current possession started (first tick this player held the ball) */
    public long possessionStartTick;
    /** Player index who started this possession (ballController at possession start) */
    public int possessionOwnerPlayer;
    /** Team side of current possession (0=home, 1=away) — tracked independently of player ID */
    public int possessionTeamSide;
    /** Tick when possession last changed to a new team/player */
    public long lastPossessionChangeTick;
    /** Legacy — team side of last possession (used by Phase 2 tracking in V32TickEngine) */
    public int teamSideOfLastPossession;
    /** Tick when ball last became loose (-1 if currently controlled) */
    public long looseBallStartTick;
    /** ballController value from previous tick (-1 initially) */
    public int prevBallController;
    // ==================== CLEAN PIPELINE FUNNEL COUNTERS ====================
    /** Pipeline invocations — each tick where ballController != -1 AND pipeline runs */
    public long pipelineInvocations;
    /** Step 1 rejections — distance cap exceeded */
    public long step1DistanceRejections;
    /** Step 2 rejections — pressure veto (<2.5m) */
    public long step2PressureRejections;
    /** Step 3 rejections — memory check fail (ticksSinceShot < 150 AND viability < threshold × 0.60) */
    public long step3MemoryRejections;
    /** Step 4 rejections — viability floor < 0.40 */
    public long step4ViabilityRejections;
    /** Step 5 rejections — role threshold fail */
    public long step5RoleRejections;
    /** CB diagnostic: sum of viabilityScore at STEP5 rejection */
    public double step5CbViabilitySum;
    /** CB diagnostic: sum of threshold at STEP5 rejection */
    public double step5CbThresholdSum;
    /** CB diagnostic: count of STEP5 rejections for CB */
    public long step5CbCount;
    /** CB diagnostic: sum of player X at STEP5 rejection */
    public double step5CbPlayerXSum;
    /** CB diagnostic: sum of ball X at STEP5 rejection */
    public double step5CbBallXSum;
    /** Pass fallback triggered */
    public long passFallbackTriggered;
    /** Pipeline returned SHOOT decision */
    public long pipelineShootApprovals;

    // ==================== BUDGET PRESSURE DIAGNOSTICS ====================
    /** Accumulated shot deficit (expected - taken) across all ticks for avg */
    public double budgetDeficitSum;
    /** Count of deficit measurements (one per shot evaluation tick) */
    public long budgetDeficitCount;
    /** Shots that passed due to budget pressure (adjustedFloor/Threshold below base) */
    public long shotsUnderBudgetPressure;
    /** Sum of adjustedFloor values across all STEP4 evaluations */
    public double adjustedFloorSum;
    /** Count of STEP4 evaluations for adjustedFloor avg */
    public long adjustedFloorCount;

    // Per-role breakdown for shot pipeline analysis
    public long[] approvalsByRole;
    public long[] step4RejByRole;
    public long[] step5RejByRole;
    public long[] step1RejByRole;
    public long[] step2RejByRole;
    public long[] pipelineEvalsByRole;

    // ==================== POST-FILTER LOSS DIAGNOSTICS ====================
    /** Post-STEP5: viability < budget-forced-reduced threshold (budget fallback fail) */
    public long lostBudgetForcedFail;
    /** Post-STEP5: viability < possession-age-reduced threshold (possession-age fallback fail) */
    public long lostPossessionAgeFail;
    /** Post-STEP5: player in DECISION_COOLDOWN after clearing STEP5 (lock short-circuit) */
    public long lostLockShortCircuit;
    /** Pipeline approved SHOOT but blocked by team-level shot refractory */
    public long lostTeamRefractoryBypass;
    /** Post-STEP5: non-finisher (DM/CB/LB/RB) in final third with valid attacker target (final-third pass) */
    public long lostFinalThirdPass;
    /** Post-STEP5: non-finisher in final third, no valid attacker target */
    public long lostFinalThirdNoTarget;
    /** Post-STEP5: non-finisher but ball not in attacking third */
    public long lostNonFinisherNoAttacking;
    /** Post-STEP5: unclassified dribble return */
    public long lostDribbleGeneric;

    // ==================== STEP1 DIAGNOSTICS ====================
    /** Count of forced forward passes from defender-in-attacking-third */
    public long forcedForwardPasses;
    /** Count of finalization transfers (non-finisher → finisher in attacking third) */
    public long finalizationTransfers;
    /** Count of progression transfers (midfield non-finisher → finisher, before final third) */
    public long progressionTransfers;
    /** Count of times ST was considered as finalization target */
    public long stConsideredAsFinisher;
    /** Count of times ST was rejected because too far behind ball */
    public long stRejectedTooFarBehind;
    /** Count of times ST was selected as finalization target */
    public long stSelectedAsFinisher;
    /** Count of times RW/LW were selected as finalization target */
    public long wingerSelectedAsFinisher;
    /** Sum of distToGoal for STEP1 rejected candidates by role */
    public double[] step1DistSumByRole;
    /** Sum of playerX for STEP1 rejected candidates by role */
    public double[] step1PlayerXSumByRole;
    /** Sum of ballX for STEP1 rejected candidates by role */
    public double[] step1BallXSumByRole;
    /** Count of STEP1 rejections by role and zone: [role][0=ownHalf, 1=mid, 2=attackingThird] */
    public long[][] step1CountByRoleZone;
    /** Count of STEP1 rejections by role and context: [role][0=BUILD_UP, 1=TRANSITION, 2=DESPERATION] */
    public long[][] step1CountByRoleContext;
    /** Count of STEP1 rejections by role: near-miss (+5m over cap) */
    public long[] step1NearMiss5ByRole;
    /** Count of STEP1 rejections by role: +10m over cap */
    public long[] step1NearMiss10ByRole;
    /** Count of STEP1 rejections by role: +15m over cap */
    public long[] step1NearMiss15ByRole;

    public int dribbleLeadTicksRemaining;

    // ==================== PRESSURE / STEP2 DIAGNOSTICS ====================
    /** Sum of closestOppDist across all STEP2 evaluations (for avg calculation) */
    public double pressureDistSum;
    /** Count of STEP2 evaluations (for avg calculation) */
    public long pressureDistCount;
    /** Min closestOppDist observed at STEP2 */
    public float pressureDistMin;
    /** Max closestOppDist observed at STEP2 */
    public float pressureDistMax;
    /** Histogram: closestOppDist buckets [0-1m, 1-2m, ..., 19-20m, 20m+] */
    public long[] pressureDistHistogram;

    // STEP2 per-role breakdown
    /** Sum of closestOppDist for STEP2 REJECTED candidates by role */
    public double[] step2DistSumByRole;
    /** Sum of playerX for STEP2 REJECTED candidates by role */
    public double[] step2PlayerXSumByRole;
    /** Sum of ballX for STEP2 REJECTED candidates by role */
    public double[] step2BallXSumByRole;
    /** Sum of closestOppDist for STEP2 ACCEPTED candidates (passed pressure) by role */
    public double[] step2AcceptDistSumByRole;
    /** Count of accepted candidates by role (for avg calculation) */
    public long[] step2AcceptCountByRole;
    /** Histogram of closestOppDist for STEP2 REJECTED candidates */
    public long[] step2RejectHistogram;

    // Context distribution counters
    public long contextBuildUp;
    public long contextTransition;
    public long contextDesperation;

    // ==================== ACTUAL EXECUTE SHOT COUNTERS ====================
    /** Pipeline SHOOT decisions that successfully entered executeShot() */
    public long executeShotCalled;
    /** executeShot called but blocked by 5-tick per-player cooldown */
    public long executeShotBlockedByCooldown;
    /** Pipeline SHOOT -> executeShot conversion rate tracking */
    public long pipelineToExecuteShotConversions;

    // ==================== PER-POSSESSION STATISTICS ====================
    /** Total possessions across all matches (for averaging) */
    public long totalPossessions;
    /** Possessions where >=1 pipeline SHOOT was approved */
    public long possessionsWithShootApproval;
    /** Total pipeline SHOOT approvals across all possessions (for avg calculation) */
    public long totalShootApprovalsInPossessions;
    /** Possessions where >=1 executeShot was called */
    public long possessionsWithExecuteShot;
    /** Total executeShot calls across all possessions */
    public long totalExecuteShotsInPossessions;

    // ==================== REPEATED-DECISION STATISTICS ====================
    /** Pipeline SHOOT approvals where same player was approved in the SAME possession */
    public long repeatedShootApprovals;
    /** Ticks between repeated SHOOT approvals (same player, same possession) — accumulated */
    public long sumTicksBetweenRepeatedShootApprovals;
    /** Count of repeated SHOOT pairs (for avg calculation) */
    public long repeatedShootApprovalPairs;
    /** executeShot calls where same player called twice in SAME possession */
    public long repeatedExecuteShotCalls;
    /** Ticks between repeated executeShot calls (same player, same possession) — accumulated */
    public long sumTicksBetweenRepeatedExecuteShots;
    /** Count of repeated executeShot pairs */
    public long repeatedExecuteShotPairs;

    // ==================== APPROVAL-TO-EXECUTION AUDIT ====================
    /** Pipeline SHOOT returned — reached applyDecision SHOOT case */
    public long approvalReachedApplyDecision;
    /** Approved SHOOT discarded in lock mode before reaching applyDecision */
    public long approvalDiscardedInLockMode;
    /** Approved SHOOT reached executeShot — blocked by 5-tick cooldown */
    public long approvalBlockedByCooldown;
    /** Approved SHOOT reached executeShot — passed cooldown, actually executed */
    public long approvalPassedCooldown;
    /** Approved SHOOT reached executeShot — result was GOAL (shouldScore true) */
    public long approvalResultedInGoal;
    /** Approved SHOOT reached executeShot — result was SAVE/MISS/BLOCK */
    public long approvalResultedInNonGoal;

    // ==================== SHOT APPROVAL TRACKING (within possession) ====================
    /** Shots approved so far in current possession */
    public int shotsInCurrentPossession;
    /** Player ID of last SHOOT approval in current possession */
    public int lastShootApprovalPlayer;
    /** Tick of last SHOOT approval */
    public long lastShootApprovalTick;
    /** Cumulative possession duration across all possessions */
    public long totalPossessionDuration;

    // ==================== SHOT DECISION LOCK (per player, per possession) ====================
    /** Per-player tick of last pipeline SHOOT decision.
     *  Blocks same player from generating SHOOT approvals for DECISION_COOLDOWN ticks.
     *  Distinct from lastShotAttemptTick (which tracks executeShot entry, not pipeline decision). */
    public final int[] lastShotDecisionTick = new int[22];

    // ==================== LEGACY TRACKING FIELDS (still used by V32TickEngine) ====================
    /** Per-player rebound flag (single-receiver rule) */
    public final boolean[] justRebounded = new boolean[22];
    /** Tick of last blocked shot (0 = never blocked) */
    public long lastBlockTick;
    /** Tick of last missed shot (0 = never missed) */
    public long lastReboundTick;
    /** Whether a shot was taken this tick (for outcome classification) */
    public boolean shotTakenThisTick;

    // ==================== POSSESSION STATE ====================

    // ==================== PLAYER ARRAYS (22 players) ====================
    // Indices 0-10: Home team (0 = GK)
    // Indices 11-21: Away team (11 = GK)

    /** Player X positions */
    public final float[] playerX = new float[22];
    /** Player Y positions */
    public final float[] playerY = new float[22];
    /** Player Z positions (height, 0 = ground) */
    public final float[] playerZ = new float[22];

    /** Player velocity X */
    public final float[] playerVX = new float[22];
    /** Player velocity Y */
    public final float[] playerVY = new float[22];
    /** Player velocity Z (for jumps) */
    public final float[] playerVZ = new float[22];

    /** Player rotation angle (radians) */
    public final float[] playerRotation = new float[22];

    /** Player roles */
    public final PlayerRole[] playerRole = new PlayerRole[22];

    /** Player OVR ratings */
    public final byte[] playerOvr = new byte[22];

    /** Player energy levels (0-1) */
    public final float[] playerEnergy = new float[22];

    /** Player stamina levels (0-1) */
    public final float[] playerStamina = new float[22];

    /** Player team (0 = home, 1 = away) */
    public final byte[] playerTeam = new byte[22];

    /** Tick when player last acquired ball (for dribble timeout tracking) */
    public final long[] lastBallAcquireTick = new long[22];

    /** When true, executePass skips setting justKicked (full-speed pass, no damping). Auto-clears. */
    public boolean skipVelocityDamping;

    // ==================== TACTICAL STATE ====================
    /** Home team formation index */
    public int homeFormation;
    /** Away team formation index */
    public int awayFormation;

    /** Home team tactical style (0=DEFEND, 1=COUNTER, 2=BALANCED, 3=ATTACK) */
    public byte homeTacticalStyle;
    /** Away team tactical style */
    public byte awayTacticalStyle;

    /** Home team pressing intensity (0-1) */
    public float homePressingIntensity;
    /** Away team pressing intensity */
    public float awayPressingIntensity;

    /** Home team momentum (0-1, 0.5 = neutral) */
    public float homeMomentum;
    /** Away team momentum */
    public float awayMomentum;

    // ==================== POSSESSION STATE ====================
    /** Possessing team (0=home, 1=away, -1=loose) */
    public int possessionTeam;
    /** Possessing player index (-1 if loose) */
    public int possessionPlayer;

    /** Home team possession percentage */
    public float homePossession;
    /** Away team possession percentage */
    public float awayPossession;

    /** Home team shots count */
    public int homeShots;
    /** Away team shots count */
    public int awayShots;

    /** Home team shots on target */
    public int homeShotsOnTarget;
    /** Away team shots on target */
    public int awayShotsOnTarget;

    /** Home team fouls */
    public int homeFouls;
    /** Away team fouls */
    public int awayFouls;

    // ==================== HALF TIME / EXTRA TIME ====================
    public boolean firstHalfOver;
    public int extraTimeMinutes;
    public boolean penalties;

    // ==================== WEATHER / CONDITIONS ====================
    /** Temperature in Celsius */
    public byte temperature;
    /** Humidity percentage */
    public byte humidity;
    /** Wind speed in m/s */
    public float windSpeed;

    public MatchStateSoA() {
        this.ballZone = BallZone.DEFENSIVE_ZONE;
        this.phase = MatchPhase.BUILD_UP;
        this.homeFormation = 0;
        this.awayFormation = 0;
        this.homeTacticalStyle = 2; // BALANCED
        this.awayTacticalStyle = 2;
        this.homePressingIntensity = 0.5f;
        this.awayPressingIntensity = 0.5f;
        this.homeMomentum = 0.5f;
        this.awayMomentum = 0.5f;
        this.possessionTeam = -1;
        this.possessionPlayer = -1;
        this.homePossession = 0.5f;
        this.awayPossession = 0.5f;
        this.temperature = 20;
        this.humidity = 50;
        this.windSpeed = 2.0f;
        this.extraTimeMinutes = 0;
        this.penalties = false;
        this.goalJustScored = false;
        this.goalCooldownTicks = 0;
        this.lastKickTick = -100000;
        this.lastShooterId = -1;
        this.lastShotTick = -100000;
        this.lastTeamShotTick[0] = -1000000;  // Far enough back that first shot always passes refractory
        this.lastTeamShotTick[1] = -1000000;
        // Per-segment shot budget pacing init
        this.currentSegment = 0;
        for (int t = 0; t < 2; t++) {
            this.segmentBudgetPerTeam[t] = 0;
            this.segmentAllowanceRemaining[t] = 0;
            this.segmentCarryOverRemaining[t] = 0;
            this.shotsAllowedBySegment[t] = 0;
            this.shotsBlockedBySegmentBudget[t] = 0;
        }
        // Initialize pacing blocks
        for (int i = 0; i < 6; i++) this.teamPacingBlocksBySegment[i] = 0;
        this.teamPacingBlocksTotal = 0;
        // Initialize lastShotAttemptTick array
        for (int i = 0; i < 22; i++) {
            this.lastShotAttemptTick[i] = -1000;
            this.lastFinalizationTransferTick[i] = -1000;
            this.justReceivedFinalizationPassTicks[i] = 0;
            this.attackingAnchorTicks[i] = 0;
            this.lastBallAcquireTick[i] = -100000;
            this.lastShotDecisionTick[i] = -1000;
        }
        this.lastClearPlayerId = -1;
        this.lastClearTick = -100000;
        // GK confidence starts neutral
        this.homeGKConfidence = 0.6f;
        this.awayGKConfidence = 0.6f;
        this.homeGKRecentSaves = 0;
        this.homeGKRecentGoals = 0;
        this.awayGKRecentSaves = 0;
        this.awayGKRecentGoals = 0;
        this.reboundTicks = 0;
        this.reboundAttackerSide = -1;
        this.lastGKSaveTick = -100000;
        this.homeGoalStreak = 0;
        this.awayGoalStreak = 0;
        this.lastHomeGoalTick = -100000;
        this.lastAwayGoalTick = -100000;
        this.homeExpectedGoals = 0f;
        this.awayExpectedGoals = 0f;
        this.lastGoalTick = -100000;
        this.goalAntiStreakCooldown = 0;
        this.goalDampeningTicks = 0;
        // Shot budget system
        this.homeShots = 0;
        this.awayShots = 0;
        this.homeShotsTaken = 0;
        this.awayShotsTaken = 0;
        this.homeShotBudget = 0;
        this.awayShotBudget = 0;
        this.shotBudgetInitialized = false;
        this.currentShotShouldScore = false;
        this.currentShotTaker = -1;
        this.pipelineApprovedShot = false;
        // Goal authority instrumentation
        this.legacyGoals = 0;
        this.physicsGoals = 0;
        this.legacyShots = 0;
        // Pending goal system
        this.pendingGoal = false;
        this.pendingGoalTicks = 0;
        this.pendingGoalPlayerId = -1;
        this.pendingGoalTeamSide = -1;
        this.pendingGoalXg = 0f;
        this.pendingGoalBallX = 0f;
        this.pendingGoalBallY = 0f;
        this.pendingGoalBallZ = 0f;
        // Shot record system
        this.shotRecordCount = 0;
        // Fast ball recovery system
        this.ballFreeTick = -100000;
        this.justKicked = false;
        this.preKickBallSpeed = 0;
        // Clean possession model
        this.possessionSeq = 0;
        this.possessionStartTick = 0;
        this.possessionOwnerPlayer = -1;
        this.possessionTeamSide = -1;
        this.lastPossessionChangeTick = 0;
        this.teamSideOfLastPossession = -1;
        this.looseBallStartTick = -1;
        this.prevBallController = -1;
        // Pipeline funnel instrumentation
        this.pipelineInvocations = 0;
        this.step1DistanceRejections = 0;
        this.step2PressureRejections = 0;
        this.step3MemoryRejections = 0;
        this.step4ViabilityRejections = 0;
        this.step5RoleRejections = 0;
        this.step5CbViabilitySum = 0;
        this.step5CbThresholdSum = 0;
        this.step5CbThresholdSum = 0;
        this.step5CbCount = 0;
        this.step5CbPlayerXSum = 0;
        this.step5CbBallXSum = 0;
        this.passFallbackTriggered = 0;
        this.pipelineShootApprovals = 0;
        this.dribbleLeadTicksRemaining = 0;
        this.budgetDeficitSum = 0;
        this.budgetDeficitCount = 0;
        this.shotsUnderBudgetPressure = 0;
        this.adjustedFloorSum = 0;
        this.adjustedFloorCount = 0;
        this.approvalsByRole = new long[10];
        this.step4RejByRole = new long[10];
        this.step5RejByRole = new long[10];
        this.step1RejByRole = new long[10];
        this.step2RejByRole = new long[10];
        this.pipelineEvalsByRole = new long[10];
        this.lostBudgetForcedFail = 0;
        this.lostPossessionAgeFail = 0;
        this.lostLockShortCircuit = 0;
        this.lostTeamRefractoryBypass = 0;
        this.lostFinalThirdPass = 0;
        this.lostFinalThirdNoTarget = 0;
        this.lostNonFinisherNoAttacking = 0;
        this.lostDribbleGeneric = 0;
        this.step1DistSumByRole = new double[10];
        this.step1PlayerXSumByRole = new double[10];
        this.step1BallXSumByRole = new double[10];
        this.step1CountByRoleZone = new long[10][3];
        this.step1CountByRoleContext = new long[10][3];
        this.step1NearMiss5ByRole = new long[10];
        this.step1NearMiss10ByRole = new long[10];
        this.step1NearMiss15ByRole = new long[10];
        this.forcedForwardPasses = 0;
        this.finalizationTransfers = 0;
        this.progressionTransfers = 0;
        this.stConsideredAsFinisher = 0;
        this.stRejectedTooFarBehind = 0;
        this.stSelectedAsFinisher = 0;
        this.wingerSelectedAsFinisher = 0;
        this.pressureDistSum = 0;
        this.pressureDistCount = 0;
        this.pressureDistMin = Float.MAX_VALUE;
        this.pressureDistMax = 0;
        this.pressureDistHistogram = new long[21]; // 0-20m+ in 1m buckets
        this.step2DistSumByRole = new double[10];
        this.step2PlayerXSumByRole = new double[10];
        this.step2BallXSumByRole = new double[10];
        this.step2AcceptDistSumByRole = new double[10];
        this.step2AcceptCountByRole = new long[10];
        this.step2RejectHistogram = new long[21]; // 0-20m+ in 1m buckets
        this.executeShotCalled = 0;
        this.executeShotBlockedByCooldown = 0;
        this.pipelineToExecuteShotConversions = 0;
        this.approvalReachedApplyDecision = 0;
        this.approvalDiscardedInLockMode = 0;
        this.approvalBlockedByCooldown = 0;
        this.approvalPassedCooldown = 0;
        this.approvalResultedInGoal = 0;
        this.approvalResultedInNonGoal = 0;
        this.contextBuildUp = 0;
        this.contextTransition = 0;
        this.contextDesperation = 0;
        // Per-possession statistics
        this.totalPossessions = 0;
        this.possessionsWithShootApproval = 0;
        this.totalShootApprovalsInPossessions = 0;
        this.possessionsWithExecuteShot = 0;
        this.totalExecuteShotsInPossessions = 0;
        // Repeated-decision statistics
        this.repeatedShootApprovals = 0;
        this.sumTicksBetweenRepeatedShootApprovals = 0;
        this.repeatedShootApprovalPairs = 0;
        this.repeatedExecuteShotCalls = 0;
        this.sumTicksBetweenRepeatedExecuteShots = 0;
        this.repeatedExecuteShotPairs = 0;
        // Shot approval tracking
        this.shotsInCurrentPossession = 0;
        this.lastShootApprovalPlayer = -1;
        this.lastShootApprovalTick = 0;
        this.totalPossessionDuration = 0;
        // Legacy tracking fields
        for (int i = 0; i < 22; i++) {
            this.justRebounded[i] = false;
        }
        this.lastBlockTick = 0;
        this.lastReboundTick = 0;
        this.shotTakenThisTick = false;
    }

    // ==================== PLAYER ACCESSORS ====================

    public float getPlayerX(int idx) { return playerX[idx]; }
    public float getPlayerY(int idx) { return playerY[idx]; }
    public float getPlayerZ(int idx) { return playerZ[idx]; }
    public float getPlayerVX(int idx) { return playerVX[idx]; }
    public float getPlayerVY(int idx) { return playerVY[idx]; }
    public float getPlayerVZ(int idx) { return playerVZ[idx]; }
    public float getPlayerRotation(int idx) { return playerRotation[idx]; }
    public PlayerRole getPlayerRole(int idx) { return playerRole[idx]; }
    public byte getPlayerOvr(int idx) { return playerOvr[idx]; }
    public float getPlayerEnergy(int idx) { return playerEnergy[idx]; }
    public float getPlayerStamina(int idx) { return playerStamina[idx]; }
    public byte getPlayerTeam(int idx) { return playerTeam[idx]; }

    public void setPlayerX(int idx, float v) { playerX[idx] = v; }
    public void setPlayerY(int idx, float v) { playerY[idx] = v; }
    public void setPlayerZ(int idx, float v) { playerZ[idx] = v; }
    public void setPlayerVX(int idx, float v) { playerVX[idx] = v; }
    public void setPlayerVY(int idx, float v) { playerVY[idx] = v; }
    public void setPlayerVZ(int idx, float v) { playerVZ[idx] = v; }
    public void setPlayerRotation(int idx, float v) { playerRotation[idx] = v; }
    public void setPlayerEnergy(int idx, float v) { playerEnergy[idx] = v; }
    public void setPlayerStamina(int idx, float v) { playerStamina[idx] = v; }

    public float getPlayerStrength(int idx) {
        return 0.6f + playerEnergy[idx] * 0.4f;
    }

    public int getPlayerTeamSide(int idx) {
        return idx < 11 ? 0 : 1;
    }

    // ==================== CLONE ====================

    /**
     * Creates a deep copy of this state.
     */
    public MatchStateSoA copy() {
        MatchStateSoA copy = new MatchStateSoA();
        copy.ballX = this.ballX;
        copy.ballY = this.ballY;
        copy.ballZ = this.ballZ;
        copy.ballVX = this.ballVX;
        copy.ballVY = this.ballVY;
        copy.ballVZ = this.ballVZ;
        copy.prevBallX = this.prevBallX;
        copy.prevBallY = this.prevBallY;
        copy.ballZone = this.ballZone;
        copy.ballController = this.ballController;
        copy.goalJustScored = this.goalJustScored;
        copy.goalCooldownTicks = this.goalCooldownTicks;
        copy.homeTeamId = this.homeTeamId;
        copy.awayTeamId = this.awayTeamId;
        copy.homeScore = this.homeScore;
        copy.awayScore = this.awayScore;
        copy.currentMinute = this.currentMinute;
        copy.currentSecond = this.currentSecond;
        copy.matchOver = this.matchOver;
        copy.phase = this.phase;
        copy.totalTicks = this.totalTicks;
        System.arraycopy(this.playerX, 0, copy.playerX, 0, 22);
        System.arraycopy(this.playerY, 0, copy.playerY, 0, 22);
        System.arraycopy(this.playerZ, 0, copy.playerZ, 0, 22);
        System.arraycopy(this.playerVX, 0, copy.playerVX, 0, 22);
        System.arraycopy(this.playerVY, 0, copy.playerVY, 0, 22);
        System.arraycopy(this.playerVZ, 0, copy.playerVZ, 0, 22);
        System.arraycopy(this.playerRotation, 0, copy.playerRotation, 0, 22);
        System.arraycopy(this.playerRole, 0, copy.playerRole, 0, 22);
        System.arraycopy(this.playerOvr, 0, copy.playerOvr, 0, 22);
        System.arraycopy(this.playerEnergy, 0, copy.playerEnergy, 0, 22);
        System.arraycopy(this.playerStamina, 0, copy.playerStamina, 0, 22);
        System.arraycopy(this.playerTeam, 0, copy.playerTeam, 0, 22);
        copy.homeFormation = this.homeFormation;
        copy.awayFormation = this.awayFormation;
        copy.homeTacticalStyle = this.homeTacticalStyle;
        copy.awayTacticalStyle = this.awayTacticalStyle;
        copy.homePressingIntensity = this.homePressingIntensity;
        copy.awayPressingIntensity = this.awayPressingIntensity;
        copy.homeMomentum = this.homeMomentum;
        copy.awayMomentum = this.awayMomentum;
        copy.possessionTeam = this.possessionTeam;
        copy.possessionPlayer = this.possessionPlayer;
        copy.homePossession = this.homePossession;
        copy.awayPossession = this.awayPossession;
        copy.homeShots = this.homeShots;
        copy.awayShots = this.awayShots;
        copy.homeShotsOnTarget = this.homeShotsOnTarget;
        copy.awayShotsOnTarget = this.awayShotsOnTarget;
        copy.homeFouls = this.homeFouls;
        copy.awayFouls = this.awayFouls;
        copy.firstHalfOver = this.firstHalfOver;
        copy.extraTimeMinutes = this.extraTimeMinutes;
        copy.penalties = this.penalties;
        copy.temperature = this.temperature;
        copy.humidity = this.humidity;
        copy.windSpeed = this.windSpeed;
        // Shot budget system
        copy.homeShotBudget = this.homeShotBudget;
        copy.awayShotBudget = this.awayShotBudget;
        copy.homeShotsTaken = this.homeShotsTaken;
        copy.awayShotsTaken = this.awayShotsTaken;
        copy.shotBudgetInitialized = this.shotBudgetInitialized;
        copy.currentShotShouldScore = this.currentShotShouldScore;
        copy.currentShotTaker = this.currentShotTaker;
        copy.pipelineApprovedShot = this.pipelineApprovedShot;
        copy.legacyGoals = this.legacyGoals;
        copy.physicsGoals = this.physicsGoals;
        copy.legacyShots = this.legacyShots;
        copy.pendingGoal = this.pendingGoal;
        copy.pendingGoalPlayerId = this.pendingGoalPlayerId;
        copy.pendingGoalTeamSide = this.pendingGoalTeamSide;
        copy.pendingGoalXg = this.pendingGoalXg;
        copy.pendingGoalBallX = this.pendingGoalBallX;
        copy.pendingGoalBallY = this.pendingGoalBallY;
        copy.pendingGoalBallZ = this.pendingGoalBallZ;
        // Shot record system
        copy.shotRecordCount = this.shotRecordCount;
        System.arraycopy(this.shotRecordXg, 0, copy.shotRecordXg, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordRole, 0, copy.shotRecordRole, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordTeam, 0, copy.shotRecordTeam, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordBallX, 0, copy.shotRecordBallX, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordBallY, 0, copy.shotRecordBallY, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordDistToGoal, 0, copy.shotRecordDistToGoal, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordOutcome, 0, copy.shotRecordOutcome, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.shotRecordTick, 0, copy.shotRecordTick, 0, MAX_SHOT_RECORD);
        System.arraycopy(this.homeGoalsByRole, 0, copy.homeGoalsByRole, 0, 10);
        System.arraycopy(this.awayGoalsByRole, 0, copy.awayGoalsByRole, 0, 10);
        System.arraycopy(this.homeXgByRole, 0, copy.homeXgByRole, 0, 10);
        System.arraycopy(this.awayXgByRole, 0, copy.awayXgByRole, 0, 10);
        // Fast ball recovery system
        copy.ballFreeTick = this.ballFreeTick;
        copy.justKicked = this.justKicked;
        copy.preKickBallSpeed = this.preKickBallSpeed;
        // Shot selection system
        copy.lastPossessionChangeTick = this.lastPossessionChangeTick;
        copy.teamSideOfLastPossession = this.teamSideOfLastPossession;
        System.arraycopy(this.justRebounded, 0, copy.justRebounded, 0, 22);
        copy.lastBlockTick = this.lastBlockTick;
        copy.lastReboundTick = this.lastReboundTick;
        copy.shotTakenThisTick = this.shotTakenThisTick;
        // Team shot pacing
        System.arraycopy(this.lastTeamShotTick, 0, copy.lastTeamShotTick, 0, 2);
        System.arraycopy(this.teamPacingBlocksBySegment, 0, copy.teamPacingBlocksBySegment, 0, 6);
        copy.teamPacingBlocksTotal = this.teamPacingBlocksTotal;
        // Per-segment shot budget
        copy.currentSegment = this.currentSegment;
        System.arraycopy(this.segmentBudgetPerTeam, 0, copy.segmentBudgetPerTeam, 0, 2);
        System.arraycopy(this.segmentAllowanceRemaining, 0, copy.segmentAllowanceRemaining, 0, 2);
        System.arraycopy(this.segmentCarryOverRemaining, 0, copy.segmentCarryOverRemaining, 0, 2);
        System.arraycopy(this.shotsAllowedBySegment, 0, copy.shotsAllowedBySegment, 0, 2);
        System.arraycopy(this.shotsBlockedBySegmentBudget, 0, copy.shotsBlockedBySegmentBudget, 0, 2);
        return copy;
    }
}

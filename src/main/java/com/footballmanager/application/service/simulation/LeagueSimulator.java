package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.domain.TeamOverallCalculator;
import com.footballmanager.application.service.simulation.v24.V24SuspensionLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24InjuryRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24InjuryMutationApplier;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerRatingsAssembler;
import com.footballmanager.application.service.simulation.v24.V24CareerMutationPolicy;
import com.footballmanager.application.service.simulation.v24.V24CareerMutationResult;
import com.footballmanager.application.service.simulation.v24.V24CareerMutationService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngineProvider;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResultAdapter;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.service.MatchSimulator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Simula TODOS los partidos de la liga del usuario.
 * Incluye todas las divisiones de la carrera del usuario.
 *
 * <p>Phase 10C2: Optional V23 engine path behind useV23LeagueEngine flag.
 * Default is false — existing DefaultMatchSimulator path is used.
 * When flag is true, MatchEngineImpl.simulateWithStrength() is used with
 * computed OVRs from TeamOverallCalculator and in-memory Team objects.
 *
 * <p>V24D5B: Optional V24 detailed engine path behind useV24DetailedEngine flag.
 * Default is false. When flag is true, V24DetailedMatchEngine.simulate() is used
 * via V24MatchContextFactory. Aggregate result is mapped to MatchResultData.
 * If context build fails, falls back to default/V23 path — round must complete.
 */
@Slf4j
public class LeagueSimulator {

    private final MatchSimulator matchSimulator;
    private final MatchEngineImpl matchEngine;
    private final boolean useV23LeagueEngine;
    private final boolean useV24DetailedEngine;
    private final boolean persistDetail;
    private final V24MatchContextFactory v24ContextFactory;
    private final V24DetailedMatchEngineProvider v24EngineProvider;
    private final V24DetailedMatchStoragePort storagePort;
    private final V24PlayerRatingsAssembler v24PlayerRatingsAssembler;
    private final V24CareerMutationService v24MutationService;
    private final V24CareerMutationPolicy v24MutationPolicy;
    private final V24SuspensionLifecycleApplier v24SuspensionLifecycleApplier = new V24SuspensionLifecycleApplier();
    private final V24InjuryRecoveryLifecycleApplier v24InjuryRecoveryLifecycleApplier = new V24InjuryRecoveryLifecycleApplier();

    /**
     * Primary constructor — useV23LeagueEngine and useV24DetailedEngine default to false.
     * Maintains existing Spring wiring compatibility.
     */
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false, false, false, null, false, false, false, false, false);
    }

    /**
     * Three-argument constructor for backward compatibility with existing tests.
     * useV24DetailedEngine defaults to false.
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, false, false, null, false, false, false, false, false);
    }

    /**
     * Four-argument constructor for backward compatibility with existing tests.
     * persistDetail defaults to false.
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, useV24DetailedEngine, false, null, false, false, false, false, false);
    }

    /**
     * Five-argument constructor for backward compatibility with existing tests.
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine,
                          boolean persistDetail, V24DetailedMatchStoragePort storagePort) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, useV24DetailedEngine,
                persistDetail, storagePort, false, false, false, false, false);
    }

    /**
     * Full constructor with optional V23 engine, V24 flags, and career mutation flags.
     * @param matchSimulator      the existing domain service for DefaultMatchSimulator path
     * @param matchEngine         optional MatchEngineImpl for V23 path (can be null if flag is false)
     * @param useV23LeagueEngine  if true, V23 engine path is used; if false, DefaultMatchSimulator path
     * @param useV24DetailedEngine if true, V24 detailed engine path is attempted
     * @param persistDetail       if true, V24 detail snapshot is saved to Redis after simulation
     * @param storagePort         V24DetailedMatchStoragePort for persistence (can be null if persistDetail is false)
     * @param mutateCareerState  master gate for career mutation (all effects disabled if false)
     * @param persistInjuries     if true, apply INJURY events from V24 timeline to SessionPlayer
     * @param persistFatigue      if true, apply energy drain (not yet implemented)
     * @param persistDiscipline  if true, apply card/suspension logic (not yet implemented)
     * @param persistForm         if true, apply form updates (not yet implemented)
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine,
                          boolean persistDetail, V24DetailedMatchStoragePort storagePort,
                          boolean mutateCareerState, boolean persistInjuries,
                          boolean persistFatigue, boolean persistDiscipline,
                          boolean persistForm) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, useV24DetailedEngine,
                persistDetail, storagePort, mutateCareerState, persistInjuries,
                persistFatigue, persistDiscipline, persistForm,
                new V24DetailedMatchEngine());
    }

    /**
     * Internal constructor with full control including V24 engine provider.
     * Used by production (default engine) and tests (fake/stub engine injection).
     */
    LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                    boolean useV23LeagueEngine, boolean useV24DetailedEngine,
                    boolean persistDetail, V24DetailedMatchStoragePort storagePort,
                    boolean mutateCareerState, boolean persistInjuries,
                    boolean persistFatigue, boolean persistDiscipline,
                    boolean persistForm,
                    V24DetailedMatchEngineProvider v24EngineProvider) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
        this.useV24DetailedEngine = useV24DetailedEngine;
        this.persistDetail = persistDetail;
        this.storagePort = storagePort;
        this.v24ContextFactory = new V24MatchContextFactory();
        this.v24EngineProvider = v24EngineProvider;
        this.v24PlayerRatingsAssembler = new V24PlayerRatingsAssembler();
        this.v24MutationPolicy = new V24CareerMutationPolicy(
                mutateCareerState, persistInjuries, persistFatigue,
                persistDiscipline, persistForm);
        this.v24MutationService = new V24CareerMutationService(new V24InjuryMutationApplier());
    }

    /**
     * Simula todos los partidos de la fecha en la liga del usuario.
     * Esto incluye TODAS las divisiones de su carrera.
     */
    public void simulateLeagueRound(CareerSave career, int round) {
        TournamentState tournamentState = career.getTournamentState();
        List<MatchFixture> allFixtures = tournamentState.getFixtures();

        // V24D6D6B: Capture pre-round state before fixture loop
        V24RoundMutationTracking tracking = new V24RoundMutationTracking();

        // V24D6I2: Capture pre-round injured players for injury recovery lifecycle
        Set<String> preRoundInjured = capturePreRoundInjuredPlayerIds(career);

        for (MatchFixture fixture : allFixtures) {
            if (fixture.getRound() != round) continue;
            if (!fixture.canBeSimulated()) continue;

            // Phase 10C1: OVR computed via TeamOverallCalculator
            int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId());
            int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());

            if (useV24DetailedEngine) {
                V24DetailedMatchResult v24Result = simulateWithV24Engine(career, fixture, homeOvr, awayOvr, tournamentState, tracking);
                if (v24Result != null) {
                    tracking.v24RoundProcessed = true;
                }
            } else if (useV23LeagueEngine) {
                simulateWithV23Engine(fixture, homeOvr, awayOvr, tournamentState);
            } else {
                simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            }
        }

        // V24D6D6B: Run suspension lifecycle after full round loop
        applyV24SuspensionLifecycle(career, round, allFixtures, tracking);

        // V24D6I2: Run injury recovery lifecycle after suspension lifecycle
        applyV24InjuryRecoveryLifecycle(career, round, allFixtures, tracking, preRoundInjured);
    }

    // ========== DefaultMatchSimulator Path (original behavior) ==========

    private void simulateWithDefaultEngine(MatchFixture fixture, int homeOvr, int awayOvr, TournamentState tournamentState) {
        MatchSimulator.MatchResult result = matchSimulator.simulateQuick(
                fixture.getHomeTeamId(),
                fixture.getAwayTeamId(),
                homeOvr,
                awayOvr
        );

        // Hardcoded possession (50/50) and shots (5/5) — original behavior
        MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
                result.homeGoals(), result.awayGoals(), 50, 50, 5, 5
        );

        tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
    }

    // ========== V23 Engine Path (optional, behind flag) ==========

    private void simulateWithV23Engine(MatchFixture fixture, int homeOvr, int awayOvr, TournamentState tournamentState) {
        if (matchEngine == null) {
            throw new IllegalStateException("useV23LeagueEngine is true but MatchEngineImpl is not provided");
        }

        Team homeTeam = buildMinimalTeam(fixture.getHomeTeamId(), "Home Team");
        Team awayTeam = buildMinimalTeam(fixture.getAwayTeamId(), "Away Team");
        long seed = deriveSeed(fixture);

        MatchResult result = matchEngine.simulateWithStrength(homeTeam, awayTeam, homeOvr, awayOvr, seed)
                .block(Duration.ofSeconds(5));

        if (result == null) {
            throw new IllegalStateException("V23 engine returned null for fixture " + fixture.getMatchId());
        }

        // Map to MatchResultData — events and summary are discarded
        MatchFixture.MatchResultData resultData = MatchResultDataAdapter.fromMatchResult(result);
        tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
    }

    // ========== V24 Detailed Engine Path (V24D5B, behind flag) ==========

    /**
     * Attempts V24 detailed engine simulation for a fixture.
     * If context build fails, falls back to default engine — round must complete.
     *
     * @return V24DetailedMatchResult if V24 path succeeded, null if fell back to default
     */
    private V24DetailedMatchResult simulateWithV24Engine(CareerSave career, MatchFixture fixture,
                                        int homeOvr, int awayOvr, TournamentState tournamentState,
                                        V24RoundMutationTracking tracking) {
        long seed = deriveSeed(fixture);
        SessionTeam homeTeam = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam awayTeam = career.getSessionTeam(fixture.getAwayTeamId());

        if (homeTeam == null || awayTeam == null) {
            log.warn("[V24D5B] Cannot simulate fixture {} with V24: missing team data, falling back to default",
                    fixture.getMatchId());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        }

        try {
            V24MatchContext context = v24ContextFactory.build(
                    career, fixture, homeTeam, awayTeam, seed);

            V24DetailedMatchResult v24Result = v24EngineProvider.simulate(context, seed);
            MatchFixture.MatchResultData resultData = V24DetailedMatchResultAdapter.toMatchResultData(v24Result);
            tournamentState.recordMatchResult(fixture.getMatchId(), resultData);

            log.debug("[V24D5B] Fixture {} simulated with V24 engine: {} - {}",
                    fixture.getMatchId(), resultData.homeGoals, resultData.awayGoals);

            // V24D5C: persist detail if flag is enabled
            if (persistDetail && storagePort != null) {
                persistV24Detail(career, fixture, homeTeam.getName(), awayTeam.getName(), v24Result);
            }

            // V24D6D6B: collect participation from starting XI
            collectStartingXIParticipation(context, tracking);

            // V24D6D6B: collect RED_CARD and timeline participation from result
            collectV24ResultParticipation(v24Result, tracking);

            // V24D6B3: apply career mutation if enabled
            applyV24CareerMutation(career, v24Result, tracking);

            return v24Result;

        } catch (IllegalArgumentException e) {
            log.warn("[V24D5B] V24 context build failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        } catch (Exception e) {
            log.warn("[V24D5B] V24 simulation failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        }
    }

    /**
     * V24D5C: Persist V24DetailedMatchData snapshot to Redis via storage port.
     * V24D5F: Persist V24 detailed match data including per-player ratings.
     * Player ratings are derived from CareerSave starting XI + match timeline.
     * Best-effort: failures are logged and do not fail the match/round.
     */
    private void persistV24Detail(CareerSave career, MatchFixture fixture,
                                   String homeTeamName, String awayTeamName,
                                   V24DetailedMatchResult v24Result) {
        try {
            String careerId = career.getData().getCareerId();
            Integer seasonNumber = career.getSeasonManager().getCurrentSeason();
            Integer round = fixture.getRound();

            // V24D5F: derive per-player ratings from starting XI + timeline
            List<V24PlayerMatchRatingDto> playerRatings =
                    v24PlayerRatingsAssembler.assemblePlayerRatings(career, fixture, v24Result);

            V24DetailedMatchData detail = V24DetailedMatchData.fromResult(
                    careerId,
                    seasonNumber,
                    round,
                    homeTeamName,
                    awayTeamName,
                    v24Result,
                    playerRatings
            );

            storagePort.save(careerId, detail);
            log.debug("[V24D5F] Detail saved for fixture {} in career {}", fixture.getMatchId(), careerId);

        } catch (Exception e) {
            log.warn("[V24D5F] Failed to persist detail for fixture {}: {}, continuing round",
                    fixture.getMatchId(), e.getMessage());
        }
    }

    /**
     * V24D6B3: Apply career mutations from V24 match result to CareerSave SessionPlayers.
     *
     * <p>Called only after successful V24 simulation. Mutation is best-effort:
     * failures are logged and do not fail the match/round.
     *
     * <p>Mutation is skipped if:
     * - V24DetailedEngine path is not enabled
     * - mutate-career-state master flag is false
     * - all specific mutation flags (injury/fatigue/discipline/form) are false
     */
    private void applyV24CareerMutation(CareerSave career, V24DetailedMatchResult v24Result,
                                         V24RoundMutationTracking tracking) {
        try {
            // V24D6H4: capture pre-mutation suspended IDs for snapshot comparison
            // (includes RED_CARD + yellow-threshold suspensions applied in this mutation)
            Set<String> preMutationSuspended = capturePreRoundSuspendedPlayerIds(career);

            V24CareerMutationResult mutationResult =
                    v24MutationService.applyMutations(career, v24Result, v24MutationPolicy);

            if (!mutationResult.failures().isEmpty()) {
                log.warn("[V24D6B3] Career mutation partial failures for career {}: {}",
                        career.getData().getCareerId(), mutationResult.failures());
            }

            if (mutationResult.injuriesApplied() > 0) {
                log.debug("[V24D6C3] Applied {} injury mutations for career {}",
                        mutationResult.injuriesApplied(), career.getData().getCareerId());
            }

            if (mutationResult.fatigueApplied() > 0) {
                log.debug("[V24D6C3] Applied {} fatigue mutations for career {}",
                        mutationResult.fatigueApplied(), career.getData().getCareerId());
            }

            if (mutationResult.disciplineApplied() > 0) {
                log.debug("[V24D6D5] Applied {} discipline mutations for career {}",
                        mutationResult.disciplineApplied(), career.getData().getCareerId());
            }

            // V24D6H4: snapshot comparison — detect newly suspended players from this mutation
            // Includes both RED_CARD and yellow-threshold suspensions via snapshot diff
            if (v24MutationPolicy.isDisciplinePersistenceEnabled()) {
                Set<String> postMutationSuspended = capturePreRoundSuspendedPlayerIds(career);
                postMutationSuspended.removeAll(preMutationSuspended);
                if (!postMutationSuspended.isEmpty()) {
                    tracking.newlySuspendedPlayerIds.addAll(postMutationSuspended);
                    log.debug("[V24D6H4] Newly suspended from mutation: {}",
                            postMutationSuspended);
                }
            }

            // V24D6I2: snapshot comparison — detect newly injured players from this mutation
            if (v24MutationPolicy.isInjuryPersistenceEnabled()) {
                Set<String> preMutationInjured = capturePreRoundInjuredPlayerIds(career);
                Set<String> postMutationInjured = capturePreRoundInjuredPlayerIds(career);
                postMutationInjured.removeAll(preMutationInjured);
                if (!postMutationInjured.isEmpty()) {
                    tracking.newlyInjuredPlayerIds.addAll(postMutationInjured);
                    log.debug("[V24D6I2] Newly injured from mutation: {}",
                            postMutationInjured);
                }
            }

        } catch (Exception e) {
            log.warn("[V24D6B3] Career mutation failed unexpectedly for career {}: {}, continuing round",
                    career.getData().getCareerId(), e.getMessage());
        }
    }

    // ========== V24D6D6B: Suspension Lifecycle ==========

    /**
     * V24D6D6B: Runs suspension lifecycle after the full round loop.
     * Called once per simulateLeagueRound call, only when at least one V24 fixture succeeded.
     * Best-effort: failures are logged and do not fail the round.
     */
    private void applyV24SuspensionLifecycle(CareerSave career, int round,
                                              List<MatchFixture> allFixtures,
                                              V24RoundMutationTracking tracking) {
        try {
            // Only run if V24 path processed at least one fixture
            if (!tracking.v24RoundProcessed) return;
            if (!v24MutationPolicy.isDisciplinePersistenceEnabled()) return;

            // Capture pre-round suspended players
            Set<String> preRoundSuspended = capturePreRoundSuspendedPlayerIds(career);
            if (preRoundSuspended.isEmpty()) return;

            // Collect round fixtures for the current round
            List<MatchFixture> roundFixtures = allFixtures.stream()
                    .filter(f -> f.getRound() == round)
                    .collect(java.util.stream.Collectors.toList());

            int served = v24SuspensionLifecycleApplier.applyServedSuspensions(
                    career,
                    round,
                    roundFixtures,
                    preRoundSuspended,
                    tracking.newlySuspendedPlayerIds,
                    tracking.participatedPlayerIds,
                    v24MutationPolicy
            );

            if (served > 0) {
                log.debug("[V24D6D6B] Served {} suspensions for career {} round {}",
                        served, career.getData().getCareerId(), round);
            }
        } catch (Exception e) {
            log.warn("[V24D6D6B] Suspension lifecycle failed unexpectedly for career {} round {}: {}, continuing round",
                    career.getData().getCareerId(), round, e.getMessage());
        }
    }

    /**
     * V24D6D6B: Captures player IDs that were suspended BEFORE the round started.
     * Only includes players where suspended=true AND suspensionRemainingMatches > 0.
     */
    private Set<String> capturePreRoundSuspendedPlayerIds(CareerSave career) {
        Set<String> suspended = new HashSet<>();
        for (SessionTeam team : career.getAllSessionTeams()) {
            for (String playerId : career.getSquadPlayerIds(team.getSessionTeamId())) {
                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;
                if (Boolean.TRUE.equals(player.getSuspended())) {
                    Integer remaining = player.getSuspensionRemainingMatches();
                    if (remaining != null && remaining > 0) {
                        suspended.add(playerId);
                    }
                }
            }
        }
        return suspended;
    }

    /**
     * V24D6I2: Captures player IDs that were injured BEFORE the round started.
     * Only includes players where injured=true AND injuryRemainingMatches > 0.
     */
    private Set<String> capturePreRoundInjuredPlayerIds(CareerSave career) {
        Set<String> injured = new HashSet<>();
        for (SessionTeam team : career.getAllSessionTeams()) {
            for (String playerId : career.getSquadPlayerIds(team.getSessionTeamId())) {
                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;
                if (Boolean.TRUE.equals(player.getInjured())) {
                    Integer remaining = player.getInjuryRemainingMatches();
                    if (remaining != null && remaining > 0) {
                        injured.add(playerId);
                    }
                }
            }
        }
        return injured;
    }

    // ========== V24D6I2: Injury Recovery Lifecycle ==========

    /**
     * V24D6I2: Runs injury recovery lifecycle after the full round loop.
     * Called once per simulateLeagueRound call, only when at least one V24 fixture succeeded.
     * Best-effort: failures are logged and do not fail the round.
     */
    private void applyV24InjuryRecoveryLifecycle(CareerSave career, int round,
                                                  List<MatchFixture> allFixtures,
                                                  V24RoundMutationTracking tracking,
                                                  Set<String> preRoundInjuredPlayerIds) {
        try {
            // Only run if V24 path processed at least one fixture
            if (!tracking.v24RoundProcessed) return;
            if (!v24MutationPolicy.isInjuryPersistenceEnabled()) return;

            // Collect round fixtures for the current round
            List<MatchFixture> roundFixtures = allFixtures.stream()
                    .filter(f -> f.getRound() == round)
                    .collect(java.util.stream.Collectors.toList());

            int recovered = v24InjuryRecoveryLifecycleApplier.applyRecovery(
                    career,
                    round,
                    roundFixtures,
                    preRoundInjuredPlayerIds,
                    tracking.newlyInjuredPlayerIds,
                    tracking.participatedPlayerIds,
                    v24MutationPolicy
            );

            if (recovered > 0) {
                log.debug("[V24D6I2] Recovered {} injuries for career {} round {}",
                        recovered, career.getData().getCareerId(), round);
            }
        } catch (Exception e) {
            log.warn("[V24D6I2] Injury recovery lifecycle failed unexpectedly for career {} round {}: {}, continuing round",
                    career.getData().getCareerId(), round, e.getMessage());
        }
    }

    /**
     * V24D6D6B: Collects starting XI participation from V24 context.
     * All 11 starters per team are considered to have participated.
     */
    private void collectStartingXIParticipation(V24MatchContext context,
                                                  V24RoundMutationTracking tracking) {
        for (SessionPlayer p : context.homeStartingPlayers()) {
            if (p != null && p.getSessionPlayerId() != null) {
                tracking.participatedPlayerIds.add(p.getSessionPlayerId());
            }
        }
        for (SessionPlayer p : context.awayStartingPlayers()) {
            if (p != null && p.getSessionPlayerId() != null) {
                tracking.participatedPlayerIds.add(p.getSessionPlayerId());
            }
        }
    }

    /**
     * V24D6D6B: Collects timeline participation and RED_CARD events from V24 result.
     * playerId and relatedPlayerId from all events count as participation.
     * RED_CARD events populate newlySuspendedPlayerIds.
     */
    private void collectV24ResultParticipation(V24DetailedMatchResult v24Result,
                                               V24RoundMutationTracking tracking) {
        if (v24Result == null || v24Result.timeline() == null) return;

        for (V24MatchEvent event : v24Result.timeline().events()) {
            if (event.playerId() != null && !event.playerId().isBlank()) {
                tracking.participatedPlayerIds.add(event.playerId());
            }
            if (event.relatedPlayerId() != null && !event.relatedPlayerId().isBlank()) {
                tracking.participatedPlayerIds.add(event.relatedPlayerId());
            }
            if (event.type() == V24MatchEventType.RED_CARD) {
                if (event.playerId() != null && !event.playerId().isBlank()) {
                    tracking.newlySuspendedPlayerIds.add(event.playerId());
                }
            }
        }
    }

    /**
     * V24D6D6B: Tracks suspension lifecycle data across a single round.
     * All fields are mutated in-place during the fixture loop.
     */
    private static class V24RoundMutationTracking {
        final Set<String> newlySuspendedPlayerIds = new HashSet<>();
        final Set<String> participatedPlayerIds = new HashSet<>();
        final Set<String> newlyInjuredPlayerIds = new HashSet<>();
        boolean v24RoundProcessed = false;
    }

    /**
     * Build minimal in-memory Team for V23 engine.
     * No DB, no repository. SessionTeamId used as TeamId.
     */
    private Team buildMinimalTeam(String sessionTeamId, String fallbackName) {
        String name = (fallbackName != null && fallbackName.length() >= 3)
                ? fallbackName
                : "Team " + sessionTeamId;

        UUID teamUuid = UUID.fromString(sessionTeamId);
        return Team.create(
                TeamId.of(teamUuid),
                UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000000")),
                name,
                "AI",
                BigDecimal.ZERO,
                Formation.ofDefault()
        );
    }

    /**
     * Derive deterministic seed from fixture matchId.
     */
    private long deriveSeed(MatchFixture fixture) {
        return fixture.getMatchId().hashCode();
    }

    // ========== OVR Calculation (Phase 10C1) ==========

    private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
        // Preserve legacy empty-squad behavior: old LeagueSimulator returned 50
        List<String> squadPlayerIds = career.getTeamManager().getSquadPlayerIds(sessionTeamId);
        if (squadPlayerIds == null || squadPlayerIds.isEmpty()) {
            return 50;
        }
        // Delegate to TeamOverallCalculator for non-empty squads
        return TeamOverallCalculator.calculateFromSessionTeam(
                sessionTeamId,
                career.getTeamManager(),
                career.getPlayerManager()
        );
    }
}
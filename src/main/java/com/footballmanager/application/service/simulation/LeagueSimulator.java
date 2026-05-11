package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.domain.TeamOverallCalculator;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResultAdapter;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerRatingsAssembler;
import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.service.MatchSimulator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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
    private final V24DetailedMatchEngine v24Engine;
    private final V24DetailedMatchStoragePort storagePort;
    private final V24PlayerRatingsAssembler v24PlayerRatingsAssembler;

    /**
     * Primary constructor — useV23LeagueEngine and useV24DetailedEngine default to false.
     * Maintains existing Spring wiring compatibility.
     */
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false, false, false, null);
    }

    /**
     * Three-argument constructor for backward compatibility with existing tests.
     * useV24DetailedEngine defaults to false.
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, false, false, null);
    }

    /**
     * Four-argument constructor for backward compatibility with existing tests.
     * persistDetail defaults to false.
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, useV24DetailedEngine, false, null);
    }

    /**
     * Full constructor with optional V23 engine and flags.
     * @param matchSimulator      the existing domain service for DefaultMatchSimulator path
     * @param matchEngine         optional MatchEngineImpl for V23 path (can be null if flag is false)
     * @param useV23LeagueEngine  if true, V23 engine path is used; if false, DefaultMatchSimulator path
     * @param useV24DetailedEngine if true, V24 detailed engine path is attempted
     * @param persistDetail       if true, V24 detail snapshot is saved to Redis after simulation
     * @param storagePort         V24DetailedMatchStoragePort for persistence (can be null if persistDetail is false)
     */
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine,
                          boolean persistDetail, V24DetailedMatchStoragePort storagePort) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useV23LeagueEngine = useV23LeagueEngine;
        this.useV24DetailedEngine = useV24DetailedEngine;
        this.persistDetail = persistDetail;
        this.storagePort = storagePort;
        this.v24ContextFactory = new V24MatchContextFactory();
        this.v24Engine = new V24DetailedMatchEngine();
        this.v24PlayerRatingsAssembler = new V24PlayerRatingsAssembler();
    }

    /**
     * Simula todos los partidos de la fecha en la liga del usuario.
     * Esto incluye TODAS las divisiones de su carrera.
     */
    public void simulateLeagueRound(CareerSave career, int round) {
        TournamentState tournamentState = career.getTournamentState();
        List<MatchFixture> allFixtures = tournamentState.getFixtures();

        for (MatchFixture fixture : allFixtures) {
            if (fixture.getRound() != round) continue;
            if (!fixture.canBeSimulated()) continue;

            // Phase 10C1: OVR computed via TeamOverallCalculator
            int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId());
            int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());

            if (useV24DetailedEngine) {
                simulateWithV24Engine(career, fixture, homeOvr, awayOvr, tournamentState);
            } else if (useV23LeagueEngine) {
                simulateWithV23Engine(fixture, homeOvr, awayOvr, tournamentState);
            } else {
                simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            }
        }
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
     */
    private void simulateWithV24Engine(CareerSave career, MatchFixture fixture,
                                        int homeOvr, int awayOvr, TournamentState tournamentState) {
        long seed = deriveSeed(fixture);
        SessionTeam homeTeam = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam awayTeam = career.getSessionTeam(fixture.getAwayTeamId());

        if (homeTeam == null || awayTeam == null) {
            log.warn("[V24D5B] Cannot simulate fixture {} with V24: missing team data, falling back to default",
                    fixture.getMatchId());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return;
        }

        try {
            V24MatchContext context = v24ContextFactory.build(
                    career, fixture, homeTeam, awayTeam, seed);

            V24DetailedMatchResult v24Result = v24Engine.simulate(context, seed);
            MatchFixture.MatchResultData resultData = V24DetailedMatchResultAdapter.toMatchResultData(v24Result);
            tournamentState.recordMatchResult(fixture.getMatchId(), resultData);

            log.debug("[V24D5B] Fixture {} simulated with V24 engine: {} - {}",
                    fixture.getMatchId(), resultData.homeGoals, resultData.awayGoals);

            // V24D5C: persist detail if flag is enabled
            if (persistDetail && storagePort != null) {
                persistV24Detail(career, fixture, homeTeam.getName(), awayTeam.getName(), v24Result);
            }

        } catch (IllegalArgumentException e) {
            log.warn("[V24D5B] V24 context build failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
        } catch (Exception e) {
            log.warn("[V24D5B] V24 simulation failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
        }
    }

    /**
     * V24D5C: Persist V24DetailedMatchData snapshot to Redis via storage port.
     * Best-effort: failures are logged and do not fail the match/round.
    /**
     * V24D5F: Persist V24 detailed match data including per-player ratings.
     * Player ratings are derived from CareerSave starting XI + match timeline.
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
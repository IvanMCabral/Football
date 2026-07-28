package com.footballmanager.application.service.simulation;
import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.domain.TeamOverallCalculator;
import com.footballmanager.application.service.simulation.v24.V24SuspensionLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24InjuryRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24EnergyRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24InjuryMutationApplier;
import com.footballmanager.application.service.simulation.v24.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24MatchTimeline;
import com.footballmanager.application.service.simulation.v24.V24MatchLineupPlayerDto;
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
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Slf4j
public class LeagueSimulator {
    private static final Duration DETAIL_PERSIST_TIMEOUT = Duration.ofSeconds(5);

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
    private final V24EnergyRecoveryLifecycleApplier v24EnergyRecoveryLifecycleApplier = new V24EnergyRecoveryLifecycleApplier();
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false, false, false, null, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, false, false, null, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, useV24DetailedEngine, false, null, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useV23LeagueEngine, boolean useV24DetailedEngine,
                          boolean persistDetail, V24DetailedMatchStoragePort storagePort) {
        this(matchSimulator, matchEngine, useV23LeagueEngine, useV24DetailedEngine,
                persistDetail, storagePort, false, false, false, false, false);
    }
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
    public void simulateLeagueRound(CareerSave career, int round) {
        TournamentState tournamentState = career.getTournamentState();
        List<MatchFixture> allFixtures = tournamentState.getFixtures();
        V24RoundMutationTracking tracking = new V24RoundMutationTracking();
        Set<String> preRoundInjured = capturePreRoundInjuredPlayerIds(career);
        for (MatchFixture fixture : allFixtures) {
            if (fixture.getRound() != round) continue;
            if (!fixture.canBeSimulated()) continue;
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
        roundLifecycleService().applyEndOfRound(career, round, allFixtures, tracking, preRoundInjured);
    }
    private void simulateWithDefaultEngine(MatchFixture fixture, int homeOvr, int awayOvr, TournamentState tournamentState) {
        MatchSimulator.MatchResult result = matchSimulator.simulateQuick(
                fixture.getHomeTeamId(),
                fixture.getAwayTeamId(),
                homeOvr,
                awayOvr
        );
        MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
                result.homeGoals(), result.awayGoals(), 50, 50, 5, 5
        );
        tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
    }
    private void simulateWithV23Engine(MatchFixture fixture, int homeOvr, int awayOvr, TournamentState tournamentState) {
        if (matchEngine == null) {
            throw new IllegalStateException("useV23LeagueEngine is true but MatchEngineImpl is not provided");
        }
        Team homeTeam = buildMinimalTeam(fixture.getHomeTeamId(), "Home Team");
        Team awayTeam = buildMinimalTeam(fixture.getAwayTeamId(), "Away Team");
        long seed = deriveSeed(fixture);
        MatchResult result = matchEngine.simulateWithStrengthSync(homeTeam, awayTeam, homeOvr, awayOvr, seed);
        if (result == null) {
            throw new IllegalStateException("V23 engine returned null for fixture " + fixture.getMatchId());
        }
        MatchFixture.MatchResultData resultData = MatchResultDataAdapter.fromMatchResult(result);
        tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
    }
    private V24DetailedMatchResult simulateWithV24Engine(CareerSave career, MatchFixture fixture,
                                        int homeOvr, int awayOvr, TournamentState tournamentState,
                                        V24RoundMutationTracking tracking) {
        long seed = deriveSeed(fixture);
        SessionTeam homeTeam = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam awayTeam = career.getSessionTeam(fixture.getAwayTeamId());
        if (homeTeam == null || awayTeam == null) {
            log.warn("Cannot simulate fixture {} with V24: missing team data, falling back to default",
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
            log.debug("Fixture {} simulated with V24 engine: {} - {}",
                    fixture.getMatchId(), resultData.homeGoals, resultData.awayGoals);
            if (persistDetail && storagePort != null) {
                persistV24Detail(career, fixture, homeTeam.getName(), awayTeam.getName(), v24Result, context);
            }
            collectStartingXIParticipation(context, tracking);
            collectV24ResultParticipation(v24Result, tracking);
            applyV24CareerMutation(career, v24Result, tracking);
            return v24Result;
        } catch (IllegalArgumentException e) {
            log.warn("V24 context build failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        } catch (Exception e) {
            log.warn("V24 simulation failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        }
    }
    private void persistV24Detail(CareerSave career, MatchFixture fixture,
                                   String homeTeamName, String awayTeamName,
                                   V24DetailedMatchResult v24Result,
                                   V24MatchContext context) {
        /*
         * simulateLeagueRound is a synchronous league/batch workflow: callers
         * expect the fixture, standings and optional V24 detail snapshot to be
         * settled before the round returns. The bounded block stays at this
         * batch boundary and is not used from a WebFlux controller pipeline.
         */
        try {
            String careerId = career.getData().getCareerId();
            Integer seasonNumber = career.getSeasonManager().getCurrentSeason();
            Integer round = fixture.getRound();
            List<V24PlayerMatchRatingDto> playerRatings =
                    v24PlayerRatingsAssembler.assemblePlayerRatings(career, fixture, v24Result);
            String homeFormation = resolveFormation(career, fixture.getHomeTeamId());
            String awayFormation = resolveFormation(career, fixture.getAwayTeamId());
            V24DetailedMatchData detail = V24DetailedMatchData.fromResult(
                    careerId,
                    seasonNumber,
                    round,
                    homeTeamName,
                    awayTeamName,
                    homeFormation,
                    awayFormation,
                    v24Result,
                    playerRatings,
                    lineupSnapshot(context.homeStartingPlayers()),
                    lineupSnapshot(context.homeBenchPlayers()),
                    lineupSnapshot(context.awayStartingPlayers()),
                    lineupSnapshot(context.awayBenchPlayers())
            );
            storagePort.save(careerId, detail)
                    .doOnSuccess(ignored -> log.debug(
                            "Detail saved for fixture {} in career {}",
                            fixture.getMatchId(), careerId))
                    .onErrorResume(e -> {
                        log.warn("Failed to persist detail for fixture {}: {}, continuing round",
                                fixture.getMatchId(), e.getMessage());
                        return Mono.empty();
                    })
                    .block(DETAIL_PERSIST_TIMEOUT);
        } catch (Exception e) {
            log.warn("Failed to persist detail for fixture {}: {}, continuing round",
                    fixture.getMatchId(), e.getMessage());
        }
    }
    private List<V24MatchLineupPlayerDto> lineupSnapshot(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        return players.stream()
                .filter(java.util.Objects::nonNull)
                .map(V24MatchLineupPlayerDto::fromSessionPlayer)
                .toList();
    }
    private void applyV24CareerMutation(CareerSave career, V24DetailedMatchResult v24Result,
                                         V24RoundMutationTracking tracking) {
        try {
            Set<String> preMutationSuspended = capturePreRoundSuspendedPlayerIds(career);
            V24CareerMutationResult mutationResult =
                    v24MutationService.applyMutations(career, v24Result, v24MutationPolicy);
            if (!mutationResult.failures().isEmpty()) {
                log.warn("Career mutation partial failures for career {}: {}",
                        career.getData().getCareerId(), mutationResult.failures());
            }
            if (mutationResult.injuriesApplied() > 0) {
                log.debug("Applied {} injury mutations for career {}",
                        mutationResult.injuriesApplied(), career.getData().getCareerId());
            }
            if (mutationResult.fatigueApplied() > 0) {
                log.debug("Applied {} fatigue mutations for career {}",
                        mutationResult.fatigueApplied(), career.getData().getCareerId());
            }
            if (mutationResult.disciplineApplied() > 0) {
                log.debug("Applied {} discipline mutations for career {}",
                        mutationResult.disciplineApplied(), career.getData().getCareerId());
            }
            if (v24MutationPolicy.isDisciplinePersistenceEnabled()) {
                Set<String> postMutationSuspended = capturePreRoundSuspendedPlayerIds(career);
                postMutationSuspended.removeAll(preMutationSuspended);
                if (!postMutationSuspended.isEmpty()) {
                    tracking.newlySuspendedPlayerIds.addAll(postMutationSuspended);
                    log.debug("Newly suspended from mutation: {}",
                            postMutationSuspended);
                }
            }
            if (v24MutationPolicy.isInjuryPersistenceEnabled()) {
                Set<String> preMutationInjured = capturePreRoundInjuredPlayerIds(career);
                Set<String> postMutationInjured = capturePreRoundInjuredPlayerIds(career);
                postMutationInjured.removeAll(preMutationInjured);
                if (!postMutationInjured.isEmpty()) {
                    tracking.newlyInjuredPlayerIds.addAll(postMutationInjured);
                    log.debug("Newly injured from mutation: {}",
                            postMutationInjured);
                }
            }
        } catch (Exception e) {
            log.warn("Career mutation failed unexpectedly for career {}: {}, continuing round",
                    career.getData().getCareerId(), e.getMessage());
        }
    }
    void applyLiveMatchCareerMutations(CareerSave career, V24DetailedMatchResult v24Result,
                                       LiveRoundMutationTracking tracking) {
        liveMutationService().apply(career, v24Result, tracking);
    }
    public Set<String> capturePreRoundSuspendedPlayerIds(CareerSave career) {
        return liveLifecycleService().capturePreRoundSuspendedPlayerIds(career);
    }

    public Set<String> capturePreRoundInjuredPlayerIds(CareerSave career) {
        return liveLifecycleService().capturePreRoundInjuredPlayerIds(career);
    }
    public void applyEndOfRoundLiveLifecycle(
            CareerSave career,
            int currentRound,
            List<MatchFixture> allFixtures,
            LiveRoundMutationTracking tracking) {
        liveLifecycleService().applyEndOfRoundLiveLifecycle(career, currentRound, allFixtures, tracking);
    }

    private void collectStartingXIParticipation(V24MatchContext context,
                                                  V24RoundMutationTracking tracking) {
        for (SessionPlayer p : context.homeStartingPlayers()) {
            if (p != null && p.getSessionPlayerId() != null && !Boolean.TRUE.equals(p.getSuspended())) {
                tracking.participatedPlayerIds.add(p.getSessionPlayerId());
            }
        }
        for (SessionPlayer p : context.awayStartingPlayers()) {
            if (p != null && p.getSessionPlayerId() != null && !Boolean.TRUE.equals(p.getSuspended())) {
                tracking.participatedPlayerIds.add(p.getSessionPlayerId());
            }
        }
    }
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
    private long deriveSeed(MatchFixture fixture) {
        return fixture.getMatchId().hashCode();
    }
    public Mono<Void> persistV24DetailForLiveMatch(
            CareerSave career,
            V24DetailedMatchResult v24Result,
            String homeTeamId,
            String awayTeamId,
            int homeGoals,
            int awayGoals) {
        return liveDetailPersister().persist(career, v24Result, homeTeamId, awayTeamId, null);
    }

    public Mono<Void> persistV24DetailForLiveMatch(
            CareerSave career,
            V24DetailedMatchResult v24Result,
            String homeTeamId,
            String awayTeamId,
            int homeGoals,
            int awayGoals,
            LiveRoundMutationTracking tracking) {
        return liveDetailPersister().persist(career, v24Result, homeTeamId, awayTeamId, tracking);
    }

    public Mono<Void> persistV24DetailForLiveMatch(
            CareerSave career,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            int homeGoals,
            int awayGoals) {
        return persistV24DetailForLiveMatch(
                career,
                null,
                homeTeamId.toString(),
                awayTeamId.toString(),
                homeGoals,
                awayGoals
        );
    }

    private V24LiveLifecycleService liveLifecycleService() {
        return new V24LiveLifecycleService(v24MutationPolicy, log);
    }

    private V24RoundLifecycleService roundLifecycleService() {
        return new V24RoundLifecycleService(
                v24MutationPolicy,
                v24SuspensionLifecycleApplier,
                v24InjuryRecoveryLifecycleApplier,
                v24EnergyRecoveryLifecycleApplier,
                liveLifecycleService(),
                log);
    }

    private V24LiveMutationService liveMutationService() {
        return new V24LiveMutationService(
                v24MutationService,
                v24MutationPolicy,
                liveLifecycleService(),
                log);
    }

    private V24LiveDetailPersister liveDetailPersister() {
        return new V24LiveDetailPersister(
                persistDetail,
                useV24DetailedEngine,
                storagePort,
                v24PlayerRatingsAssembler,
                liveMutationService()::apply,
                log);
    }
    private String resolveFormation(CareerSave career, String teamId) {
        if (career == null || teamId == null || teamId.isBlank()) {
            return null;
        }
        return resolveFormation(career.getSessionTeam(teamId));
    }
    private String resolveFormation(SessionTeam team) {
        if (team == null) {
            return null;
        }
        String formation = team.getFormation();
        return (formation != null && !formation.isBlank()) ? formation : null;
    }
    private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
        List<String> squadPlayerIds = career.getTeamManager().getSquadPlayerIds(sessionTeamId);
        if (squadPlayerIds == null || squadPlayerIds.isEmpty()) {
            return 50;
        }
        return TeamOverallCalculator.calculateFromSessionTeam(
                sessionTeamId,
                career.getTeamManager(),
                career.getPlayerManager()
        );
    }
}

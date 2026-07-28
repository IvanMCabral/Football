package com.footballmanager.application.service.simulation;
import com.footballmanager.application.service.domain.MatchEngineImpl;
import com.footballmanager.application.service.domain.TeamOverallCalculator;
import com.footballmanager.application.service.simulation.detailed.SuspensionLifecycleApplier;
import com.footballmanager.application.service.simulation.detailed.InjuryRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.detailed.EnergyRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.detailed.InjuryMutationApplier;
import com.footballmanager.application.service.simulation.detailed.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.application.service.simulation.detailed.MatchTimeline;
import com.footballmanager.application.service.simulation.detailed.MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.detailed.PlayerRatingsAssembler;
import com.footballmanager.application.service.simulation.detailed.CareerMutationPolicy;
import com.footballmanager.application.service.simulation.detailed.CareerMutationResult;
import com.footballmanager.application.service.simulation.detailed.CareerMutationService;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngineProvider;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResultAdapter;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
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
    private final boolean useClassicLeagueEngine;
    private final boolean useDetailedMatchEngine;
    private final boolean persistDetail;
    private final MatchContextFactory matchContextFactory;
    private final DetailedMatchEngineProvider detailedEngineProvider;
    private final DetailedMatchStoragePort storagePort;
    private final PlayerRatingsAssembler playerRatingsAssembler;
    private final CareerMutationService careerMutationService;
    private final CareerMutationPolicy careerMutationPolicy;
    private final SuspensionLifecycleApplier suspensionLifecycleApplier = new SuspensionLifecycleApplier();
    private final InjuryRecoveryLifecycleApplier injuryRecoveryLifecycleApplier = new InjuryRecoveryLifecycleApplier();
    private final EnergyRecoveryLifecycleApplier energyRecoveryLifecycleApplier = new EnergyRecoveryLifecycleApplier();
    public LeagueSimulator(MatchSimulator matchSimulator) {
        this(matchSimulator, null, false, false, false, null, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useClassicLeagueEngine) {
        this(matchSimulator, matchEngine, useClassicLeagueEngine, false, false, null, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useClassicLeagueEngine, boolean useDetailedMatchEngine) {
        this(matchSimulator, matchEngine, useClassicLeagueEngine, useDetailedMatchEngine, false, null, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useClassicLeagueEngine, boolean useDetailedMatchEngine,
                          boolean persistDetail, DetailedMatchStoragePort storagePort) {
        this(matchSimulator, matchEngine, useClassicLeagueEngine, useDetailedMatchEngine,
                persistDetail, storagePort, false, false, false, false, false);
    }
    public LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                          boolean useClassicLeagueEngine, boolean useDetailedMatchEngine,
                          boolean persistDetail, DetailedMatchStoragePort storagePort,
                          boolean mutateCareerState, boolean persistInjuries,
                          boolean persistFatigue, boolean persistDiscipline,
                          boolean persistForm) {
        this(matchSimulator, matchEngine, useClassicLeagueEngine, useDetailedMatchEngine,
                persistDetail, storagePort, mutateCareerState, persistInjuries,
                persistFatigue, persistDiscipline, persistForm,
                new DetailedMatchEngine());
    }
    LeagueSimulator(MatchSimulator matchSimulator, MatchEngineImpl matchEngine,
                    boolean useClassicLeagueEngine, boolean useDetailedMatchEngine,
                    boolean persistDetail, DetailedMatchStoragePort storagePort,
                    boolean mutateCareerState, boolean persistInjuries,
                    boolean persistFatigue, boolean persistDiscipline,
                    boolean persistForm,
                    DetailedMatchEngineProvider detailedEngineProvider) {
        this.matchSimulator = matchSimulator;
        this.matchEngine = matchEngine;
        this.useClassicLeagueEngine = useClassicLeagueEngine;
        this.useDetailedMatchEngine = useDetailedMatchEngine;
        this.persistDetail = persistDetail;
        this.storagePort = storagePort;
        this.matchContextFactory = new MatchContextFactory();
        this.detailedEngineProvider = detailedEngineProvider;
        this.playerRatingsAssembler = new PlayerRatingsAssembler();
        this.careerMutationPolicy = new CareerMutationPolicy(
                mutateCareerState, persistInjuries, persistFatigue,
                persistDiscipline, persistForm);
        this.careerMutationService = new CareerMutationService(new InjuryMutationApplier());
    }
    public void simulateLeagueRound(CareerSave career, int round) {
        TournamentState tournamentState = career.getTournamentState();
        List<MatchFixture> allFixtures = tournamentState.getFixtures();
        RoundMutationTracking tracking = new RoundMutationTracking();
        Set<String> preRoundInjured = capturePreRoundInjuredPlayerIds(career);
        for (MatchFixture fixture : allFixtures) {
            if (fixture.getRound() != round) continue;
            if (!fixture.canBeSimulated()) continue;
            int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId());
            int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());
            if (useDetailedMatchEngine) {
                DetailedMatchResult detailedResult = simulateWithDetailedEngine(career, fixture, homeOvr, awayOvr, tournamentState, tracking);
                if (detailedResult != null) {
                    tracking.v24RoundProcessed = true;
                }
            } else if (useClassicLeagueEngine) {
                simulateWithClassicEngine(fixture, homeOvr, awayOvr, tournamentState);
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
    private void simulateWithClassicEngine(MatchFixture fixture, int homeOvr, int awayOvr, TournamentState tournamentState) {
        if (matchEngine == null) {
            throw new IllegalStateException("useClassicLeagueEngine is true but MatchEngineImpl is not provided");
        }
        Team homeTeam = buildMinimalTeam(fixture.getHomeTeamId(), "Home Team");
        Team awayTeam = buildMinimalTeam(fixture.getAwayTeamId(), "Away Team");
        long seed = deriveSeed(fixture);
        MatchResult result = matchEngine.simulateWithStrengthSync(homeTeam, awayTeam, homeOvr, awayOvr, seed);
        if (result == null) {
            throw new IllegalStateException("classic engine returned null for fixture " + fixture.getMatchId());
        }
        MatchFixture.MatchResultData resultData = MatchResultDataAdapter.fromMatchResult(result);
        tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
    }
    private DetailedMatchResult simulateWithDetailedEngine(CareerSave career, MatchFixture fixture,
                                        int homeOvr, int awayOvr, TournamentState tournamentState,
                                        RoundMutationTracking tracking) {
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
            MatchContext context = matchContextFactory.build(
                    career, fixture, homeTeam, awayTeam, seed);
            DetailedMatchResult detailedResult = detailedEngineProvider.simulate(context, seed);
            MatchFixture.MatchResultData resultData = DetailedMatchResultAdapter.toMatchResultData(detailedResult);
            tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
            log.debug("Fixture {} simulated with detailed match engine: {} - {}",
                    fixture.getMatchId(), resultData.homeGoals, resultData.awayGoals);
            if (persistDetail && storagePort != null) {
                persistDetailedMatchDetail(career, fixture, homeTeam.getName(), awayTeam.getName(), detailedResult, context);
            }
            collectStartingXIParticipation(context, tracking);
            collectDetailedResultParticipation(detailedResult, tracking);
            applyDetailedCareerMutation(career, detailedResult, tracking);
            return detailedResult;
        } catch (IllegalArgumentException e) {
            log.warn("detailed match context build failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        } catch (Exception e) {
            log.warn("detailed match simulation failed for fixture {}: {}, falling back to default",
                    fixture.getMatchId(), e.getMessage());
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
            return null;
        }
    }
    private void persistDetailedMatchDetail(CareerSave career, MatchFixture fixture,
                                   String homeTeamName, String awayTeamName,
                                   DetailedMatchResult detailedResult,
                                   MatchContext context) {
        /*
         * simulateLeagueRound is a synchronous league/batch workflow: callers
         * expect the fixture, standings and optional detailed match detail snapshot to be
         * settled before the round returns. The bounded block stays at this
         * batch boundary and is not used from a WebFlux controller pipeline.
         */
        try {
            String careerId = career.getData().getCareerId();
            Integer seasonNumber = career.getSeasonManager().getCurrentSeason();
            Integer round = fixture.getRound();
            List<PlayerMatchRatingDto> playerRatings =
                    playerRatingsAssembler.assemblePlayerRatings(career, fixture, detailedResult);
            String homeFormation = resolveFormation(career, fixture.getHomeTeamId());
            String awayFormation = resolveFormation(career, fixture.getAwayTeamId());
            DetailedMatchData detail = DetailedMatchData.fromResult(
                    careerId,
                    seasonNumber,
                    round,
                    homeTeamName,
                    awayTeamName,
                    homeFormation,
                    awayFormation,
                    detailedResult,
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
    private List<MatchLineupPlayerDto> lineupSnapshot(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        return players.stream()
                .filter(java.util.Objects::nonNull)
                .map(MatchLineupPlayerDto::fromSessionPlayer)
                .toList();
    }
    private void applyDetailedCareerMutation(CareerSave career, DetailedMatchResult detailedResult,
                                         RoundMutationTracking tracking) {
        try {
            Set<String> preMutationSuspended = capturePreRoundSuspendedPlayerIds(career);
            CareerMutationResult mutationResult =
                    careerMutationService.applyMutations(career, detailedResult, careerMutationPolicy);
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
            if (careerMutationPolicy.isDisciplinePersistenceEnabled()) {
                Set<String> postMutationSuspended = capturePreRoundSuspendedPlayerIds(career);
                postMutationSuspended.removeAll(preMutationSuspended);
                if (!postMutationSuspended.isEmpty()) {
                    tracking.newlySuspendedPlayerIds.addAll(postMutationSuspended);
                    log.debug("Newly suspended from mutation: {}",
                            postMutationSuspended);
                }
            }
            if (careerMutationPolicy.isInjuryPersistenceEnabled()) {
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
    void applyLiveMatchCareerMutations(CareerSave career, DetailedMatchResult detailedResult,
                                       LiveRoundMutationTracking tracking) {
        liveMutationService().apply(career, detailedResult, tracking);
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

    private void collectStartingXIParticipation(MatchContext context,
                                                  RoundMutationTracking tracking) {
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
    private void collectDetailedResultParticipation(DetailedMatchResult detailedResult,
                                               RoundMutationTracking tracking) {
        if (detailedResult == null || detailedResult.timeline() == null) return;
        for (DetailedMatchEvent event : detailedResult.timeline().events()) {
            if (event.playerId() != null && !event.playerId().isBlank()) {
                tracking.participatedPlayerIds.add(event.playerId());
            }
            if (event.relatedPlayerId() != null && !event.relatedPlayerId().isBlank()) {
                tracking.participatedPlayerIds.add(event.relatedPlayerId());
            }
            if (event.type() == DetailedMatchEventType.RED_CARD) {
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
    public Mono<Void> persistDetailedMatchDetailForLiveMatch(
            CareerSave career,
            DetailedMatchResult detailedResult,
            String homeTeamId,
            String awayTeamId,
            int homeGoals,
            int awayGoals) {
        return liveDetailPersister().persist(career, detailedResult, homeTeamId, awayTeamId, null);
    }

    public Mono<Void> persistDetailedMatchDetailForLiveMatch(
            CareerSave career,
            DetailedMatchResult detailedResult,
            String homeTeamId,
            String awayTeamId,
            int homeGoals,
            int awayGoals,
            LiveRoundMutationTracking tracking) {
        return liveDetailPersister().persist(career, detailedResult, homeTeamId, awayTeamId, tracking);
    }

    public Mono<Void> persistDetailedMatchDetailForLiveMatch(
            CareerSave career,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            int homeGoals,
            int awayGoals) {
        return persistDetailedMatchDetailForLiveMatch(
                career,
                null,
                homeTeamId.toString(),
                awayTeamId.toString(),
                homeGoals,
                awayGoals
        );
    }

    private LiveMatchLifecycleService liveLifecycleService() {
        return new LiveMatchLifecycleService(careerMutationPolicy, log);
    }

    private RoundLifecycleService roundLifecycleService() {
        return new RoundLifecycleService(
                careerMutationPolicy,
                suspensionLifecycleApplier,
                injuryRecoveryLifecycleApplier,
                energyRecoveryLifecycleApplier,
                liveLifecycleService(),
                log);
    }

    private LiveMatchMutationService liveMutationService() {
        return new LiveMatchMutationService(
                careerMutationService,
                careerMutationPolicy,
                liveLifecycleService(),
                log);
    }

    private LiveDetailPersister liveDetailPersister() {
        return new LiveDetailPersister(
                persistDetail,
                useDetailedMatchEngine,
                storagePort,
                playerRatingsAssembler,
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

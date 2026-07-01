package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.career.CareerNotificationService;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentResult;
import com.footballmanager.domain.model.repository.CareerRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * MatchSimulationOrchestrator - ÚNICA AUTORIDAD para careerPhase.
 *
 * Responsabilidades:
 * 1. Procesar resultados de partidos
 * 2. Gestionar transiciones de careerPhase
 * 3. Guardar carrera de forma atómica
 * 4. Notificar cambios de estado
 */
@Slf4j
@Service
public class MatchSimulationOrchestrator {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final CareerNotificationService notificationService;
    private final MatchResultProcessor resultProcessor;
    private final LeagueSimulator leagueSimulator;
    private final RoundEngineRegistry roundEngineRegistry;

    private final ConcurrentMap<String, Semaphore> userSemaphores = new ConcurrentHashMap<>();
    private final Scheduler orchestratorScheduler;

    public MatchSimulationOrchestrator(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            CareerNotificationService notificationService,
            MatchResultProcessor resultProcessor,
            LeagueSimulator leagueSimulator,
            RoundEngineRegistry roundEngineRegistry) {
        this.careerRepository = careerRepository;
        this.careerSessionService = careerSessionService;
        this.notificationService = notificationService;
        this.resultProcessor = resultProcessor;
        this.leagueSimulator = leagueSimulator;
        this.roundEngineRegistry = roundEngineRegistry;

        this.orchestratorScheduler = reactor.core.scheduler.Schedulers.newBoundedElastic(
                20, 100, "orchestrator-%d");
    }

    @PreDestroy
    public void shutdown() {
        if (orchestratorScheduler != null && !orchestratorScheduler.isDisposed()) {
            orchestratorScheduler.dispose();
        }
    }

    public Mono<Void> processMatchDayResults(String userId, java.util.List<MatchResultProcessor.MatchResultInfo> results) {
        if (results.isEmpty()) {
            // C55.8 B1.b fix: empty results may indicate a BYE round for the user
            // (no fixtures in currentRound involve userSessionTeamId). Previously
            // returned Mono.empty() silently and the career got stuck. Now we
            // route to handlePotentialBye which advances if it's a real BYE.
            return handlePotentialBye(userId);
        }

        UUID userUUID = UUID.fromString(userId);
        Semaphore semaphore = userSemaphores.computeIfAbsent(userId, k -> new Semaphore(1));
        boolean acquired = semaphore.tryAcquire();

        if (!acquired) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    try {
                        return processResultsInternal(userId, userUUID, results);
                    } finally {
                        semaphore.release();
                        userSemaphores.remove(userId, semaphore);
                    }
                })
                .subscribeOn(orchestratorScheduler)
                .doOnNext(career -> {
                    if (career != null) {
                        int currentRound = career.getTournamentState().getCurrentRound();
                        long withResults = career.getTournamentState().getFixtures().stream()
                                .filter(f -> f.getResult() != null)
                                .count();
                        notificationService.emitResultsUpdated(userId, String.valueOf(currentRound), (int) withResults);
                    }
                })
                .onErrorResume(e -> {
                    semaphore.release();
                    userSemaphores.remove(userId, semaphore);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * C55.8 B1.b: handle the case where processMatchDayResults is called with an
     * empty results list. If the current round is a BYE for the user (no fixture
     * involves userSessionTeamId), advance career.currentRound to the next round
     * (or finish the tournament if it was the last round). If it is NOT a BYE,
     * log an error and stay stuck — we don't know how to advance without
     * match data, and silently dropping the call would lose state.
     */
    private Mono<Void> handlePotentialBye(String userId) {
        UUID userUUID = UUID.fromString(userId);
        Semaphore semaphore = userSemaphores.computeIfAbsent(userId, k -> new Semaphore(1));
        boolean acquired = semaphore.tryAcquire();

        if (!acquired) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    try {
                        return processByeRound(userId, userUUID);
                    } finally {
                        semaphore.release();
                        userSemaphores.remove(userId, semaphore);
                    }
                })
                .subscribeOn(orchestratorScheduler)
                .doOnNext(career -> {
                    if (career != null) {
                        int currentRound = career.getTournamentState().getCurrentRound();
                        notificationService.emitResultsUpdated(userId, String.valueOf(currentRound), 0);
                    }
                })
                .onErrorResume(e -> {
                    semaphore.release();
                    userSemaphores.remove(userId, semaphore);
                    return Mono.empty();
                })
                .then();
    }

    private CareerSave processByeRound(String userId, UUID userUUID) {
        CareerSave career = careerSessionService.getCareerFromCache(userUUID)
                .block(java.time.Duration.ofSeconds(10));

        if (career == null) {
            return null;
        }

        var tournamentState = career.getTournamentState();
        int currentRound = tournamentState.getCurrentRound();
        int totalRounds = tournamentState.getTotalRounds();
        String userTeamId = career.getUserSessionTeamId();

        boolean isBye = tournamentState.getFixturesForRound(currentRound).stream()
                .noneMatch(f -> userTeamId != null
                        && (userTeamId.equals(f.getHomeTeamId())
                            || userTeamId.equals(f.getAwayTeamId())));

        if (!isBye) {
            log.error("[orchestrator] userId={} round={} empty results but round is NOT a BYE for user "
                    + "(userTeamId={}, fixtures={}) — staying stuck. Investigate caller.",
                    userId, currentRound, userTeamId, tournamentState.getFixturesForRound(currentRound).size());
            return null;
        }

        log.info("[orchestrator] userId={} round={} detected BYE round — auto-advancing career", userId, currentRound);

        if (currentRound >= totalRounds) {
            java.util.List<TournamentResult> allResults = resultProcessor.determineDivisionChampions(career);
            finishTournament(career, allResults);
        } else {
            tournamentState.setCurrentRound(currentRound + 1);
            // Phase is already WAITING_USER from the previous round completion — keep it.
            tournamentState.setCareerPhase(CareerPhase.WAITING_USER);
        }

        careerSessionService.saveCareer(career)
                .block(java.time.Duration.ofSeconds(10));

        roundEngineRegistry.unregister(userUUID);

        return career;
    }

    private CareerSave processResultsInternal(String userId, UUID userUUID, java.util.List<MatchResultProcessor.MatchResultInfo> results) {
        CareerSave career = careerSessionService.getCareerFromCache(userUUID)
                .block(java.time.Duration.ofSeconds(10));

        if (career == null) {
            return null;
        }

        var tournamentState = career.getTournamentState();
        int careerCurrentRound = tournamentState.getCurrentRound();

        // C55.8 B1.a fix: handle stale/future matchId (previously silent-rejected).
        // - fixture.round < careerCurrentRound → idempotent skip (already processed)
        // - fixture.round == careerCurrentRound → normal flow
        // - fixture.round > careerCurrentRound → advance career to catch up, then process
        if (!results.isEmpty()) {
            String firstMatchId = results.get(0).matchId();
            var firstFixture = tournamentState.getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(firstMatchId))
                    .findFirst()
                    .orElse(null);

            if (firstFixture != null && firstFixture.getRound() != careerCurrentRound) {
                int fixtureRound = firstFixture.getRound();
                if (fixtureRound < careerCurrentRound) {
                    log.warn("[orchestrator] userId={} stale matchId {} from round={} (careerCurrentRound={}) — already processed, skipping",
                            userId, firstMatchId, fixtureRound, careerCurrentRound);
                    return null;
                }
                log.warn("[orchestrator] userId={} future matchId {} from round={} (careerCurrentRound={}) — advancing career",
                        userId, firstMatchId, fixtureRound, careerCurrentRound);
                tournamentState.setCurrentRound(fixtureRound);
                careerCurrentRound = fixtureRound;
            }
        }

        career.getTournamentState().setCareerPhase(CareerPhase.IN_MATCH);

        int nuevosProcesados = resultProcessor.process(career, results);
        if (nuevosProcesados == 0) {
            return null;
        }

        career.getTournamentState().finishMatchDay();

        try {
            career.getTournamentState().enterWaitingUserPhase();
        } catch (IllegalStateException ignored) {
        }

        int currentRound = career.getTournamentState().getCurrentRound();
        int totalRounds = career.getTournamentState().getTotalRounds();
        java.util.List<TournamentResult> allResults = resultProcessor.determineDivisionChampions(career);

        if (currentRound >= totalRounds) {
            finishTournament(career, allResults);
        } else {
            career.getTournamentState().setCurrentRound(currentRound + 1);
            career.getTournamentState().setCareerPhase(CareerPhase.WAITING_USER);
        }

        careerSessionService.saveCareer(career)
                .block(java.time.Duration.ofSeconds(10));

        roundEngineRegistry.unregister(userUUID);

        return career;
    }

    private void finishTournament(CareerSave career, java.util.List<TournamentResult> allResults) {
        var tournamentState = career.getTournamentState();

        if (tournamentState.getCareerPhase() == CareerPhase.FINISHED) {
            return;
        }

        tournamentState.setCareerPhase(CareerPhase.FINISHED);
        tournamentState.setFinished(true);

        for (TournamentResult result : allResults) {
            career.addTournamentResult(result);
            career.updateTopTeams(result);
        }

        career.calculatePromotionsAndRelegations(tournamentState);

        var userDivision = career.getUserDivision();
        if (userDivision != null) {
            String championId = allResults.stream()
                    .filter(r -> userDivision.getDivisionId().equals(r.getDivisionId()))
                    .findFirst()
                    .map(TournamentResult::getChampionTeamId)
                    .orElse(null);
            tournamentState.setChampionTeamId(championId);
        }
    }
}

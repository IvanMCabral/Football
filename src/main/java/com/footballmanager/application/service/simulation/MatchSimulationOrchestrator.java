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
            return Mono.empty();
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

    private CareerSave processResultsInternal(String userId, UUID userUUID, java.util.List<MatchResultProcessor.MatchResultInfo> results) {
        CareerSave career = careerSessionService.getCareerFromCache(userUUID)
                .block(java.time.Duration.ofSeconds(10));

        if (career == null) {
            return null;
        }

        int careerCurrentRound = career.getTournamentState().getCurrentRound();

        // Verificar que los resultados son para la ronda actual
        if (!results.isEmpty()) {
            String firstMatchId = results.get(0).matchId();
            var firstFixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(firstMatchId))
                    .findFirst()
                    .orElse(null);

            if (firstFixture != null && firstFixture.getRound() != careerCurrentRound) {
                return null;
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

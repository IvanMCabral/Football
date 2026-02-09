package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.fixture.CareerFixtureService;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.ContinueSeasonUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementación de UseCase para continuar a nueva temporada.
 *
 * Maneja: inicio de temporada, promociones, fixtures y standings.
 * Los fixtures son generados por CareerFixtureService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContinueSeasonUseCaseImpl implements ContinueSeasonUseCase {

    private final CareerRepository careerRepository;
    private final CareerFixtureService careerFixtureService;
    private final CareerSessionService careerSessionService;
    private final MatchSessionRegistry matchSessionRegistry;
    private final RoundEngineRegistry roundEngineRegistry;

    @Override
    public Mono<ContinueResult> continueToNewSeason(UUID userId) {
        // LIMPIEZA TOTAL DE REGISTROS ANTES de nueva temporada
        log.info("[ContinueSeason] Cleaning up all registries before new season for userId={}", userId);
        matchSessionRegistry.clearAllSessions();
        roundEngineRegistry.stopAllEngines();

        // Invalidar cache ANTES de leer para asegurar datos frescos de Redis
        careerSessionService.invalidateCache(userId);
        log.info("[ContinueSeason] Cache invalidated for userId={}, loading fresh career from Redis", userId);

        // Obtener carrera fresca del cache (que ahora leerá de Redis)
        return careerSessionService.getCareerFromCache(userId)
            .flatMap(career -> {
                if (career == null) {
                    return Mono.just(ContinueResult.error("CARRERA_NO_ENCONTRADA", "Career no encontrado"));
                }

                TournamentState tournament = career.getTournamentState();

                // VALIDACIÓN: Verificar que el torneo este en FINISHED
                if (tournament.getCareerPhase() != CareerPhase.FINISHED) {
                    log.warn("[ContinueSeason] Invalid phase: {} for userId={}, expected FINISHED",
                        tournament.getCareerPhase(), userId);
                    return Mono.just(ContinueResult.error("FASE_INVALIDA",
                        "No se puede iniciar nuevo torneo. El torneo actual debe estar finalizado."));
                }

                log.info("[ContinueSeason] Phase validated: FINISHED for userId={}, proceeding with new season", userId);

                // Usar método de dominio para iniciar nueva temporada
                career.startNewSeason();
                int newSeason = career.getCurrentSeason();

                // Resetear estado del torneo para nueva temporada
                tournament.resetForNewSeason();

                // EJECUTAR ascensos y descensos calculados al inicio de la nueva temporada
                career.executePromotionsAndRelegations();

                // Generar fixtures y standings (usa CareerFixtureService compartido)
                careerFixtureService.setupCareerFixtures(career, false);

                // Configurar totalRounds de la división del usuario
                Division userDivision = career.getUserDivision();
                if (userDivision != null) {
                    int totalRounds = careerFixtureService.calculateTotalRounds(userDivision.getTeamCount());
                    tournament.setTotalRounds(totalRounds);
                }

                // Preparar resultado
                String userTeamName = career.getSessionTeam(career.getUserSessionTeamId()) != null
                    ? career.getSessionTeam(career.getUserSessionTeamId()).getName()
                    : "Unknown";

                ContinueResult result = new ContinueResult(
                    true,
                    null,
                    "Temporada " + newSeason + " iniciada!",
                    newSeason,
                    1,
                    tournament.getTotalRounds(),
                    "PRE_MATCH",
                    career.getUserSessionTeamId(),
                    userTeamName
                );

                // Invalidar cache antes de guardar y guardar carrera
                careerSessionService.invalidateCache(userId);
                return careerRepository.save(career).thenReturn(result);
            })
            .onErrorResume(e -> {
                log.error("[ContinueSeason] Error continuing season for userId={}: {}", userId, e.getMessage(), e);
                return Mono.just(ContinueResult.error("ERROR_INTERNO", "Error: " + e.getMessage()));
            });
    }
}

package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Implementación de UseCase para avanzar a la siguiente fecha.
 *
 * Transiciona de WAITING_USER a PRE_MATCH.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvanceRoundUseCaseImpl implements AdvanceRoundUseCase {

    private final CareerSessionService careerSessionService;
    private final CareerLifecycleCoordinator lifecycleCoordinator;

    @Override
    public Mono<AdvanceResult> advanceToNextRound(java.util.UUID userId, String careerId) {
        return lifecycleCoordinator.serialize(userId, Mono.defer(() ->
            careerSessionService.getCareerFromCache(userId).flatMap(career -> {
                // VALIDACIÓN 1: Verificar que la carrera pertenece al usuario
                if (!career.getUserId().equals(userId)) {
                    return Mono.just(AdvanceResult.error("ACCESO_DENEGADO", "Esta carrera no pertenece al usuario"));
                }
                if (!career.getCareerId().equals(careerId)) {
                    return Mono.just(AdvanceResult.error("CARRERA_NO_ENCONTRADA", "La carrera solicitada ya no está activa"));
                }

                TournamentState tournament = career.getTournamentState();

                // VALIDACIÓN 2: Verificar estado WAITING_USER o PRE_MATCH
                // PRE_MATCH es válido porque el usuario ya confirmó el lineup
                CareerPhase currentPhase = tournament.getCareerPhase();
                if (currentPhase != CareerPhase.WAITING_USER && currentPhase != CareerPhase.PRE_MATCH) {
                    return Mono.just(AdvanceResult.error("FASE_INVALIDA",
                        "No se puede avanzar. Estado actual: " + currentPhase.name() + ". Esperado: WAITING_USER o PRE_MATCH"));
                }

                // VALIDACIÓN 3: Verificar que no haya terminado el torneo
                if (Boolean.TRUE.equals(tournament.getFinished())) {
                    return Mono.just(AdvanceResult.error("TORNEO_FINALIZADO", "El torneo ya ha finalizado"));
                }

                int currentRound = tournament.getCurrentRound();

                AdvanceResult result = new AdvanceResult(
                    true,
                    false,
                    null,
                    "Fecha " + currentRound + " lista. A jugar!",
                    currentRound,
                    tournament.getTotalRounds(),
                    career.getCurrentSeason(),
                    "PRE_MATCH",
                    null,
                    null,
                    List.of(),
                    List.of()
                );

                // Repeated clicks on a prepared date are reads, not new writes.
                if (currentPhase == CareerPhase.PRE_MATCH) return Mono.just(result);

                tournament.setCareerPhase(CareerPhase.PRE_MATCH);
                careerSessionService.markDirty(userId);
                return careerSessionService.saveCareer(career)
                    .onErrorResume(error -> {
                        tournament.setCareerPhase(currentPhase);
                        return Mono.error(error);
                    }).thenReturn(result);
            }).switchIfEmpty(Mono.just(AdvanceResult.error("CARRERA_NO_ENCONTRADA", "Career no encontrado: " + careerId)))))
            .onErrorResume(e -> {
                log.error("[AdvanceRound] Failed to advance careerId={} for userId={}", careerId, userId, e);
                return Mono.just(AdvanceResult.error("ERROR_INTERNO", "No se pudo procesar el avance de ronda."));
            });
    }
}

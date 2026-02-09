package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase;
import lombok.RequiredArgsConstructor;
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
public class AdvanceRoundUseCaseImpl implements AdvanceRoundUseCase {

    private final CareerRepository careerRepository;

    @Override
    public Mono<AdvanceResult> advanceToNextRound(java.util.UUID userId, String careerId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.just(AdvanceResult.error("CARRERA_NO_ENCONTRADA", "Career no encontrado: " + careerId));
                }

                CareerSave career = optionalCareer.get();

                // VALIDACIÓN 1: Verificar que la carrera pertenece al usuario
                if (!career.getUserId().equals(userId)) {
                    return Mono.just(AdvanceResult.error("ACCESO_DENEGADO", "Esta carrera no pertenece al usuario"));
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
                if (tournament.getFinished()) {
                    return Mono.just(AdvanceResult.error("TORNEO_FINALIZADO", "El torneo ya ha finalizado"));
                }

                // Transicionar de WAITING_USER a PRE_MATCH
                tournament.setCareerPhase(CareerPhase.PRE_MATCH);

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

                return careerRepository.save(career).thenReturn(result);
            })
            .onErrorResume(e -> {
                return Mono.just(AdvanceResult.error("ERROR_INTERNO", "Error al procesar avance: " + e.getMessage()));
            });
    }
}

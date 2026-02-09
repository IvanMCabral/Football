package com.footballmanager.application.service.match;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.match.StartRoundUseCase;
import com.footballmanager.domain.ports.out.match.MatchRuntimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementacion de StartRoundUseCase.
 * Inicia todos los partidos de una ronda.
 *
 * USA CareerSessionService para mantener cache actualizado.
 *
 * NOTA: careerPhase es gestionado por MatchSimulationOrchestrator.
 * Este use case SOLO inicia los partidos, no modifica el estado de la carrera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartRoundUseCaseImpl implements StartRoundUseCase {

    private final CareerSessionService careerSessionService;
    private final MatchRuntimeRepository runtimeRepository;

    @Override
    public Mono<List<RuntimeMatch>> startRound(UUID userId, String careerId, int round) {
        UUID userUUID = UUID.fromString(careerId); // careerId es el userId

        // Forzar recarga desde Redis para obtener la última versión
        careerSessionService.invalidateCache(userUUID);

        return careerSessionService.getCareerFromCache(userUUID)
            .flatMap(career -> {
                log.info("[START-ROUND] Round {} starting. Career currentRound={}, careerPhase={}",
                    round, career.getTournamentState().getCurrentRound(), career.getTournamentState().getCareerPhase());

                // NOTA: careerPhase NO se modifica aquí
                // MatchSimulationOrchestrator.setCareerPhase(WAITING_USER) cuando la ronda termina

                List<MatchFixture> fixtures = career.getTournamentState()
                    .getFixturesForRound(round);

                if (fixtures.isEmpty()) {
                    log.warn("[START-ROUND] No fixtures found for round {} in career {}", round, careerId);
                    return Mono.just(List.<RuntimeMatch>of());
                }

                log.info("[START-ROUND] Found {} fixtures for round {}", fixtures.size(), round);

                return Flux.fromIterable(fixtures)
                    .filter(MatchFixture::canBeSimulated)
                    .map(fixture -> new RuntimeMatch(
                        fixture.getMatchId(),
                        careerId,
                        fixture.getHomeTeamId(),
                        fixture.getAwayTeamId(),
                        round
                    ))
                    .flatMap(runtime ->
                        runtimeRepository.save(userId, runtime).thenReturn(runtime))
                    .collectList();
            });
    }
}

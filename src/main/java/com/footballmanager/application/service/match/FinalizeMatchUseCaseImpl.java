package com.footballmanager.application.service.match;

import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.match.FinalizeMatchUseCase;
import com.footballmanager.domain.ports.out.match.MatchRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de FinalizeMatchUseCase.
 * Finaliza un partido y persiste resultados en CareerSave.
 */
@Service
@RequiredArgsConstructor
public class FinalizeMatchUseCaseImpl implements FinalizeMatchUseCase {

    private final MatchRuntimeRepository runtimeRepository;
    private final CareerRepository careerRepository;

    @Override
    public Mono<Void> finalizeMatch(UUID userId, String matchId) {
        return runtimeRepository.findByMatchId(userId, matchId)
            .switchIfEmpty(Mono.error(
                new IllegalArgumentException("Match not found: " + matchId)))
            .flatMap(runtimeMatch -> {
                if (!runtimeMatch.isFinished()) {
                    return Mono.error(new IllegalStateException(
                        "El partido no ha finalizado: " + matchId));
                }

                String careerId = runtimeMatch.getCareerId();

                return careerRepository.findById(careerId)
                    .flatMap(optionalCareer -> optionalCareer.isPresent()
                        ? Mono.just(optionalCareer.get())
                        : Mono.error(new IllegalArgumentException(
                            "Career not found: " + careerId)))
                    .flatMap(career -> {
                        try {
                            career.getTournamentState().processMatchResult(
                                matchId,
                                runtimeMatch.getHomeGoals(),
                                runtimeMatch.getAwayGoals(),
                                runtimeMatch.getEvents()
                            );

                            return careerRepository.save(career).thenReturn(career);
                        } catch (IllegalStateException | IllegalArgumentException e) {
                            return Mono.error(e);
                        }
                    })
                    .then(runtimeRepository.delete(userId, matchId));
            });
    }
}

package com.footballmanager.application.service.match;

import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.port.in.match.AdvanceMatchUseCase;
import com.footballmanager.domain.ports.out.match.MatchRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de AdvanceMatchUseCase.
 * Avanza el partido 1 minuto.
 */
@Service
@RequiredArgsConstructor
public class AdvanceMatchUseCaseImpl implements AdvanceMatchUseCase {

    private final MatchRuntimeRepository runtimeRepository;

    @Override
    public Mono<RuntimeMatch> advanceMatch(UUID userId, String matchId) {
        return runtimeRepository.findByMatchId(userId, matchId)
            .switchIfEmpty(Mono.error(
                new IllegalArgumentException("Match not found: " + matchId)))
            .flatMap(match -> {
                try {
                    match.advanceMinute();

                    // Simulacion simple: 10% chance de gol
                    if (Math.random() < 0.1) {
                        boolean isHomeGoal = Math.random() < 0.5;
                        String teamId = isHomeGoal ? match.getHomeTeamId() : match.getAwayTeamId();
                        match.recordGoal(teamId, "player-placeholder", match.getCurrentMinute());
                    }

                    if (match.getCurrentMinute() >= 90) {
                        match.finish();
                    }

                    return runtimeRepository.save(userId, match).thenReturn(match);

                } catch (IllegalStateException e) {
                    return Mono.error(e);
                }
            });
    }
}

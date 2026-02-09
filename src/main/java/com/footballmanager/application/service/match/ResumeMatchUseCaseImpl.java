package com.footballmanager.application.service.match;

import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.port.in.match.ResumeMatchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de ResumeMatchUseCase.
 * Reanuda un partido que estaba en pausa.
 */
@Service
@RequiredArgsConstructor
public class ResumeMatchUseCaseImpl implements ResumeMatchUseCase {

    private final MatchSessionRegistry sessionRegistry;

    @Override
    public Mono<Void> execute(UUID userId, UUID matchId) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> {
                session.resume();
                return Mono.<Void>empty();
            })
            .orElseGet(() -> {
                return Mono.error(new IllegalStateException("Sesion no encontrada para partido: " + matchId));
            });
    }
}

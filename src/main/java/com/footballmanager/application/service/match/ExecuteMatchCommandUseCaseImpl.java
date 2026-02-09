package com.footballmanager.application.service.match;

import com.footballmanager.application.engine.match.MatchCommandHandler;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.port.in.match.ExecuteMatchCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de ExecuteMatchCommandUseCase.
 * Ejecuta comandos tacticos durante un partido.
 */
@Service
@RequiredArgsConstructor
public class ExecuteMatchCommandUseCaseImpl implements ExecuteMatchCommandUseCase {

    private final MatchSessionRegistry sessionRegistry;
    private final MatchCommandHandler commandHandler;

    @Override
    public Mono<Boolean> execute(UUID userId, UUID matchId, MatchCommand command) {
        return sessionRegistry.getSession(userId, matchId)
            .<Mono<Boolean>>map(session -> Mono.just(session.queueCommand(command, commandHandler)))
            .orElseGet(() -> Mono.just(false));
    }
}

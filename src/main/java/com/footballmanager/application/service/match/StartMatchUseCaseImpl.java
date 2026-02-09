package com.footballmanager.application.service.match;

import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.port.in.match.StartMatchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Implementación de StartMatchUseCase.
 */
@Service
@RequiredArgsConstructor
public class StartMatchUseCaseImpl implements StartMatchUseCase {

    private final MatchSessionRegistry sessionRegistry;

    @Override
    public Flux<MatchStateSnapshot> execute(UUID userId, UUID matchId, Consumer<MatchStateSnapshot> onFinishCallback) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> {
                session.setOnFinishCallback(onFinishCallback);
                session.start();
                return session.getStateStream();
            })
            .orElseGet(() -> Flux.error(new IllegalStateException("Sesión no disponible para partido: " + matchId)));
    }
}

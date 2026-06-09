package com.footballmanager.application.service.match;

import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.port.in.match.StartMatchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Implementación de StartMatchUseCase.
 *
 * <p>V24D6M11: Provides both legacy path (execute) and V24 path (executeV24).
 * The V24 path is used when V24LiveSession is active and should use
 * MatchFinishedResult callback to carry V24DetailedMatchResult for persistence.
 */
@Service
@RequiredArgsConstructor
public class StartMatchUseCaseImpl implements StartMatchUseCase {

    private final MatchSessionRegistry sessionRegistry;

    /**
     * Legacy path: returns MatchStateSnapshot to SSE stream and legacy callback.
     */
    @Override
    public Flux<MatchStateSnapshot> execute(UUID userId, UUID matchId, Consumer<MatchStateSnapshot> onFinishCallback) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> {
                session.setOnFinishCallbackLegacy(onFinishCallback);
                session.start();
                return session.getStateStream();
            })
            .orElseGet(() -> Flux.error(new IllegalStateException("Sesión no disponible para partido: " + matchId)));
    }

    /**
     * V24D6M11: V24 path with MatchFinishedResult callback.
     * Used when V24LiveSession is active for the match.
     */
    public Flux<MatchStateSnapshot> executeV24(UUID userId, UUID matchId,
                                               Consumer<MatchFinishedResult> onFinishCallback,
                                               V24LiveSession v24LiveSession) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> {
                session.setOnFinishCallback(onFinishCallback);
                session.start();
                return session.getStateStream();
            })
            .orElseGet(() -> Flux.error(new IllegalStateException("Sesión no disponible para partido: " + matchId)));
    }
}
package com.footballmanager.application.service.match;

import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
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
 * The detailed match path is used when LiveSession is active and should use
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
     * Used when LiveSession is active for the match.
     */
    public Flux<MatchStateSnapshot> executeDetailedMatch(UUID userId, UUID matchId,
                                               Consumer<MatchFinishedResult> onFinishCallback,
                                               LiveSession detailedMatchSession) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> {
                session.setOnFinishCallback(onFinishCallback);
                session.start();
                return session.getStateStream();
            })
            .orElseGet(() -> Flux.error(new IllegalStateException("Sesión no disponible para partido: " + matchId)));
    }
}

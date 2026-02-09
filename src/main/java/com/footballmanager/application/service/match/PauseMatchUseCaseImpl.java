package com.footballmanager.application.service.match;

import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.port.in.match.PauseMatchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de PauseMatchUseCase.
 * Pausa un partido en curso.
 *
 * NOTA: Implementacion intencionalmente similar a StopMatchUseCaseImpl.
 * La duplicacion es minima (~5 lineas) y mejora la claridad de la API:
 * - pause(): Solo pausar, puede reanudar despues
 * - stop(): Pausar Y eliminar la sesion (no se puede reanudar)
 *
 * Si se requiere reducir duplicacion, ambas clases pueden fusionarse
 * en MatchSessionControlUseCase con un parametro de accion.
 */
@Service
@RequiredArgsConstructor
public class PauseMatchUseCaseImpl implements PauseMatchUseCase {

    private final MatchSessionRegistry sessionRegistry;

    @Override
    public Mono<Void> execute(UUID userId, UUID matchId) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> {
                session.pause();
                return Mono.<Void>empty();
            })
            .orElseGet(() -> {
                return Mono.error(new IllegalStateException("Sesion no encontrada para partido: " + matchId));
            });
    }
}

package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.port.in.career.FreePlayersQueryUseCase;
import com.footballmanager.domain.port.in.career.UserPlayerManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * CareerPlayerService - Facade para gestión de jugadores en Career.
 *
 * REFACTORED: Delega a UseCases especializados:
 * - UserPlayerManagementUseCase: Asignar/remover jugadores del usuario
 * - FreePlayersQueryUseCase: Consultar free players
 *
 * Mantiene compatibilidad hacia atrás.
 */
@Service
@RequiredArgsConstructor
public class CareerPlayerService {

    private final UserPlayerManagementUseCase userPlayerManagementUseCase;
    private final FreePlayersQueryUseCase freePlayersQueryUseCase;

    /**
     * Asigna un jugador libre al equipo del usuario.
     *
     * @deprecated Usar UserPlayerManagementUseCase directamente
     */
    @Deprecated
    public Mono<CareerSave> assignPlayerToUserTeam(UUID userId, String sessionPlayerId) {
        return userPlayerManagementUseCase.assignPlayerToUserTeam(userId, sessionPlayerId);
    }

    /**
     * Remueve un jugador libre de la carrera.
     *
     * @deprecated Usar UserPlayerManagementUseCase directamente
     */
    @Deprecated
    public Mono<CareerSave> removePlayer(UUID userId, String sessionPlayerId) {
        return userPlayerManagementUseCase.removePlayerFromUserTeam(userId, sessionPlayerId);
    }

    /**
     * Obtiene el squad del usuario.
     *
     * @deprecated Usar UserPlayerManagementUseCase directamente
     */
    @Deprecated
    public Mono<List<SessionPlayer>> getUserSquad(UUID userId) {
        return userPlayerManagementUseCase.getUserSquad(userId);
    }

    /**
     * Obtiene los free players (modo dual Editor/Gameplay).
     *
     * @deprecated Usar FreePlayersQueryUseCase directamente
     */
    @Deprecated
    public Mono<List<SessionPlayer>> getFreePlayers(UUID userId) {
        return freePlayersQueryUseCase.getFreePlayers(userId);
    }

    /**
     * Copy-on-write conditional - clona jugador solo si el usuario interactúa.
     *
     * @deprecated Método interno, usar UserPlayerManagementUseCase
     */
    @Deprecated
    public Mono<CareerSave> ensureSessionPlayerExists(CareerSave career, String worldPlayerId) {
        return Mono.just(career);
    }
}

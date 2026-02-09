package com.footballmanager.domain.ports.in.player;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de entrada: Asigna un jugador a un equipo
 */
public interface AssignPlayerUseCase {
    Mono<WorldSnapshot> execute(UUID userId, String worldPlayerId, String worldTeamId);
}

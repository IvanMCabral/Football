package com.footballmanager.domain.ports.in.player;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de entrada: Remueve un jugador de su equipo (queda como free agent)
 */
public interface RemovePlayerUseCase {
    Mono<WorldSnapshot> execute(UUID userId, String worldPlayerId);
}

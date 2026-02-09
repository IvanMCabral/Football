package com.footballmanager.domain.ports.in.query;

import com.footballmanager.domain.model.entity.WorldPlayer;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada: Obtiene todos los jugadores de un equipo
 */
public interface GetPlayersByTeamUseCase {
    Mono<List<WorldPlayer>> execute(UUID userId, String worldTeamId);
}

package com.footballmanager.domain.ports.in.query;

import com.footballmanager.domain.model.entity.WorldLeague;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada: Obtiene todas las ligas
 */
public interface GetLeaguesUseCase {
    Mono<List<WorldLeague>> execute(UUID userId);
}

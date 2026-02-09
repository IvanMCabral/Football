package com.footballmanager.domain.ports.in.query;

import com.footballmanager.domain.model.entity.WorldTeam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada: Obtiene todos los equipos de una liga
 */
public interface GetTeamsByLeagueUseCase {
    Mono<List<WorldTeam>> execute(UUID userId, UUID leagueId);
}

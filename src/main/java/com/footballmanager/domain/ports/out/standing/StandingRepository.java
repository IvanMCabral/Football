package com.footballmanager.domain.ports.out.standing;

import com.footballmanager.domain.model.entity.Standing;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository port para Standings.
 * Implementación en Redis con scope de usuario.
 *
 * La "seasonKey" puede ser un seasonId (int) o tournamentId (UUID).
 */
public interface StandingRepository {

    /**
     * Guarda o actualiza un standing
     */
    Mono<Void> save(UUID userId, Standing standing);

    /**
     * Busca un standing por seasonKey y teamId
     */
    Mono<Standing> findBySeasonKeyAndTeamId(UUID userId, String seasonKey, UUID teamId);

    /**
     * Busca todos los standings de una temporada/torneo
     */
    Flux<Standing> findBySeasonKey(UUID userId, String seasonKey);

    /**
     * Verifica si existe un standing
     */
    Mono<Boolean> exists(UUID userId, String seasonKey, UUID teamId);

    /**
     * Elimina un standing
     */
    Mono<Void> delete(UUID userId, String seasonKey, UUID teamId);

    /**
     * Elimina todos los standings de una temporada/torneo
     */
    Mono<Void> deleteBySeasonKey(UUID userId, String seasonKey);
}

package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.entity.MatchCommand;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Puerto para persistencia de comandos pendientes de partidos.
 * Permite almacenar comandos reactivamente, distribuidos y sin estado en memoria del application layer.
 */
public interface MatchCommandRepository {

    /**
     * Guarda un comando pendiente para un partido.
     * @param userId ID del usuario
     * @param matchId ID del partido
     * @param command Comando a guardar
     * @return Mono vacío cuando se completa la operación
     */
    Mono<Void> saveCommand(UUID userId, UUID matchId, MatchCommand command);

    /**
     * Recupera todos los comandos pendientes de un partido.
     * @param userId ID del usuario
     * @param matchId ID del partido
     * @return Lista de comandos pendientes (puede ser vacía)
     */
    Mono<List<MatchCommand>> findPendingCommands(UUID userId, UUID matchId);

    /**
     * Elimina todos los comandos pendientes de un partido después de aplicarlos.
     * @param userId ID del usuario
     * @param matchId ID del partido
     * @return Mono vacío cuando se completa la operación
     */
    Mono<Void> deleteCommands(UUID userId, UUID matchId);
}

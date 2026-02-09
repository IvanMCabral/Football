package com.footballmanager.domain.ports.in.query;

import com.footballmanager.domain.model.view.WorldView;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de entrada - Use Case para construir la vista del mundo.
 *
 * Responsabilidad única: construir WorldView desde SQL + Redis.
 * Este use case es de SOLO LECTURA - no modifica ningún dato.
 *
 * Flujo:
 * 1. Leer datos base de PostgreSQL (ligas, equipos, jugadores)
 * 2. Leer datos custom del usuario desde Redis (user-world)
 * 3. Merge en memoria
 * 4. Retornar WorldView
 *
 * No persiste nada - WorldView es un POJO en memoria.
 */
public interface BuildWorldViewUseCase {

    /**
     * Construye la vista del mundo para un usuario específico.
     *
     * Proceso:
     * - Carga ligas desde PostgreSQL (LeagueRepository)
     * - Carga equipos base desde PostgreSQL (TeamRepository)
     * - Carga jugadores base desde PostgreSQL (PlayerRepository)
     * - Carga equipos custom desde Redis (UserWorldRepository)
     * - Carga jugadores custom desde Redis (UserWorldRepository)
     * - Merge todos los datos en memoria
     * - Retorna WorldView
     *
     * @param userId ID del usuario
     * @return Mono<WorldView> - Vista del mundo en memoria
     */
    Mono<WorldView> build(UUID userId);
}

package com.footballmanager.application.service.world;

import com.footballmanager.adapters.in.web.dashboard.dto.WorldStatusResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Query service para estado del mundo del usuario.
 * Lee el WorldSnapshot del usuario (SQL base + Redis customizations).
 */
@Service
public class WorldStatusQueryService {

    private final WorldService worldService;

    public WorldStatusQueryService(WorldService worldService) {
        this.worldService = worldService;
    }

    /**
     * Obtiene el estado del mundo para un usuario especifico.
     * DTO: clubs (teams), players, matches (desde WorldSnapshot).
     * Usa WorldService que inicializa el snapshot desde SQL si no existe.
     */
    public Mono<WorldStatusResponse> getWorldStatus(UUID userId) {
        return worldService.getWorldSnapshot(userId)
                .map(snapshot -> new WorldStatusResponse(
                        snapshot.getAllWorldTeams() != null ? snapshot.getAllWorldTeams().size() : 0,
                        snapshot.getAllWorldPlayers() != null ? snapshot.getAllWorldPlayers().size() : 0,
                        0 // matches - requeriria contar de CareerSave o PostgreSQL
                ))
                .switchIfEmpty(Mono.just(new WorldStatusResponse(0, 0, 0)));
    }
}

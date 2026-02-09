package com.footballmanager.application.service.world;

import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Servicio de gestión del WorldSnapshot.
 * Delega la creación a WorldSnapshotCreator.
 */
@Service
@RequiredArgsConstructor
public class WorldSnapshotService {

    private final RedisWorldRepository worldRepository;
    private final WorldSnapshotCreator snapshotCreator;

    /**
     * Verifica si existe el WorldSnapshot para un usuario.
     */
    public Mono<Boolean> exists(UUID userId) {
        return worldRepository.existsByUserId(userId);
    }

    /**
     * Alias para compatibilidad.
     */
    public Mono<Boolean> existsByUserId(UUID userId) {
        return exists(userId);
    }

    /**
     * Obtiene el WorldSnapshot de un usuario.
     */
    public Mono<WorldSnapshot> getSnapshot(UUID userId) {
        return worldRepository.findByUserId(userId)
                .flatMap(snapshot -> {
                    if (isSnapshotIncomplete(snapshot)) {
                        return snapshotCreator.create(userId);
                    }
                    return Mono.just(snapshot);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    return snapshotCreator.create(userId);
                }));
    }

    /**
     * Guarda el WorldSnapshot en Redis.
     */
    public Mono<WorldSnapshot> saveSnapshot(WorldSnapshot snapshot) {
        return worldRepository.save(snapshot);
    }

    /**
     * Inicializa el WorldSnapshot desde PostgreSQL.
     */
    public Mono<WorldSnapshot> initializeFromDatabase(UUID userId) {
        return worldRepository.findByUserId(userId)
                .flatMap(snapshot -> {
                    if (isSnapshotIncomplete(snapshot)) {
                        return snapshotCreator.create(userId);
                    }
                    return Mono.just(snapshot);
                })
                .switchIfEmpty(Mono.defer(() -> snapshotCreator.create(userId)));
    }

    /**
     * Regenera el WorldSnapshot desde PostgreSQL.
     */
    public Mono<WorldSnapshot> reloadFromDatabase(UUID userId) {
        return snapshotCreator.create(userId);
    }

    /**
     * Verifica si el snapshot está incompleto.
     */
    private boolean isSnapshotIncomplete(WorldSnapshot snapshot) {
        if (snapshot == null) return true;
        if (snapshot.getLeagues() == null || snapshot.getLeagues().isEmpty()) return true;
        if (snapshot.getWorldTeams() == null || snapshot.getWorldTeams().isEmpty()) return true;
        if (snapshot.getWorldPlayers() == null || snapshot.getWorldPlayers().isEmpty()) return true;
        return false;
    }
}

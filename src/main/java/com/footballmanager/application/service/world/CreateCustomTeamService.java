package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Servicio para crear equipos custom.
 * Los equipos custom se guardan en el WorldSnapshot usando WorldSnapshotService.
 */
@Service
@RequiredArgsConstructor
public class CreateCustomTeamService {

    private final WorldSnapshotService snapshotService;

    /**
     * Crea un equipo custom y lo guarda en el WorldSnapshot
     */
    public Mono<WorldTeam> createCustomTeam(
            UUID userId,
            String name,
            String country,
            BigDecimal budget,
            String formation) {

        WorldTeam team = WorldTeam.createCustom(name, country, budget, formation);

        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    snapshot.addWorldTeam(team);
                    return snapshotService.saveSnapshot(snapshot).thenReturn(team);
                });
    }
}

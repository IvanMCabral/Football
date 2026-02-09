package com.footballmanager.application.service.admin;

import com.footballmanager.domain.port.in.fixture.MigrateFixturesUseCase;
import com.footballmanager.domain.port.in.fixture.RegenerateFixturesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * FixtureAdminService - Facade para servicios de fixtures.
 *
 * REFACTORED: Este servicio ahora delega a UseCases especializados:
 * - MigrateFixturesUseCase: Migración de fixtures por división
 * - RegenerateFixturesUseCase: Regeneración completa de fixtures
 *
 * Mantiene compatibilidad con código existente que depende de FixtureAdminService.
 */
@Service
@RequiredArgsConstructor
public class FixtureAdminService {

    private final MigrateFixturesUseCase migrateFixturesUseCase;
    private final RegenerateFixturesUseCase regenerateFixturesUseCase;

    /**
     * Migra/regenera fixtures usando career.tournamentState como fuente de verdad.
     *
     * @deprecated Usar MigrateFixturesUseCase directamente
     */
    @Deprecated
    public Mono<Void> migrateFixtures(String userId) {
        return migrateFixturesUseCase.migrate(userId);
    }

    /**
     * Regenera el fixture de la carrera actual.
     *
     * @deprecated Usar RegenerateFixturesUseCase directamente
     */
    @Deprecated
    public Mono<Void> regenerateFixtures(String userId) {
        return regenerateFixturesUseCase.regenerate(UUID.fromString(userId));
    }
}

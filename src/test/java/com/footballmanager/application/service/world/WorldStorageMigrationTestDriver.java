package com.footballmanager.application.service.world;

import com.footballmanager.application.service.career.CareerLifecycleCoordinator;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Public-API test driver for the productive migration orchestrator. */
public final class WorldStorageMigrationTestDriver {

    private WorldStorageMigrationTestDriver() { }

    public static WorldStorageMigrationOrchestrator create(WorldStorageMigrationExecutor executor,
                                                            CanonicalWorldCatalogSource canonicalSource) {
        return create(executor, canonicalSource, null);
    }

    public static WorldStorageMigrationOrchestrator create(WorldStorageMigrationExecutor executor,
                                                            CanonicalWorldCatalogSource canonicalSource,
                                                            CareerSave career) {
        CareerRepository careers = new CareerRepository() {
            @Override
            public Mono<Optional<CareerSave>> findById(String id) {
                return Mono.just(Optional.ofNullable(career));
            }

            @Override public Mono<Void> createInitialCareer(CareerSave value) { return Mono.empty(); }
            @Override public Mono<Void> saveExistingCareer(CareerWriteContext context, CareerSave value) {
                return Mono.empty();
            }
            @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        };
        CanonicalWorldCatalogSource source = canonicalSource != null ? canonicalSource
                : owner -> executor.inspect(owner).map(WorldStorageMigrationExecutor.SourceInspection::snapshot);
        return new WorldStorageMigrationOrchestrator(executor, source, careers,
                new WorldMigrationReferenceInventory(), new CareerLifecycleCoordinator(Duration.ofSeconds(30)));
    }

    public static WorldStorageMigrationOrchestrator.Outcome migrate(WorldStorageMigrationExecutor executor,
                                                                     CanonicalWorldCatalogSource source,
                                                                     UUID ownerId,
                                                                     long currentBytes,
                                                                     long quotaBytes,
                                                                     long safetyMarginBytes) {
        return create(executor, source).migrate(ownerId,
                        new WorldStorageMigrationOrchestrator.CapacitySnapshot(
                                currentBytes, quotaBytes, safetyMarginBytes))
                .block(Duration.ofSeconds(30));
    }

    public static WorldStorageMigrationOrchestrator.Outcome migrate(WorldStorageMigrationExecutor executor,
                                                                     CanonicalWorldCatalogSource source,
                                                                     UUID ownerId,
                                                                     long currentBytes,
                                                                     long quotaBytes,
                                                                     long safetyMarginBytes,
                                                                     long localAccountingUncertaintyMarginBytes) {
        return create(executor, source).migrate(ownerId,
                        new WorldStorageMigrationOrchestrator.CapacitySnapshot(
                                currentBytes, quotaBytes, safetyMarginBytes,
                                localAccountingUncertaintyMarginBytes))
                .block(Duration.ofSeconds(30));
    }
}

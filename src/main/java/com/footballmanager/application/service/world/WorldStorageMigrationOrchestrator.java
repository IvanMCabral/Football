package com.footballmanager.application.service.world;

import com.footballmanager.application.service.career.CareerLifecycleCoordinator;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Productive, non-HTTP orchestrator for a single admitted World V2 migration. */
@Service
public final class WorldStorageMigrationOrchestrator {

    private final WorldStorageMigrationExecutor executor;
    private final CanonicalWorldCatalogSource canonicalSource;
    private final CareerRepository careerRepository;
    private final WorldMigrationReferenceInventory referenceInventory;
    private final WorldStorageMigrationPlanner planner;
    private final CareerLifecycleCoordinator lifecycleCoordinator;

    public WorldStorageMigrationOrchestrator(WorldStorageMigrationExecutor executor,
                                             CanonicalWorldCatalogSource canonicalSource,
                                             CareerRepository careerRepository,
                                             WorldMigrationReferenceInventory referenceInventory,
                                             CareerLifecycleCoordinator lifecycleCoordinator) {
        this.executor = executor;
        this.canonicalSource = canonicalSource;
        this.careerRepository = careerRepository;
        this.referenceInventory = referenceInventory;
        this.planner = new WorldStorageMigrationPlanner();
        this.lifecycleCoordinator = lifecycleCoordinator;
    }

    public Mono<Outcome> migrate(UUID ownerId, CapacitySnapshot capacity) {
        if (ownerId == null || capacity == null) {
            return Mono.just(new Outcome(Status.INVALID_LEGACY, "owner and capacity are required", 0));
        }
        Mono<Outcome> operation = executor.inspect(ownerId)
                .flatMap(inspection -> inspection.state() == WorldStorageMigrationExecutor.StoredState.COMMITTED
                        ? validateCommittedReferences(ownerId, capacity, inspection)
                        : migrateLegacy(ownerId, capacity, inspection))
                .onErrorResume(WorldStorageMigrationExecutor.InspectionException.class,
                        error -> Mono.just(new Outcome(
                                error.status() == WorldStorageMigrationExecutor.Status.INVALID_V2
                                        ? Status.INVALID_V2 : Status.INVALID_LEGACY,
                                publicReason(error), 0)))
                .onErrorResume(error -> Mono.just(new Outcome(Status.RETRYABLE_PARTIAL,
                        publicReason(error), 0)));
        return lifecycleCoordinator == null ? operation : lifecycleCoordinator.serialize(ownerId, operation);
    }

    private Mono<Outcome> migrateLegacy(UUID ownerId, CapacitySnapshot capacity,
                                        WorldStorageMigrationExecutor.SourceInspection inspection) {
        WorldStorageMigrationLimits.Validation bounds = WorldStorageMigrationLimits.validate(
                inspection.snapshot(), inspection.serializedBytes());
        if (!bounds.valid()) return Mono.just(new Outcome(Status.INVALID_LEGACY, bounds.reason(), 0));
        return canonicalSource.rebuild(ownerId)
                .flatMap(canonical -> careerRepository.findById(ownerId.toString())
                        .map(optional -> referenceInventory.discover(optional.orElse(null)))
                        .defaultIfEmpty(WorldReferenceGraph.empty())
                        .flatMap(references -> {
                            WorldStorageMigrationPlanner.MigrationPlan plan =
                                    planner.plan(inspection.snapshot(), canonical, references);
                            if (!plan.ready()) {
                                return Mono.just(new Outcome(Status.BLOCKED_REFERENCE, plan.reason(), 0));
                            }
                            WorldMigrationAdmission admission = WorldMigrationAdmission.approved(ownerId,
                                    inspection.sourceChecksum(), canonical, plan, capacity.currentDatasetBytes(),
                                    capacity.quotaBytes(), capacity.safetyMarginBytes(),
                                    capacity.localAccountingUncertaintyMarginBytes());
                            return executor.execute(admission).map(this::map);
                        }));
    }

    private Mono<Outcome> validateCommittedReferences(UUID ownerId, CapacitySnapshot capacity,
                                                       WorldStorageMigrationExecutor.SourceInspection inspection) {
        return referenceGraph(ownerId).flatMap(references -> {
            WorldStorageMigrationPlanner.MigrationPlan plan = planner.plan(
                    inspection.snapshot(), inspection.snapshot(), references);
            if (!plan.ready()) return Mono.just(new Outcome(Status.BLOCKED_REFERENCE, plan.reason(), 0));
            return executor.execute(WorldMigrationAdmission.approved(ownerId, inspection.sourceChecksum(),
                    inspection.snapshot(), plan, capacity.currentDatasetBytes(), capacity.quotaBytes(),
                    capacity.safetyMarginBytes(), capacity.localAccountingUncertaintyMarginBytes())).map(this::map);
        });
    }

    private Mono<WorldReferenceGraph> referenceGraph(UUID ownerId) {
        return careerRepository.findById(ownerId.toString())
                .map(optional -> referenceInventory.discover(optional.orElse(null)))
                .defaultIfEmpty(WorldReferenceGraph.empty());
    }

    private Outcome map(WorldStorageMigrationExecutor.ExecutionResult result) {
        Status status = switch (result.status()) {
            case MIGRATED -> Status.MIGRATED;
            case ALREADY_MIGRATED_VALID -> Status.ALREADY_MIGRATED_VALID;
            case BLOCKED_CAPACITY -> Status.BLOCKED_CAPACITY;
            case SOURCE_CHANGED -> Status.SOURCE_CHANGED;
            case INVALID_LEGACY -> Status.INVALID_LEGACY;
            case INVALID_V2 -> Status.INVALID_V2;
            case RETRYABLE_PARTIAL -> Status.RETRYABLE_PARTIAL;
        };
        return new Outcome(status, result.reason(), result.plannedPeakBytes());
    }

    private static String publicReason(Throwable error) {
        return error == null || error.getMessage() == null ? "migration validation failed" : error.getMessage();
    }

    public enum Status {
        MIGRATED,
        ALREADY_MIGRATED_VALID,
        BLOCKED_CAPACITY,
        BLOCKED_REFERENCE,
        SOURCE_CHANGED,
        INVALID_LEGACY,
        INVALID_V2,
        RETRYABLE_PARTIAL
    }

    public record CapacitySnapshot(long currentDatasetBytes, long quotaBytes, long safetyMarginBytes,
                                   long localAccountingUncertaintyMarginBytes) {
        public CapacitySnapshot(long currentDatasetBytes, long quotaBytes, long safetyMarginBytes) {
            this(currentDatasetBytes, quotaBytes, safetyMarginBytes,
                    WorldStoragePhysicalCapacityModel.DEFAULT_LOCAL_ACCOUNTING_UNCERTAINTY_MARGIN_BYTES);
        }

        public CapacitySnapshot {
            if (currentDatasetBytes < 0 || quotaBytes <= 0 || safetyMarginBytes < 0
                    || localAccountingUncertaintyMarginBytes < 0
                    || safetyMarginBytes + localAccountingUncertaintyMarginBytes >= quotaBytes) {
                throw new IllegalArgumentException("invalid capacity snapshot");
            }
        }
    }

    public record Outcome(Status status, String reason, long plannedPeakBytes) { }
}

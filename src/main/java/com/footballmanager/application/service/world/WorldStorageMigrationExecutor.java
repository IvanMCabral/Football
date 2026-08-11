package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound application port for the Redis World V2 migration state machine. */
public interface WorldStorageMigrationExecutor {

    Mono<SourceInspection> inspect(UUID ownerId);

    Mono<ExecutionResult> execute(WorldMigrationAdmission admission);

    enum StoredState { LEGACY, PREPARED, COMMITTED }

    enum Status {
        MIGRATED,
        ALREADY_MIGRATED_VALID,
        BLOCKED_CAPACITY,
        SOURCE_CHANGED,
        INVALID_LEGACY,
        INVALID_V2,
        RETRYABLE_PARTIAL
    }

    record SourceInspection(UUID ownerId, StoredState state, WorldSnapshot snapshot,
                            String sourceChecksum, int serializedBytes) { }

    record ExecutionResult(Status status, long preparedBytes, long committedBytes,
                           long plannedPeakBytes, boolean recoverable, String reason) { }

    final class InspectionException extends IllegalStateException {
        private final Status status;

        public InspectionException(Status status, String message) {
            super(message);
            if (status != Status.INVALID_LEGACY && status != Status.INVALID_V2) {
                throw new IllegalArgumentException("inspection status must classify stored data");
            }
            this.status = status;
        }

        public Status status() { return status; }
    }
}

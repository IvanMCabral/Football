package com.footballmanager.application.service.world.canary;

import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Read-only product probe for the selected owner's migration preconditions. */
public interface WorldV2CanarySourceProbe {

    Mono<SourceSnapshot> inspect(UUID ownerId);

    record SourceSnapshot(WorldStorageMigrationExecutor.StoredState state,
                          String sourceSha,
                          boolean ownerMatch,
                          boolean careerAbsent,
                          int referenceCount,
                          boolean catalogCompatible,
                          boolean catalogPresent,
                          String canonicalFingerprint) {
        public SourceSnapshot {
            if (state == null || sourceSha == null || canonicalFingerprint == null
                    || referenceCount < 0) {
                throw new IllegalArgumentException("incomplete canary source snapshot");
            }
        }
    }
}

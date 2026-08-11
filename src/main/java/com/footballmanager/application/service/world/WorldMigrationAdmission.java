package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;

import java.util.Objects;
import java.util.UUID;

/**
 * Unforgeable outside the application package: only the orchestrator can turn
 * a concrete source inspection plus a successful reference plan into a write
 * admission.
 */
public final class WorldMigrationAdmission {

    private final UUID ownerId;
    private final String sourceChecksum;
    private final WorldSnapshot canonicalSnapshot;
    private final WorldStorageMigrationPlanner.MigrationPlan referencePlan;
    private final long currentDatasetBytes;
    private final long quotaBytes;
    private final long safetyMarginBytes;
    private final long localAccountingUncertaintyMarginBytes;

    private WorldMigrationAdmission(UUID ownerId, String sourceChecksum, WorldSnapshot canonicalSnapshot,
                                    WorldStorageMigrationPlanner.MigrationPlan referencePlan,
                                    long currentDatasetBytes, long quotaBytes,
                                    long safetyMarginBytes, long localAccountingUncertaintyMarginBytes) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        this.canonicalSnapshot = Objects.requireNonNull(canonicalSnapshot, "canonicalSnapshot");
        this.referencePlan = Objects.requireNonNull(referencePlan, "referencePlan");
        if (!referencePlan.ready()) {
            throw new IllegalArgumentException("reference plan must be ready");
        }
        if (currentDatasetBytes < 0 || quotaBytes <= 0 || safetyMarginBytes < 0
                || localAccountingUncertaintyMarginBytes < 0
                || safetyMarginBytes + localAccountingUncertaintyMarginBytes >= quotaBytes) {
            throw new IllegalArgumentException("invalid migration capacity snapshot");
        }
        this.currentDatasetBytes = currentDatasetBytes;
        this.quotaBytes = quotaBytes;
        this.safetyMarginBytes = safetyMarginBytes;
        this.localAccountingUncertaintyMarginBytes = localAccountingUncertaintyMarginBytes;
    }

    static WorldMigrationAdmission approved(UUID ownerId, String sourceChecksum, WorldSnapshot canonicalSnapshot,
                                             WorldStorageMigrationPlanner.MigrationPlan referencePlan,
                                             long currentDatasetBytes, long quotaBytes,
                                             long safetyMarginBytes,
                                             long localAccountingUncertaintyMarginBytes) {
        return new WorldMigrationAdmission(ownerId, sourceChecksum, canonicalSnapshot, referencePlan,
                currentDatasetBytes, quotaBytes, safetyMarginBytes, localAccountingUncertaintyMarginBytes);
    }

    public UUID ownerId() { return ownerId; }
    public String sourceChecksum() { return sourceChecksum; }
    public WorldSnapshot canonicalSnapshot() { return canonicalSnapshot; }
    public WorldStorageMigrationPlanner.MigrationPlan referencePlan() { return referencePlan; }
    public long currentDatasetBytes() { return currentDatasetBytes; }
    public long quotaBytes() { return quotaBytes; }
    public long safetyMarginBytes() { return safetyMarginBytes; }
    public long localAccountingUncertaintyMarginBytes() { return localAccountingUncertaintyMarginBytes; }
}

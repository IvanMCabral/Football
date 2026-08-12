package com.footballmanager.application.service.world;

import java.util.List;
import java.util.Set;

/**
 * A physical persistence boundary discovered independently from its
 * classification metadata.  The distinction is deliberate: a missing
 * annotation must produce an unclassified boundary, never an invisible one.
 */
public record DurablePersistenceBoundary(
        Class<?> boundaryType,
        StorageTechnology technology,
        OperationType operationType,
        Set<Class<?>> persistedTypes,
        List<Declaration> declarations,
        Classification classification,
        String exclusionReason,
        ClassificationSource classificationSource) {

    public enum StorageTechnology {
        REDIS,
        POSTGRESQL,
        FILE_BLOB,
        OTHER_DURABLE_ADAPTER
    }

    public enum OperationType {
        READ,
        WRITE,
        READ_WRITE,
        UNKNOWN
    }

    public enum Classification {
        WORLD_REFERENCE_RELEVANT,
        OUT_OF_SCOPE_CANONICAL_SOURCE,
        OUT_OF_SCOPE_NON_WORLD_IDENTITY,
        OUT_OF_SCOPE_WITH_EXPLICIT_REASON,
        UNCLASSIFIED
    }

    public enum ClassificationSource {
        WORLD_PERSISTED_WRITER,
        DURABLE_BOUNDARY_CLASSIFICATION,
        UNCLASSIFIED_DISCOVERY
    }

    public record Declaration(String method, Class<?> persistedType, String storageFamily,
                              Classification classification, String reason) { }

    public boolean isClassified() {
        return classification != Classification.UNCLASSIFIED;
    }

    public boolean isWorldReferenceRelevant() {
        return classification == Classification.WORLD_REFERENCE_RELEVANT;
    }
}

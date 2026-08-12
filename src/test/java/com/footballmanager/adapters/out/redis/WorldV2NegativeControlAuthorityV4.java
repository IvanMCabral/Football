package com.footballmanager.adapters.out.redis;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Canonical one-to-one evidence authority with truthful execution modes. */
final class WorldV2NegativeControlAuthorityV4 {

    enum Mode {
        REDIS_PHYSICAL,
        JVM_PROCESS_PHYSICAL,
        BUILD_DISCOVERY_PHYSICAL,
        PRE_WRITE_GUARD,
        SOURCE_PROVEN
    }

    record Definition(WorldStorageV2NegativeControlsTest.Control controlId,
                      String invariantId, Mode requiredMode, String exactFixture,
                      String physicalKeys, String mutation, String pipelineEntry,
                      String expectedRejection) { }

    private static final Set<WorldStorageV2NegativeControlsTest.Control> SOURCE_ONLY = Set.of(
            WorldStorageV2NegativeControlsTest.Control.RANDOM_CANONICAL_PLAYER_ID,
            WorldStorageV2NegativeControlsTest.Control.WRONG_NAMESPACE,
            WorldStorageV2NegativeControlsTest.Control.NORMALIZATION_DRIFT,
            WorldStorageV2NegativeControlsTest.Control.CATALOG_SEMANTIC_COLLISION,
            WorldStorageV2NegativeControlsTest.Control.FIELD_AUTHORITY_UNCOVERED,
            WorldStorageV2NegativeControlsTest.Control.REFERENCE_REGISTRY_UNCOVERED);

    private WorldV2NegativeControlAuthorityV4() { }

    static Map<WorldStorageV2NegativeControlsTest.Control, Definition> definitions() {
        Map<WorldStorageV2NegativeControlsTest.Control, Definition> result =
                new EnumMap<>(WorldStorageV2NegativeControlsTest.Control.class);
        for (WorldStorageV2NegativeControlsTest.Control control
                : WorldStorageV2NegativeControlsTest.Control.values()) {
            Mode mode = control == WorldStorageV2NegativeControlsTest.Control.MISSING_TTL
                    ? Mode.PRE_WRITE_GUARD
                    : SOURCE_ONLY.contains(control) ? Mode.SOURCE_PROVEN : Mode.REDIS_PHYSICAL;
            var trace = WorldV2NegativeControlAuthorityV2.definitions().get(control);
            result.put(control, new Definition(control, trace.invariantId(), mode,
                    trace.exactFixture(), trace.physicalKeys(), trace.mutation(),
                    trace.pipelineEntry(), trace.expectedRejection()));
        }
        return Map.copyOf(result);
    }
}

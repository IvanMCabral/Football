package com.footballmanager.adapters.out.redis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Self-destruction tests for negative-control evidence claims. */
class WorldV2EvidenceIntegrityTest {

    @Test
    void acceptsOnlyEvidenceWithARealPhysicalMutationAndProductSignal() {
        assertDoesNotThrow(() -> WorldV2EvidenceContract.validate(List.of(
                new WorldV2EvidenceContract.Evidence("REAL", "WORLD", WorldV2NegativeControlAuthorityV4.Mode.REDIS_PHYSICAL,
                        true, "before", "after", "RedisWorldRepository.findByUserId", "REJECTED"))));
    }

    @Test
    void rejectsFakePhysicalEvidence() {
        assertThrows(IllegalStateException.class, () -> WorldV2EvidenceContract.validate(List.of(
                new WorldV2EvidenceContract.Evidence("FAKE", "WORLD", WorldV2NegativeControlAuthorityV4.Mode.REDIS_PHYSICAL,
                        false, "same", "same", "", "COMPARATOR_ONLY"))));
    }

    @Test
    void rejectsDuplicateInvariantMissingEvidenceAndUnknownMode() {
        var first = new WorldV2EvidenceContract.Evidence("A", "DUP", WorldV2NegativeControlAuthorityV4.Mode.SOURCE_PROVEN,
                false, null, null, "WorldMigrationPlanner", "BLOCKED");
        var duplicate = new WorldV2EvidenceContract.Evidence("B", "DUP", WorldV2NegativeControlAuthorityV4.Mode.SOURCE_PROVEN,
                false, null, null, "WorldMigrationPlanner", "BLOCKED");
        assertThrows(IllegalStateException.class, () -> WorldV2EvidenceContract.validate(List.of(first, duplicate)));
        assertThrows(IllegalStateException.class, () -> WorldV2EvidenceContract.validate(List.of(
                new WorldV2EvidenceContract.Evidence("MISSING", "MISSING", null,
                        false, null, null, "", ""))));
    }
}

final class WorldV2EvidenceContract {
    private WorldV2EvidenceContract() { }

    static void validate(List<Evidence> evidence) {
        var invariants = new java.util.HashSet<String>();
        for (Evidence item : evidence) {
            if (item.mode() == null || item.invariantId() == null || item.invariantId().isBlank()
                    || !invariants.add(item.invariantId())) {
                throw new IllegalStateException("invalid or duplicate negative-control evidence");
            }
            if (item.mode() == WorldV2NegativeControlAuthorityV4.Mode.REDIS_PHYSICAL) {
                if (!item.redisInstanceUsed() || item.beforeChecksum() == null || item.afterChecksum() == null
                        || item.beforeChecksum().equals(item.afterChecksum()) || item.productEntryPoint().isBlank()
                        || item.productResult().isBlank() || "COMPARATOR_ONLY".equals(item.productResult())) {
                    throw new IllegalStateException("REDIS_PHYSICAL evidence is incomplete");
                }
            } else if (item.productEntryPoint().isBlank() || item.productResult().isBlank()) {
                throw new IllegalStateException("evidence is missing product signal");
            }
        }
    }

    record Evidence(String controlId, String invariantId, WorldV2NegativeControlAuthorityV4.Mode mode,
                    boolean redisInstanceUsed, String beforeChecksum, String afterChecksum,
                    String productEntryPoint, String productResult) { }
}

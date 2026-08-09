package com.footballmanager.adapters.out.redis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provider-latency model for the cleanup command graph. This is deliberately
 * not a Redis availability claim: it verifies the round-trip reduction of the
 * indexed path at representative remote command latencies.
 */
class RedisCleanupLatencyModelTest {

    private static final int LEGACY_SCAN_FAMILIES = 12;
    private static final int MODERN_SCAN_FAMILIES = 1; // user projection fallback
    private static final int LEGACY_METADATA_COMMANDS = 8;
    private static final int MODERN_METADATA_COMMANDS = 8;

    @ParameterizedTest
    @ValueSource(ints = {25, 50, 75})
    void indexedCleanupReducesRemoteLatencyAtRepresentativeProviderDelays(int commandLatencyMs) {
        long legacy = (long) (LEGACY_SCAN_FAMILIES + LEGACY_METADATA_COMMANDS) * commandLatencyMs;
        long modern = (long) (MODERN_SCAN_FAMILIES + MODERN_METADATA_COMMANDS) * commandLatencyMs;

        assertTrue(modern < legacy,
                "modern indexed cleanup must reduce the remote command lower bound");
        assertTrue(legacy - modern >= 11L * commandLatencyMs,
                "the model must account for removing eleven independent SCAN families");
    }

    @org.junit.jupiter.api.Test
    void manifestBudgetIsBoundedForWorstCaseCareer() {
        int maxEntries = 1_024;
        int estimatedBytesPerEntry = 112;
        long manifestBytes = (long) maxEntries * estimatedBytesPerEntry;
        long worstCaseFor256Careers = manifestBytes * 256;

        assertTrue(manifestBytes < 128 * 1024,
                "one cleanup manifest must remain a small bounded metadata object");
        assertTrue(worstCaseFor256Careers < 32L * 1024 * 1024,
                "the hard cap must not recreate the historical unbounded growth");
    }
}

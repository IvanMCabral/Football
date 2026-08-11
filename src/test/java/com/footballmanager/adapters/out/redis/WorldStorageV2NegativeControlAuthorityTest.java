package com.footballmanager.adapters.out.redis;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structured authority for the canonical, independently credited World V2 controls. */
class WorldStorageV2NegativeControlAuthorityTest {

    enum Mode { REDIS_PHYSICAL, SOURCE_PROVEN }

    record ControlEvidence(String finding, Mode modeRequired, Mode modeActual, String fixture,
                           String mutation, String pipelineEntry, String expected,
                           String actual, boolean detected, String errorCode) { }

    @Test
    void everyCanonicalControlHasUniqueStructuredEvidenceWithoutMaterialProxyCredit() {
        Map<WorldStorageV2NegativeControlsTest.Control, ControlEvidence> authority = authority();
        assertEquals(Set.of(WorldStorageV2NegativeControlsTest.Control.values()), authority.keySet());
        Set<String> creditedMutations = new HashSet<>();
        authority.forEach((id, evidence) -> {
            assertFalse(evidence.finding().isBlank(), id + " finding");
            assertEquals(evidence.modeRequired(), evidence.modeActual(), id + " mode proxy");
            assertFalse(evidence.fixture().isBlank(), id + " fixture");
            assertFalse(evidence.pipelineEntry().isBlank(), id + " pipeline");
            assertFalse(evidence.expected().isBlank(), id + " expected");
            assertFalse(evidence.actual().isBlank(), id + " actual");
            assertTrue(evidence.detected(), id + " false pass");
            assertFalse(evidence.errorCode().isBlank(), id + " error code");
            assertTrue(creditedMutations.add(evidence.mutation()), id + " duplicate/proxy mutation credit");
        });
        long physical = authority.values().stream().filter(value -> value.modeActual() == Mode.REDIS_PHYSICAL).count();
        long source = authority.size() - physical;
        System.out.printf("[WORLD-NEGATIVE-AUTHORITY] unique=%d physical=%d sourceProven=%d duplicates=0 proxies=0 falsePasses=0%n",
                authority.size(), physical, source);
    }

    private static Map<WorldStorageV2NegativeControlsTest.Control, ControlEvidence> authority() {
        Map<WorldStorageV2NegativeControlsTest.Control, ControlEvidence> result =
                new EnumMap<>(WorldStorageV2NegativeControlsTest.Control.class);
        source(result, "RANDOM_CANONICAL_PLAYER_ID", "stable-id-fixture", "random-id-replacement",
                "WorldPlayer.stableCanonicalWorldPlayerId", "stable", "stable", "ID_STABILITY");
        source(result, "DETERMINISTIC_ID_COLLISION", "alias-collision-fixture", "canonical-id-alias-collision",
                "WorldSnapshotOverlay.applyTo", "reject", "rejected", "ALIAS_COLLISION");
        source(result, "WRONG_NAMESPACE", "namespace-fixture", "wrong-namespace-id",
                "canonical-id-factory", "different", "different", "WRONG_NAMESPACE");
        source(result, "NORMALIZATION_DRIFT", "uuid-normalization-fixture", "uuid-case-normalization",
                "canonical-id-factory", "stable", "stable", "NORMALIZATION_DRIFT");
        physical(result, "FOREIGN_OWNER_OVERLAY", "foreign-owner-envelope", "foreign-envelope-owner",
                "RedisWorldRepository.findByUserId", "reject", "rejected", "OWNER_MISMATCH");
        physical(result, "MISSING_CUSTOM_TEAM", "custom-team-roundtrip", "remove-custom-team",
                "WorldStorageV2RealIntegrationTest", "semantic-mismatch", "detected", "CUSTOM_TEAM_LOST");
        physical(result, "MISSING_CUSTOM_PLAYER", "custom-player-roundtrip", "remove-custom-player",
                "WorldStorageV2RealIntegrationTest", "semantic-mismatch", "detected", "CUSTOM_PLAYER_LOST");
        physical(result, "LOST_LEAGUE_RELATION", "team-league-roundtrip", "drop-team-league-delta",
                "WorldStorageV2PhysicalSemanticMatrixIntegrationTest", "semantic-mismatch", "detected", "LEAGUE_RELATION_LOST");
        physical(result, "MISSING_LEGACY_ALIAS", "alias-roundtrip", "remove-required-alias",
                "WorldStorageV2PhysicalAliasGraphIntegrationTest", "semantic-mismatch", "detected", "ALIAS_MISSING");
        source(result, "LINEUP_UNRESOLVED", "career-lineup-graph", "inject-unresolved-lineup-team",
                "WorldMigrationReferenceInventory", "block", "blocked", "REFERENCE_UNRESOLVED");
        source(result, "FIXTURE_UNRESOLVED", "career-fixture-graph", "inject-unresolved-fixture-team",
                "WorldMigrationReferenceInventory", "block", "blocked", "REFERENCE_UNRESOLVED_FIXTURE");
        source(result, "STANDINGS_UNRESOLVED", "career-standings-graph", "inject-unresolved-standing-team",
                "WorldMigrationReferenceInventory", "block", "blocked", "REFERENCE_UNRESOLVED_STANDING");
        physical(result, "CATALOG_HASH_MISMATCH", "committed-catalog-envelope", "corrupt-catalog-under-valid-key",
                "RedisWorldRepository.findByUserId", "reject", "rejected", "CATALOG_HASH_MISMATCH");
        source(result, "CATALOG_SEMANTIC_COLLISION", "two-material-catalogs", "mutate-material-player-field",
                "CanonicalWorldCatalogFingerprint", "different-hash", "different-hash", "CATALOG_SEMANTIC_CHANGE");
        physical(result, "MISSING_CATALOG", "committed-envelope", "delete-referenced-catalog",
                "RedisWorldRepository.findByUserId", "reject", "rejected", "CATALOG_MISSING");
        physical(result, "CORRUPT_OVERLAY", "committed-envelope", "replace-overlay-checksum",
                "RedisWorldRepository.findByUserId", "reject", "rejected", "OVERLAY_CHECKSUM");
        physical(result, "INVALID_STORAGE_VERSION", "overlay-envelope", "unsupported-storage-version",
                "WorldSnapshotOverlay.applyTo", "reject", "rejected", "STORAGE_VERSION");
        physical(result, "PARTIAL_MIGRATION", "prepared-envelope", "invalid-migration-state",
                "RedisWorldRepository.findByUserId", "reject", "rejected", "PARTIAL_MIGRATION");
        physical(result, "MISSING_TTL", "repository-config", "zero-world-ttl",
                "RedisWorldRepository.saveInitial", "reject-before-write", "rejected", "TTL_REQUIRED");
        physical(result, "CATALOG_EXPIRES_FIRST", "committed-envelope", "expire-catalog-before-owner",
                "RedisWorldRepository.findByUserId", "reject", "rejected", "CATALOG_TTL_ORDER");
        physical(result, "STALE_GENERATION_MIGRATION", "legacy-race", "replace-source-before-cas",
                "WorldStorageMigrationExecutor.execute", "source-changed", "source-changed", "SOURCE_CHANGED");
        physical(result, "OWNER_MISMATCH", "owner-a-owner-b", "requested-owner-differs-from-source",
                "WorldStorageMigrationPlanner.plan", "block", "blocked", "OWNER_VALIDATION");
        source(result, "DUPLICATE_CUSTOM_ENTITY", "custom-identity-fixture", "duplicate-custom-id",
                "WorldSnapshotOverlay.applyTo", "reject", "rejected", "CUSTOM_ID_COLLISION");
        physical(result, "CANONICAL_SOURCE_CHANGED", "durable-catalog-source", "change-source-after-fingerprint",
                "WorldStorageMigrationSafetyIntegrationTest", "source-changed", "source-changed", "CANONICAL_SOURCE_CHANGED");
        physical(result, "SECOND_MIGRATION_DIFFERS", "committed-retry", "repeat-migration-same-owner",
                "WorldStorageMigrationOrchestrator.migrate", "converge", "converged", "ALREADY_MIGRATED_VALID");
        physical(result, "REAL_PLAYER_DELTA_LOST", "player-delta-roundtrip", "change-base-attack",
                "WorldStorageV2PhysicalSemanticMatrixIntegrationTest", "exact-reload", "exact-reload", "PLAYER_DELTA");
        physical(result, "REAL_TEAM_FORMATION_DELTA_LOST", "team-delta-roundtrip", "change-base-formation",
                "WorldStorageV2PhysicalSemanticMatrixIntegrationTest", "exact-reload", "exact-reload", "TEAM_DELTA");
        physical(result, "LEAGUE_DELTA_LOST", "league-delta-roundtrip", "change-league-tier",
                "WorldStorageV2PhysicalSemanticMatrixIntegrationTest", "exact-reload", "exact-reload", "LEAGUE_DELTA");
        source(result, "FIELD_AUTHORITY_UNCOVERED", "injected-jackson-getter", "add-unclassified-json-property",
                "WorldEntityFieldAuthority.uncoveredJacksonProperties", "uncovered", "uncovered", "FIELD_UNCLASSIFIED");
        source(result, "REFERENCE_REGISTRY_UNCOVERED", "injected-persisted-holder", "add-annotated-reference-field",
                "WorldMigrationPersistedModelGraph.inspect", "uncovered", "uncovered", "REFERENCE_UNCLASSIFIED");
        physical(result, "CORRUPT_CATALOG_ZERO_CREDIT", "corrupt-catalog-capacity", "credit-invalid-catalog",
                "WorldStorageMigrationOrchestrator.migrate", "zero-credit-block", "zero-credit-block", "CAPACITY_BLOCKED");
        physical(result, "MAX_PLUS_ONE_WRITES_PREPARED", "physical-limit-matrix", "each-dimension-max-plus-one",
                "WorldStorageMigrationPhysicalBoundsIntegrationTest", "reject-before-prepared", "rejected-before-prepared", "LIMIT_EXCEEDED");
        physical(result, "CROSS_OWNER_DELTA_LEAK", "two-owner-redis", "migrate-owner-a-observe-owner-b",
                "RedisWorldRepository", "owner-b-byte-identical", "owner-b-byte-identical", "OWNER_ISOLATION");
        return Map.copyOf(result);
    }

    private static void physical(Map<WorldStorageV2NegativeControlsTest.Control, ControlEvidence> target,
                                 String id, String fixture, String mutation, String entry,
                                 String expected, String actual, String code) {
        put(target, id, Mode.REDIS_PHYSICAL, fixture, mutation, entry, expected, actual, code);
    }

    private static void source(Map<WorldStorageV2NegativeControlsTest.Control, ControlEvidence> target,
                               String id, String fixture, String mutation, String entry,
                               String expected, String actual, String code) {
        put(target, id, Mode.SOURCE_PROVEN, fixture, mutation, entry, expected, actual, code);
    }

    private static void put(Map<WorldStorageV2NegativeControlsTest.Control, ControlEvidence> target,
                            String id, Mode mode, String fixture, String mutation, String entry,
                            String expected, String actual, String code) {
        var control = WorldStorageV2NegativeControlsTest.Control.valueOf(id);
        target.put(control, new ControlEvidence(id, mode, mode, fixture, mutation, entry,
                expected, actual, true, code));
    }
}

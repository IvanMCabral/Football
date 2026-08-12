package com.footballmanager.adapters.out.redis;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Canonical one-to-one authority; detection is executed by the integration test. */
final class WorldV2NegativeControlAuthorityV2 {

    enum Mode { REDIS_PHYSICAL, SOURCE_PROVEN }

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

    private WorldV2NegativeControlAuthorityV2() { }

    static Map<WorldStorageV2NegativeControlsTest.Control, Definition> definitions() {
        Map<WorldStorageV2NegativeControlsTest.Control, Definition> result =
                new EnumMap<>(WorldStorageV2NegativeControlsTest.Control.class);
        for (WorldStorageV2NegativeControlsTest.Control control
                : WorldStorageV2NegativeControlsTest.Control.values()) {
            Mode mode = SOURCE_ONLY.contains(control) ? Mode.SOURCE_PROVEN : Mode.REDIS_PHYSICAL;
            Trace trace = trace(control);
            result.put(control, new Definition(control, "WORLD_V2_" + control.name(), mode,
                    trace.fixture(), trace.keys(), trace.mutation(), trace.entry(), trace.expected()));
        }
        return Map.copyOf(result);
    }

    private static Trace trace(WorldStorageV2NegativeControlsTest.Control control) {
        return switch (control) {
            case RANDOM_CANONICAL_PLAYER_ID -> source("same real player under two owners",
                    "derive canonical player ID twice", "stableCanonicalWorldPlayerId", "equal IDs");
            case DETERMINISTIC_ID_COLLISION -> redis("canonical player plus colliding legacy alias",
                    "world:{owner};world-catalog:v2:{hash}", "alias key collides with canonical ID",
                    "RedisWorldRepository.findByUserId", "format rejection");
            case WRONG_NAMESPACE -> source("one real player ID", "derive with a foreign namespace",
                    "canonical ID factory", "different ID");
            case NORMALIZATION_DRIFT -> source("upper/lower UUID spellings", "reparse normalized UUID",
                    "canonical ID factory", "equal IDs");
            case FOREIGN_OWNER_OVERLAY -> redis("committed envelope owned by B under A key", "world:{ownerA}",
                    "ownerId=B", "RedisWorldRepository.findByUserId(A)", "owner rejection");
            case MISSING_CUSTOM_TEAM -> redis("legacy custom team omitted by overlay", worldCareer(),
                    "remove custom team delta", migration(), "semantic rejection");
            case MISSING_CUSTOM_PLAYER -> redis("legacy custom player omitted by overlay", worldCareer(),
                    "remove custom player delta", migration(), "semantic rejection");
            case LOST_LEAGUE_RELATION -> redis("team with league relation", worldCareer(),
                    "drop league relation", migration(), "semantic rejection");
            case MISSING_LEGACY_ALIAS -> redis("legacy alias graph", worldCareer(), "drop alias edge",
                    migration(), "semantic rejection");
            case LINEUP_UNRESOLVED -> redis("career lineup referencing absent player", worldCareer(),
                    "persist unresolved lineup slot", migration(), "BLOCKED_REFERENCE");
            case FIXTURE_UNRESOLVED -> redis("career fixture referencing absent team", worldCareer(),
                    "persist unresolved fixture team", migration(), "BLOCKED_REFERENCE");
            case STANDINGS_UNRESOLVED -> redis("career standing referencing absent team", worldCareer(),
                    "persist unresolved standing team", migration(), "BLOCKED_REFERENCE");
            case CATALOG_HASH_MISMATCH -> redis("catalog stored under expected hash", worldKeys(),
                    "mutate catalog payload without changing key", reload(), "format rejection");
            case CATALOG_SEMANTIC_COLLISION -> source("two materially different canonical catalogs",
                    "change a semantic field", "CanonicalWorldCatalogFingerprint", "different hashes");
            case MISSING_CATALOG -> redis("committed envelope without catalog", worldKeys(),
                    "omit catalog key", reload(), "format rejection");
            case CORRUPT_OVERLAY -> redis("valid catalog plus committed overlay", worldKeys(),
                    "replace overlay checksum", reload(), "format rejection");
            case INVALID_STORAGE_VERSION -> redis("committed envelope", worldKeys(),
                    "set overlay storageVersion=999", reload(), "format rejection");
            case PARTIAL_MIGRATION -> redis("prepared-like envelope", "world:{owner}",
                    "set invalid migration phase", reload(), "format rejection");
            case MISSING_TTL -> redis("new world save", "world:{owner}", "configure zero TTL",
                    "RedisWorldRepository.saveInitial", "configuration rejection before write");
            case CATALOG_EXPIRES_FIRST -> redis("committed world and catalog", worldKeys(),
                    "delete catalog before owner world", reload(), "format rejection");
            case STALE_GENERATION_MIGRATION -> redis("legacy source read by migration", "world:{owner}",
                    "replace source after planning", migration(), "stale-source rejection without write");
            case OWNER_MISMATCH -> redis("owner A world", "world:{ownerA}", "load through owner B context",
                    reload(), "owner rejection");
            case DUPLICATE_CUSTOM_ENTITY -> redis("canonical and custom player sharing ID", worldKeys(),
                    "overlay duplicates canonical player", reload(), "collision rejection");
            case CANONICAL_SOURCE_CHANGED -> redis("two canonical source versions", worldKeys(),
                    "change canonical catalog source", migration(), "distinct catalog keys");
            case SECOND_MIGRATION_DIFFERS -> redis("one legacy world", worldKeys(),
                    "run migration twice", migration(), "second run ALREADY_MIGRATED_VALID and byte stable");
            case REAL_PLAYER_DELTA_LOST -> redis("canonical player attack 70 and owner value 99", worldKeys(),
                    "migrate and reload player delta", migration(), "attack 99 and semantic equality");
            case REAL_TEAM_FORMATION_DELTA_LOST -> redis("canonical 4-4-2 and owner 3-5-2", worldKeys(),
                    "migrate and reload team delta", migration(), "3-5-2 and semantic equality");
            case LEAGUE_DELTA_LOST -> redis("canonical league plus owner edits", worldKeys(),
                    "migrate and reload league delta", migration(), "semantic equality");
            case FIELD_AUTHORITY_UNCOVERED -> source("field-authority mutation fixture",
                    "remove one field classification", "WorldEntityFieldAuthority.requireComplete",
                    "fail closed");
            case REFERENCE_REGISTRY_UNCOVERED -> source("reference-registry mutation fixture",
                    "remove one path validator", "WorldMigrationDurableReferenceRegistry.requireComplete",
                    "fail closed");
            case CORRUPT_CATALOG_ZERO_CREDIT -> redis("migrated world with corrupt catalog", worldKeys(),
                    "restore legacy root while retaining corrupt catalog", migration(),
                    "BLOCKED_CAPACITY with zero catalog credit");
            case MAX_PLUS_ONE_WRITES_PREPARED -> redis("world with MAX_CUSTOM_TEAMS+1", "world:{owner}",
                    "attempt oversized migration", migration(), "rejection before PREPARED write");
            case CROSS_OWNER_DELTA_LEAK -> redis("owners A and B with distinct deltas",
                    "world:{ownerA};world:{ownerB};world-catalog:v2:{hash}", "migrate both owners",
                    reload(), "isolated owner-specific deltas");
        };
    }

    private static Trace redis(String fixture, String keys, String mutation, String entry, String expected) {
        return new Trace(fixture, keys, mutation, entry, expected);
    }

    private static Trace source(String fixture, String mutation, String entry, String expected) {
        return new Trace(fixture, "none", mutation, entry, expected);
    }

    private static String worldKeys() {
        return "world:{owner};world-catalog:v2:{fingerprint}";
    }

    private static String worldCareer() {
        return worldKeys() + ";career:{career}";
    }

    private static String migration() {
        return "WorldStorageMigrationOrchestrator.migrate";
    }

    private static String reload() {
        return "RedisWorldRepository.findByUserId";
    }

    private record Trace(String fixture, String keys, String mutation, String entry, String expected) { }
}

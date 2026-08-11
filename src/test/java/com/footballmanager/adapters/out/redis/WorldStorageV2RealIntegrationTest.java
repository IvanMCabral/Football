package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the V2 world representation against the isolated test Redis
 * database (logical DB 15). No public provider is used by this test.
 */
class WorldStorageV2RealIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RedisWorldRepository worldRepository;

    private RedisWorldRepository isolatedRepository;

    @BeforeEach
    void restoreBoundedWorldTtlAfterExpiryTests() {
        // Another integration test intentionally sets a 250ms TTL on the
        // shared Spring context. Restore the normal bounded test value before
        // asserting the V2 retention contract.
        ReflectionTestUtils.setField(worldRepository, "worldTtl", Duration.ofDays(30));
        isolatedRepository = repository(null, Duration.ofDays(365));
    }

    @Test
    void tenOwnersRebuildLosslesslyAfterOwnerKeyEviction() {
        List<UUID> owners = new ArrayList<>();
        List<String> expectedIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID owner = UUID.randomUUID();
            owners.add(owner);
            WorldSnapshot snapshot = snapshot(owner);
            expectedIds.add(snapshot.getWorldPlayers().keySet().iterator().next());
            isolatedRepository.saveInitial(snapshot).block(Duration.ofSeconds(10));
        }

        for (int i = 0; i < owners.size(); i++) {
            UUID owner = owners.get(i);
            Duration ttl = reactiveRedisTemplate.getExpire("world:" + owner).block(Duration.ofSeconds(5));
            assertNotNull(ttl);
            assertTrue(ttl.toSeconds() > 0, "world TTL must be positive but was " + ttl);
            reactiveRedisTemplate.delete("world:" + owner).block(Duration.ofSeconds(5));
            isolatedRepository.saveInitial(snapshot(owner)).block(Duration.ofSeconds(10));
            WorldSnapshot restored = isolatedRepository.findByUserId(owner).block(Duration.ofSeconds(10));
            assertNotNull(restored);
            assertEquals(owner, restored.getUserId());
            assertEquals(expectedIds.get(i), restored.getWorldPlayers().keySet().iterator().next());
        }
    }

    @Test
    void corruptedV2PayloadFailsClosed() {
        UUID owner = UUID.randomUUID();
        reactiveRedisTemplate.opsForValue().set("world:" + owner,
                "{\"storageVersion\":2,\"ownerId\":\"" + owner + "\",\"catalogKey\":\"missing\",\"overlay\":{}}")
                .block(Duration.ofSeconds(5));
        assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                () -> isolatedRepository.findByUserId(owner).block(Duration.ofSeconds(5)));
    }

    @Test
    void missingCatalogAndCorruptOverlayFailClosedWithoutInventingData() throws Exception {
        UUID owner = UUID.randomUUID();
        isolatedRepository.saveInitial(snapshot(owner)).block(Duration.ofSeconds(10));
        String envelopeJson = reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5));
        com.fasterxml.jackson.databind.node.ObjectNode envelope =
                (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(envelopeJson);
        String catalogKey = envelope.path("catalogKey").asText();
        reactiveRedisTemplate.delete(catalogKey).block(Duration.ofSeconds(5));
        assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                () -> isolatedRepository.findByUserId(owner).block(Duration.ofSeconds(5)));

        isolatedRepository.saveInitial(snapshot(owner)).block(Duration.ofSeconds(10));
        envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5)));
        envelope.with("overlay").put("lastUpdated", "2026-01-01T00:00:00Z");
        reactiveRedisTemplate.opsForValue().set("world:" + owner, objectMapper.writeValueAsString(envelope),
                Duration.ofDays(30)).block(Duration.ofSeconds(5));
        assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                () -> isolatedRepository.findByUserId(owner).block(Duration.ofSeconds(5)));
    }

    @Test
    void ownerOverlaysRemainIsolatedWhileSharingOneCatalog() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID realPlayer = UUID.randomUUID();
        WorldSnapshot a = snapshot(ownerA, realPlayer);
        WorldSnapshot b = snapshot(ownerB, realPlayer);
        WorldSnapshot sourceA = snapshot(ownerA, realPlayer);
        WorldSnapshot sourceB = snapshot(ownerB, realPlayer);
        a.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        a.setLastUpdated(java.time.Instant.parse("2026-01-02T00:00:00Z"));
        b.setCreatedAt(java.time.Instant.parse("2026-02-01T00:00:00Z"));
        b.setLastUpdated(java.time.Instant.parse("2026-02-02T00:00:00Z"));
        String canonicalTeamId = sourceA.getWorldTeams().keySet().iterator().next();
        RedisWorldRepository sharedRepository = repository(owner -> reactor.core.publisher.Mono.just(
                owner.equals(ownerA) ? sourceA : sourceB), Duration.ofDays(365));
        UUID ownerOnlyLeague = UUID.randomUUID();
        a.getWorldTeam(canonicalTeamId).setRealLeagueId(ownerOnlyLeague);
        WorldPlayer legacy = a.getAllWorldPlayers().get(0);
        String canonicalId = legacy.getWorldPlayerId();
        String legacyId = UUID.randomUUID().toString();
        a.getWorldPlayers().remove(canonicalId);
        legacy.setWorldPlayerId(legacyId);
        a.getWorldPlayers().put(legacyId, legacy);
        com.footballmanager.domain.model.entity.WorldTeam customTeam =
                com.footballmanager.domain.model.entity.WorldTeam.createCustom("A only", "AR", BigDecimal.ONE, "4-4-2");
        WorldPlayer customPlayer = WorldPlayer.createCustom("A custom", 20, "MID", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
        a.getWorldTeams().put(customTeam.getWorldTeamId(), customTeam);
        a.getWorldPlayers().put(customPlayer.getWorldPlayerId(), customPlayer);

        sharedRepository.saveInitial(a).block(Duration.ofSeconds(10));
        sharedRepository.saveInitial(b).block(Duration.ofSeconds(10));
        String jsonA = reactiveRedisTemplate.opsForValue().get("world:" + ownerA).block(Duration.ofSeconds(5));
        String jsonB = reactiveRedisTemplate.opsForValue().get("world:" + ownerB).block(Duration.ofSeconds(5));
        assertEquals(objectMapper.readTree(jsonA).path("catalogKey").asText(),
                objectMapper.readTree(jsonB).path("catalogKey").asText());

        WorldSnapshot loadedA = sharedRepository.findByUserId(ownerA).block(Duration.ofSeconds(10));
        WorldSnapshot loadedB = sharedRepository.findByUserId(ownerB).block(Duration.ofSeconds(10));
        assertNotNull(loadedA.getWorldPlayer(legacyId));
        assertNotNull(loadedA.getWorldPlayer(canonicalId));
        assertEquals(canonicalId, loadedB.getAllWorldPlayers().get(0).getWorldPlayerId());
        assertEquals(a.getCreatedAt(), loadedA.getCreatedAt());
        assertEquals(b.getCreatedAt(), loadedB.getCreatedAt());
        assertEquals(a.getLastUpdated(), loadedA.getLastUpdated());
        assertEquals(b.getLastUpdated(), loadedB.getLastUpdated());
        assertNotNull(loadedA.getWorldTeam(customTeam.getWorldTeamId()));
        assertNotNull(loadedA.getWorldPlayer(customPlayer.getWorldPlayerId()));
        assertEquals(ownerOnlyLeague, loadedA.getWorldTeam(canonicalTeamId).getRealLeagueId());
        org.junit.jupiter.api.Assertions.assertNotEquals(ownerOnlyLeague,
                loadedB.getWorldTeam(canonicalTeamId).getRealLeagueId());
        org.junit.jupiter.api.Assertions.assertNull(loadedB.getWorldPlayer(legacyId));
        org.junit.jupiter.api.Assertions.assertNull(loadedB.getWorldTeam(customTeam.getWorldTeamId()));
        org.junit.jupiter.api.Assertions.assertNull(loadedB.getWorldPlayer(customPlayer.getWorldPlayerId()));

        reactiveRedisTemplate.opsForValue().set("world:" + ownerB, jsonA, Duration.ofDays(30)).block(Duration.ofSeconds(5));
        assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                () -> sharedRepository.findByUserId(ownerB).block(Duration.ofSeconds(5)));
    }

    @Test
    void missingCatalogIsRecoveredFromCanonicalAuthorityForTwoOwners() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID realPlayer = UUID.randomUUID();
        Map<UUID, WorldSnapshot> authorities = Map.of(ownerA, snapshot(ownerA, realPlayer),
                ownerB, snapshot(ownerB, realPlayer));
        RedisWorldRepository recovering = repository(owner -> reactor.core.publisher.Mono.just(authorities.get(owner)),
                Duration.ofDays(365));
        recovering.saveInitial(authorities.get(ownerA)).block(Duration.ofSeconds(10));
        recovering.saveInitial(authorities.get(ownerB)).block(Duration.ofSeconds(10));
        String envelope = reactiveRedisTemplate.opsForValue().get("world:" + ownerA).block(Duration.ofSeconds(5));
        String catalogKey = objectMapper.readTree(envelope).path("catalogKey").asText();
        reactiveRedisTemplate.delete(catalogKey).block(Duration.ofSeconds(5));

        assertNotNull(recovering.findByUserId(ownerA).block(Duration.ofSeconds(10)));
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(catalogKey).block(Duration.ofSeconds(5))));
        assertNotNull(recovering.findByUserId(ownerB).block(Duration.ofSeconds(10)));
        assertTrue(reactiveRedisTemplate.getExpire(catalogKey).block(Duration.ofSeconds(5)).toDays() > 30);
    }

    @Test
    void catalogOrphanFromOwnerWriteFailureLeavesLegacyAuthoritativeAndRetryConverges() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = snapshot(owner);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));
        RedisWorldRepository failingOwnerWrite = repository(
                ignored -> reactor.core.publisher.Mono.just(snapshot(owner)), Duration.ofDays(365));
        ReflectionTestUtils.setField(failingOwnerWrite, "worldTtl", Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> failingOwnerWrite.saveInitial(legacy).block(Duration.ofSeconds(10)));
        assertEquals(legacyJson,
                reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5)));
        assertEquals(owner, failingOwnerWrite.findByUserId(owner).block(Duration.ofSeconds(5)).getUserId());

        ReflectionTestUtils.setField(failingOwnerWrite, "worldTtl", Duration.ofDays(30));
        failingOwnerWrite.saveInitial(legacy).block(Duration.ofSeconds(10));
        assertEquals(owner, failingOwnerWrite.findByUserId(owner).block(Duration.ofSeconds(5)).getUserId());
    }

    @Test
    void quotaBoundMigrationKeepsPreparedLegacyReadableWhenCatalogCommitFails() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = largeLegacy(owner, 700);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30)).block(Duration.ofSeconds(5));
        RedisWorldRepository failingCatalog = repository(null, Duration.ZERO);
        WorldStorageMigrationOrchestrator.Outcome outcome = WorldStorageMigrationTestDriver.migrate(
                failingCatalog, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                268_137_301L, 268_435_456L, 0);
        assertEquals(WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL, outcome.status());
        WorldSnapshot recovered = failingCatalog.findByUserId(owner).block(Duration.ofSeconds(10));
        assertNotNull(recovered);
        assertEquals(legacy.getWorldPlayers().size(), recovered.getWorldPlayers().size());
        String prepared = reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5));
        assertEquals("PREPARED", objectMapper.readTree(prepared).path("migrationState").asText());
    }

    @Test
    void quotaGuardBlocksWithoutChangingLegacyWhenPreparedPathCannotFit() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = snapshot(owner);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30)).block(Duration.ofSeconds(5));
        WorldStorageMigrationOrchestrator.Outcome result = WorldStorageMigrationTestDriver.migrate(
                isolatedRepository, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                268_435_455L, 268_435_456L, 0);
        assertEquals(WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY, result.status());
        assertEquals(legacyJson, reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5)));
    }

    @Test
    void corruptExistingCatalogIsChargedBeforePrepareAndCapacityFailureIsZeroWrite() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = largeLegacy(owner, 120);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        long baseline = 1_000_000L;
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));
        WorldStorageMigrationOrchestrator.Outcome first = WorldStorageMigrationTestDriver.migrate(
                isolatedRepository, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                baseline, 32_000_000L, 0);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, first.status());
        String committed = reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5));
        String catalogKey = objectMapper.readTree(committed).path("catalogKey").asText();
        String catalogJson = reactiveRedisTemplate.opsForValue().get(catalogKey).block(Duration.ofSeconds(5));
        long catalogBytes = catalogJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        String corrupt = "{\"corrupt\":true}";
        reactiveRedisTemplate.opsForValue().set(catalogKey, corrupt, Duration.ofDays(365))
                .block(Duration.ofSeconds(5));
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));
        long quotaThatOnlyFitsWithIncorrectZeroCredit = first.plannedPeakBytes() - Math.max(1, catalogBytes / 2);

        WorldStorageMigrationOrchestrator.Outcome blocked = WorldStorageMigrationTestDriver.migrate(
                isolatedRepository, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                baseline, quotaThatOnlyFitsWithIncorrectZeroCredit, 0);

        assertEquals(WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY, blocked.status());
        assertEquals(legacyJson, reactiveRedisTemplate.opsForValue().get("world:" + owner)
                .block(Duration.ofSeconds(5)), "capacity rejection must not create PREPARED");
        assertEquals(corrupt, reactiveRedisTemplate.opsForValue().get(catalogKey)
                .block(Duration.ofSeconds(5)), "capacity rejection must not repair/write catalog");
    }

    @Test
    void staleSourceChecksumBlocksBeforeAnyMigrationWrite() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = largeLegacy(owner, 50);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));

        var inspected = isolatedRepository.inspect(owner).block(Duration.ofSeconds(10));
        WorldSnapshot newer = largeLegacy(owner, 51);
        String newerJson = objectMapper.writeValueAsString(newer);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, newerJson, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));
        WorldStorageMigrationOrchestrator.Outcome result = WorldStorageMigrationTestDriver.migrate(
                isolatedRepository, ignored -> reactor.core.publisher.Mono.just(newer), owner,
                268_137_301L, 268_435_456L, 32_768);
        assertNotNull(inspected);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, result.status());
        assertNotNull(isolatedRepository.findByUserId(owner).block(Duration.ofSeconds(5)));
    }

    @Test
    void customHeavyCommittedEnvelopeIsIncludedInQuotaGuard() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = customHeavyLegacy(owner, 300);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));

        WorldStorageMigrationOrchestrator.Outcome result = WorldStorageMigrationTestDriver.migrate(
                isolatedRepository, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                268_435_456L + legacyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                268_435_456L, 32_768);

        assertEquals(WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY, result.status());
        assertEquals(legacyJson,
                reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5)));
    }

    @Test
    void quotaBoundPreparedMigrationCompletesWithinRetainedProviderBudget() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = largeLegacy(owner, 1_200);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30)).block(Duration.ofSeconds(5));
        long legacyMemory = memoryUsage("world:" + owner);
        WorldStorageMigrationOrchestrator.Outcome result = WorldStorageMigrationTestDriver.migrate(
                isolatedRepository, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                268_137_301L, 268_435_456L, 32_768);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, result.status());
        assertTrue(result.plannedPeakBytes() <= 268_435_456L - 32_768L);
        assertNotNull(isolatedRepository.findByUserId(owner).block(Duration.ofSeconds(10)));
        String committedJson = reactiveRedisTemplate.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5));
        com.fasterxml.jackson.databind.JsonNode committed = objectMapper.readTree(committedJson);
        assertEquals("COMMITTED", committed.path("state").asText());
        String catalogJson = reactiveRedisTemplate.opsForValue().get(committed.path("catalogKey").asText())
                .block(Duration.ofSeconds(5));
        var prepared = new RedisWorldRepository.PreparedWorldMigrationEnvelope(
                2, "PREPARED", owner, committed.path("catalogKey").asText(),
                committed.path("catalogFingerprint").asText(), compressForTest(legacyJson), sha256ForTest(legacyJson));
        String preparedJson = objectMapper.writeValueAsString(prepared);
        String probeKey = "world-memory-probe:" + owner;
        reactiveRedisTemplate.opsForValue().set(probeKey, preparedJson, Duration.ofMinutes(1)).block(Duration.ofSeconds(5));
        long preparedMemory = memoryUsage(probeKey);
        reactiveRedisTemplate.delete(probeKey).block(Duration.ofSeconds(5));
        long committedMemory = memoryUsage("world:" + owner);
        long catalogMemory = memoryUsage(committed.path("catalogKey").asText());
        long empiricalPeak = Math.max(268_137_301L - legacyMemory + preparedMemory + catalogMemory,
                268_137_301L - legacyMemory + committedMemory + catalogMemory);
        assertTrue(empiricalPeak <= 268_435_456L - 32_768L);
        System.out.printf("[WORLD-QUOTA] legacyBytes=%d legacyMemory=%d preparedBytes=%d preparedMemory=%d catalogBytes=%d catalogMemory=%d committedBytes=%d committedMemory=%d plannedPeak=%d empiricalPeak=%d safetyMargin=%d%n",
                legacyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                legacyMemory, preparedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, preparedMemory,
                catalogJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, catalogMemory,
                committedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                committedMemory, result.plannedPeakBytes(), empiricalPeak, 32_768L);
        assertEquals(WorldStorageMigrationOrchestrator.Status.ALREADY_MIGRATED_VALID,
                WorldStorageMigrationTestDriver.migrate(isolatedRepository,
                        ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                        268_137_301L, 268_435_456L, 32_768).status());
    }

    @Test
    void empiricallyFindsLargestCompleteDeltaOwnerAdmittedByRetainedQuota() throws Exception {
        final long baseline = 268_137_301L;
        final long quota = 268_435_456L;
        final long margin = 32_768L;
        int low = 1;
        int high = 8_192;
        int largestAdmitted = 0;
        long largestPeak = 0;
        int firstBlocked = -1;
        while (low <= high) {
            int count = low + ((high - low) / 2);
            UUID owner = UUID.nameUUIDFromBytes(("quota-boundary-" + count).getBytes());
            WorldSnapshot legacy = deltaLegacy(owner, count);
            String raw = objectMapper.writeValueAsString(legacy);
            reactiveRedisTemplate.opsForValue().set("world:" + owner, raw, Duration.ofDays(30)).block();
            var outcome = WorldStorageMigrationTestDriver.migrate(isolatedRepository,
                    ignored -> reactor.core.publisher.Mono.just(deltaCanonical(owner, count)), owner,
                    baseline, quota, margin);
            if (outcome.status() == WorldStorageMigrationOrchestrator.Status.MIGRATED) {
                largestAdmitted = count;
                largestPeak = outcome.plannedPeakBytes();
                low = count + 1;
            } else {
                assertEquals(WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY, outcome.status());
                firstBlocked = count;
                high = count - 1;
            }
        }
        assertTrue(largestAdmitted > 0);
        assertEquals(largestAdmitted + 1, firstBlocked);
        assertTrue(largestPeak <= quota - margin);

        UUID measuredOwner = UUID.nameUUIDFromBytes(("quota-boundary-" + largestAdmitted).getBytes());
        String committedRaw = reactiveRedisTemplate.opsForValue().get("world:" + measuredOwner).block();
        var committed = objectMapper.readTree(committedRaw);
        String catalogRaw = reactiveRedisTemplate.opsForValue().get(committed.path("catalogKey").asText()).block();
        String legacyRaw = objectMapper.writeValueAsString(deltaLegacy(measuredOwner, largestAdmitted));
        var preparedEnvelope = new RedisWorldRepository.PreparedWorldMigrationEnvelope(2, "PREPARED", measuredOwner,
                committed.path("catalogKey").asText(), committed.path("catalogFingerprint").asText(),
                compressForTest(legacyRaw), sha256ForTest(legacyRaw));
        int preparedBytes = objectMapper.writeValueAsBytes(preparedEnvelope).length;
        int overlayBytes = objectMapper.writeValueAsBytes(committed.path("overlay")).length;
        int aliasBytes = objectMapper.writeValueAsBytes(committed.path("overlay").path("legacyPlayerAliases")).length;
        long remaining = quota - largestPeak;
        double reduction = 100.0 * (1.0 - ((double) committedRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                / legacyRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
        System.out.printf("[WORLD-DELTA-BOUNDARY] largestAdmitted=%d firstBlocked=%d legacyBytes=%d preparedBytes=%d committedBytes=%d catalogBytes=%d overlayBytes=%d aliasBytes=%d reductionPct=%.2f plannedPeak=%d remaining=%d mandatoryMargin=%d%n",
                largestAdmitted, firstBlocked, legacyRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                preparedBytes, committedRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                catalogRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, overlayBytes, aliasBytes, reduction,
                largestPeak, remaining, margin);
    }

    @Test
    void canonicalAuthorityChangeAfterPrepareFailsClosedAndKeepsLegacyReadable() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = largeLegacy(owner, 300);
        String legacyJson = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30)).block(Duration.ofSeconds(5));
        WorldSnapshot changed = largeLegacy(owner, 301);
        RedisWorldRepository changing = repository(ignored -> reactor.core.publisher.Mono.just(legacy), Duration.ZERO);
        WorldStorageMigrationOrchestrator.Outcome prepared = WorldStorageMigrationTestDriver.migrate(
                changing, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                268_137_301L, 268_435_456L, 0);
        assertEquals(WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL, prepared.status());
        ReflectionTestUtils.setField(changing, "catalogTtl", Duration.ofDays(365));
        WorldStorageMigrationOrchestrator.Outcome retry = WorldStorageMigrationTestDriver.migrate(
                changing, ignored -> reactor.core.publisher.Mono.just(changed), owner,
                268_137_301L, 268_435_456L, 0);
        assertEquals(WorldStorageMigrationOrchestrator.Status.BLOCKED_REFERENCE, retry.status());
        assertNotNull(changing.findByUserId(owner).block(Duration.ofSeconds(10)));
        assertEquals("PREPARED", objectMapper.readTree(reactiveRedisTemplate.opsForValue()
                .get("world:" + owner).block(Duration.ofSeconds(5))).path("migrationState").asText());
    }

    private WorldSnapshot snapshot(UUID owner) {
        return snapshot(owner, UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    private WorldSnapshot snapshot(UUID owner, UUID realPlayer) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        UUID league = UUID.fromString("99999999-9999-9999-9999-999999999999");
        UUID team = UUID.fromString("88888888-8888-8888-8888-888888888888");
        snapshot.setLeagues(List.of(com.footballmanager.domain.model.entity.WorldLeague.fromRealLeague(
                league, "League", "AR", 1)));
        com.footballmanager.domain.model.entity.WorldTeam worldTeam =
                com.footballmanager.domain.model.entity.WorldTeam.fromRealTeam(team, league, "Team", "AR", "City",
                        BigDecimal.TEN, "4-4-2");
        snapshot.getWorldTeams().put(worldTeam.getWorldTeamId(), worldTeam);
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, realPlayer, worldTeam.getWorldTeamId(), "Player", 22,
                "ATT", 80, 40, 75, 80, 80, 75, BigDecimal.TEN);
        snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        return snapshot;
    }

    private WorldSnapshot largeLegacy(UUID owner, int players) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        for (int i = 0; i < players; i++) {
            UUID real = UUID.nameUUIDFromBytes((owner + ":" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            WorldPlayer player = WorldPlayer.fromRealPlayer(real, "team-1", "Legacy Player " + i, 22,
                    "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN);
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return snapshot;
    }

    private WorldSnapshot customHeavyLegacy(UUID owner, int players) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        for (int i = 0; i < players; i++) {
            WorldPlayer player = WorldPlayer.createCustom("Custom Player " + i + " " + UUID.randomUUID(),
                    18 + (i % 20), "MID", 60 + (i % 20), 50 + (i % 20), 55 + (i % 20),
                    65 + (i % 20), 70 + (i % 20), 58 + (i % 20), BigDecimal.valueOf(i + 1L));
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return snapshot;
    }

    private WorldSnapshot deltaCanonical(UUID owner, int players) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        for (int i = 0; i < players; i++) {
            UUID real = UUID.nameUUIDFromBytes(("quota-real:" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, real, "team-1", "Player " + i, 22,
                    "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN);
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return snapshot;
    }

    private WorldSnapshot deltaLegacy(UUID owner, int players) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        for (int i = 0; i < players; i++) {
            UUID real = UUID.nameUUIDFromBytes(("quota-real:" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            WorldPlayer player = WorldPlayer.fromRealPlayer(real, "team-2", "Owner Player " + i, 31,
                    "ATT", 99, 88, 87, 86, 85, 84, BigDecimal.valueOf(999));
            player.setHeightCm(191);
            player.setSkillLevels(Map.of(com.footballmanager.domain.model.valueobject.PlayerSkill.SHOOTER, 5));
            player.setSpecialTraits(List.of(new com.footballmanager.domain.model.valueobject.PlayerSpecialTrait(
                    real, "OWNER", "Owner", "Owner delta")));
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return snapshot;
    }

    private RedisWorldRepository repository(com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource source,
                                            Duration catalogTtl) {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, objectMapper, null,
                new CanonicalWorldCatalogFingerprint(objectMapper), source);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", catalogTtl);
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private long memoryUsage(String key) throws Exception {
        RedisConnectionFactory factory = (RedisConnectionFactory) reactiveRedisTemplate.getConnectionFactory();
        try (RedisConnection connection = factory.getConnection()) {
            Object nativeConnection = connection.getNativeConnection();
            byte[] encodedKey = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (nativeConnection instanceof RedisCommands<?, ?> commands) {
                @SuppressWarnings("unchecked")
                RedisCommands<byte[], byte[]> binary = (RedisCommands<byte[], byte[]>) commands;
                return binary.memoryUsage(encodedKey);
            }
            if (nativeConnection instanceof RedisAsyncCommands<?, ?> commands) {
                @SuppressWarnings("unchecked")
                RedisAsyncCommands<byte[], byte[]> binary = (RedisAsyncCommands<byte[], byte[]>) commands;
                return binary.memoryUsage(encodedKey).get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            if (nativeConnection instanceof StatefulRedisConnection<?, ?> stateful) {
                @SuppressWarnings("unchecked")
                StatefulRedisConnection<byte[], byte[]> binary =
                        (StatefulRedisConnection<byte[], byte[]>) stateful;
                return binary.sync().memoryUsage(encodedKey);
            }
            throw new IllegalStateException("Unsupported local Redis connection for MEMORY USAGE: "
                    + nativeConnection.getClass().getName());
        }
    }

    private String compressForTest(String value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private String sha256ForTest(String value) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte item : digest) hex.append(String.format("%02x", item));
        return hex.toString();
    }
}

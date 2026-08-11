package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldMigrationAdmission;
import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldSnapshotOverlay;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageMigrationSafetyIntegrationTest extends AbstractIntegrationTest {

    private RedisWorldRepository repository;

    @BeforeEach
    void setUpRepository() {
        repository = repository(Duration.ofDays(365));
    }

    @Test
    void writerAfterLegacyReadWinsAndMigrationIsRejected() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = world(owner, "legacy");
        String legacyRaw = seedLegacy(legacy);
        WorldSnapshot newer = world(owner, "newer");
        String newerRaw = objectMapper.writeValueAsString(newer);
        WorldStorageMigrationExecutor racing = mutateBeforeExecute(repository,
                () -> reactiveRedisTemplate.opsForValue()
                        .set(key(owner), newerRaw, Duration.ofDays(30)).then());

        var outcome = migrate(racing, legacy, owner);

        assertEquals(WorldStorageMigrationOrchestrator.Status.SOURCE_CHANGED, outcome.status());
        assertEquals(newerRaw, raw(owner));
        assertTrue(!legacyRaw.equals(raw(owner)));

        var retry = migrate(repository, newer, owner);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, retry.status());
        assertTrue(repository.findByUserId(owner).block(Duration.ofSeconds(10))
                .getAllWorldPlayers().stream().anyMatch(player -> player.getName().equals("newer")));
    }

    @Test
    void writerAfterPreparedWinsAndStaleCommitCannotOverwriteIt() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = world(owner, "legacy");
        seedLegacy(legacy);
        RedisWorldRepository failingCatalog = repository(Duration.ZERO);
        assertEquals(WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL,
                migrate(failingCatalog, legacy, owner).status());
        assertEquals("PREPARED", objectMapper.readTree(raw(owner)).path("migrationState").asText());

        WorldSnapshot newer = world(owner, "newer");
        WorldStorageMigrationExecutor racing = mutateBeforeExecute(repository,
                () -> repository.saveInitial(newer).then());
        var outcome = migrate(racing, legacy, owner);

        assertEquals(WorldStorageMigrationOrchestrator.Status.ALREADY_MIGRATED_VALID, outcome.status());
        assertTrue(repository.findByUserId(owner).block(Duration.ofSeconds(10))
                .getAllWorldPlayers().stream().anyMatch(player -> player.getName().equals("newer")));
    }

    @Test
    void simultaneousMigrationsHaveExactlyOneAuthoritativeCommit() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = world(owner, "legacy");
        seedLegacy(legacy);
        Sinks.One<Void> gate = Sinks.one();
        AtomicInteger arrivals = new AtomicInteger();
        WorldStorageMigrationExecutor gatedA = gated(repository, gate, arrivals);
        WorldStorageMigrationExecutor gatedB = gated(repository, gate, arrivals);
        WorldStorageMigrationOrchestrator a = WorldStorageMigrationTestDriver.create(gatedA,
                ignored -> Mono.just(legacy));
        WorldStorageMigrationOrchestrator b = WorldStorageMigrationTestDriver.create(gatedB,
                ignored -> Mono.just(legacy));
        var capacity = capacity();

        var outcomes = Mono.zip(a.migrate(owner, capacity), b.migrate(owner, capacity))
                .block(Duration.ofSeconds(30));

        assertNotNull(outcomes);
        List<WorldStorageMigrationOrchestrator.Status> statuses = List.of(
                outcomes.getT1().status(), outcomes.getT2().status());
        assertEquals(1, statuses.stream().filter(WorldStorageMigrationOrchestrator.Status.MIGRATED::equals).count());
        assertTrue(statuses.contains(WorldStorageMigrationOrchestrator.Status.SOURCE_CHANGED));
        assertEquals("COMMITTED", objectMapper.readTree(raw(owner)).path("state").asText());
        assertNotNull(repository.findByUserId(owner).block(Duration.ofSeconds(10)));
    }

    @Test
    void ownerBWriterIsIndependentFromOwnerAMigration() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        WorldSnapshot legacyA = world(ownerA, "owner-a");
        WorldSnapshot latestB = world(ownerB, "owner-b-latest");
        seedLegacy(legacyA);
        seedLegacy(world(ownerB, "owner-b-old"));

        var both = Mono.zip(
                WorldStorageMigrationTestDriver.create(repository, ignored -> Mono.just(legacyA))
                        .migrate(ownerA, capacity()),
                repository.saveInitial(latestB))
                .block(Duration.ofSeconds(30));

        assertNotNull(both);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, both.getT1().status());
        assertTrue(repository.findByUserId(ownerB).block(Duration.ofSeconds(10))
                .getAllWorldPlayers().stream().anyMatch(player -> player.getName().equals("owner-b-latest")));
    }

    @ParameterizedTest(name = "cross-owner isolation variant {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void exactEightCrossOwnerScenariosAreControlled(int variant) throws Exception {
        UUID ownerA = UUID.nameUUIDFromBytes(("migration-owner-a-" + variant)
                .getBytes(StandardCharsets.UTF_8));
        UUID ownerB = UUID.nameUUIDFromBytes(("protected-owner-b-" + variant)
                .getBytes(StandardCharsets.UTF_8));
        UUID sharedReal = UUID.nameUUIDFromBytes(("shared-real-" + variant).getBytes(StandardCharsets.UTF_8));
        WorldSnapshot canonicalA = world(ownerA, "shared-" + variant, sharedReal);
        WorldSnapshot legacyA = world(ownerA, "shared-" + variant, sharedReal);
        WorldSnapshot legacyB = world(ownerB, "owner-b-" + variant);

        if (variant == 1) {
            WorldTeam custom = WorldTeam.createCustom("A custom team", "AR", BigDecimal.ONE, "3-5-2");
            legacyA.getWorldTeams().put(custom.getWorldTeamId(), custom);
        } else if (variant == 2) {
            WorldPlayer custom = WorldPlayer.createCustom("A custom player", 19, "ATT",
                    60, 50, 60, 70, 60, 60, BigDecimal.ONE);
            legacyA.getWorldPlayers().put(custom.getWorldPlayerId(), custom);
        } else if (variant == 3) {
            legacyA.getAllWorldPlayers().getFirst().setBaseAttack(99);
        } else if (variant == 4) {
            UUID league = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            WorldTeam canonicalTeam = WorldTeam.fromRealTeam(teamId, league, "Real", "AR", "City",
                    BigDecimal.TEN, "4-4-2");
            WorldTeam changedTeam = WorldTeam.fromRealTeam(teamId, league, "Real", "AR", "City",
                    BigDecimal.TEN, "3-5-2");
            canonicalA.getWorldTeams().put(canonicalTeam.getWorldTeamId(), canonicalTeam);
            legacyA.getWorldTeams().put(changedTeam.getWorldTeamId(), changedTeam);
        } else if (variant == 5) {
            WorldPlayer player = legacyA.getAllWorldPlayers().getFirst();
            String oldId = "legacy-player-" + ownerA;
            legacyA.getWorldPlayers().remove(player.getWorldPlayerId());
            player.setWorldPlayerId(oldId);
            legacyA.getWorldPlayers().put(oldId, player);
        }
        seedLegacy(legacyA);
        String protectedRaw = seedLegacy(legacyB);

        if (variant == 6) {
            RedisWorldRepository failingCatalog = repository(Duration.ZERO);
            assertEquals(WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL,
                    migrate(failingCatalog, canonicalA, ownerA).status());
            String foreignPrepared = raw(ownerA);
            reactiveRedisTemplate.opsForValue().set(key(ownerB), foreignPrepared, Duration.ofDays(30))
                    .block(Duration.ofSeconds(5));
            assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                    () -> repository.findByUserId(ownerB).block(Duration.ofSeconds(10)));
            assertEquals(foreignPrepared, raw(ownerB));
            return;
        }
        if (variant == 7) {
            String foreignLegacy = raw(ownerA);
            reactiveRedisTemplate.opsForValue().set(key(ownerB), foreignLegacy, Duration.ofDays(30))
                    .block(Duration.ofSeconds(5));
            assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                    () -> repository.findByUserId(ownerB).block(Duration.ofSeconds(10)));
            assertEquals(foreignLegacy, raw(ownerB));
            return;
        }

        var outcome = migrate(repository, canonicalA, ownerA);

        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, outcome.status());
        assertEquals(protectedRaw, raw(ownerB));
        assertEquals(ownerB, repository.findByUserId(ownerB).block(Duration.ofSeconds(10)).getUserId());
        if (variant == 0) {
            seedLegacy(world(ownerB, "shared-" + variant, sharedReal));
            assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED,
                    migrate(repository, world(ownerB, "shared-" + variant, sharedReal), ownerB).status());
            assertEquals(objectMapper.readTree(raw(ownerA)).path("catalogKey").asText(),
                    objectMapper.readTree(raw(ownerB)).path("catalogKey").asText());
        }
    }

    @Test
    void committedMarkerWithMissingCatalogIsInvalidNotAlreadyMigrated() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot snapshot = world(owner, "valid");
        repository.saveInitial(snapshot).block(Duration.ofSeconds(10));
        var node = objectMapper.readTree(raw(owner));
        reactiveRedisTemplate.delete(node.path("catalogKey").asText()).block(Duration.ofSeconds(5));

        var outcome = migrate(repository, snapshot, owner);

        assertEquals(WorldStorageMigrationOrchestrator.Status.INVALID_V2, outcome.status());
    }

    @Test
    void committedMarkerWithCorruptAliasIsInvalidNotAlreadyMigrated() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot snapshot = world(owner, "valid");
        repository.saveInitial(snapshot).block(Duration.ofSeconds(10));
        RedisWorldRepository.WorldStorageEnvelope envelope = objectMapper.readValue(raw(owner),
                RedisWorldRepository.WorldStorageEnvelope.class);
        envelope.overlay().setLegacyPlayerAliases(Map.of("legacy-id", "missing-player"));
        RedisWorldRepository.WorldStorageEnvelope corrupt = new RedisWorldRepository.WorldStorageEnvelope(
                envelope.storageVersion(), envelope.state(), envelope.ownerId(), envelope.catalogKey(),
                envelope.catalogFingerprint(), "pending", envelope.overlay());
        com.fasterxml.jackson.databind.node.ObjectNode corruptNode = objectMapper.valueToTree(corrupt);
        corruptNode.put("overlayChecksum", sha256(objectMapper.writeValueAsBytes(corruptNode.path("overlay"))));
        reactiveRedisTemplate.opsForValue().set(key(owner), objectMapper.writeValueAsString(corruptNode),
                Duration.ofDays(30)).block(Duration.ofSeconds(5));

        var outcome = migrate(repository, snapshot, owner);

        assertEquals(WorldStorageMigrationOrchestrator.Status.INVALID_V2, outcome.status());
    }

    @Test
    void swappedLegacyOwnerFailsClosedForReadAndMigration() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String payloadA = objectMapper.writeValueAsString(world(ownerA, "owner-a"));
        reactiveRedisTemplate.opsForValue().set(key(ownerB), payloadA, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));

        assertThrows(RedisWorldRepository.WorldStorageFormatException.class,
                () -> repository.findByUserId(ownerB).block(Duration.ofSeconds(10)));
        assertEquals(WorldStorageMigrationOrchestrator.Status.INVALID_LEGACY,
                migrate(repository, world(ownerB, "owner-b"), ownerB).status());
        assertEquals(payloadA, raw(ownerB));
    }

    @Test
    void preparedStateRecoversWithANewRepositoryAndOrchestratorInstance() throws Exception {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = world(owner, "legacy");
        seedLegacy(legacy);
        RedisWorldRepository failingCatalog = repository(Duration.ZERO);
        assertEquals(WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL,
                migrate(failingCatalog, legacy, owner).status());

        RedisWorldRepository restarted = repository(Duration.ofDays(365), objectMapper.copy());
        var outcome = migrate(restarted, legacy, owner);

        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, outcome.status());
        assertEquals("COMMITTED", objectMapper.readTree(raw(owner)).path("state").asText());
        assertNotNull(restarted.findByUserId(owner).block(Duration.ofSeconds(10)));
    }

    private WorldStorageMigrationExecutor mutateBeforeExecute(WorldStorageMigrationExecutor delegate,
                                                               java.util.function.Supplier<Mono<Void>> mutation) {
        return new WorldStorageMigrationExecutor() {
            @Override public Mono<SourceInspection> inspect(UUID ownerId) { return delegate.inspect(ownerId); }
            @Override public Mono<ExecutionResult> execute(WorldMigrationAdmission admission) {
                return mutation.get().then(delegate.execute(admission));
            }
        };
    }

    private WorldStorageMigrationExecutor gated(WorldStorageMigrationExecutor delegate, Sinks.One<Void> gate,
                                                 AtomicInteger arrivals) {
        return new WorldStorageMigrationExecutor() {
            @Override public Mono<SourceInspection> inspect(UUID ownerId) { return delegate.inspect(ownerId); }
            @Override public Mono<ExecutionResult> execute(WorldMigrationAdmission admission) {
                if (arrivals.incrementAndGet() == 2) gate.tryEmitEmpty();
                return gate.asMono().then(delegate.execute(admission));
            }
        };
    }

    private WorldStorageMigrationOrchestrator.Outcome migrate(WorldStorageMigrationExecutor executor,
                                                               WorldSnapshot canonical, UUID owner) {
        return WorldStorageMigrationTestDriver.create(executor, ignored -> Mono.just(canonical))
                .migrate(owner, capacity()).block(Duration.ofSeconds(30));
    }

    private WorldStorageMigrationOrchestrator.CapacitySnapshot capacity() {
        return new WorldStorageMigrationOrchestrator.CapacitySnapshot(1_000_000, 32_000_000, 32_768);
    }

    private String seedLegacy(WorldSnapshot snapshot) throws Exception {
        String raw = objectMapper.writeValueAsString(snapshot);
        reactiveRedisTemplate.opsForValue().set(key(snapshot.getUserId()), raw, Duration.ofDays(30))
                .block(Duration.ofSeconds(5));
        return raw;
    }

    private String raw(UUID owner) {
        return reactiveRedisTemplate.opsForValue().get(key(owner)).block(Duration.ofSeconds(5));
    }

    private static String key(UUID owner) { return "world:" + owner; }

    private WorldSnapshot world(UUID owner, String marker) {
        UUID real = UUID.nameUUIDFromBytes((owner + ":canonical").getBytes(StandardCharsets.UTF_8));
        return world(owner, marker, real);
    }

    private WorldSnapshot world(UUID owner, String marker, UUID real) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        WorldPlayer canonical = WorldPlayer.fromCanonicalPlayer(owner, real, "team", marker, 22,
                "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN);
        snapshot.getWorldPlayers().put(canonical.getWorldPlayerId(), canonical);
        return snapshot;
    }

    private RedisWorldRepository repository(Duration catalogTtl) {
        return repository(catalogTtl, objectMapper);
    }

    private RedisWorldRepository repository(Duration catalogTtl, ObjectMapper mapper) {
        RedisWorldRepository value = new RedisWorldRepository(reactiveRedisTemplate, mapper, null,
                new CanonicalWorldCatalogFingerprint(mapper), null);
        ReflectionTestUtils.setField(value, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(value, "catalogTtl", catalogTtl);
        ReflectionTestUtils.setField(value, "storageVersion", 2);
        return value;
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}

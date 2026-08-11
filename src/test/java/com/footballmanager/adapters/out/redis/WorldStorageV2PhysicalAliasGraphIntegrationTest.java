package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldSemanticComparator;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldSnapshotOverlay;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldStorageV2PhysicalAliasGraphIntegrationTest extends AbstractIntegrationTest {

    @TestFactory
    Stream<DynamicTest> aliasGraphMutationsAreDetectedThroughCommittedRedisReload() {
        List<AliasAttack> attacks = List.of(
                new AliasAttack("extra", aliases -> with(aliases, "extra", "first"), false),
                new AliasAttack("missing", aliases -> without(aliases, "legacy-first"), false),
                new AliasAttack("wrong-target", aliases -> with(aliases, "legacy-first", "second"), false),
                new AliasAttack("self-alias", aliases -> with(aliases, "first", "first"), true),
                new AliasAttack("cycle", aliases -> Map.of("cycle-a", "cycle-b", "cycle-b", "cycle-a"), true),
                new AliasAttack("foreign-target", aliases -> with(aliases, "legacy-first", "foreign"), true),
                new AliasAttack("collapse", aliases -> with(aliases, "legacy-second", "first"), false)
        );
        return attacks.stream().map(attack -> DynamicTest.dynamicTest(attack.name(), () -> execute(attack)));
    }

    private void execute(AliasAttack attack) throws Exception {
        UUID owner = UUID.nameUUIDFromBytes(("alias-physical-" + attack.name()).getBytes(StandardCharsets.UTF_8));
        Fixture fixture = fixture(owner);
        RedisWorldRepository repository = repository();
        reactiveRedisTemplate.opsForValue().set("world:" + owner, objectMapper.writeValueAsString(fixture.legacy()),
                Duration.ofMinutes(5)).block();
        var result = WorldStorageMigrationTestDriver.migrate(repository,
                ignored -> reactor.core.publisher.Mono.just(fixture.canonical()), owner,
                1_000_000, 8_000_000, 32_768, 65_536);
        org.junit.jupiter.api.Assertions.assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED,
                result.status(), result.reason());

        String committedRaw = reactiveRedisTemplate.opsForValue().get("world:" + owner).block();
        RedisWorldRepository.WorldStorageEnvelope committed = objectMapper.readValue(committedRaw,
                RedisWorldRepository.WorldStorageEnvelope.class);
        WorldSnapshotOverlay overlay = committed.overlay();
        overlay.setLegacyPlayerAliases(resolveSymbolic(attack.mutation().apply(overlay.getLegacyPlayerAliases()), fixture));
        String checksum = checksum(overlay);
        var attacked = new RedisWorldRepository.WorldStorageEnvelope(committed.storageVersion(), committed.state(),
                committed.ownerId(), committed.catalogKey(), committed.catalogFingerprint(), checksum, overlay);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, objectMapper.writeValueAsString(attacked),
                Duration.ofMinutes(5)).block();

        if (attack.reloadMustFail()) {
            assertThrows(RuntimeException.class,
                    () -> repository().findByUserId(owner).block(Duration.ofSeconds(5)));
        } else {
            WorldSnapshot reloaded = repository().findByUserId(owner).block(Duration.ofSeconds(5));
            assertFalse(new WorldSemanticComparator().compare(fixture.legacy(), reloaded).equivalent(),
                    "comparator accepted physical alias attack " + attack.name());
        }
    }

    private String checksum(WorldSnapshotOverlay overlay) throws Exception {
        JsonNode storedShape = objectMapper.readTree(objectMapper.writeValueAsBytes(overlay));
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(storedShape)));
    }

    private static Map<String, String> resolveSymbolic(Map<String, String> aliases, Fixture fixture) {
        Map<String, String> resolved = new LinkedHashMap<>();
        aliases.forEach((key, value) -> resolved.put(symbol(key, fixture), symbol(value, fixture)));
        return resolved;
    }

    private static String symbol(String value, Fixture fixture) {
        return switch (value) {
            case "first" -> fixture.firstId();
            case "second" -> fixture.secondId();
            default -> value;
        };
    }

    private static Map<String, String> with(Map<String, String> aliases, String key, String value) {
        Map<String, String> result = new LinkedHashMap<>(aliases);
        result.put(key, value);
        return result;
    }

    private static Map<String, String> without(Map<String, String> aliases, String key) {
        Map<String, String> result = new LinkedHashMap<>(aliases);
        result.remove(key);
        return result;
    }

    private static Fixture fixture(UUID owner) {
        UUID firstReal = UUID.nameUUIDFromBytes("alias-first".getBytes(StandardCharsets.UTF_8));
        UUID secondReal = UUID.nameUUIDFromBytes("alias-second".getBytes(StandardCharsets.UTF_8));
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setUserId(owner);
        canonical.setCreatedAt(Instant.EPOCH);
        canonical.setLastUpdated(Instant.EPOCH);
        WorldPlayer first = WorldPlayer.fromCanonicalPlayer(owner, firstReal, null, "First", 24, "MID",
                70, 70, 70, 70, 70, 70, BigDecimal.ONE);
        WorldPlayer second = WorldPlayer.fromCanonicalPlayer(owner, secondReal, null, "Second", 24, "MID",
                70, 70, 70, 70, 70, 70, BigDecimal.ONE);
        canonical.setWorldPlayers(new LinkedHashMap<>(Map.of(first.getWorldPlayerId(), first,
                second.getWorldPlayerId(), second)));
        WorldSnapshot legacy = copy(canonical);
        legacy.setWorldPlayerAliases(Map.of("legacy-first", first.getWorldPlayerId(),
                "legacy-second", second.getWorldPlayerId()));
        return new Fixture(canonical, legacy, first.getWorldPlayerId(), second.getWorldPlayerId());
    }

    private static WorldSnapshot copy(WorldSnapshot value) {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            return mapper.readValue(mapper.writeValueAsBytes(value), WorldSnapshot.class);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private RedisWorldRepository repository() {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, objectMapper);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private record Fixture(WorldSnapshot canonical, WorldSnapshot legacy, String firstId, String secondId) { }
    private record AliasAttack(String name, Function<Map<String, String>, Map<String, String>> mutation,
                               boolean reloadMustFail) { }
}

package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.application.service.world.WorldSemanticComparator;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.application.service.world.WorldMigrationTransitionObserver;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Separate-process harness: process A writes PREPARED; process B resumes through production code. */
public final class WorldPreparedMigrationProcessHarness {

    private WorldPreparedMigrationProcessHarness() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("mode, host, port, database and owner are required");
        String password = System.getenv("MANAGER_SEPARATE_JVM_REDIS_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Redis credential is unavailable");
        UUID owner = UUID.fromString(args[4]);
        try (GenericApplicationContext context = context(args[0], args[1], Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), password)) {
            ReactiveStringRedisTemplate redis = context.getBean(ReactiveStringRedisTemplate.class);
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            RedisWorldRepository repository = context.getBean(RedisWorldRepository.class);
            if ("prepare".equals(args[0])) {
                prepareThroughProductOrchestrator(redis, mapper, repository, owner);
                System.out.println("PREPARED_WRITTEN_BY_PRODUCT_ORCHESTRATOR");
            } else if ("recover".equals(args[0])) {
                recover(repository, owner);
                System.out.println("RECOVERY_OK");
            } else {
                throw new IllegalArgumentException("unknown harness mode");
            }
        }
    }

    private static GenericApplicationContext context(String mode, String host, int port, int database,
                                                     String password) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        context.registerBean(LettuceConnectionFactory.class, () -> {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
            configuration.setDatabase(database);
            configuration.setPassword(RedisPassword.of(password));
            return new LettuceConnectionFactory(configuration);
        });
        context.registerBean(ReactiveStringRedisTemplate.class,
                () -> new ReactiveStringRedisTemplate(context.getBean(LettuceConnectionFactory.class)));
        context.registerBean(RedisWorldRepository.class, () -> {
            WorldMigrationTransitionObserver observer = "prepare".equals(mode)
                    ? ignored -> reactor.core.publisher.Mono.just(
                            WorldMigrationTransitionObserver.Decision.PAUSE)
                    : WorldMigrationTransitionObserver.noOp();
            RedisWorldRepository repository = new RedisWorldRepository(
                    context.getBean(ReactiveStringRedisTemplate.class), context.getBean(ObjectMapper.class),
                    null, new CanonicalWorldCatalogFingerprint(context.getBean(ObjectMapper.class)), null, observer);
            ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
            ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
            ReflectionTestUtils.setField(repository, "storageVersion", 2);
            return repository;
        });
        context.refresh();
        return context;
    }

    private static void prepareThroughProductOrchestrator(ReactiveStringRedisTemplate redis, ObjectMapper mapper,
                                                          RedisWorldRepository repository, UUID owner)
            throws Exception {
        WorldSnapshot legacy = legacy(owner);
        String legacyJson = mapper.writeValueAsString(legacy);
        Boolean stored = redis.opsForValue().set("world:" + owner, legacyJson,
                Duration.ofMinutes(10)).block(Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(stored)) throw new IllegalStateException("Legacy state was not stored");
        WorldStorageMigrationOrchestrator.Outcome outcome = WorldStorageMigrationTestDriver.migrate(repository,
                ignored -> reactor.core.publisher.Mono.just(canonical(owner)), owner,
                1_000_000, 8_000_000, 32_768, 65_536);
        if (outcome == null || outcome.status() != WorldStorageMigrationOrchestrator.Status.RETRYABLE_PARTIAL) {
            throw new IllegalStateException("Product orchestrator did not stop after PREPARED");
        }
        String preparedRaw = redis.opsForValue().get("world:" + owner).block(Duration.ofSeconds(5));
        if (preparedRaw == null || !"PREPARED".equals(mapper.readTree(preparedRaw).path("migrationState").asText())) {
            throw new IllegalStateException("Product orchestrator did not persist PREPARED");
        }
    }

    private static void recover(RedisWorldRepository repository, UUID owner) {
        WorldSnapshot expected = legacy(owner);
        WorldStorageMigrationOrchestrator.Outcome outcome = WorldStorageMigrationTestDriver.migrate(repository,
                ignored -> reactor.core.publisher.Mono.just(canonical(owner)), owner,
                1_000_000, 8_000_000, 32_768, 65_536);
        if (outcome == null || outcome.status() != WorldStorageMigrationOrchestrator.Status.MIGRATED) {
            throw new IllegalStateException("PREPARED recovery did not migrate");
        }
        WorldSnapshot reloaded = repository.findByUserId(owner).block(Duration.ofSeconds(5));
        new WorldSemanticComparator().requireEquivalent(expected, reloaded);
    }

    private static WorldSnapshot canonical(UUID owner) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        snapshot.setCreatedAt(Instant.EPOCH);
        snapshot.setLastUpdated(Instant.EPOCH);
        UUID real = UUID.nameUUIDFromBytes("separate-jvm-player".getBytes(StandardCharsets.UTF_8));
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, real, null, "Canonical", 24, "MID",
                70, 71, 72, 73, 74, 75, BigDecimal.ONE);
        snapshot.setWorldPlayers(new LinkedHashMap<>(java.util.Map.of(player.getWorldPlayerId(), player)));
        return snapshot;
    }

    private static WorldSnapshot legacy(UUID owner) {
        WorldSnapshot snapshot = canonical(owner);
        snapshot.setCreatedAt(null);
        snapshot.setLastUpdated(null);
        snapshot.getAllWorldPlayers().getFirst().setName("Owner override");
        snapshot.getAllWorldPlayers().getFirst().setBaseAttack(99);
        return snapshot;
    }

}

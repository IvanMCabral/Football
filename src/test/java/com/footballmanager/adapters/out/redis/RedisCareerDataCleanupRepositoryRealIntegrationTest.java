package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the owner cleanup adapter against the ephemeral Redis started by the
 * test environment post-processor, rather than Mockito-only doubles.
 */
class RedisCareerDataCleanupRepositoryRealIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RedisCareerDataCleanupRepository cleanupRepository;

    @Autowired
    private RedisCareerRepository careerRepository;

    @Autowired
    private RedisWorldRepository worldRepository;

    @Autowired
    private CareerOwnershipTouchService ownershipTouchService;

    @Test
    void worldTtlExpiresWithoutDeletingCareerRoot() throws InterruptedException {
        UUID owner = UUID.randomUUID();
        CareerSave career = new CareerSave();
        career.setUserId(owner);
        careerRepository.save(career).block(Duration.ofSeconds(10));

        ReflectionTestUtils.setField(worldRepository, "worldTtl", Duration.ofMillis(250));
        com.footballmanager.domain.model.entity.WorldSnapshot world =
                new com.footballmanager.domain.model.entity.WorldSnapshot();
        world.setUserId(owner);
        worldRepository.save(world).block(Duration.ofSeconds(10));
        Duration ttl = reactiveRedisTemplate.getExpire("world:" + owner).block(Duration.ofSeconds(5));
        assertTrue(ttl != null && !ttl.isNegative() && !ttl.isZero());

        Thread.sleep(700);
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("world:" + owner).block()));
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + owner).block()));
    }

    @Test
    void careerSaveCreatesImmutableOwnerMappingAndIndex() {
        UUID ownerA = UUID.randomUUID();
        String careerId = "career-real-mapping";
        CareerSave career = new CareerSave();
        career.setUserId(ownerA);
        career.getData().setCareerId(careerId);

        careerRepository.save(career).block(Duration.ofSeconds(10));

        assertEquals(ownerA.toString(), reactiveRedisTemplate.opsForValue()
                .get("career-owner:" + careerId).block(Duration.ofSeconds(5)));
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.opsForSet()
                .isMember("user:" + ownerA + ":career-ids", careerId).block(Duration.ofSeconds(5))));
        Duration mappingTtl = reactiveRedisTemplate.getExpire("career-owner:" + careerId)
                .block(Duration.ofSeconds(5));
        assertTrue(mappingTtl != null && !mappingTtl.isNegative() && !mappingTtl.isZero());
    }

    @Test
    void careerIdCannotBeReassignedToAnotherOwner() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String careerId = "career-real-immutable";
        CareerSave first = new CareerSave();
        first.setUserId(ownerA);
        first.getData().setCareerId(careerId);
        careerRepository.save(first).block(Duration.ofSeconds(10));

        CareerSave conflicting = new CareerSave();
        conflicting.setUserId(ownerB);
        conflicting.getData().setCareerId(careerId);

        assertThrows(RuntimeException.class,
                () -> careerRepository.save(conflicting).block(Duration.ofSeconds(10)));
        assertEquals(ownerA.toString(), reactiveRedisTemplate.opsForValue()
                .get("career-owner:" + careerId).block(Duration.ofSeconds(5)));
    }

    @Test
    void indexCardinalityAllows256AndRejects257WithoutPartialState() {
        UUID owner = UUID.randomUUID();
        seedCareerIndex(owner, 255, "cardinality");

        careerRepository.save(career(owner, "cardinality-accepted")).block(Duration.ofSeconds(10));
        assertEquals(256, indexSize(owner));

        assertThrows(RuntimeException.class,
                () -> careerRepository.save(career(owner, "cardinality-rejected"))
                        .block(Duration.ofSeconds(10)));

        assertEquals(256, indexSize(owner));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "career-owner:cardinality-rejected").block()));
    }

    @Test
    void existingEntryAt256RenewsAndConcurrentNewSavesAllowExactlyOne() {
        UUID owner = UUID.randomUUID();
        seedCareerIndex(owner, 256, "existing");

        careerRepository.save(career(owner, "existing-0")).block(Duration.ofSeconds(10));
        assertEquals(256, indexSize(owner));

        UUID concurrentOwner = UUID.randomUUID();
        seedCareerIndex(concurrentOwner, 255, "concurrent");
        Mono<Boolean> first = careerRepository.save(career(concurrentOwner, "concurrent-256"))
                .thenReturn(true).onErrorReturn(false);
        Mono<Boolean> second = careerRepository.save(career(concurrentOwner, "concurrent-257"))
                .thenReturn(true).onErrorReturn(false);

        long successes = Flux.merge(first, second)
                .filter(Boolean::booleanValue)
                .count()
                .block(Duration.ofSeconds(20));

        assertEquals(1, successes);
        assertEquals(256, indexSize(concurrentOwner));
    }

    @Test
    void ownershipTouchRenewsRootMappingAndIndexBeforeDerivedWrite() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-touch";
        reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-touch"))
                .block(Duration.ofSeconds(5));
        reactiveRedisTemplate.opsForValue().set("career:" + owner, "root")
                .then(reactiveRedisTemplate.expire("career:" + owner, Duration.ofSeconds(1)))
                .then(reactiveRedisTemplate.expire("career-owner:" + careerId, Duration.ofSeconds(1)))
                .then(reactiveRedisTemplate.expire("user:" + owner + ":career-ids", Duration.ofSeconds(1)))
                .block(Duration.ofSeconds(5));

        String written = ownershipTouchService.touchBeforeWrite(careerId,
                () -> Mono.just("derived-written")).block(Duration.ofSeconds(10));

        assertEquals("derived-written", written);
        assertTrue(reactiveRedisTemplate.getExpire("career:" + owner).block(Duration.ofSeconds(5))
                .compareTo(Duration.ofSeconds(1)) > 0);
        assertTrue(reactiveRedisTemplate.getExpire("career-owner:" + careerId).block(Duration.ofSeconds(5))
                .compareTo(Duration.ofSeconds(1)) > 0);
        assertTrue(reactiveRedisTemplate.getExpire("user:" + owner + ":career-ids").block(Duration.ofSeconds(5))
                .compareTo(Duration.ofSeconds(1)) > 0);
    }

    @Test
    void staleGenerationCannotWriteAfterResetAndNewCareerGeneration() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-generation-stale";
        reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-old"))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + owner, "root"))
                .block(Duration.ofSeconds(5));

        reactiveRedisTemplate.delete("career-owner:" + careerId, "career-generation:" + careerId,
                        "career:" + owner, "user:" + owner + ":career-ids")
                .then(reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId))
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-new"))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + owner, "root-new"))
                .block(Duration.ofSeconds(5));

        assertThrows(RuntimeException.class, () -> ownershipTouchService
                .touchBeforeWrite(careerId, "generation-old", () -> Mono.just("must-not-write"))
                .block(Duration.ofSeconds(10)));
        assertEquals("generation-new", reactiveRedisTemplate.opsForValue()
                .get("career-generation:" + careerId).block(Duration.ofSeconds(5)));
    }

    @Test
    void cleansOnlyOwnerAAndReconcilesRedisCounters() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String careerA = "career-real-a";
        String careerB = "career-real-b";
        seedOwner(ownerA, careerA, "detail-a", 205);
        seedOwner(ownerB, careerB, "detail-b", 2);

        long before = dbSize();
        CareerDataCleanupResult result = cleanupRepository.deleteOwnedData(ownerA, null).block(Duration.ofSeconds(15));
        long after = dbSize();

        assertEquals(before - after, result.keysActuallyDeleted());
        assertTrue(result.keysRequestedForDeletion() >= 205);
        assertTrue(result.maxBatchSize() <= 100);
        assertTrue(result.batchCount() >= 3);
        assertEquals(CareerDataCleanupResult.Status.COMPLETED, result.status());
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + careerA).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career-cleanup:" + ownerA).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + careerA + ":match-detail:detail-a").block()));
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + careerB + ":match-detail:detail-b").block()));
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career-owner:" + careerB).block()));
    }

    @Test
    void corruptOwnerIndexRejectsBeforeAnyForeignKeyIsScanned() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String careerB = "career-real-corrupt-b";
        reactiveRedisTemplate.opsForSet().add("user:" + ownerA + ":career-ids", careerB)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerB, ownerB.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + careerB + ":match-detail:m1", "foreign"))
                .block(Duration.ofSeconds(10));
        long before = dbSize();

        CareerDataCleanupException failure = assertThrows(CareerDataCleanupException.class,
                () -> cleanupRepository.deleteOwnedData(ownerA, null).block(Duration.ofSeconds(10)));

        assertEquals(CareerDataCleanupResult.Status.REJECTED_OWNERSHIP, failure.result().status());
        assertEquals(before, dbSize());
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + careerB + ":match-detail:m1").block()));
    }

    private void seedOwner(UUID owner, String careerId, String detailId, int projections) {
        String index = "user:" + owner + ":career-ids";
        reactiveRedisTemplate.opsForSet().add(index, careerId)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-" + careerId))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + owner, "root"))
                .then(reactiveRedisTemplate.opsForValue().set("world:" + owner, "world"))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + careerId + ":match-detail:" + detailId, "detail"))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + careerId + ":match-baseline:" + detailId, "baseline"))
                .then(reactiveRedisTemplate.opsForValue().set("runtime:match:" + owner + ":m1", "runtime"))
                .thenMany(reactiveRedisTemplate.opsForValue().set("user:" + owner + ":team:1", "team")
                        .thenMany(reactiveRedisTemplate.opsForValue().set("user:" + owner + ":projection:0", "projection"))
                        .thenMany(reactiveRedisTemplate.opsForValue().set("user:" + owner + ":projection:1", "projection")))
                .thenMany(reactiveRedisTemplate.opsForValue().set("user:" + owner + ":projection:extra:" + UUID.randomUUID(), "projection"))
                .then()
                .block(Duration.ofSeconds(10));
        for (int i = 2; i < projections; i++) {
            reactiveRedisTemplate.opsForValue().set("user:" + owner + ":projection:" + i, "projection")
                    .block(Duration.ofSeconds(5));
        }
    }

    private CareerSave career(UUID owner, String careerId) {
        CareerSave career = new CareerSave();
        career.setUserId(owner);
        career.getData().setCareerId(careerId);
        return career;
    }

    private void seedCareerIndex(UUID owner, int count, String prefix) {
        String index = "user:" + owner + ":career-ids";
        for (int i = 0; i < count; i++) {
            String careerId = prefix + "-" + i;
            reactiveRedisTemplate.opsForSet().add(index, careerId)
                    .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                    .block(Duration.ofSeconds(5));
        }
        reactiveRedisTemplate.expire(index, Duration.ofDays(31)).block(Duration.ofSeconds(5));
    }

    private long indexSize(UUID owner) {
        return reactiveRedisTemplate.opsForSet().size("user:" + owner + ":career-ids")
                .block(Duration.ofSeconds(5));
    }

    private long dbSize() {
        return reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
                .serverCommands().dbSize().block(Duration.ofSeconds(5));
    }
}

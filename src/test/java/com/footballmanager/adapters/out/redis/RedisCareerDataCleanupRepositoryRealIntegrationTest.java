package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import com.footballmanager.infrastructure.adapter.out.redis.RedisMatchStateRepository;
import com.footballmanager.adapters.out.redis.RedisMatchRuntimeRepository;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import com.footballmanager.domain.port.in.match.AdvanceMatchUseCase;
import com.footballmanager.domain.port.in.match.MatchSimulationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @Autowired
    private RedisMatchStateRepository matchStateRepository;

    @Autowired
    private RedisMatchRuntimeRepository runtimeMatchRepository;

    @Autowired
    private AdvanceMatchUseCase advanceMatchUseCase;

    @Autowired
    private MatchSimulationUseCase matchSimulationUseCase;

    @Test
    void cleanupPerformanceProfileTwentyRealRedisSamples() {
        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < 20; sample++) {
            UUID owner = UUID.randomUUID();
            seedOwner(owner, "career-perf-" + sample, "detail-" + sample, 2);
            long started = System.nanoTime();
            CareerDataCleanupResult result = cleanupRepository
                    .deleteOwnedData(owner, null)
                    .block(Duration.ofSeconds(15));
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            assertEquals(CareerDataCleanupResult.Status.COMPLETED, result.status());
            durations.add(elapsed);
        }
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        long p50 = sorted.get(sorted.size() / 2);
        long p95 = sorted.get((int) Math.ceil(sorted.size() * .95) - 1);
        long max = sorted.get(sorted.size() - 1);
        System.out.printf("[CLEANUP-PERF] n=20 p50Ms=%d p95Ms=%d maxMs=%d samples=%s%n",
                p50, p95, max, sorted);
        assertTrue(p50 <= 1500, "local cleanup p50 exceeded target: " + p50);
        assertTrue(p95 <= 3000, "local cleanup p95 exceeded target: " + p95);
    }

    @Test
    void modernCareerWritersRegisterExactKeysInBoundedManifest() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-modern-manifest-" + owner;
        CareerSave career = career(owner, careerId);
        careerRepository.createInitialCareer(career).block(Duration.ofSeconds(5));

        CareerWriteContext context = ownershipTouchService.capture(owner, careerId)
                .block(Duration.ofSeconds(5));
        String matchId = "manifest-match";
        RuntimeMatch runtime = new RuntimeMatch(matchId, careerId, "home", "away", 1,
                context.expectedGeneration());
        runtimeMatchRepository.save(owner, runtime, context).block(Duration.ofSeconds(5));

        String manifest = CareerOwnershipTouchService.manifestKey(careerId);
        assertEquals("1", reactiveRedisTemplate.opsForValue()
                .get(CareerOwnershipTouchService.manifestVersionKey(careerId))
                .block(Duration.ofSeconds(5)));
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.opsForSet()
                .isMember(manifest, "runtime:match:" + owner + ":" + matchId)
                .block(Duration.ofSeconds(5))));

        cleanupRepository.deleteOwnedData(owner, careerId).block(Duration.ofSeconds(10));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(manifest).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "runtime:match:" + owner + ":" + matchId).block()));
    }

    @Test
    void modernEmptyManifestUsesOneFencedFinalizationCommand() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-modern-empty-" + owner;
        seedOwnership(owner, careerId, "generation-empty");
        reactiveRedisTemplate.opsForValue()
                .set(CareerOwnershipTouchService.manifestVersionKey(careerId), "1")
                .then(reactiveRedisTemplate.opsForValue().set("world:" + owner, "world"))
                .block(Duration.ofSeconds(5));

        CareerDataCleanupResult result = cleanupRepository.deleteOwnedData(owner, careerId)
                .block(Duration.ofSeconds(10));

        assertEquals(CareerDataCleanupResult.Status.COMPLETED, result.status());
        assertEquals("MODERN_MANIFEST", result.diagnostic("path"));
        assertEquals("0", result.diagnostic("manifestEntries"));
        assertEquals("1", result.diagnostic("projectionScanMatches"));
        assertEquals("0", result.diagnostic("childDeleteCommands"));
        assertEquals("1", result.diagnostic("atomicScriptCommands"));
        assertEquals("3", result.diagnostic("sequentialRemoteLayers"));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + owner).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career-cleanup:" + owner).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("world:" + owner).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career-owner:" + careerId).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career-generation:" + careerId).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.opsForSet()
                .isMember("user:" + owner + ":career-ids", careerId).block()));
    }

    @Test
    void tokenlessCareerStateAndRuntimeWritersFailClosedAgainstRealRedis() {
        UUID owner = UUID.randomUUID();
        CareerSave career = career(owner, "career-tokenless");
        assertThrows(RuntimeException.class,
                () -> careerRepository.save(career).block(Duration.ofSeconds(5)));

        MatchState state = new MatchState(UUID.randomUUID());
        state.setCareerId("career-tokenless");
        state.setLifecycleGeneration("generation-old");
        assertThrows(RuntimeException.class,
                () -> matchStateRepository.save(owner, state).block(Duration.ofSeconds(5)));

        RuntimeMatch runtime = new RuntimeMatch("match-tokenless", "career-tokenless",
                "home", "away", 1);
        runtime.setLifecycleGeneration("generation-old");
        assertThrows(RuntimeException.class,
                () -> runtimeMatchRepository.save(owner, runtime).block(Duration.ofSeconds(5)));

        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "match:state:" + owner + ":" + state.getMatchId()).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "runtime:match:" + owner + ":match-tokenless").block()));
    }

    @Test
    void productiveAdvanceRejectsRuntimeGenerationCapturedBeforeReset() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-productive-runtime-fence";
        String matchId = "match-productive-runtime-fence";
        seedOwnership(owner, careerId, "generation-one");
        CareerWriteContext first = new CareerWriteContext(owner, careerId, "generation-one");
        RuntimeMatch runtime = new RuntimeMatch(matchId, careerId, "home", "away", 1,
                first.expectedGeneration());
        runtimeMatchRepository.save(owner, runtime, first).block(Duration.ofSeconds(5));
        String before = reactiveRedisTemplate.opsForValue()
                .get("runtime:match:" + owner + ":" + matchId).block(Duration.ofSeconds(5));
        long dbSizeBeforeStale = dbSize();

        reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-two")
                .block(Duration.ofSeconds(5));

        assertThrows(RuntimeException.class,
                () -> advanceMatchUseCase.advanceMatch(owner, matchId).block(Duration.ofSeconds(5)));
        assertEquals(before, reactiveRedisTemplate.opsForValue()
                .get("runtime:match:" + owner + ":" + matchId).block(Duration.ofSeconds(5)));
        assertEquals("generation-two", reactiveRedisTemplate.opsForValue()
                .get("career-generation:" + careerId).block(Duration.ofSeconds(5)));
        assertEquals(dbSizeBeforeStale, dbSize());

        RuntimeMatch current = new RuntimeMatch(matchId, careerId, "home", "away", 1, "generation-two");
        runtimeMatchRepository.save(owner, current,
                new CareerWriteContext(owner, careerId, "generation-two"))
                .block(Duration.ofSeconds(5));
        assertEquals("generation-two", advanceMatchUseCase.advanceMatch(owner, matchId)
                .block(Duration.ofSeconds(5)).getLifecycleGeneration());
    }

    @Test
    void productiveAdvanceRejectsMatchStateGenerationCapturedBeforeReset() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-productive-state-fence";
        UUID matchId = UUID.randomUUID();
        seedOwnership(owner, careerId, "generation-one");
        CareerWriteContext first = new CareerWriteContext(owner, careerId, "generation-one");
        MatchState state = new MatchState(matchId, owner, careerId, first.expectedGeneration());
        matchStateRepository.save(owner, state, first).block(Duration.ofSeconds(5));
        String before = reactiveRedisTemplate.opsForValue()
                .get("match:state:" + owner + ":" + matchId).block(Duration.ofSeconds(5));
        long dbSizeBeforeStale = dbSize();

        reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-two")
                .block(Duration.ofSeconds(5));

        assertThrows(RuntimeException.class,
                () -> matchSimulationUseCase.advanceMatch(owner, matchId, 1)
                        .block(Duration.ofSeconds(5)));
        assertEquals(before, reactiveRedisTemplate.opsForValue()
                .get("match:state:" + owner + ":" + matchId).block(Duration.ofSeconds(5)));
        assertEquals("generation-two", reactiveRedisTemplate.opsForValue()
                .get("career-generation:" + careerId).block(Duration.ofSeconds(5)));
        assertEquals(dbSizeBeforeStale, dbSize());

        MatchState current = new MatchState(matchId, owner, careerId, "generation-two");
        matchStateRepository.save(owner, current,
                new CareerWriteContext(owner, careerId, "generation-two"))
                .block(Duration.ofSeconds(5));
        assertEquals("generation-two", matchSimulationUseCase.advanceMatch(owner, matchId, 1)
                .block(Duration.ofSeconds(5)).getLifecycleGeneration());
    }

    @Test
    void staleStateAndRuntimeContextsDoNotMutateGenerationTwo() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-state-runtime-stale";
        CareerWriteContext stale = new CareerWriteContext(owner, careerId, "generation-one");
        reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-two"))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + owner, "generation-two-root"))
                .block(Duration.ofSeconds(5));

        MatchState state = new MatchState(UUID.randomUUID());
        state.setCareerId(careerId);
        state.setLifecycleGeneration(stale.expectedGeneration());
        assertThrows(RuntimeException.class,
                () -> matchStateRepository.save(owner, state, stale).block(Duration.ofSeconds(5)));

        RuntimeMatch runtime = new RuntimeMatch("match-state-runtime-stale", careerId,
                "home", "away", 1);
        runtime.setLifecycleGeneration(stale.expectedGeneration());
        assertThrows(RuntimeException.class,
                () -> runtimeMatchRepository.save(owner, runtime, stale).block(Duration.ofSeconds(5)));

        assertEquals("generation-two", reactiveRedisTemplate.opsForValue()
                .get("career-generation:" + careerId).block(Duration.ofSeconds(5)));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "match:state:" + owner + ":" + state.getMatchId()).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "runtime:match:" + owner + ":match-state-runtime-stale").block()));
    }

    @Test
    void worldTtlExpiresWithoutDeletingCareerRoot() throws InterruptedException {
        UUID owner = UUID.randomUUID();
        CareerSave career = new CareerSave();
        career.setUserId(owner);
        career.getData().setCareerId("career-world-ttl");
        ReflectionTestUtils.setField(worldRepository, "worldTtl", Duration.ofMillis(250));
        com.footballmanager.domain.model.entity.WorldSnapshot world =
                new com.footballmanager.domain.model.entity.WorldSnapshot();
        world.setUserId(owner);
        worldRepository.saveInitial(world).block(Duration.ofSeconds(10));
        careerRepository.createInitialCareer(career).block(Duration.ofSeconds(10));
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

        careerRepository.createInitialCareer(career).block(Duration.ofSeconds(10));

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
        careerRepository.createInitialCareer(first).block(Duration.ofSeconds(10));

        CareerSave conflicting = new CareerSave();
        conflicting.setUserId(ownerB);
        conflicting.getData().setCareerId(careerId);

        assertThrows(RuntimeException.class,
                () -> careerRepository.createInitialCareer(conflicting).block(Duration.ofSeconds(10)));
        assertEquals(ownerA.toString(), reactiveRedisTemplate.opsForValue()
                .get("career-owner:" + careerId).block(Duration.ofSeconds(5)));
    }

    @Test
    void indexCardinalityAllows256AndRejects257WithoutPartialState() {
        UUID owner = UUID.randomUUID();
        seedCareerIndex(owner, 255, "cardinality");

        careerRepository.createInitialCareer(career(owner, "cardinality-accepted")).block(Duration.ofSeconds(10));
        assertEquals(256, indexSize(owner));

        assertThrows(RuntimeException.class,
                () -> careerRepository.createInitialCareer(career(owner, "cardinality-rejected"))
                        .block(Duration.ofSeconds(10)));

        assertEquals(256, indexSize(owner));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(
                "career-owner:cardinality-rejected").block()));
    }

    @Test
    void existingEntryAt256RenewsAndConcurrentNewSavesAllowExactlyOne() {
        UUID owner = UUID.randomUUID();
        seedCareerIndex(owner, 255, "existing");

        careerRepository.createInitialCareer(career(owner, "existing-new")).block(Duration.ofSeconds(10));
        assertEquals(256, indexSize(owner));

        UUID concurrentOwner = UUID.randomUUID();
        seedCareerIndex(concurrentOwner, 255, "concurrent");
        Mono<Boolean> first = careerRepository.createInitialCareer(career(concurrentOwner, "concurrent-256"))
                .thenReturn(true).onErrorReturn(false);
        Mono<Boolean> second = careerRepository.createInitialCareer(career(concurrentOwner, "concurrent-257"))
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

        String written = ownershipTouchService.touchBeforeWrite(careerId, "generation-touch",
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
    void staleWorldWriterCannotRecreateWorldAfterReset() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-world-stale";
        CareerWriteContext oldContext = new CareerWriteContext(owner, careerId, "generation-old");
        reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-new"))
                .then(reactiveRedisTemplate.delete("world:" + owner, "career:" + owner,
                        "career-owner:" + careerId, "career-generation:" + careerId,
                        "user:" + owner + ":career-ids"))
                .block(Duration.ofSeconds(5));

        com.footballmanager.domain.model.entity.WorldSnapshot world =
                new com.footballmanager.domain.model.entity.WorldSnapshot();
        world.setUserId(owner);
        assertThrows(RuntimeException.class,
                () -> worldRepository.saveWithContext(oldContext, world).block(Duration.ofSeconds(5)));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("world:" + owner).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career:" + owner).block()));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("career-generation:" + careerId).block()));
    }

    @Test
    void initialWorldWriterCannotOverwriteOwnerWithCareerRoot() {
        UUID owner = UUID.randomUUID();
        reactiveRedisTemplate.opsForValue().set("career:" + owner, "active-career")
                .block(Duration.ofSeconds(5));
        com.footballmanager.domain.model.entity.WorldSnapshot world =
                new com.footballmanager.domain.model.entity.WorldSnapshot();
        world.setUserId(owner);

        assertThrows(RuntimeException.class,
                () -> worldRepository.saveInitial(world).block(Duration.ofSeconds(5)));
        assertFalse(Boolean.TRUE.equals(reactiveRedisTemplate.hasKey("world:" + owner).block()));
    }

    @Test
    void staleCareerWriterCannotRecreateRootAfterResetAndReplacement() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-root-stale";
        CareerSave oldCareer = career(owner, careerId);
        oldCareer.setLifecycleGeneration("generation-old");

        reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, "generation-new")
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + owner, "replacement"))
                .block(Duration.ofSeconds(5));

        assertThrows(RuntimeException.class,
                () -> careerRepository.saveExistingCareer(new CareerWriteContext(owner, careerId, "generation-old"), oldCareer).block(Duration.ofSeconds(10)));
        assertEquals("replacement", reactiveRedisTemplate.opsForValue()
                .get("career:" + owner).block(Duration.ofSeconds(5)));
        assertEquals("generation-new", reactiveRedisTemplate.opsForValue()
                .get("career-generation:" + careerId).block(Duration.ofSeconds(5)));
    }

    @Test
    void compensationCannotDeleteOwnershipAdvancedByConcurrentSave() {
        UUID owner = UUID.randomUUID();
        String careerId = "career-compensation-race";
        String tokenA = "save-a";
        String tokenB = "save-b";
        String mappingToken = "career-mapping-token:" + careerId;
        String mapping = "career-owner:" + careerId;
        String root = "career:" + owner;
        String index = "user:" + owner + ":career-ids";
        reactiveRedisTemplate.opsForValue().set(mappingToken, tokenB)
                .then(reactiveRedisTemplate.opsForValue().set(mapping, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set(root, "root-b"))
                .then(reactiveRedisTemplate.opsForSet().add(index, careerId))
                .block(Duration.ofSeconds(5));

        Long deleted = reactiveRedisTemplate.execute(RedisCareerRepository.COMPENSATE_SAVE_IF_OWNER,
                java.util.List.of(mappingToken, mapping, root, index), tokenA, careerId)
                .next().block(Duration.ofSeconds(5));

        assertEquals(0L, deleted);
        assertEquals(tokenB, reactiveRedisTemplate.opsForValue().get(mappingToken).block());
        assertEquals(owner.toString(), reactiveRedisTemplate.opsForValue().get(mapping).block());
        assertEquals("root-b", reactiveRedisTemplate.opsForValue().get(root).block());
        assertTrue(Boolean.TRUE.equals(reactiveRedisTemplate.opsForSet().isMember(index, careerId).block()));
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

    private void seedOwnership(UUID owner, String careerId, String generation) {
        reactiveRedisTemplate.opsForSet().add("user:" + owner + ":career-ids", careerId)
                .then(reactiveRedisTemplate.opsForValue().set("career-owner:" + careerId, owner.toString()))
                .then(reactiveRedisTemplate.opsForValue().set("career-generation:" + careerId, generation))
                .then(reactiveRedisTemplate.opsForValue().set("career:" + owner, "root-" + generation))
                .block(Duration.ofSeconds(5));
    }

    private long dbSize() {
        return reactiveRedisTemplate.getConnectionFactory().getReactiveConnection()
                .serverCommands().dbSize().block(Duration.ofSeconds(5));
    }
}

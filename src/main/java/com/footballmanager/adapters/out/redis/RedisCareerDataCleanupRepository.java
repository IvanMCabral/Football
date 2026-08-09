package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.application.observability.RuntimeOperationMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reactive, exact-owner cleanup for all career Redis projections. */
@Repository
public class RedisCareerDataCleanupRepository implements CareerDataCleanupRepository {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int SCAN_COUNT_HINT = 1_000;
    private static final int DISCOVERY_CONCURRENCY = 8;
    private static final int MAX_INDEX_CARDINALITY = 256;
    private static final String CAREER_OWNER_PREFIX = "career-owner:";
    private static final Duration INDEX_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SCAN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration UNLINK_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EXISTS_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration TOMBSTONE_TTL = Duration.ofMinutes(15);
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Duration indexTimeout;
    private final Duration scanTimeout;
    private final Duration unlinkTimeout;
    private final Duration existsTimeout;
    private final Duration totalTimeout;

    @org.springframework.beans.factory.annotation.Autowired
    public RedisCareerDataCleanupRepository(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this(redisTemplate, INDEX_TIMEOUT, SCAN_TIMEOUT, UNLINK_TIMEOUT, EXISTS_TIMEOUT, TOTAL_TIMEOUT);
    }

    RedisCareerDataCleanupRepository(
            ReactiveRedisTemplate<String, String> redisTemplate,
            Duration indexTimeout,
            Duration scanTimeout,
            Duration unlinkTimeout,
            Duration existsTimeout,
            Duration totalTimeout) {
        this.redisTemplate = redisTemplate;
        this.indexTimeout = indexTimeout;
        this.scanTimeout = scanTimeout;
        this.unlinkTimeout = unlinkTimeout;
        this.existsTimeout = existsTimeout;
        this.totalTimeout = totalTimeout;
    }

    @Override
    public Mono<CareerDataCleanupResult> deleteOwnedData(UUID userId, String careerId) {
        return deleteOwnedData(userId, careerId, false);
    }

    @Override
    public Mono<CareerDataCleanupResult> deleteOwnedDataPreservingWorld(UUID userId, String careerId) {
        return deleteOwnedData(userId, careerId, true);
    }

    private Mono<CareerDataCleanupResult> deleteOwnedData(UUID userId, String careerId, boolean preserveWorld) {
        if (userId == null) {
            return Mono.error(new IllegalArgumentException("userId must not be null"));
        }

        CleanupAccumulator accumulator = new CleanupAccumulator(userId, careerId);
        Mono<List<String>> indexedCareerIds = careerId != null && !careerId.isBlank()
                ? Mono.just(List.of(careerId))
                : redisTemplate.opsForSet()
                        .members(indexKey(userId))
                        .timeout(indexTimeout)
                        .take(MAX_INDEX_CARDINALITY + 1L)
                        .collectList()
                        .cache();
        // The tombstone must be durable before any destructive operation, but
        // it does not depend on the read-only owner index. Start both in the
        // same subscription to remove one provider round-trip from reset.
        Mono<List<String>> ownershipReady;
        if (careerId != null && !careerId.isBlank()) {
            Mono<List<String>> validated = validateOwnership(userId, careerId, List.of(careerId), accumulator).cache();
            ownershipReady = Mono.when(writeTombstone(userId, careerId), validated).then(validated);
        } else {
            ownershipReady = Mono.when(writeTombstone(userId, careerId), indexedCareerIds)
                    .then(indexedCareerIds)
                    .flatMap(indexedIds -> {
                        if (indexedIds.size() > MAX_INDEX_CARDINALITY) {
                            accumulator.fail("CAREER_INDEX_CARDINALITY_EXCEEDED");
                            return Mono.error(new IllegalStateException("career index cardinality exceeded"));
                        }
                        return validateOwnership(userId, careerId, indexedIds, accumulator);
                    });
        }
        return ownershipReady.flatMap(ownedIds -> {
                    return Mono.just(ownedIds);
                })
                .flatMapMany(ownedCareerIds -> {
                    accumulator.setCareerCount(ownedCareerIds, careerId);
                    List<PatternSpec> specs = patterns(userId, careerId, ownedCareerIds, preserveWorld);
                    specs.forEach(accumulator::patternEvaluated);
                    return discoverForReset(userId, careerId, ownedCareerIds, specs, preserveWorld)
                            .flatMapMany(discoveries -> deleteDiscoveries(discoveries, accumulator));
                })
                .then(Mono.fromSupplier(() -> accumulator.result(false, "")))
                .flatMap(result -> clearTombstone(userId).thenReturn(result))
                .timeout(totalTimeout)
                .onErrorResume(error -> accumulator.restoreDiscovery(redisTemplate)
                        .onErrorResume(ignored -> Mono.empty())
                        .then(accumulator.isOwnershipRejected()
                                ? clearTombstone(userId)
                                : Mono.empty())
                        .then(Mono.error(error)))
                .onErrorMap(error -> {
                    if (error instanceof CareerDataCleanupException) {
                        return error;
                    }
                    accumulator.fail(accumulator.failureReasonFor(error));
                    return new CareerDataCleanupException(accumulator.result(true, accumulator.failedFamily()), error);
                });
    }

    /**
     * Uses the exact lifecycle manifest for careers created after manifest
     * support was enabled.  The user projection remains a single legacy scan
     * because those keys are shared by older adapters and are not career-ID
     * derivable.  Careers without the explicit marker retain the complete
     * twelve-family fallback and are never treated as modern accidentally.
     */
    private Mono<List<Discovery>> discoverForReset(UUID userId, String careerId,
                                                    List<String> ownedCareerIds,
                                                    List<PatternSpec> legacySpecs,
                                                    boolean preserveWorld) {
        if (careerId == null || careerId.isBlank() || ownedCareerIds.size() != 1
                || !ownedCareerIds.contains(careerId)) {
            return discoverLegacy(legacySpecs);
        }
        return manifestVersion(careerId)
                .filter(RedisCareerDataCleanupRepository::isModernManifest)
                .flatMap(ignored -> discoverModern(userId, careerId, preserveWorld))
                .switchIfEmpty(discoverLegacy(legacySpecs));
    }

    private Mono<List<Discovery>> discoverModern(UUID userId, String careerId, boolean preserveWorld) {
        List<Discovery> discoveries = new ArrayList<>();
        PatternSpec projection = new PatternSpec("user-projection", "user:" + userId + ":*");
        return discoverKeys(projection).flatMap(projectionKeys -> {
            int order = 0;
            discoveries.add(new Discovery(order++, projection, projectionKeys));
            if (!preserveWorld) {
                discoveries.add(new Discovery(order++, new PatternSpec("world", "world:" + userId),
                        List.of("world:" + userId)));
            }
            discoveries.add(new Discovery(order++, new PatternSpec("career-index", indexKey(userId)),
                    List.of(indexKey(userId))));
            int nextOrder = order;
            return manifestMembers(careerId).map(members -> {
            List<String> exact = new ArrayList<>(members);
            exact.add(RedisCareerOwnershipKeys.manifestKey(careerId));
            exact.add(RedisCareerOwnershipKeys.manifestVersionKey(careerId));
            int orderInManifest = nextOrder;
            discoveries.add(new Discovery(orderInManifest++, new PatternSpec("cleanup-manifest",
                    RedisCareerOwnershipKeys.manifestKey(careerId)), exact));
            discoveries.add(new Discovery(orderInManifest++, new PatternSpec("career-owner-mapping",
                    "career-owner:" + careerId), List.of("career-owner:" + careerId)));
            discoveries.add(new Discovery(orderInManifest++, new PatternSpec("career-generation",
                    "career-generation:" + careerId), List.of("career-generation:" + careerId)));
            discoveries.add(new Discovery(orderInManifest++, new PatternSpec("career-mapping-token",
                    "career-mapping-token:" + careerId), List.of("career-mapping-token:" + careerId)));
            discoveries.add(new Discovery(orderInManifest, new PatternSpec("career-root", "career:" + userId),
                    List.of("career:" + userId)));
            return discoveries;
            });
        });
    }

    private Mono<List<Discovery>> discoverLegacy(List<PatternSpec> specs) {
        return Flux.range(0, specs.size())
                .flatMap(index -> discoverKeys(specs.get(index))
                                .map(keys -> new Discovery(index, specs.get(index), keys)),
                        DISCOVERY_CONCURRENCY)
                .collectList()
                .map(discoveries -> {
                    discoveries.sort(java.util.Comparator.comparingInt(Discovery::order));
                    return discoveries;
                });
    }

    private Mono<String> manifestVersion(String careerId) {
        var operations = redisTemplate.opsForValue();
        if (operations == null) {
            return Mono.empty();
        }
        Mono<String> value = operations.get(RedisCareerOwnershipKeys.manifestVersionKey(careerId));
        return value == null ? Mono.empty() : value.timeout(indexTimeout);
    }

    private Mono<List<String>> manifestMembers(String careerId) {
        var operations = redisTemplate.opsForSet();
        if (operations == null) {
            return Mono.just(List.of());
        }
        Flux<String> members = operations.members(RedisCareerOwnershipKeys.manifestKey(careerId));
        return members == null ? Mono.just(List.of()) : members.collectList().timeout(indexTimeout);
    }

    private static boolean isModernManifest(String version) {
        return RedisCareerOwnershipKeys.MANIFEST_VERSION.equals(version);
    }

    private Mono<List<String>> validateOwnership(UUID userId, String explicitCareerId,
                                                   List<String> indexedCareerIds,
                                                   CleanupAccumulator accumulator) {
        if (indexedCareerIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            accumulator.rejectOwnership("OWNER_INDEX_ENTRY_INVALID");
            return Mono.error(new OwnershipRejectedException());
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        indexedCareerIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(candidates::add);
        if (explicitCareerId != null && !explicitCareerId.isBlank()) {
            candidates.add(explicitCareerId);
        }
        return Flux.fromIterable(candidates)
                .concatMap(candidate -> redisTemplate.opsForValue()
                        .get(ownerMappingKey(candidate))
                        .timeout(indexTimeout)
                        .switchIfEmpty(Mono.defer(() -> {
                            accumulator.rejectOwnership("OWNER_MAPPING_MISSING");
                            return Mono.error(new OwnershipRejectedException());
                        }))
                        .flatMap(mappedOwner -> mappedOwner.equals(userId.toString())
                                ? Mono.just(candidate)
                                : Mono.defer(() -> {
                                    accumulator.rejectOwnership("OWNER_MAPPING_MISMATCH");
                                    return Mono.error(new OwnershipRejectedException());
                                })))
                .collectList();
    }

    private List<PatternSpec> patterns(UUID userId, String careerId, List<String> indexedCareerIds, boolean preserveWorld) {
        List<PatternSpec> patterns = new ArrayList<>(List.of(
                new PatternSpec("user-projection", "user:" + userId + ":*"),
                new PatternSpec("career-index", indexKey(userId)),
                new PatternSpec("runtime", "runtime:match:" + userId + ":*"),
                new PatternSpec("match-state", "match:state:" + userId + ":*"),
                new PatternSpec("match-commands", "match:commands:" + userId + ":*")));
        if (!preserveWorld) {
            patterns.add(0, new PatternSpec("world", "world:" + userId));
        }

        List<String> careerIds = new ArrayList<>(indexedCareerIds);
        if (careerId != null && !careerId.isBlank() && !careerIds.contains(careerId)) {
            careerIds.add(careerId);
        }
        for (String indexedCareerId : careerIds) {
            if (indexedCareerId != null && !indexedCareerId.isBlank()) {
                patterns.add(new PatternSpec("career-owner-mapping", ownerMappingKey(indexedCareerId)));
                patterns.add(new PatternSpec("career-generation", generationKey(indexedCareerId)));
                patterns.add(new PatternSpec("career-mapping-token", mappingTokenKey(indexedCareerId)));
                patterns.add(new PatternSpec("match-detail", "career:" + indexedCareerId + ":match-detail:*"));
                patterns.add(new PatternSpec("match-baseline", "career:" + indexedCareerId + ":match-baseline:*"));
            }
        }
        // The career root is intentionally last. If any child, projection,
        // index or ownership cleanup fails, the root remains the retry anchor.
        patterns.add(new PatternSpec("career-root", "career:" + userId));
        return patterns;
    }

    private Flux<String> scanKeys(PatternSpec spec) {
        Flux<String> scanned = redisTemplate.scan(ScanOptions.scanOptions()
                .match(spec.pattern())
                .count(SCAN_COUNT_HINT)
                .build())
                .timeout(scanTimeout);
        // Game entities and their index are independent user-owned resources;
        // a career reset must not silently remove them. The broad legacy
        // projection namespace remains for career projections, but game keys
        // are explicitly protected here.
        return "user-projection".equals(spec.family())
                ? scanned.filter(key -> !key.contains(":game:") && !key.endsWith(":game-ids"))
                : scanned;
    }

    private Mono<List<String>> discoverKeys(PatternSpec spec) {
        return RuntimeOperationMetrics.measure(
                "career.cleanup.discovery." + spec.family(),
                scanKeys(spec).collectList());
    }

    private Flux<Long> deleteDiscoveries(List<Discovery> discoveries, CleanupAccumulator accumulator) {
        List<KeyHit> children = new ArrayList<>();
        List<KeyHit> roots = new ArrayList<>();
        for (Discovery discovery : discoveries) {
            String family = discovery.spec().family();
            accumulator.activeFamily(family);
            for (String key : discovery.keys()) {
                KeyHit hit = new KeyHit(family, key);
                accumulator.discovered(hit);
                if ("career-root".equals(family)) {
                    roots.add(hit);
                } else {
                    children.add(hit);
                }
            }
        }
        // Child deletion is safe to coalesce because ownership and generation
        // were validated before discovery. The root remains a separate final
        // batch so root-last and retry-anchor semantics are unchanged.
        return deleteBatches(children, accumulator)
                .concatWith(deleteBatches(roots, accumulator));
    }

    private Flux<Long> deleteBatches(List<KeyHit> hits, CleanupAccumulator accumulator) {
        List<List<KeyHit>> batches = new ArrayList<>();
        List<KeyHit> current = new ArrayList<>(MAX_BATCH_SIZE);
        for (KeyHit hit : hits) {
            if (!accumulator.markUnique(hit.key(), hit.family())) {
                continue;
            }
            current.add(hit);
            if (current.size() == MAX_BATCH_SIZE) {
                batches.add(current);
                current = new ArrayList<>(MAX_BATCH_SIZE);
            }
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return Flux.fromIterable(batches)
                .concatMap(batch -> {
                    accumulator.activeFamily(batch.get(0).family());
                    accumulator.batchRequested(batch);
                    List<String> keys = batch.stream().map(KeyHit::key).toList();
                    return RuntimeOperationMetrics.measure(
                            "career.cleanup.unlink.batch",
                            redisTemplate.unlink(Flux.fromIterable(keys)).timeout(unlinkTimeout))
                            .flatMap(deleted -> accumulator.batchDeleted(redisTemplate, batch, deleted));
                });
    }

    private String indexKey(UUID userId) {
        return "user:" + userId + ":career-ids";
    }

    private String ownerMappingKey(String careerId) {
        return CAREER_OWNER_PREFIX + careerId;
    }

    private String generationKey(String careerId) {
        return "career-generation:" + careerId;
    }

    private String mappingTokenKey(String careerId) {
        return "career-mapping-token:" + careerId;
    }

    private static final class RedisCareerOwnershipKeys {
        private static final String MANIFEST_VERSION = "1";

        static String manifestKey(String careerId) {
            return "career-cleanup-members:" + careerId;
        }

        static String manifestVersionKey(String careerId) {
            return "career-cleanup-manifest-version:" + careerId;
        }
    }

    private Mono<Void> writeTombstone(UUID userId, String careerId) {
        String payload = "ownerHash=" + shortHash(userId.toString())
                + ";career=" + (careerId == null ? "" : careerId)
                + ";state=RESETTING;phase=DELETE";
        Mono<Boolean> write = redisTemplate.opsForValue().set(
                "career-cleanup:" + userId, payload, TOMBSTONE_TTL);
        return write == null ? Mono.empty() : write.then();
    }

    private Mono<Void> clearTombstone(UUID userId) {
        Mono<Long> delete = redisTemplate.delete("career-cleanup:" + userId);
        return delete == null ? Mono.empty() : delete.then();
    }

    private static final class OwnershipRejectedException extends RuntimeException {
    }

    private record PatternSpec(String family, String pattern) { }
    private record Discovery(int order, PatternSpec spec, List<String> keys) { }
    private record KeyHit(String family, String key) { }

    private static final class CleanupAccumulator {
        private final Instant started = Instant.now();
        private final String ownerHash;
        private final UUID ownerId;
        private final List<String> validatedCareerIds = new ArrayList<>();
        private int careerCount;
        private final Map<String, MutableFamily> families = new LinkedHashMap<>();
        private final Map<String, String> uniqueKeyFamilies = new LinkedHashMap<>();
        private long discovered;
        private long requested;
        private long deleted;
        private long missingAtDelete;
        private long unexplainedShortfall;
        private int patterns;
        private int batches;
        private int maxBatch;
        private int ownershipMismatchCount;
        private String activeFamily = "unknown";
        private CareerDataCleanupResult.Status status = CareerDataCleanupResult.Status.IN_PROGRESS;
        private String failureReason = "";

        private CleanupAccumulator(UUID userId, String careerId) {
            this.ownerId = userId;
            this.ownerHash = shortHash(userId.toString());
            this.careerCount = careerId == null || careerId.isBlank() ? 0 : 1;
        }

        void setCareerCount(List<String> indexedCareerIds, String explicitCareerId) {
            validatedCareerIds.clear();
            validatedCareerIds.addAll(indexedCareerIds);
            this.careerCount = (int) indexedCareerIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .count();
            if (explicitCareerId != null && !explicitCareerId.isBlank()
                    && !indexedCareerIds.contains(explicitCareerId)) {
                this.careerCount++;
                validatedCareerIds.add(explicitCareerId);
            }
        }

        Mono<Void> restoreDiscovery(ReactiveRedisTemplate<String, String> redis) {
            if (status == CareerDataCleanupResult.Status.REJECTED_OWNERSHIP || validatedCareerIds.isEmpty()) {
                return Mono.empty();
            }
            String index = "user:" + ownerId + ":career-ids";
            return Flux.fromIterable(validatedCareerIds)
                    .distinct()
                    .concatMap(careerId -> redis.opsForValue()
                            .setIfAbsent("career-owner:" + careerId, ownerId.toString(), java.time.Duration.ofDays(31))
                            .then(redis.opsForSet().add(index, careerId)))
                    .then(redis.expire(index, java.time.Duration.ofDays(31)))
                    .timeout(Duration.ofSeconds(10))
                    .then();
        }

        boolean isOwnershipRejected() {
            return status == CareerDataCleanupResult.Status.REJECTED_OWNERSHIP;
        }

        void patternEvaluated(PatternSpec spec) {
            patterns++;
            families.computeIfAbsent(spec.family(), ignored -> new MutableFamily());
            activeFamily = spec.family();
        }

        void activeFamily(String family) {
            activeFamily = family;
        }

        void discovered(KeyHit hit) {
            discovered++;
            families.computeIfAbsent(hit.family(), ignored -> new MutableFamily()).discovered++;
        }

        boolean markUnique(String key, String family) {
            return uniqueKeyFamilies.putIfAbsent(key, family) == null;
        }

        void batchRequested(List<KeyHit> batch) {
            requested += batch.size();
            batches++;
            maxBatch = Math.max(maxBatch, batch.size());
            batch.forEach(hit -> families.computeIfAbsent(hit.family(), ignored -> new MutableFamily()).requested++);
        }

        Mono<Long> batchDeleted(ReactiveRedisTemplate<String, String> redis,
                                List<KeyHit> batch, long count) {
            if (count > batch.size()) {
                failHard("REDIS_DELETE_COUNT_INVALID");
                return Mono.error(new IllegalStateException("Redis deleted more keys than requested"));
            }
            long shortfall = batch.size() - count;
            if (shortfall == 0) {
                batch.forEach(hit -> recordDeleted(hit.family(), 1));
                return Mono.just(count);
            }
            return Flux.fromIterable(batch)
                    .concatMap(hit -> redis.hasKey(hit.key()).timeout(EXISTS_TIMEOUT))
                    .filter(Boolean::booleanValue)
                    .count()
                    .flatMap(stillPresent -> {
                        missingAtDelete += shortfall - stillPresent;
                        if (stillPresent > 0) {
                            unexplainedShortfall += stillPresent;
                            fail("UNEXPLAINED_SHORTFALL");
                            return Mono.error(new IllegalStateException("Redis cleanup shortfall remains"));
                        }
                        batch.stream().limit((int) count).forEach(hit -> recordDeleted(hit.family(), 1));
                        return Mono.just(count);
                    });
        }

        private void recordDeleted(String familyName, long count) {
            deleted += count;
            MutableFamily family = families.computeIfAbsent(familyName, ignored -> new MutableFamily());
            family.deleted += count;
        }

        String failedFamily() {
            return activeFamily;
        }

        void rejectOwnership(String reason) {
            ownershipMismatchCount++;
            status = CareerDataCleanupResult.Status.REJECTED_OWNERSHIP;
            failureReason = reason;
        }

        void fail(String reason) {
            if (status == CareerDataCleanupResult.Status.REJECTED_OWNERSHIP
                    || status == CareerDataCleanupResult.Status.FAILED) {
                return;
            }
            status = requested > 0
                    ? CareerDataCleanupResult.Status.PARTIAL_RETRYABLE
                    : CareerDataCleanupResult.Status.FAILED;
            failureReason = reason;
        }

        void failHard(String reason) {
            if (status == CareerDataCleanupResult.Status.REJECTED_OWNERSHIP) {
                return;
            }
            status = CareerDataCleanupResult.Status.FAILED;
            failureReason = reason;
        }

        String failureReasonFor(Throwable error) {
            if (!failureReason.isBlank()) {
                return failureReason;
            }
            if (error instanceof java.util.concurrent.TimeoutException) {
                return "CLEANUP_TIMEOUT";
            }
            return error instanceof OwnershipRejectedException ? "OWNER_MAPPING_REJECTED" : "REDIS_OPERATION_FAILED";
        }

        CareerDataCleanupResult result(boolean partialFailure, String failedFamily) {
            Map<String, CareerDataCleanupResult.FamilyCleanupResult> counts = new LinkedHashMap<>();
            families.forEach((family, values) -> counts.put(family,
                    new CareerDataCleanupResult.FamilyCleanupResult(values.discovered, values.requested, values.deleted)));
            CareerDataCleanupResult.Status resultStatus = partialFailure
                    ? status
                    : CareerDataCleanupResult.Status.COMPLETED;
            return new CareerDataCleanupResult(patterns, discovered, uniqueKeyFamilies.size(), requested, deleted,
                    missingAtDelete, unexplainedShortfall, batches, maxBatch, ownerHash, careerCount,
                    ownershipMismatchCount, counts, partialFailure, failedFamily, failureReason, resultStatus,
                    java.time.Duration.between(started, Instant.now()).toMillis());
        }

        private static final class MutableFamily {
            long discovered;
            long requested;
            long deleted;
        }
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}

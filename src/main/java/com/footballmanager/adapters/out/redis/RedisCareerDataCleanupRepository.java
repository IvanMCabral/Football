package com.footballmanager.adapters.out.redis;

import com.footballmanager.application.service.world.WorldPersistedWriter;

import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.application.observability.RuntimeOperationMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
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
@WorldPersistedWriter(root = String.class, writeMethod = "writeCleanupMarker",
        storageFamily = "career-cleanup:*",
        role = WorldPersistedWriter.DurabilityRole.EXPLICITLY_NON_WORLD_REFERENCE)
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
    private static final RedisScript<Long> COUNT_EXISTING_KEYS = RedisScript.of(
            "local count = 0; "
                    + "for i = 1, #KEYS do count = count + redis.call('exists', KEYS[i]) end; "
                    + "return count",
            Long.class);
    /** Empty modern manifests can finalize in one bounded, fenced script. */
    private static final RedisScript<Long> EMPTY_MODERN_CLEANUP = RedisScript.of(
            "local owner = redis.call('get', KEYS[1]); "
                    + "if owner ~= ARGV[1] then return -1 end; "
                    + "if redis.call('get', KEYS[2]) ~= ARGV[2] then return -2 end; "
                    + "if redis.call('get', KEYS[3]) ~= ARGV[3] then return -3 end; "
                    + "local tomb = redis.call('get', KEYS[4]); "
                    + "if not tomb or not string.find(tomb, 'state=RESETTING') then return -4 end; "
                    + "if redis.call('scard', KEYS[5]) ~= 0 then return -5 end; "
                    + "if redis.call('sismember', KEYS[6], ARGV[4]) ~= 1 then return -6 end; "
                    + "if redis.call('exists', KEYS[7]) ~= 1 then return -7 end; "
                    + "local projectionCount = tonumber(ARGV[5]); "
                    + "local metadataCount = tonumber(ARGV[6]); "
                    + "local projectionDeleted = 0; "
                    + "local metadataDeleted = 0; "
                    + "for i = 8, 7 + projectionCount do projectionDeleted = projectionDeleted + redis.call('UNLINK', KEYS[i]) end; "
                    + "local metadataStart = 8 + projectionCount; "
                    + "for i = metadataStart, metadataStart + metadataCount - 1 do metadataDeleted = metadataDeleted + redis.call('UNLINK', KEYS[i]) end; "
                    + "metadataDeleted = metadataDeleted + redis.call('UNLINK', KEYS[1]); "
                    + "metadataDeleted = metadataDeleted + redis.call('UNLINK', KEYS[2]); "
                    + "metadataDeleted = metadataDeleted + redis.call('UNLINK', KEYS[3]); "
                    + "metadataDeleted = metadataDeleted + redis.call('UNLINK', KEYS[5]); "
                    + "metadataDeleted = metadataDeleted + redis.call('UNLINK', KEYS[6]); "
                    + "local rootDeleted = redis.call('UNLINK', KEYS[7]); "
                    + "redis.call('DEL', KEYS[4]); "
                    + "return projectionDeleted + metadataDeleted + rootDeleted",
            Long.class);
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
        Mono<OwnershipValidation> ownershipReady;
        long metadataStarted = System.nanoTime();
        if (careerId != null && !careerId.isBlank()) {
            Mono<OwnershipValidation> validated = validateOwnershipAndManifest(userId, careerId, accumulator).cache();
            ownershipReady = Mono.when(timedTombstone(userId, careerId, accumulator), validated).then(validated);
        } else {
            ownershipReady = Mono.when(timedTombstone(userId, careerId, accumulator), indexedCareerIds)
                    .then(indexedCareerIds)
                    .flatMap(indexedIds -> {
                        if (indexedIds.size() > MAX_INDEX_CARDINALITY) {
                            accumulator.fail("CAREER_INDEX_CARDINALITY_EXCEEDED");
                            return Mono.error(new IllegalStateException("career index cardinality exceeded"));
                        }
                        return validateOwnership(userId, careerId, indexedIds, accumulator)
                                .map(ids -> new OwnershipValidation(ids, null, null));
                    });
        }
        ownershipReady = ownershipReady.doOnSuccess(ignored ->
                accumulator.ownershipValidation(System.nanoTime() - metadataStarted));
        return ownershipReady.flatMap(ownedIds -> {
                    return Mono.just(ownedIds);
                })
                .flatMapMany(validation -> {
                    List<String> ownedCareerIds = validation.careerIds();
                    accumulator.setCareerCount(ownedCareerIds, careerId);
                    accumulator.generation(validation.generation());
                    List<PatternSpec> specs = patterns(userId, careerId, ownedCareerIds, preserveWorld);
                    specs.forEach(accumulator::patternEvaluated);
                    long discoveryStarted = System.nanoTime();
                    return discoverForReset(userId, careerId, ownedCareerIds, specs, preserveWorld, accumulator,
                            validation.manifestVersion())
                            .doOnNext(ignored -> accumulator.discovery(System.nanoTime() - discoveryStarted))
                            .flatMapMany(discoveries -> deleteDiscoveries(userId, careerId, discoveries, accumulator));
                })
                .then(Mono.fromSupplier(() -> accumulator.result(false, "")))
                .flatMap(result -> clearTombstone(userId, accumulator).thenReturn(result))
                .timeout(totalTimeout)
                .onErrorResume(error -> accumulator.restoreDiscovery(redisTemplate)
                        .onErrorResume(ignored -> Mono.empty())
                        .then(accumulator.isOwnershipRejected()
                                ? clearTombstone(userId, accumulator)
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
                                                    boolean preserveWorld,
                                                    CleanupAccumulator accumulator,
                                                    String knownManifestVersion) {
        if (careerId == null || careerId.isBlank() || ownedCareerIds.size() != 1
                || !ownedCareerIds.contains(careerId)) {
            accumulator.pathLegacy();
            return discoverLegacy(legacySpecs, accumulator);
        }
        if (knownManifestVersion != null) {
            accumulator.manifestVersion(knownManifestVersion);
            return selectDiscoveryPath(userId, careerId, legacySpecs, preserveWorld, accumulator,
                    knownManifestVersion);
        }
        long started = System.nanoTime();
        return manifestVersion(careerId)
                .doOnNext(version -> {
                    accumulator.manifestRead(System.nanoTime() - started);
                    accumulator.manifestVersion(version);
                })
                .flatMap(version -> selectDiscoveryPath(userId, careerId, legacySpecs, preserveWorld,
                        accumulator, version))
                .switchIfEmpty(Mono.defer(() -> {
                    accumulator.manifestRead(System.nanoTime() - started);
                    accumulator.pathLegacy();
                    return discoverLegacy(legacySpecs, accumulator);
                }));
    }

    private Mono<List<Discovery>> selectDiscoveryPath(UUID userId, String careerId,
                                                       List<PatternSpec> legacySpecs,
                                                       boolean preserveWorld,
                                                       CleanupAccumulator accumulator,
                                                       String version) {
        if (version != null) {
            accumulator.manifestVersion(version);
        }
        return Mono.justOrEmpty(version)
                .flatMap(marker -> {
                    if (!isModernManifest(marker)) {
                        accumulator.pathLegacy();
                        return discoverLegacy(legacySpecs, accumulator);
                    }
                    accumulator.pathModern();
                    return discoverModern(userId, careerId, preserveWorld, accumulator);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    accumulator.pathLegacy();
                    return discoverLegacy(legacySpecs, accumulator);
                }));
    }

    private Mono<List<Discovery>> discoverModern(UUID userId, String careerId, boolean preserveWorld,
                                                 CleanupAccumulator accumulator) {
        List<Discovery> discoveries = new ArrayList<>();
        PatternSpec projection = new PatternSpec("user-projection", "user:" + userId + ":*");
        long projectionStarted = System.nanoTime();
        long manifestStarted = System.nanoTime();
        Mono<List<String>> projectionKeys = discoverKeys(projection, accumulator).doOnNext(keys -> {
            accumulator.projectionMatches(keys.size());
            accumulator.projectionScan(System.nanoTime() - projectionStarted);
        });
        Mono<List<String>> manifestKeys = manifestMembers(careerId).doOnNext(members -> {
            accumulator.manifestEntries(members.size());
            accumulator.manifestRead(System.nanoTime() - manifestStarted);
        });
        return Mono.zip(projectionKeys, manifestKeys).map(values -> {
            int order = 0;
            discoveries.add(new Discovery(order++, projection, values.getT1()));
            if (!preserveWorld) {
                discoveries.add(new Discovery(order++, new PatternSpec("world", "world:" + userId),
                        List.of("world:" + userId)));
            }
            discoveries.add(new Discovery(order++, new PatternSpec("career-index", indexKey(userId)),
                    List.of(indexKey(userId))));
            int nextOrder = order;
            List<String> members = values.getT2();
            int orderInManifest = nextOrder;
            discoveries.add(new Discovery(orderInManifest++, new PatternSpec("cleanup-manifest",
                    RedisCareerOwnershipKeys.manifestKey(careerId)), new ArrayList<>(members)));
            discoveries.add(new Discovery(orderInManifest++, new PatternSpec("cleanup-manifest-metadata",
                    RedisCareerOwnershipKeys.manifestKey(careerId)), List.of(
                            RedisCareerOwnershipKeys.manifestKey(careerId),
                            RedisCareerOwnershipKeys.manifestVersionKey(careerId))));
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
    }

    private Mono<List<Discovery>> discoverLegacy(List<PatternSpec> specs, CleanupAccumulator accumulator) {
        return Flux.range(0, specs.size())
                .flatMap(index -> discoverKeys(specs.get(index), accumulator)
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

    /**
     * Explicit-career reset validation reads the owner mapping and lifecycle
     * marker together.  This removes one sequential provider round trip while
     * keeping the mapping check fail-closed; the marker is only a discovery
     * hint and never an ownership decision.
     */
    private Mono<OwnershipValidation> validateOwnershipAndManifest(UUID userId,
                                                                     String careerId,
                                                                     CleanupAccumulator accumulator) {
        var operations = redisTemplate.opsForValue();
        if (operations == null) {
            return validateOwnership(userId, careerId, List.of(careerId), accumulator)
                    .map(ids -> new OwnershipValidation(ids, null, null));
        }
        Mono<List<String>> values = operations.multiGet(List.of(
                ownerMappingKey(careerId),
                RedisCareerOwnershipKeys.manifestVersionKey(careerId),
                generationKey(careerId)));
        if (values == null) {
            return validateOwnership(userId, careerId, List.of(careerId), accumulator)
                    .map(ids -> new OwnershipValidation(ids, null, null));
        }
        return values.timeout(indexTimeout)
                .flatMap(items -> {
                    String mappedOwner = items != null && !items.isEmpty() ? items.get(0) : null;
                    if (mappedOwner == null || mappedOwner.isBlank()) {
                        accumulator.rejectOwnership("OWNER_MAPPING_MISSING");
                        return Mono.error(new OwnershipRejectedException());
                    }
                    if (!mappedOwner.equals(userId.toString())) {
                        accumulator.rejectOwnership("OWNER_MAPPING_MISMATCH");
                        return Mono.error(new OwnershipRejectedException());
                    }
                    String version = items.size() > 1 ? items.get(1) : null;
                    String generation = items.size() > 2 ? items.get(2) : null;
                    return Mono.just(new OwnershipValidation(List.of(careerId), version, generation));
                });
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

    private Mono<List<String>> discoverKeys(PatternSpec spec, CleanupAccumulator accumulator) {
        accumulator.scanStarted(spec.family());
        return discoverKeys(spec).doOnNext(ignored -> accumulator.scanCompleted(spec.family()));
    }

    private Flux<Long> deleteDiscoveries(UUID userId, String careerId,
                                         List<Discovery> discoveries, CleanupAccumulator accumulator) {
        List<KeyHit> children = new ArrayList<>();
        List<KeyHit> metadata = new ArrayList<>();
        List<KeyHit> roots = new ArrayList<>();
        for (Discovery discovery : discoveries) {
            String family = discovery.spec().family();
            accumulator.activeFamily(family);
            for (String key : discovery.keys()) {
                KeyHit hit = new KeyHit(family, key);
                accumulator.discovered(hit);
                if ("career-root".equals(family)) {
                    roots.add(hit);
                } else if ("cleanup-manifest".equals(family) || "user-projection".equals(family)) {
                    children.add(hit);
                } else {
                    metadata.add(hit);
                }
            }
        }
        if (accumulator.canUseEmptyModernFastPath(careerId, children, metadata, roots)) {
            return deleteEmptyModernFastPath(userId, careerId, children, metadata, roots, accumulator);
        }
        // Child deletion is safe to coalesce because ownership and generation
        // were validated before discovery. The root remains a separate final
        // batch so root-last and retry-anchor semantics are unchanged.
        return deleteBatches(children, accumulator, DeleteKind.CHILD)
                .concatWith(deleteBatches(metadata, accumulator, DeleteKind.METADATA))
                .concatWith(deleteBatches(roots, accumulator, DeleteKind.ROOT));
    }

    private Flux<Long> deleteEmptyModernFastPath(UUID userId, String careerId,
                                                  List<KeyHit> children,
                                                  List<KeyHit> metadata,
                                                  List<KeyHit> roots,
                                                  CleanupAccumulator accumulator) {
        List<String> projectionKeys = children.stream()
                .filter(hit -> "user-projection".equals(hit.family()))
                .map(KeyHit::key)
                .distinct()
                .toList();
        List<String> protectedKeys = List.of(
                ownerMappingKey(careerId), generationKey(careerId),
                RedisCareerOwnershipKeys.manifestVersionKey(careerId),
                "career-cleanup:" + userId,
                RedisCareerOwnershipKeys.manifestKey(careerId), indexKey(userId),
                roots.get(0).key());
        List<String> metadataKeys = metadata.stream().map(KeyHit::key).distinct()
                .filter(key -> !protectedKeys.contains(key))
                .toList();
        if (roots.size() != 1 || projectionKeys.size() + metadataKeys.size() + 1 > MAX_BATCH_SIZE
                || accumulator.generation.isBlank()) {
            return deleteBatches(children, accumulator, DeleteKind.CHILD)
                    .concatWith(deleteBatches(metadata, accumulator, DeleteKind.METADATA))
                    .concatWith(deleteBatches(roots, accumulator, DeleteKind.ROOT));
        }
        List<KeyHit> all = new ArrayList<>();
        all.addAll(children);
        all.addAll(metadata);
        all.addAll(roots);
        if (!accumulator.prepareAtomic(all)) {
            return Flux.empty();
        }
        List<String> keys = new ArrayList<>();
        keys.add(ownerMappingKey(careerId));
        keys.add(generationKey(careerId));
        keys.add(RedisCareerOwnershipKeys.manifestVersionKey(careerId));
        keys.add("career-cleanup:" + userId);
        keys.add(RedisCareerOwnershipKeys.manifestKey(careerId));
        keys.add(indexKey(userId));
        keys.add(roots.get(0).key());
        keys.addAll(projectionKeys);
        keys.addAll(metadataKeys);
        long started = System.nanoTime();
        Flux<Long> result = redisTemplate.execute(
                EMPTY_MODERN_CLEANUP,
                keys,
                userId.toString(), accumulator.generation,
                RedisCareerOwnershipKeys.MANIFEST_VERSION,
                careerId, Integer.toString(projectionKeys.size()),
                Integer.toString(metadataKeys.size()));
        if (result == null) {
            return deleteBatches(children, accumulator, DeleteKind.CHILD)
                    .concatWith(deleteBatches(metadata, accumulator, DeleteKind.METADATA))
                    .concatWith(deleteBatches(roots, accumulator, DeleteKind.ROOT));
        }
        return result.timeout(unlinkTimeout)
                .singleOrEmpty()
                .flatMapMany(deleted -> {
                    accumulator.atomicCleanup(System.nanoTime() - started);
                    if (deleted < 0) {
                        accumulator.failHard("MODERN_EMPTY_FENCE_REJECTED");
                        return Flux.error(new IllegalStateException("modern empty cleanup fence rejected"));
                    }
                    accumulator.atomicScriptCompleted(projectionKeys, metadataKeys, roots.get(0).key(), deleted);
                    return Flux.just(deleted);
                });
    }

    private Flux<Long> deleteBatches(List<KeyHit> hits, CleanupAccumulator accumulator, DeleteKind kind) {
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
                    long unlinkStarted = System.nanoTime();
                    return RuntimeOperationMetrics.measure(
                            "career.cleanup.unlink.batch",
                            redisTemplate.unlink(Flux.fromIterable(keys)).timeout(unlinkTimeout))
                            .flatMap(deleted -> {
                                accumulator.unlinkDuration(kind, System.nanoTime() - unlinkStarted);
                                return accumulator.batchDeleted(redisTemplate, batch, deleted);
                            });
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

    private Mono<Void> timedTombstone(UUID userId, String careerId, CleanupAccumulator accumulator) {
        long started = System.nanoTime();
        return writeTombstone(userId, careerId)
                .doOnSubscribe(ignored -> accumulator.tombstoneCommand())
                .doFinally(signal -> accumulator.tombstone(System.nanoTime() - started));
    }

    private Mono<Void> clearTombstone(UUID userId, CleanupAccumulator accumulator) {
        accumulator.tombstoneClearCommand();
        Mono<Long> delete = redisTemplate.delete("career-cleanup:" + userId);
        return delete == null ? Mono.empty() : delete.then();
    }

    private static final class OwnershipRejectedException extends RuntimeException {
    }

    private record PatternSpec(String family, String pattern) { }
    private record Discovery(int order, PatternSpec spec, List<String> keys) { }
    private record KeyHit(String family, String key) { }
    private enum DeleteKind { CHILD, METADATA, ROOT }
    private record OwnershipValidation(List<String> careerIds, String manifestVersion, String generation) { }

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
        private String path = "";
        private String manifestVersion = "";
        private int manifestEntries;
        private long scanCount;
        private long discoveryNanos;
        private long manifestReadNanos;
        private long projectionScanNanos;
        private long childUnlinkNanos;
        private long rootUnlinkNanos;
        private long metadataNanos;
        private long ownershipValidationNanos;
        private long existsNanos;
        private long tombstoneNanos;
        private long atomicCleanupNanos;
        private long projectionMatches;
        private int childDeleteCommands;
        private int metadataDeleteCommands;
        private int rootDeleteCommands;
        private int tombstoneCommands;
        private int tombstoneClearCommands;
        private int existsCommands;
        private int atomicScriptCommands;
        private String generation = "";

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

        void pathModern() { path = "MODERN_MANIFEST"; }
        void pathLegacy() { path = "LEGACY_SCAN"; }
        void manifestVersion(String value) { manifestVersion = value == null ? "" : value; }
        void manifestEntries(int value) { manifestEntries = Math.max(manifestEntries, value); }
        void scanStarted(String family) { scanCount++; }
        void scanCompleted(String family) { }
        void manifestRead(long nanos) { manifestReadNanos += nanos; }
        void projectionScan(long nanos) { projectionScanNanos += nanos; }
        void discovery(long nanos) { discoveryNanos += nanos; }
        void unlinkDuration(DeleteKind kind, long nanos) {
            switch (kind) {
                case CHILD -> { childUnlinkNanos += nanos; childDeleteCommands++; }
                case METADATA -> { metadataNanos += nanos; metadataDeleteCommands++; }
                case ROOT -> { rootUnlinkNanos += nanos; rootDeleteCommands++; }
            }
        }
        void ownershipValidation(long nanos) { ownershipValidationNanos += nanos; }
        void exists(long nanos) { existsNanos += nanos; }
        void tombstone(long nanos) { tombstoneNanos += nanos; }
        void tombstoneCommand() { tombstoneCommands++; }
        void tombstoneClearCommand() { tombstoneClearCommands++; }
        void projectionMatches(long count) { projectionMatches += count; }
        void generation(String value) { generation = value == null ? "" : value; }
        void atomicCleanup(long nanos) { atomicCleanupNanos += nanos; }

        boolean canUseEmptyModernFastPath(String careerId, List<KeyHit> children,
                                          List<KeyHit> metadata, List<KeyHit> roots) {
            return "MODERN_MANIFEST".equals(path)
                    && manifestEntries == 0
                    && careerId != null && !careerId.isBlank()
                    && children.stream().noneMatch(hit -> "cleanup-manifest".equals(hit.family()))
                    && roots.size() == 1;
        }

        boolean prepareAtomic(List<KeyHit> hits) {
            for (KeyHit hit : hits) {
                if (!markUnique(hit.key(), hit.family())) {
                    continue;
                }
            }
            batchRequested(hits);
            return true;
        }

        void atomicScriptCompleted(List<String> projections, List<String> metadata,
                                   String root, long deleted) {
            atomicScriptCommands++;
            metadataDeleteCommands++;
            rootDeleteCommands++;
            long projectionDeleted = Math.min(deleted, projections.size());
            long rootDeleted = deleted >= projectionDeleted + 1 ? 1 : 0;
            long metadataDeleted = Math.max(0, deleted - projectionDeleted - rootDeleted);
            projections.stream().limit(projectionDeleted).forEach(key -> recordDeleted("user-projection", 1));
            metadata.stream().limit(metadataDeleted).forEach(key -> recordDeleted("metadata", 1));
            if (rootDeleted > 0) {
                recordDeleted("career-root", 1);
            }
            missingAtDelete += Math.max(0, requested - deleted);
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
            long existsStarted = System.nanoTime();
            Mono<Long> existing = countExistingKeys(redis, batch);
            return existing
                    .doOnSubscribe(ignored -> existsCommands++)
                    .doFinally(signal -> {
                        existsNanos += System.nanoTime() - existsStarted;
                    })
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

        private Mono<Long> countExistingKeys(ReactiveRedisTemplate<String, String> redis,
                                              List<KeyHit> batch) {
            List<String> keys = batch.stream().map(KeyHit::key).toList();
            Flux<Long> scriptResult = redis.execute(COUNT_EXISTING_KEYS, keys);
            if (scriptResult != null) {
                return scriptResult.singleOrEmpty().timeout(EXISTS_TIMEOUT);
            }
            return Flux.fromIterable(batch)
                    .concatMap(hit -> redis.hasKey(hit.key()).timeout(EXISTS_TIMEOUT))
                    .filter(Boolean::booleanValue)
                    .count();
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
            Map<String, String> diagnostics = new LinkedHashMap<>();
            diagnostics.put("path", path);
            diagnostics.put("manifestVersion", manifestVersion);
            diagnostics.put("manifestEntries", Integer.toString(manifestEntries));
            diagnostics.put("scanCount", Long.toString(scanCount));
            diagnostics.put("projectionScanMatches", Long.toString(projectionMatches));
            diagnostics.put("discoveryMs", Long.toString(discoveryNanos / 1_000_000L));
            diagnostics.put("manifestReadMs", Long.toString(manifestReadNanos / 1_000_000L));
            diagnostics.put("projectionScanMs", Long.toString(projectionScanNanos / 1_000_000L));
            diagnostics.put("childUnlinkMs", Long.toString(childUnlinkNanos / 1_000_000L));
            diagnostics.put("rootUnlinkMs", Long.toString(rootUnlinkNanos / 1_000_000L));
            diagnostics.put("metadataMs", Long.toString(metadataNanos / 1_000_000L));
            diagnostics.put("ownershipValidationMs", Long.toString(ownershipValidationNanos / 1_000_000L));
            diagnostics.put("existsMs", Long.toString(existsNanos / 1_000_000L));
            diagnostics.put("atomicCleanupMs", Long.toString(atomicCleanupNanos / 1_000_000L));
            diagnostics.put("atomicScriptCommands", Integer.toString(atomicScriptCommands));
            diagnostics.put("tombstoneMs", Long.toString(tombstoneNanos / 1_000_000L));
            diagnostics.put("childDeleteCommands", Integer.toString(childDeleteCommands));
            diagnostics.put("metadataDeleteCommands", Integer.toString(metadataDeleteCommands));
            diagnostics.put("rootDeleteCommands", Integer.toString(rootDeleteCommands));
            diagnostics.put("existsCommands", Integer.toString(existsCommands));
            diagnostics.put("tombstoneCommands", Integer.toString(tombstoneCommands + tombstoneClearCommands));
            int sequentialLayers = "MODERN_MANIFEST".equals(path)
                    ? (atomicScriptCommands > 0
                    ? 3
                    : 2 + (childDeleteCommands > 0 ? 1 : 0)
                    + (metadataDeleteCommands > 0 ? 1 : 0)
                    + (rootDeleteCommands > 0 ? 1 : 0)
                    + (tombstoneClearCommands > 0 ? 1 : 0))
                    : 0;
            diagnostics.put("sequentialRemoteLayers", Integer.toString(sequentialLayers));
            return new CareerDataCleanupResult(patterns, discovered, uniqueKeyFamilies.size(), requested, deleted,
                    missingAtDelete, unexplainedShortfall, batches, maxBatch, ownerHash, careerCount,
                    ownershipMismatchCount, counts, partialFailure, failedFamily, failureReason, resultStatus,
                    java.time.Duration.between(started, Instant.now()).toMillis(), diagnostics);
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

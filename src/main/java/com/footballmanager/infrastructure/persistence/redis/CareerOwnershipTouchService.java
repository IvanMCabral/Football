package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.career.CareerLifecycleCoordinator;
import com.footballmanager.application.port.out.CareerOwnershipPort;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Renews the ownership discovery keys before writing career-derived data. */
@Component
public final class CareerOwnershipTouchService implements CareerOwnershipPort {

    private static final Duration ROOT_TTL = Duration.ofDays(30);
    private static final Duration OWNERSHIP_TTL = Duration.ofDays(31);
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_INDEX_SIZE = 256;
    private static final int MAX_MANIFEST_ENTRIES = 1_024;
    private static final Duration MANIFEST_TTL = Duration.ofDays(31);
    private static final String MANIFEST_PREFIX = "career-cleanup-members:";
    private static final String MANIFEST_VERSION_PREFIX = "career-cleanup-manifest-version:";
    private static final String MANIFEST_VERSION = "1";
    private static final RedisScript<Long> REGISTER_MANIFEST_KEY = RedisScript.of(
            "if redis.call('get', KEYS[2]) ~= ARGV[1] then return 0 end; "
                    + "if redis.call('get', KEYS[3]) ~= ARGV[2] then return -1 end; "
                    + "if redis.call('get', KEYS[4]) ~= ARGV[3] then return -2 end; "
                    + "if redis.call('exists', KEYS[5]) == 1 then return -4 end; "
                    + "if redis.call('sismember', KEYS[1], ARGV[4]) == 1 then "
                    + "redis.call('expire', KEYS[1], ARGV[5]); return 1 end; "
                    + "if redis.call('scard', KEYS[1]) >= tonumber(ARGV[6]) then return -3 end; "
                    + "redis.call('sadd', KEYS[1], ARGV[4]); redis.call('expire', KEYS[1], ARGV[5]); return 1",
            Long.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CareerLifecycleCoordinator lifecycleCoordinator;

    public CareerOwnershipTouchService(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            CareerLifecycleCoordinator lifecycleCoordinator) {
        this.redisTemplate = redisTemplate;
        this.lifecycleCoordinator = lifecycleCoordinator;
    }

    public <T> Mono<T> touchBeforeWrite(String careerId, Supplier<Mono<T>> write) {
        return Mono.error(new IllegalStateException("career writer requires captured lifecycle generation"));
    }

    /** Captures the fencing token at the operation boundary, never at write time. */
    public Mono<CareerWriteContext> capture(UUID ownerId, String careerId) {
        return lifecycleCoordinator.serializeCareer(careerId,
                redisTemplate.opsForValue().get(mappingKey(careerId)).timeout(OPERATION_TIMEOUT)
                        .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping missing")))
                        .flatMap(mappedOwner -> {
                            if (ownerId == null || !ownerId.toString().equals(mappedOwner)) {
                                return Mono.error(new IllegalStateException("career ownership mapping conflict"));
                            }
                            return redisTemplate.opsForSet().isMember(indexKey(ownerId), careerId)
                                    .timeout(OPERATION_TIMEOUT)
                                    .filter(Boolean.TRUE::equals)
                                    .switchIfEmpty(Mono.error(new IllegalStateException("career ownership index missing")))
                                    .then(currentGeneration(careerId))
                                    .map(generation -> new CareerWriteContext(ownerId, careerId, generation));
                        }));
    }

    /**
     * Token-aware variant used by engines/sessions that retain lifecycle
     * metadata.  A stale token is rejected even if a new career reuses the
     * same owner and career identifier.
     */
    public <T> Mono<T> touchBeforeWrite(String careerId, String expectedGeneration, Supplier<Mono<T>> write) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (expectedGeneration == null || expectedGeneration.isBlank()) {
            return Mono.error(new IllegalStateException("career lifecycle generation is required"));
        }
        // Ownership is deliberately read inside the career queue.  Reading it
        // before queue admission would allow a stale callback to validate an
        // old mapping, wait behind reset, and then write after the reset.
        return lifecycleCoordinator.serializeCareer(careerId, Mono.defer(() ->
                redisTemplate.opsForValue().get(tombstoneKey(careerId)).timeout(OPERATION_TIMEOUT)
                        .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career lifecycle is resetting")))
                        .switchIfEmpty(redisTemplate.opsForValue().get(mappingKey(careerId)).timeout(OPERATION_TIMEOUT)
                                .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping missing")))
                                .flatMap(ownerValue -> {
                                    UUID owner;
                                    try {
                                        owner = UUID.fromString(ownerValue);
                                    } catch (IllegalArgumentException invalidOwner) {
                                        return Mono.error(new IllegalStateException("career ownership mapping invalid", invalidOwner));
                                    }
                                    return redisTemplate.opsForValue().get(tombstoneOwnerKey(owner)).timeout(OPERATION_TIMEOUT)
                                            .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career lifecycle is resetting")))
                                            .switchIfEmpty(validateGeneration(careerId, expectedGeneration)
                                                    .then(touch(owner, careerId))
                                                    .then(Mono.defer(write)));
                                }))));
    }

    public <T> Mono<T> touchBeforeWrite(CareerWriteContext context, Supplier<Mono<T>> write) {
        if (context == null) {
            return Mono.error(new IllegalArgumentException("career write context is required"));
        }
        return touchBeforeWrite(context.careerId(), context.expectedGeneration(),
                context.ownerId(), write);
    }

    /**
     * Fenced write for a lifecycle-owned Redis key.  The exact key is added
     * to the bounded cleanup manifest while the same career coordinator
     * section is held, so a stale callback cannot register or persist data.
     */
    public <T> Mono<T> touchBeforeWrite(CareerWriteContext context, String ownedKey,
                                        Supplier<Mono<T>> write) {
        if (ownedKey == null || ownedKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("owned Redis key is required"));
        }
        return touchBeforeWrite(context,
                () -> registerManifestKey(context, ownedKey)
                        .then(Mono.defer(write))
                        .onErrorResume(error -> unregisterManifestKey(context, ownedKey)
                                .onErrorResume(ignored -> Mono.empty())
                                .then(Mono.error(error))));
    }

    private Mono<Void> registerManifestKey(CareerWriteContext context, String ownedKey) {
        List<String> keys = java.util.List.of(
                manifestKey(context.careerId()),
                manifestVersionKey(context.careerId()),
                mappingKey(context.careerId()),
                generationKey(context.careerId()),
                tombstoneKey(context.careerId()));
        Mono<Long> result = redisTemplate.execute(
                REGISTER_MANIFEST_KEY,
                keys,
                MANIFEST_VERSION,
                context.ownerId().toString(),
                context.expectedGeneration(),
                ownedKey,
                Long.toString(MANIFEST_TTL.getSeconds()),
                Integer.toString(MAX_MANIFEST_ENTRIES))
                .singleOrEmpty();
        if (result == null) {
            return Mono.error(new IllegalStateException("cleanup manifest registration unavailable"));
        }
        return result.flatMap(code -> switch (code.intValue()) {
            case 0 -> Mono.empty(); // legacy career: write remains compatible
            case 1 -> Mono.empty();
            case -1, -2, -4 -> Mono.error(new IllegalStateException("career lifecycle fence rejected"));
            case -3 -> Mono.error(new IllegalStateException("career cleanup manifest cardinality exceeded"));
            default -> Mono.error(new IllegalStateException("cleanup manifest registration failed"));
        });
    }

    private Mono<Void> unregisterManifestKey(CareerWriteContext context, String ownedKey) {
        var operations = redisTemplate.opsForSet();
        if (operations == null) {
            return Mono.empty();
        }
        Mono<Long> removed = operations.remove(manifestKey(context.careerId()), ownedKey);
        return removed == null ? Mono.empty() : removed.then();
    }

    public static String manifestKey(String careerId) {
        return MANIFEST_PREFIX + careerId;
    }

    public static String manifestVersionKey(String careerId) {
        return MANIFEST_VERSION_PREFIX + careerId;
    }

    public static String manifestVersion() {
        return MANIFEST_VERSION;
    }

    private <T> Mono<T> touchBeforeWrite(String careerId, String expectedGeneration,
                                          UUID expectedOwner, Supplier<Mono<T>> write) {
        return lifecycleCoordinator.serializeCareer(careerId, Mono.defer(() ->
                redisTemplate.opsForValue().get(tombstoneKey(careerId)).timeout(OPERATION_TIMEOUT)
                        .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career lifecycle is resetting")))
                        .switchIfEmpty(redisTemplate.opsForValue().get(mappingKey(careerId)).timeout(OPERATION_TIMEOUT)
                        .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping missing")))
                        .flatMap(ownerValue -> {
                            if (expectedOwner != null && !expectedOwner.toString().equals(ownerValue)) {
                                return Mono.error(new IllegalStateException("career ownership mapping conflict"));
                            }
                            return validateGeneration(careerId, expectedGeneration)
                                    .then(touch(UUID.fromString(ownerValue), careerId))
                                    .then(Mono.defer(write));
                        }))));
    }

    public <T> Mono<T> touchOwnerBeforeWrite(UUID owner, Supplier<Mono<T>> write) {
        if (owner == null) {
            return Mono.error(new IllegalArgumentException("owner must not be null"));
        }
        String index = indexKey(owner);
        Mono<T> operation = Mono.defer(() -> redisTemplate.opsForValue()
                .get(tombstoneOwnerKey(owner)).timeout(OPERATION_TIMEOUT)
                .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career lifecycle is resetting")))
                .switchIfEmpty(redisTemplate.opsForSet().members(index)
                        .timeout(OPERATION_TIMEOUT)
                        .take(MAX_INDEX_SIZE + 1L)
                        .collectList()
                        .flatMap(careerIds -> {
                            if (careerIds.size() > MAX_INDEX_SIZE || careerIds.isEmpty()) {
                                return Mono.error(new IllegalStateException("career ownership index unavailable"));
                            }
                            return Flux.fromIterable(careerIds)
                                    .concatMap(careerId -> validateGeneration(careerId)
                                            .then(redisTemplate.opsForValue().get(mappingKey(careerId)).timeout(OPERATION_TIMEOUT))
                                            .filter(owner.toString()::equals)
                                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                                    "career ownership mapping invalid"))))
                                    .then(renewOwner(owner, careerIds, index))
                                    .then(Mono.defer(write));
                        })));
        return lifecycleCoordinator.serialize(owner, operation);
    }

    /** World initialization is allowed before the first career exists. */
    public <T> Mono<T> touchOwnerBeforeWriteIfCareerExists(UUID owner, Supplier<Mono<T>> write) {
        String index = indexKey(owner);
        return lifecycleCoordinator.serialize(owner, Mono.defer(() ->
                redisTemplate.opsForValue().get(tombstoneOwnerKey(owner)).timeout(OPERATION_TIMEOUT)
                        .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career lifecycle is resetting")))
                        .switchIfEmpty(redisTemplate.opsForSet().size(index).timeout(OPERATION_TIMEOUT)
                        .flatMap(size -> size == null || size == 0
                                        ? Mono.defer(write)
                                        : validateAndRenewOwner(owner, write, index)))));
    }

    /** Explicit pre-career world initialization. Runtime writers must not use this path. */
    public <T> Mono<T> initializeWorld(UUID owner, Supplier<Mono<T>> write) {
        return lifecycleCoordinator.serialize(owner, Mono.defer(() ->
                redisTemplate.opsForValue().get(tombstoneOwnerKey(owner)).timeout(OPERATION_TIMEOUT)
                        .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career lifecycle is resetting")))
                        .switchIfEmpty(redisTemplate.opsForValue().get(rootKey(owner)).timeout(OPERATION_TIMEOUT)
                                .flatMap(ignored -> Mono.<T>error(new IllegalStateException("career-owned world requires lifecycle context")))
                                .switchIfEmpty(redisTemplate.opsForSet().size(indexKey(owner)).timeout(OPERATION_TIMEOUT)
                                        .flatMap(size -> size == null || size == 0 ? Mono.defer(write)
                                                : Mono.error(new IllegalStateException("career-owned world requires lifecycle context")))))));
    }

    private Mono<Void> touch(UUID owner, String careerId) {
        String index = indexKey(owner);
        return redisTemplate.opsForSet().isMember(index, careerId).timeout(OPERATION_TIMEOUT)
                .flatMap(indexed -> indexed
                        ? renew(owner, careerId, index)
                        : Mono.error(new IllegalStateException("career ownership index missing")));
    }

    private Mono<Void> renew(UUID owner, String careerId, String index) {
        return redisTemplate.expire(mappingKey(careerId), OWNERSHIP_TTL).timeout(OPERATION_TIMEOUT)
                .then(redisTemplate.expire(generationKey(careerId), OWNERSHIP_TTL).timeout(OPERATION_TIMEOUT))
                .then(redisTemplate.expire(index, OWNERSHIP_TTL).timeout(OPERATION_TIMEOUT))
                .then(redisTemplate.expire(rootKey(owner), ROOT_TTL).timeout(OPERATION_TIMEOUT))
                .then();
    }

    private Mono<Void> renewOwner(UUID owner, java.util.List<String> careerIds, String index) {
        return Flux.fromIterable(careerIds)
                .concatMap(careerId -> redisTemplate.expire(mappingKey(careerId), OWNERSHIP_TTL)
                        .then(redisTemplate.expire(generationKey(careerId), OWNERSHIP_TTL)
                        .timeout(OPERATION_TIMEOUT))
                        )
                .then(redisTemplate.expire(index, OWNERSHIP_TTL).timeout(OPERATION_TIMEOUT))
                .then(redisTemplate.expire(rootKey(owner), ROOT_TTL).timeout(OPERATION_TIMEOUT))
                .then();
    }

    private <T> Mono<T> validateAndRenewOwner(UUID owner, Supplier<Mono<T>> write, String index) {
        return redisTemplate.opsForSet().members(index)
                .timeout(OPERATION_TIMEOUT)
                .take(MAX_INDEX_SIZE + 1L)
                .collectList()
                .flatMap(careerIds -> {
                    if (careerIds.isEmpty() || careerIds.size() > MAX_INDEX_SIZE) {
                        return Mono.error(new IllegalStateException("career ownership index unavailable"));
                    }
                    return Flux.fromIterable(careerIds)
                            .concatMap(careerId -> validateGeneration(careerId)
                                    .then(redisTemplate.opsForValue().get(mappingKey(careerId)).timeout(OPERATION_TIMEOUT))
                                    .filter(owner.toString()::equals)
                                    .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping invalid"))))
                            .then(renewOwner(owner, careerIds, index))
                            .then(Mono.defer(write));
                });
    }

    public Mono<String> currentGeneration(String careerId) {
        return redisTemplate.opsForValue().get(generationKey(careerId)).timeout(OPERATION_TIMEOUT)
                .switchIfEmpty(Mono.error(new IllegalStateException("career lifecycle generation missing")));
    }

    private Mono<Void> validateGeneration(String careerId) {
        return validateGeneration(careerId, null);
    }

    private Mono<Void> validateGeneration(String careerId, String expectedGeneration) {
        return currentGeneration(careerId)
                .filter(current -> expectedGeneration == null || expectedGeneration.equals(current))
                .switchIfEmpty(Mono.error(new IllegalStateException("career lifecycle generation is stale")))
                .then();
    }

    private String mappingKey(String careerId) {
        return "career-owner:" + careerId;
    }

    private String indexKey(UUID owner) {
        return "user:" + owner + ":career-ids";
    }

    private String rootKey(UUID owner) {
        return "career:" + owner;
    }

    private String generationKey(String careerId) {
        return "career-generation:" + careerId;
    }

    private String tombstoneKey(String careerId) {
        return "career-cleanup-career:" + careerId;
    }

    private String tombstoneOwnerKey(UUID owner) {
        return "career-cleanup:" + owner;
    }
}

package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.career.CareerLifecycleCoordinator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/** Renews the ownership discovery keys before writing career-derived data. */
@Component
public final class CareerOwnershipTouchService {

    private static final Duration ROOT_TTL = Duration.ofDays(30);
    private static final Duration OWNERSHIP_TTL = Duration.ofDays(31);
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_INDEX_SIZE = 256;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CareerLifecycleCoordinator lifecycleCoordinator;

    public CareerOwnershipTouchService(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            CareerLifecycleCoordinator lifecycleCoordinator) {
        this.redisTemplate = redisTemplate;
        this.lifecycleCoordinator = lifecycleCoordinator;
    }

    public <T> Mono<T> touchBeforeWrite(String careerId, Supplier<Mono<T>> write) {
        return touchBeforeWrite(careerId, null, write);
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

    private Mono<Void> touch(UUID owner, String careerId) {
        String index = indexKey(owner);
        return redisTemplate.opsForSet().isMember(index, careerId).timeout(OPERATION_TIMEOUT)
                .flatMap(indexed -> indexed
                        ? renew(owner, careerId, index)
                        : Mono.error(new IllegalStateException("career ownership index missing")));
    }

    private Mono<Void> renew(UUID owner, String careerId, String index) {
        return redisTemplate.expire(mappingKey(careerId), OWNERSHIP_TTL).timeout(OPERATION_TIMEOUT)
                .then(redisTemplate.expire(index, OWNERSHIP_TTL).timeout(OPERATION_TIMEOUT))
                .then(redisTemplate.expire(rootKey(owner), ROOT_TTL).timeout(OPERATION_TIMEOUT))
                .then();
    }

    private Mono<Void> renewOwner(UUID owner, java.util.List<String> careerIds, String index) {
        return Flux.fromIterable(careerIds)
                .concatMap(careerId -> redisTemplate.expire(mappingKey(careerId), OWNERSHIP_TTL)
                        .timeout(OPERATION_TIMEOUT))
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

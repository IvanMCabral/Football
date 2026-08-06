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
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        return redisTemplate.opsForValue().get(mappingKey(careerId))
                .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping missing")))
                .flatMap(ownerValue -> {
                    UUID owner;
                    try {
                        owner = UUID.fromString(ownerValue);
                    } catch (IllegalArgumentException invalidOwner) {
                        return Mono.error(new IllegalStateException("career ownership mapping invalid", invalidOwner));
                    }
                    return lifecycleCoordinator.serialize(owner,
                            touch(owner, careerId).then(Mono.defer(write)));
                });
    }

    public <T> Mono<T> touchOwnerBeforeWrite(UUID owner, Supplier<Mono<T>> write) {
        if (owner == null) {
            return Mono.error(new IllegalArgumentException("owner must not be null"));
        }
        String index = indexKey(owner);
        return redisTemplate.opsForSet().members(index)
                .take(MAX_INDEX_SIZE + 1L)
                .collectList()
                .flatMap(careerIds -> {
                    if (careerIds.size() > MAX_INDEX_SIZE || careerIds.isEmpty()) {
                        return Mono.error(new IllegalStateException("career ownership index unavailable"));
                    }
                    return Flux.fromIterable(careerIds)
                            .concatMap(careerId -> redisTemplate.opsForValue()
                                    .get(mappingKey(careerId))
                                    .filter(owner.toString()::equals)
                                    .switchIfEmpty(Mono.error(new IllegalStateException(
                                            "career ownership mapping invalid"))))
                            .then(lifecycleCoordinator.serialize(owner,
                                    renewOwner(owner, careerIds, index).then(Mono.defer(write))));
                });
    }

    /** World initialization is allowed before the first career exists. */
    public <T> Mono<T> touchOwnerBeforeWriteIfCareerExists(UUID owner, Supplier<Mono<T>> write) {
        String index = indexKey(owner);
        return redisTemplate.opsForSet().size(index)
                .flatMap(size -> size == null || size == 0
                        ? Mono.defer(write)
                        : touchOwnerBeforeWrite(owner, write));
    }

    private Mono<Void> touch(UUID owner, String careerId) {
        String index = indexKey(owner);
        return redisTemplate.opsForSet().isMember(index, careerId)
                .flatMap(indexed -> indexed
                        ? renew(owner, careerId, index)
                        : redisTemplate.opsForSet().size(index)
                                .flatMap(size -> size >= MAX_INDEX_SIZE
                                        ? Mono.error(new IllegalStateException("career index limit reached"))
                                        : redisTemplate.opsForSet().add(index, careerId)
                                                .then(renew(owner, careerId, index))));
    }

    private Mono<Void> renew(UUID owner, String careerId, String index) {
        return redisTemplate.expire(mappingKey(careerId), OWNERSHIP_TTL)
                .then(redisTemplate.expire(index, OWNERSHIP_TTL))
                .then(redisTemplate.expire(rootKey(owner), ROOT_TTL))
                .then();
    }

    private Mono<Void> renewOwner(UUID owner, java.util.List<String> careerIds, String index) {
        return Flux.fromIterable(careerIds)
                .concatMap(careerId -> redisTemplate.expire(mappingKey(careerId), OWNERSHIP_TTL))
                .then(redisTemplate.expire(index, OWNERSHIP_TTL))
                .then(redisTemplate.expire(rootKey(owner), ROOT_TTL))
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
}

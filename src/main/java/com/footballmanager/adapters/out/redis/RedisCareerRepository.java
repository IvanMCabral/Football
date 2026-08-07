package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.CareerSave;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.out.career.CareerIndexLimitException;
import com.footballmanager.infrastructure.observability.RuntimeOperationMetrics;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Sinks;
import org.springframework.data.redis.core.script.RedisScript;
import com.footballmanager.application.service.career.CareerLifecycleCoordinator;

/**
 * Repositorio para guardar y cargar CareerSave en Redis.
 * Clave: career:{userId}
 */
@Slf4j
@Repository
public class RedisCareerRepository implements CareerRepository {

    private static final String KEY_PREFIX = "career:";
    private static final String CAREER_INDEX_SUFFIX = ":career-ids";
    private static final String CAREER_OWNER_PREFIX = "career-owner:";
    private static final Duration CACHE_TTL = Duration.ofDays(30); // 30 días de inactividad
    private static final Duration CAREER_INDEX_TTL = Duration.ofDays(31);
    private static final int MAX_CAREER_INDEX_SIZE = 256;
    private static final ConcurrentMap<String, OwnerSaveQueue> SAVE_QUEUES = new ConcurrentHashMap<>();

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CareerLifecycleCoordinator lifecycleCoordinator;
    static final RedisScript<Long> COMPENSATE_SAVE_IF_OWNER = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('del', KEYS[1], KEYS[2], KEYS[3]); "
                    + "redis.call('srem', KEYS[4], ARGV[2]); return 1; else return 0 end",
            Long.class);
    private static final RedisScript<Long> COMPENSATE_MAPPING_IF_OWNER = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('del', KEYS[1], KEYS[2]); redis.call('srem', KEYS[3], ARGV[2]); return 1; "
                    + "else return 0 end",
            Long.class);

    @org.springframework.beans.factory.annotation.Autowired
    public RedisCareerRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                 ObjectMapper objectMapper,
                                 CareerLifecycleCoordinator lifecycleCoordinator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.lifecycleCoordinator = lifecycleCoordinator;
    }

    /** Compatibility constructor for isolated adapters/tests. */
    public RedisCareerRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                 ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null);
    }

    /**
     * Genera la clave Redis para un usuario
     */
    private String getKey(String userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * Guarda o actualiza una carrera en Redis
     */
    @Override
    public Mono<Void> save(CareerSave careerSave) {
        String key = getKey(careerSave.getUserId().toString());
        try {
            String json = objectMapper.writeValueAsString(careerSave);
            int palmaresSize = careerSave.getSeasonManager().getPalmares() != null ? careerSave.getSeasonManager().getPalmares().size() : 0;
            log.info("[REDIS-SAVE] career state persisted, palmaresSize={}", palmaresSize);
            boolean fencedWrite = careerSave.getLifecycleGeneration() != null
                    && !careerSave.getLifecycleGeneration().isBlank();
            Mono<Void> operation = (fencedWrite
                    ? requireGeneration(careerSave.getCareerId(), careerSave.getLifecycleGeneration())
                    : ensureGeneration(careerSave.getCareerId()))
                    .doOnNext(careerSave::setLifecycleGeneration)
                    .flatMap(generation -> (fencedWrite
                            ? requireOwnerMapping(careerSave.getCareerId(), careerSave.getUserId())
                            : ensureOwnerMapping(careerSave.getCareerId(), careerSave.getUserId()))
                            .flatMap(mapping -> redisTemplate.hasKey(key)
                                    .flatMap(rootExisted -> redisTemplate.opsForValue().set(key, json, CACHE_TTL)
                                            .then(indexCareer(careerSave))
                                            .onErrorResume(error -> compensateFailedSave(
                                                    mapping, generation, key, rootExisted, error)))));
            Mono<Void> coordinated = lifecycleCoordinator == null
                    ? operation
                    : lifecycleCoordinator.serializeCareer(careerSave.getCareerId(), operation);
            return RuntimeOperationMetrics.measure("redis.career.save",
                    serializeSave(careerSave.getUserId(), coordinated));
        } catch (Exception e) {
            return RuntimeOperationMetrics.measure("redis.career.save", Mono.error(e));
        }
    }

    /**
     * Carga una carrera desde Redis
     */
    @Override
    public Mono<Optional<CareerSave>> findById(String id) {
        String key = getKey(id);
        return RuntimeOperationMetrics.measure("redis.career.load",
            redisTemplate.opsForValue()
                .get(key)
                .map(json -> {
                    try {
                        CareerSave career = objectMapper.readValue(json, CareerSave.class);
                        int palmaresSize = career.getSeasonManager() != null && career.getSeasonManager().getPalmares() != null
                            ? career.getSeasonManager().getPalmares().size() : 0;
                        log.info("[REDIS-LOAD] career state loaded, palmaresSize={}, seasonManagerNull={}",
                            palmaresSize, career.getSeasonManager() == null);
                        return Optional.of(career);
                    } catch (Exception e) {
                        log.error("[REDIS-LOAD] Error deserializing career state", e);
                        return Optional.<CareerSave>empty();
                    }
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(career -> career.isEmpty()
                        ? Mono.just(career)
                        : serializeSave(career.get().getUserId(), ensureCareerIndex(career))));
    }

    private Mono<Optional<CareerSave>> ensureCareerIndex(Optional<CareerSave> career) {
        if (career.isEmpty()) {
            return Mono.just(career);
        }
        CareerSave value = career.get();
        return redisTemplate.opsForValue().get(generationKey(value.getCareerId()))
                .switchIfEmpty(Mono.error(new IllegalStateException("career lifecycle generation missing")))
                .doOnNext(value::setLifecycleGeneration)
                .then(indexCareer(value)).thenReturn(career);
    }

    private Mono<Void> indexCareer(CareerSave career) {
        if (career.getCareerId() == null || career.getCareerId().isBlank()) {
            return Mono.empty();
        }
        String index = indexKey(career.getUserId());
        return redisTemplate.opsForSet().isMember(index, career.getCareerId())
                .flatMap(existing -> existing
                        ? redisTemplate.expire(index, CAREER_INDEX_TTL).then()
                        : redisTemplate.opsForSet().size(index)
                                .flatMap(size -> size >= MAX_CAREER_INDEX_SIZE
                                        ? Mono.error(new CareerIndexLimitException())
                                        : redisTemplate.opsForSet().add(index, career.getCareerId())
                                                .then(redisTemplate.expire(index, CAREER_INDEX_TTL))
                                                .then()));
    }

    private Mono<MappingState> ensureOwnerMapping(String careerId, UUID ownerId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        String key = ownerMappingKey(careerId);
        String operationToken = UUID.randomUUID().toString();
        return redisTemplate.opsForValue().setIfAbsent(key, ownerId.toString(), CAREER_INDEX_TTL)
                .flatMap(created -> created
                        ? setMappingToken(careerId, operationToken).map(token -> new MappingState(careerId, key, true, ownerId.toString(), token))
                        : redisTemplate.opsForValue().get(key)
                                .flatMap(existing -> existing.equals(ownerId.toString())
                                        ? setMappingToken(careerId, operationToken)
                                                .flatMap(token -> redisTemplate.expire(key, CAREER_INDEX_TTL)
                                                        .thenReturn(new MappingState(careerId, key, false, ownerId.toString(), token)))
                                        : Mono.error(new IllegalStateException("career ownership mapping conflict")))
                                .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping missing"))));
    }

    private Mono<String> ensureGeneration(String careerId) {
        String key = generationKey(careerId);
        String token = UUID.randomUUID().toString();
        return redisTemplate.opsForValue().setIfAbsent(key, token, CAREER_INDEX_TTL)
                .flatMap(created -> created ? Mono.just(token)
                        : redisTemplate.opsForValue().get(key)
                                .switchIfEmpty(Mono.error(new IllegalStateException("career lifecycle generation missing"))));
    }

    private Mono<String> requireGeneration(String careerId, String expectedGeneration) {
        return redisTemplate.opsForValue().get(generationKey(careerId))
                .filter(expectedGeneration::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("career lifecycle generation is stale")));
    }

    private Mono<MappingState> requireOwnerMapping(String careerId, UUID ownerId) {
        String key = ownerMappingKey(careerId);
        return redisTemplate.opsForValue().get(key)
                .filter(ownerId.toString()::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping is stale")))
                .flatMap(ignored -> {
                    String operationToken = UUID.randomUUID().toString();
                    return setMappingToken(careerId, operationToken)
                            .flatMap(token -> redisTemplate.expire(key, CAREER_INDEX_TTL)
                                    .thenReturn(new MappingState(careerId, key, false,
                                            ownerId.toString(), token)));
                });
    }

    private Mono<String> setMappingToken(String careerId, String operationToken) {
        String key = mappingTokenKey(careerId);
        return redisTemplate.opsForValue().set(key, operationToken, CAREER_INDEX_TTL).thenReturn(operationToken);
    }

    /**
     * Verifica si existe una carrera para un usuario
     */
    // Not part of interface
    public Mono<Boolean> existsByUserId(String userId) {
        String key = getKey(userId);
        return RuntimeOperationMetrics.measure("redis.career.exists", redisTemplate.hasKey(key));
    }

    /**
     * Elimina una carrera de Redis
     */
    @Override
    public Mono<Void> deleteById(String id) {
        String key = getKey(id);
        return RuntimeOperationMetrics.measure("redis.career.delete",
            redisTemplate.delete(key).then());
    }

    private String indexKey(UUID userId) {
        return "user:" + userId + CAREER_INDEX_SUFFIX;
    }

    private String ownerMappingKey(String careerId) {
        return CAREER_OWNER_PREFIX + careerId;
    }

    private Mono<Void> compensateFailedSave(MappingState mapping, String generation,
                                            String rootKey,
                                            boolean rootExisted, Throwable error) {
        Mono<Void> cleanup = !rootExisted
                ? redisTemplate.execute(COMPENSATE_SAVE_IF_OWNER,
                        List.of(mappingTokenKeyFromMapping(mapping.key()), mapping.key(), rootKey,
                                indexKey(UUID.fromString(mapping.ownerId()))),
                        mapping.token(), mapping.careerId()).then()
                : redisTemplate.execute(COMPENSATE_MAPPING_IF_OWNER,
                        List.of(mappingTokenKeyFromMapping(mapping.key()), mapping.key(),
                                indexKey(UUID.fromString(mapping.ownerId()))),
                        mapping.token(), mapping.careerId()).then();
        return cleanup
                .onErrorResume(ignored -> Mono.empty())
                .then(Mono.error(error));
    }

    private String mappingTokenKeyFromMapping(String mappingKey) {
        return "career-mapping-token:" + mappingKey.substring(CAREER_OWNER_PREFIX.length());
    }

    private <T> Mono<T> serializeSave(UUID ownerId, Mono<T> operation) {
        OwnerSaveQueue queue = SAVE_QUEUES.computeIfAbsent(ownerId.toString(), ignored -> new OwnerSaveQueue());
        return Mono.defer(() -> {
            Sinks.One<Void> released = Sinks.one();
            Mono<Void> marker = released.asMono().cache();
            Mono<Void> predecessor = queue.tail.getAndSet(marker);
            return predecessor.onErrorResume(ignored -> Mono.empty())
                    .then(operation)
                    .doFinally(signal -> {
                        released.tryEmitEmpty();
                        Mono<Void> idle = Mono.empty();
                        queue.tail.compareAndSet(marker, idle);
                        if (queue.tail.get() == idle) {
                            SAVE_QUEUES.remove(ownerId.toString(), queue);
                        }
                    });
        });
    }

    private record MappingState(String careerId, String key, boolean created, String ownerId, String token) {
    }

    private static final class OwnerSaveQueue {
        private final AtomicReference<Mono<Void>> tail = new AtomicReference<>(Mono.empty());
    }


    /**
     * Extiende el TTL de una carrera (para mantenerla activa)
     */
    public Mono<Boolean> extendTTL(UUID userId) {
        String key = getKey(userId.toString());
        Mono<Boolean> operation = redisTemplate.opsForValue().get(key)
                        .timeout(Duration.ofSeconds(5))
                        .flatMap(json -> {
                            try {
                                CareerSave career = objectMapper.readValue(json, CareerSave.class);
                                return redisTemplate.opsForValue().get(ownerMappingKey(career.getCareerId()))
                                        .timeout(Duration.ofSeconds(5))
                                        .filter(userId.toString()::equals)
                                        .switchIfEmpty(Mono.error(new IllegalStateException(
                                                "career ownership mapping missing")))
                                        .then(redisTemplate.opsForValue().get(generationKey(career.getCareerId()))
                                                .switchIfEmpty(Mono.error(new IllegalStateException("career lifecycle generation missing"))))
                                        .then(redisTemplate.opsForSet().isMember(indexKey(userId), career.getCareerId())
                                                .timeout(Duration.ofSeconds(5))
                                                .filter(Boolean::booleanValue)
                                                .switchIfEmpty(Mono.error(new IllegalStateException(
                                                        "career ownership index missing"))))
                                        .then(redisTemplate.expire(key, CACHE_TTL).timeout(Duration.ofSeconds(5)))
                                        .then(redisTemplate.expire(ownerMappingKey(career.getCareerId()), CAREER_INDEX_TTL).timeout(Duration.ofSeconds(5)))
                                        .then(redisTemplate.expire(generationKey(career.getCareerId()), CAREER_INDEX_TTL).timeout(Duration.ofSeconds(5)))
                                        .then(redisTemplate.expire(indexKey(userId), CAREER_INDEX_TTL).timeout(Duration.ofSeconds(5)))
                                        .thenReturn(true);
                            } catch (Exception error) {
                                return Mono.error(new IllegalStateException("career state unavailable", error));
                            }
                        })
                        .defaultIfEmpty(false);
        Mono<Boolean> coordinated = lifecycleCoordinator == null
                ? operation
                : lifecycleCoordinator.serialize(userId, operation);
        return RuntimeOperationMetrics.measure("redis.career.ttl", coordinated);
    }

    private String generationKey(String careerId) {
        return "career-generation:" + careerId;
    }

    private String mappingTokenKey(String careerId) {
        return "career-mapping-token:" + careerId;
    }

}

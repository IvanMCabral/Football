package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.CareerSave;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.infrastructure.observability.RuntimeOperationMetrics;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCareerRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
            return RuntimeOperationMetrics.measure("redis.career.save",
                ensureOwnerMapping(careerSave.getCareerId(), careerSave.getUserId())
                    .flatMap(mapping -> redisTemplate.opsForValue().set(key, json, CACHE_TTL)
                        .onErrorResume(error -> mapping.created()
                                ? redisTemplate.delete(mapping.key()).then(Mono.error(error))
                                : Mono.error(error))
                        .then(indexCareer(careerSave))));
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
                .flatMap(this::ensureCareerIndex));
    }

    private Mono<Optional<CareerSave>> ensureCareerIndex(Optional<CareerSave> career) {
        if (career.isEmpty()) {
            return Mono.just(career);
        }
        CareerSave value = career.get();
        String index = indexKey(value.getUserId());
        return redisTemplate.opsForSet().isMember(index, value.getCareerId())
                .flatMap(indexed -> indexed
                        ? Mono.just(career)
                        : redisTemplate.opsForSet().add(index, value.getCareerId())
                                .then(redisTemplate.expire(index, CAREER_INDEX_TTL))
                                .thenReturn(career));
    }

    private Mono<Void> indexCareer(CareerSave career) {
        if (career.getCareerId() == null || career.getCareerId().isBlank()) {
            return Mono.empty();
        }
        String index = indexKey(career.getUserId());
        return redisTemplate.opsForSet().add(index, career.getCareerId())
                .then(redisTemplate.expire(index, CAREER_INDEX_TTL))
                .then();
    }

    private Mono<MappingState> ensureOwnerMapping(String careerId, UUID ownerId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        String key = ownerMappingKey(careerId);
        return redisTemplate.opsForValue().setIfAbsent(key, ownerId.toString(), CAREER_INDEX_TTL)
                .flatMap(created -> created
                        ? Mono.just(new MappingState(key, true))
                        : redisTemplate.opsForValue().get(key)
                                .flatMap(existing -> existing.equals(ownerId.toString())
                                        ? redisTemplate.expire(key, CAREER_INDEX_TTL)
                                                .thenReturn(new MappingState(key, false))
                                        : Mono.error(new IllegalStateException("career ownership mapping conflict")))
                                .switchIfEmpty(Mono.error(new IllegalStateException("career ownership mapping missing"))));
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

    private record MappingState(String key, boolean created) {
    }

    /**
     * Extiende el TTL de una carrera (para mantenerla activa)
     */
    public Mono<Boolean> extendTTL(UUID userId) {
        String key = getKey(userId.toString());
        return RuntimeOperationMetrics.measure("redis.career.ttl", redisTemplate.expire(key, CACHE_TTL));
    }
}

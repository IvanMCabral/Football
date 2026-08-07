package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repositorio para WorldSnapshot en Redis.
 * Key: world:{userId}
 *
 * WorldSnapshot se crea UNA VEZ y luego solo se actualiza.
 * NO se borra automáticamente (no tiene TTL).
 */
@Repository
public class RedisWorldRepository implements WorldSnapshotRepository {

    private static final String KEY_PREFIX = "world:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CareerOwnershipTouchService ownershipTouchService;
    @Value("${app.redis.world-ttl:30d}")
    private java.time.Duration worldTtl;

    @Autowired
    public RedisWorldRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper,
                                CareerOwnershipTouchService ownershipTouchService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ownershipTouchService = ownershipTouchService;
    }

    public RedisWorldRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null);
    }

    /**
     * Genera la key de Redis para el WorldSnapshot de un usuario
     */
    private String generateKey(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }

    /**
     * Guarda o actualiza el WorldSnapshot
     */
    public Mono<WorldSnapshot> save(WorldSnapshot snapshot) {
        if (ownershipTouchService != null) {
            return Mono.error(new IllegalStateException("career world writer requires lifecycle context"));
        }
        return saveInitial(snapshot);
    }

    @Override
    public Mono<WorldSnapshot> saveInitial(WorldSnapshot snapshot) {
        String key = generateKey(snapshot.getUserId());

        Mono<WorldSnapshot> persist = Mono.fromCallable(() -> objectMapper.writeValueAsString(snapshot))
                .flatMap(json -> worldTtl == null
                        ? redisTemplate.opsForValue().set(key, json)
                        : redisTemplate.opsForValue().set(key, json, worldTtl))
                .thenReturn(snapshot)
                .onErrorResume(e -> {
                    return Mono.error(e);
                });
        return ownershipTouchService == null
                ? persist
                : ownershipTouchService.initializeWorld(snapshot.getUserId(), () -> persist);
    }

    /** Career-derived world update. It cannot degrade to first-time initialization. */
    @Override
    public Mono<WorldSnapshot> saveWithContext(CareerWriteContext context, WorldSnapshot snapshot) {
        if (context == null || snapshot == null || !context.ownerId().equals(snapshot.getUserId())) {
            return Mono.error(new IllegalArgumentException("career world context does not match snapshot"));
        }
        String key = generateKey(snapshot.getUserId());
        Mono<WorldSnapshot> persist = Mono.fromCallable(() -> objectMapper.writeValueAsString(snapshot))
                .flatMap(json -> worldTtl == null
                        ? redisTemplate.opsForValue().set(key, json)
                        : redisTemplate.opsForValue().set(key, json, worldTtl))
                .thenReturn(snapshot);
        return ownershipTouchService == null
                ? Mono.error(new IllegalStateException("career ownership service is required"))
                : ownershipTouchService.touchBeforeWrite(context, () -> persist);
    }

    /**
     * Busca el WorldSnapshot por userId
     */
    public Mono<WorldSnapshot> findByUserId(UUID userId) {
        String key = generateKey(userId);

        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, WorldSnapshot.class)))
                .onErrorResume(e -> {
                    return Mono.empty();
                });
    }

    /**
     * Verifica si existe un WorldSnapshot para el userId
     */
    public Mono<Boolean> existsByUserId(UUID userId) {
        String key = generateKey(userId);
        return redisTemplate.hasKey(key);
    }

    /**
     * Elimina el WorldSnapshot (solo para testing o reset manual)
     */
    public Mono<Boolean> deleteByUserId(UUID userId) {
        String key = generateKey(userId);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }
}

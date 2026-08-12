package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.GameEntity;
import com.footballmanager.application.service.world.WorldPersistedWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repositorio Redis para juegos con scope de usuario.
 * Keys: user:{userId}:game:{gameId}
 */
@Repository
@WorldPersistedWriter(root = GameEntity.class, writeMethod = "save", storageFamily = "user:*:game:*",
        role = WorldPersistedWriter.DurabilityRole.EXPLICITLY_NON_WORLD_REFERENCE)
public class GameRedisRepository {
    private static final String GAME_INDEX_SUFFIX = ":game-ids";
    private final ReactiveRedisTemplate<String, GameEntity> redisTemplate;
    private final ReactiveRedisTemplate<String, String> indexTemplate;

    @Autowired
    public GameRedisRepository(
            @Qualifier("gameEntityRedisTemplate") ReactiveRedisTemplate<String, GameEntity> redisTemplate,
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> indexTemplate) {
        this.redisTemplate = redisTemplate;
        this.indexTemplate = indexTemplate;
    }

    public GameRedisRepository(
            @Qualifier("gameEntityRedisTemplate") ReactiveRedisTemplate<String, GameEntity> redisTemplate) {
        this(redisTemplate, null);
    }

    private String getKey(UUID userId, UUID gameId) {
        return "user:" + userId + ":game:" + gameId;
    }

    private String getIndexKey(UUID userId) {
        return "user:" + userId + GAME_INDEX_SUFFIX;
    }

    public Mono<Boolean> save(UUID userId, GameEntity game) {
        String key = getKey(userId, game.getId());
        return redisTemplate.opsForValue().set(key, game)
                .flatMap(saved -> indexTemplate == null
                        ? Mono.just(saved)
                        : indexTemplate.opsForSet().add(getIndexKey(userId), game.getId().toString())
                        .thenReturn(saved));
    }

    public Mono<GameEntity> findById(UUID userId, UUID gameId) {
        String key = getKey(userId, gameId);
        return redisTemplate.opsForValue().get(key);
    }

    public Flux<GameEntity> findAllByUserId(UUID userId) {
        if (indexTemplate == null) {
            return Flux.empty();
        }
        return indexTemplate.opsForSet().members(getIndexKey(userId))
                .collectList()
                .flatMapMany(gameIds -> gameIds.isEmpty()
                        ? discoverLegacyGameIds(userId)
                        : Flux.fromIterable(gameIds))
                .flatMap(gameId -> redisTemplate.opsForValue()
                        .get(getKey(userId, UUID.fromString(gameId))));
    }

    /**
     * One-time compatibility path for game keys written before the owner
     * index existed. The pattern is scoped to the authenticated owner; it is
     * never a database-wide enumeration. Discovered IDs are indexed so later
     * reads stay on the bounded owner index.
     */
    private Flux<String> discoverLegacyGameIds(UUID userId) {
        String pattern = "user:" + userId + ":game:*";
        return redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(100).build())
                .map(key -> key.substring(("user:" + userId + ":game:").length()))
                .flatMap(gameId -> indexTemplate.opsForSet()
                        .add(getIndexKey(userId), gameId)
                        .thenReturn(gameId));
    }

    public Mono<Boolean> deleteById(UUID userId, UUID gameId) {
        String key = getKey(userId, gameId);
        return redisTemplate.delete(key)
                .flatMap(count -> indexTemplate == null
                        ? Mono.just(count > 0)
                        : indexTemplate.opsForSet().remove(getIndexKey(userId), gameId.toString())
                        .thenReturn(count > 0));
    }

    public Mono<Long> deleteAllByUserId(UUID userId) {
        if (indexTemplate == null) {
            return Mono.just(0L);
        }
        return indexTemplate.opsForSet().members(getIndexKey(userId))
                .collectList()
                .flatMap(gameIds -> {
                    if (gameIds.isEmpty()) {
                        return indexTemplate.delete(getIndexKey(userId));
                    }
                    String[] keys = gameIds.stream()
                            .map(gameId -> getKey(userId, UUID.fromString(gameId)))
                            .toArray(String[]::new);
                    Mono<Long> deleteGames = redisTemplate.unlink(keys);
                    Mono<Long> deleteIndex = indexTemplate.unlink(getIndexKey(userId));
                    return Mono.zip(deleteGames, deleteIndex)
                            .map(results -> results.getT1());
                });
    }
}

package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.GameEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Repositorio Redis para juegos con scope de usuario.
 * Keys: user:{userId}:game:{gameId}
 */
@Repository
public class GameRedisRepository {
    private final ReactiveRedisTemplate<String, GameEntity> redisTemplate;

    public GameRedisRepository(
            @Qualifier("gameEntityRedisTemplate") ReactiveRedisTemplate<String, GameEntity> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getKey(UUID userId, UUID gameId) {
        return "user:" + userId + ":game:" + gameId;
    }

    private String getKeyPattern(UUID userId) {
        return "user:" + userId + ":game:*";
    }

    public Mono<Boolean> save(UUID userId, GameEntity game) {
        String key = getKey(userId, game.getId());
        return redisTemplate.opsForValue().set(key, game);
    }

    public Mono<GameEntity> findById(UUID userId, UUID gameId) {
        String key = getKey(userId, gameId);
        return redisTemplate.opsForValue().get(key);
    }

    public Flux<GameEntity> findAllByUserId(UUID userId) {
        String pattern = getKeyPattern(userId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMapMany(keys -> {
                    if (keys.isEmpty()) {
                        return Flux.empty();
                    }
                    return redisTemplate.opsForValue().multiGet(keys)
                            .map(list -> {
                                if (list == null) {
                                    return new ArrayList<GameEntity>();
                                }
                                ArrayList<GameEntity> filtered = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof GameEntity) {
                                        filtered.add((GameEntity) item);
                                    }
                                }
                                return filtered;
                            })
                            .flatMapMany(values -> Flux.fromIterable(values));
                });
    }

    public Mono<Boolean> deleteById(UUID userId, UUID gameId) {
        String key = getKey(userId, gameId);
        return redisTemplate.delete(key).map(count -> count > 0);
    }

    public Mono<Long> deleteAllByUserId(UUID userId) {
        String pattern = getKeyPattern(userId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> redisTemplate.delete(keys.toArray(new String[0])));
    }
}

package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.LeagueEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Repositorio Redis para ligas con scope de usuario.
 * Keys: user:{userId}:league:{leagueId}
 */
@Repository
public class LeagueRedisRepository {
    private final ReactiveRedisTemplate<String, LeagueEntity> redisTemplate;

    public LeagueRedisRepository(
            @Qualifier("leagueEntityRedisTemplate") ReactiveRedisTemplate<String, LeagueEntity> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getKey(UUID userId, UUID leagueId) {
        return "user:" + userId + ":league:" + leagueId;
    }

    private String getKeyPattern(UUID userId) {
        return "user:" + userId + ":league:*";
    }

    public Mono<Boolean> save(UUID userId, LeagueEntity league) {
        String key = getKey(userId, league.getId());
        return redisTemplate.opsForValue().set(key, league);
    }

    public Mono<LeagueEntity> findById(UUID userId, UUID leagueId) {
        String key = getKey(userId, leagueId);
        return redisTemplate.opsForValue().get(key);
    }

    public Flux<LeagueEntity> findByCountry(UUID userId, String country) {
        return findAllByUserId(userId)
                .filter(league -> country.equals(league.getCountry()));
    }

    public Flux<LeagueEntity> findAllByUserId(UUID userId) {
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
                                    return new ArrayList<LeagueEntity>();
                                }
                                ArrayList<LeagueEntity> filtered = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof LeagueEntity) {
                                        filtered.add((LeagueEntity) item);
                                    }
                                }
                                return filtered;
                            })
                            .flatMapMany(values -> Flux.fromIterable(values));
                });
    }

    public Mono<Boolean> deleteById(UUID userId, UUID leagueId) {
        String key = getKey(userId, leagueId);
        return redisTemplate.delete(key).map(count -> count > 0);
    }
}

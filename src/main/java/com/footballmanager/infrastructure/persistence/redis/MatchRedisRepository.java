package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.infrastructure.persistence.entity.MatchEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Repositorio Redis para partidos con scope de usuario/game.
 * Keys: user:{userId}:game:{gameId}:match:{matchId}
 */
@Repository
public class MatchRedisRepository {
    private final ReactiveRedisTemplate<String, MatchEntity> redisTemplate;

    public MatchRedisRepository(
            @Qualifier("matchEntityRedisTemplate") ReactiveRedisTemplate<String, MatchEntity> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getKey(UUID userId, UUID gameId, UUID matchId) {
        return "user:" + userId + ":game:" + gameId + ":match:" + matchId;
    }

    private String getKeyPattern(UUID userId, UUID gameId) {
        return "user:" + userId + ":game:" + gameId + ":match:*";
    }

    private String getKeyPatternByUser(UUID userId) {
        return "user:" + userId + ":match:*";
    }

    public Mono<Boolean> save(UUID userId, UUID gameId, MatchEntity match) {
        String key = getKey(userId, gameId, match.getId());
        return redisTemplate.opsForValue().set(key, match);
    }

    public Mono<MatchEntity> findById(UUID userId, UUID gameId, UUID matchId) {
        String key = getKey(userId, gameId, matchId);
        return redisTemplate.opsForValue().get(key);
    }

    public Flux<MatchEntity> findByGameId(UUID userId, UUID gameId) {
        String pattern = getKeyPattern(userId, gameId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMapMany(keys -> {
                    if (keys.isEmpty()) {
                        return Flux.empty();
                    }
                    return redisTemplate.opsForValue().multiGet(keys)
                            .map(list -> {
                                if (list == null) {
                                    return new ArrayList<MatchEntity>();
                                }
                                ArrayList<MatchEntity> filtered = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof MatchEntity) {
                                        filtered.add((MatchEntity) item);
                                    }
                                }
                                return filtered;
                            })
                            .flatMapMany(values -> Flux.fromIterable(values));
                });
    }

    public Flux<MatchEntity> findByTeamId(UUID userId, UUID teamId) {
        return findAllByUserId(userId)
                .filter(match -> teamId.equals(match.getHomeTeamId()) || teamId.equals(match.getAwayTeamId()));
    }

    public Flux<MatchEntity> findScheduledMatches(UUID userId) {
        return findAllByUserId(userId)
                .filter(match -> "SCHEDULED".equals(match.getStatus()));
    }

    public Flux<MatchEntity> findAllByUserId(UUID userId) {
        String pattern = getKeyPatternByUser(userId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMapMany(keys -> {
                    if (keys.isEmpty()) {
                        return Flux.empty();
                    }
                    return redisTemplate.opsForValue().multiGet(keys)
                            .map(list -> {
                                if (list == null) {
                                    return new ArrayList<MatchEntity>();
                                }
                                ArrayList<MatchEntity> filtered = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof MatchEntity) {
                                        filtered.add((MatchEntity) item);
                                    }
                                }
                                return filtered;
                            })
                            .flatMapMany(values -> Flux.fromIterable(values));
                });
    }

    public Mono<Boolean> deleteById(UUID userId, UUID gameId, UUID matchId) {
        String key = getKey(userId, gameId, matchId);
        return redisTemplate.delete(key).map(count -> count > 0);
    }
}

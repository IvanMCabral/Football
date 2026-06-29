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
        // V25D76-C41: scan all match keys under the user's namespace.
        // Save uses the structured key `user:{userId}:game:{gameId}:match:{matchId}`
        // (with gameId possibly null for live matches where the engine never
        // set the GameId on the entity). The pre-C41 find pattern
        // `user:{userId}:match:*` (without the `game:` segment) returned
        // empty because no saved key matched it. Use a broader pattern that
        // covers both `game:null:match:*` and `game:{any}:match:*` paths.
        String userPrefix = "user:" + userId + ":";
        return redisTemplate.keys(userPrefix + "*match:*")
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

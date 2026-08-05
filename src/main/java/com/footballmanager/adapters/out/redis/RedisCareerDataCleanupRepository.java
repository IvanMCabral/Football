package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Reactive, owner-scoped cleanup for all career Redis projections. */
@Repository
public class RedisCareerDataCleanupRepository implements CareerDataCleanupRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisCareerDataCleanupRepository(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> deleteOwnedData(UUID userId, String careerId) {
        if (userId == null) {
            return Mono.error(new IllegalArgumentException("userId must not be null"));
        }

        List<String> patterns = new java.util.ArrayList<>(List.of(
                "career:" + userId,
                "world:" + userId,
                "user:" + userId + ":*",
                "runtime:match:" + userId + ":*",
                "match:state:" + userId + ":*",
                "match:commands:" + userId + ":*"));
        if (careerId != null && !careerId.isBlank()) {
            patterns.add("career:" + careerId + ":match-detail:*");
            patterns.add("career:" + careerId + ":match-baseline:*");
        }

        return Flux.fromIterable(patterns)
                .concatMap(this::scanKeys)
                .distinct()
                .buffer(100)
                .concatMap(keys -> keys.isEmpty()
                        ? Mono.empty()
                        : redisTemplate.delete(Flux.fromIterable(keys)))
                .then();
    }

    private Flux<String> scanKeys(String pattern) {
        return redisTemplate.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build());
    }
}

package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Repository
public class V24DetailedMatchRedisAdapter implements V24DetailedMatchStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_DETAIL = ":match-detail:";

    private final ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate;

    public V24DetailedMatchRedisAdapter(
            @Qualifier("v24DetailedMatchDataRedisTemplate") ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> save(String careerId, V24DetailedMatchData detail) {
        return validateSave(careerId, detail)
                .then(Mono.defer(() -> {
                    String key = buildKey(careerId, detail.matchId());
                    log.info("[V24-DETAIL-PERSIST] save key={}, careerId={}, matchId={}, homeGoals={}, awayGoals={}",
                            key, careerId, detail.matchId(), detail.homeGoals(), detail.awayGoals());
                    return redisTemplate.opsForValue().set(key, detail)
                            .doOnSuccess(saved -> log.info("[V24-DETAIL-PERSIST-SUCCESS] key={}, careerId={}, matchId={}",
                                    key, careerId, detail.matchId()))
                            .doOnError(error -> log.error("[V24-REDIS] Failed to save match detail key={}, careerId={}, matchId={}: {}",
                                    key, careerId, detail.matchId(), error.getMessage()))
                            .then();
                }));
    }

    @Override
    public Mono<Optional<V24DetailedMatchData>> findByMatchId(String careerId, String matchId) {
        return validateIds(careerId, matchId)
                .then(Mono.defer(() -> {
                    String key = buildKey(careerId, matchId);
                    log.info("[V24-DETAIL-QUERY] findByMatchId key={}, careerId={}, matchId={}",
                            key, careerId, matchId);
                    return redisTemplate.opsForValue().get(key)
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .doOnNext(found -> log.info("[V24-DETAIL-QUERY-RESULT] key={}, found={}",
                                    key, found.isPresent()))
                            .doOnError(error -> log.warn("[V24-DETAIL-QUERY-FAILED] key={}, careerId={}, matchId={}, error={}",
                                    key, careerId, matchId, error.getMessage()));
                }));
    }

    @Override
    public Flux<V24DetailedMatchData> findByCareerId(String careerId) {
        return validateCareerId(careerId)
                .thenMany(Flux.defer(() -> {
                    String pattern = buildPattern(careerId);
                    log.info("[V24-REDIS] findByCareerId careerId={}, pattern={}", careerId, pattern);
                    return redisTemplate.keys(pattern)
                            .flatMap(key -> redisTemplate.opsForValue().get(key)
                                    .doOnError(error -> log.warn("[V24-REDIS] deserialization/read failed for key={}: {}",
                                            key, error.getMessage()))
                                    .onErrorResume(error -> Mono.empty()))
                            .doOnComplete(() -> log.info("[V24-REDIS] findByCareerId completed for careerId={}", careerId))
                            .doOnError(error -> log.error("[V24-REDIS] findByCareerId FAILED for careerId={}, pattern={}: {}",
                                    careerId, pattern, error.getMessage()));
                }));
    }

    @Override
    public Mono<Void> deleteByCareerId(String careerId) {
        return validateCareerId(careerId)
                .thenMany(redisTemplate.keys(buildPattern(careerId)))
                .collectList()
                .flatMap(keys -> keys.isEmpty()
                        ? Mono.empty()
                        : redisTemplate.delete(keys.toArray(new String[0])).then());
    }

    @Override
    public Mono<Void> deleteByMatchId(String careerId, String matchId) {
        return validateIds(careerId, matchId)
                .then(Mono.defer(() -> {
                    String key = buildKey(careerId, matchId);
                    return redisTemplate.delete(key)
                            .doOnSuccess(deleted -> log.info("[V24-DETAIL-DELETE] key={}, careerId={}, matchId={}, deleted={}",
                                    key, careerId, matchId, deleted))
                            .doOnError(error -> log.warn("[V24-REDIS] Failed to delete match detail key={}, careerId={}, matchId={}: {}",
                                    key, careerId, matchId, error.getMessage()))
                            .then();
                }));
    }

    private Mono<Void> validateSave(String careerId, V24DetailedMatchData detail) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (detail == null) {
            return Mono.error(new IllegalArgumentException("detail must not be null"));
        }
        if (detail.matchId() == null || detail.matchId().isBlank()) {
            return Mono.error(new IllegalArgumentException("detail.matchId must not be blank"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateIds(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId must not be blank"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateCareerId(String careerId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        return Mono.empty();
    }

    private String buildKey(String careerId, String matchId) {
        return KEY_PREFIX + careerId + KEY_MATCH_DETAIL + matchId;
    }

    private String buildPattern(String careerId) {
        return KEY_PREFIX + careerId + KEY_MATCH_DETAIL + "*";
    }
}

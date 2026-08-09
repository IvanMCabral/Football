package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Repository
public class DetailedMatchRedisAdapter implements DetailedMatchStoragePort {

    private static final String KEY_PREFIX = "career:";
    private static final String KEY_MATCH_DETAIL = ":match-detail:";

    private final ReactiveRedisTemplate<String, DetailedMatchData> redisTemplate;
    private final CareerOwnershipTouchService ownershipTouchService;
    @Value("${app.redis.match-detail-ttl:30d}")
    private java.time.Duration matchDetailTtl;

    @Autowired
    public DetailedMatchRedisAdapter(
            @Qualifier("detailedMatchDataRedisTemplate") ReactiveRedisTemplate<String, DetailedMatchData> redisTemplate,
            CareerOwnershipTouchService ownershipTouchService) {
        this.redisTemplate = redisTemplate;
        this.ownershipTouchService = ownershipTouchService;
    }

    public DetailedMatchRedisAdapter(
            @Qualifier("detailedMatchDataRedisTemplate") ReactiveRedisTemplate<String, DetailedMatchData> redisTemplate) {
        this(redisTemplate, null);
    }

    @Override
    public Mono<Void> save(String careerId, DetailedMatchData detail) {
        if (ownershipTouchService != null) {
            return Mono.error(new IllegalStateException("career detail writer requires lifecycle context"));
        }
        return saveInternal(careerId, null, detail);
    }

    @Override
    public Mono<Void> saveWithContext(CareerWriteContext context, DetailedMatchData detail) {
        if (context == null) {
            return Mono.error(new IllegalArgumentException("career lifecycle context is required"));
        }
        return saveInternal(context.careerId(), context, detail);
    }

    private Mono<Void> saveInternal(String careerId, CareerWriteContext context, DetailedMatchData detail) {
        return validateSave(careerId, detail)
                .then(Mono.defer(() -> {
                    String key = buildKey(careerId, detail.matchId());
                    log.info("[DETAIL-PERSIST] save key={}, careerId={}, matchId={}, homeGoals={}, awayGoals={}",
                            key, careerId, detail.matchId(), detail.homeGoals(), detail.awayGoals());
                    Mono<Boolean> save = matchDetailTtl == null
                            ? redisTemplate.opsForValue().set(key, detail)
                            : redisTemplate.opsForValue().set(key, detail, matchDetailTtl);
                    Mono<Void> persist = save
                            .doOnSuccess(saved -> log.info("[DETAIL-PERSIST-SUCCESS] key={}, careerId={}, matchId={}",
                                    key, careerId, detail.matchId()))
                            .doOnError(error -> log.error("[DETAIL-REDIS] Failed to save match detail key={}, careerId={}, matchId={}: {}",
                                    key, careerId, detail.matchId(), error.getMessage()))
                            .then();
                    return ownershipTouchService == null
                            ? persist
                            : ownershipTouchService.touchBeforeWrite(context, key, () -> persist);
                }));
    }

    @Override
    public Mono<Optional<DetailedMatchData>> findByMatchId(String careerId, String matchId) {
        return validateIds(careerId, matchId)
                .then(Mono.defer(() -> {
                    String key = buildKey(careerId, matchId);
                    log.info("[DETAIL-QUERY] findByMatchId key={}, careerId={}, matchId={}",
                            key, careerId, matchId);
                    return redisTemplate.opsForValue().get(key)
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .doOnNext(found -> log.info("[DETAIL-QUERY-RESULT] key={}, found={}",
                                    key, found.isPresent()))
                            .doOnError(error -> log.warn("[DETAIL-QUERY-FAILED] key={}, careerId={}, matchId={}, error={}",
                                    key, careerId, matchId, error.getMessage()));
                }));
    }

    @Override
    public Flux<DetailedMatchData> findByCareerId(String careerId) {
        return validateCareerId(careerId)
                .thenMany(Flux.defer(() -> {
                    String pattern = buildPattern(careerId);
                    log.info("[DETAIL-REDIS] findByCareerId careerId={}, pattern={}", careerId, pattern);
                    return redisTemplate.keys(pattern)
                            .flatMap(key -> redisTemplate.opsForValue().get(key)
                                    .doOnError(error -> log.warn("[DETAIL-REDIS] deserialization/read failed for key={}: {}",
                                            key, error.getMessage()))
                                    .onErrorMap(error -> new RedisStateAccessException(
                                            "Failed to read detailed match key=" + key, error)))
                            .doOnComplete(() -> log.info("[DETAIL-REDIS] findByCareerId completed for careerId={}", careerId))
                            .doOnError(error -> log.error("[DETAIL-REDIS] findByCareerId FAILED for careerId={}, pattern={}: {}",
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
        if (ownershipTouchService != null) {
            return Mono.error(new IllegalStateException("detail deletion requires lifecycle context"));
        }
        return deleteByMatchIdInternal(careerId, matchId);
    }

    @Override
    public Mono<Void> deleteByMatchIdWithContext(String careerId, String matchId,
                                                  CareerWriteContext context) {
        if (context == null || !context.careerId().equals(careerId)) {
            return Mono.error(new IllegalArgumentException("detail lifecycle context does not match detail"));
        }
        return ownershipTouchService == null
                ? Mono.error(new IllegalStateException("career ownership service is required"))
                : ownershipTouchService.touchBeforeWrite(context,
                        () -> deleteByMatchIdInternal(careerId, matchId));
    }

    private Mono<Void> deleteByMatchIdInternal(String careerId, String matchId) {
        return validateIds(careerId, matchId)
                .then(Mono.defer(() -> {
                    String key = buildKey(careerId, matchId);
                    return redisTemplate.delete(key)
                            .doOnSuccess(deleted -> log.info("[DETAIL-DELETE] key={}, careerId={}, matchId={}, deleted={}",
                                    key, careerId, matchId, deleted))
                            .doOnError(error -> log.warn("[DETAIL-REDIS] Failed to delete match detail key={}, careerId={}, matchId={}: {}",
                                    key, careerId, matchId, error.getMessage()))
                            .then();
                }));
    }

    private Mono<Void> validateSave(String careerId, DetailedMatchData detail) {
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

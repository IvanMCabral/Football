package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reactive, exact-owner cleanup for all career Redis projections. */
@Repository
public class RedisCareerDataCleanupRepository implements CareerDataCleanupRepository {

    private static final int MAX_BATCH_SIZE = 100;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisCareerDataCleanupRepository(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<CareerDataCleanupResult> deleteOwnedData(UUID userId, String careerId) {
        if (userId == null) {
            return Mono.error(new IllegalArgumentException("userId must not be null"));
        }

        CleanupAccumulator accumulator = new CleanupAccumulator(userId, careerId);
        return redisTemplate.opsForSet()
                .members(indexKey(userId))
                .collectList()
                .flatMapMany(indexedCareerIds -> {
                    accumulator.setCareerCount(indexedCareerIds, careerId);
                    return Flux.fromIterable(patterns(userId, careerId, indexedCareerIds));
                })
                .doOnNext(accumulator::patternEvaluated)
                .concatMap(spec -> scanKeys(spec)
                        .doOnSubscribe(subscription -> accumulator.activeFamily(spec.family()))
                        .map(key -> new KeyHit(spec.family(), key))
                        .doOnNext(accumulator::discovered)
                        .filter(hit -> accumulator.markUnique(hit.key()))
                        .map(KeyHit::key)
                        .buffer(MAX_BATCH_SIZE)
                        .concatMap(batch -> batch.isEmpty()
                                ? Mono.empty()
                                : Mono.defer(() -> {
                                    accumulator.batchRequested(spec.family(), batch);
                                    return redisTemplate.delete(Flux.fromIterable(batch));
                                })
                                        .flatMap(deleted -> accumulator.batchDeleted(spec.family(), batch, deleted))))
                .then(Mono.fromSupplier(() -> accumulator.result(false, "")))
                .onErrorMap(error -> error instanceof CareerDataCleanupException
                        ? error
                        : new CareerDataCleanupException(accumulator.result(true, accumulator.failedFamily()), error));
    }

    private List<PatternSpec> patterns(UUID userId, String careerId, List<String> indexedCareerIds) {
        List<PatternSpec> patterns = new ArrayList<>(List.of(
                new PatternSpec("career-root", "career:" + userId),
                new PatternSpec("world", "world:" + userId),
                new PatternSpec("user-projection", "user:" + userId + ":*"),
                new PatternSpec("career-index", indexKey(userId)),
                new PatternSpec("runtime", "runtime:match:" + userId + ":*"),
                new PatternSpec("match-state", "match:state:" + userId + ":*"),
                new PatternSpec("match-commands", "match:commands:" + userId + ":*")));

        List<String> careerIds = new ArrayList<>(indexedCareerIds);
        if (careerId != null && !careerId.isBlank() && !careerIds.contains(careerId)) {
            careerIds.add(careerId);
        }
        for (String indexedCareerId : careerIds) {
            if (indexedCareerId != null && !indexedCareerId.isBlank()) {
                patterns.add(new PatternSpec("match-detail", "career:" + indexedCareerId + ":match-detail:*"));
                patterns.add(new PatternSpec("match-baseline", "career:" + indexedCareerId + ":match-baseline:*"));
            }
        }
        return patterns;
    }

    private Flux<String> scanKeys(PatternSpec spec) {
        return redisTemplate.scan(ScanOptions.scanOptions()
                .match(spec.pattern())
                .count(MAX_BATCH_SIZE)
                .build());
    }

    private String indexKey(UUID userId) {
        return "user:" + userId + ":career-ids";
    }

    private record PatternSpec(String family, String pattern) { }
    private record KeyHit(String family, String key) { }

    private static final class CleanupAccumulator {
        private final Instant started = Instant.now();
        private final String ownerHash;
        private int careerCount;
        private final Map<String, MutableFamily> families = new LinkedHashMap<>();
        private final Map<String, String> uniqueKeyFamilies = new LinkedHashMap<>();
        private long discovered;
        private long requested;
        private long deleted;
        private int patterns;
        private int batches;
        private int maxBatch;
        private String activeFamily = "unknown";

        private CleanupAccumulator(UUID userId, String careerId) {
            this.ownerHash = shortHash(userId.toString());
            this.careerCount = careerId == null || careerId.isBlank() ? 0 : 1;
        }

        void setCareerCount(List<String> indexedCareerIds, String explicitCareerId) {
            this.careerCount = (int) indexedCareerIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .count();
            if (explicitCareerId != null && !explicitCareerId.isBlank()
                    && !indexedCareerIds.contains(explicitCareerId)) {
                this.careerCount++;
            }
        }

        void patternEvaluated(PatternSpec spec) {
            patterns++;
            families.computeIfAbsent(spec.family(), ignored -> new MutableFamily());
            activeFamily = spec.family();
        }

        void activeFamily(String family) {
            activeFamily = family;
        }

        void discovered(KeyHit hit) {
            discovered++;
            families.computeIfAbsent(hit.family(), ignored -> new MutableFamily()).discovered++;
        }

        boolean markUnique(String key) {
            return uniqueKeyFamilies.putIfAbsent(key, activeFamily) == null;
        }

        void batchRequested(String familyName, List<String> batch) {
            requested += batch.size();
            batches++;
            maxBatch = Math.max(maxBatch, batch.size());
            MutableFamily family = families.computeIfAbsent(familyName, ignored -> new MutableFamily());
            family.requested += batch.size();
        }

        Mono<Long> batchDeleted(String familyName, List<String> batch, long count) {
            if (count > batch.size()) {
                return Mono.error(new IllegalStateException("Redis deleted more keys than requested"));
            }
            deleted += count;
            MutableFamily family = families.computeIfAbsent(familyName, ignored -> new MutableFamily());
            family.deleted += count;
            return Mono.just(count);
        }

        String failedFamily() {
            return activeFamily;
        }

        CareerDataCleanupResult result(boolean partialFailure, String failedFamily) {
            Map<String, CareerDataCleanupResult.FamilyCleanupResult> counts = new LinkedHashMap<>();
            families.forEach((family, values) -> counts.put(family,
                    new CareerDataCleanupResult.FamilyCleanupResult(values.discovered, values.requested, values.deleted)));
            return new CareerDataCleanupResult(patterns, discovered, uniqueKeyFamilies.size(), requested, deleted,
                    batches, maxBatch, ownerHash, careerCount, counts, partialFailure, failedFamily,
                    java.time.Duration.between(started, Instant.now()).toMillis());
        }

        private static final class MutableFamily {
            long discovered;
            long requested;
            long deleted;
        }
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}

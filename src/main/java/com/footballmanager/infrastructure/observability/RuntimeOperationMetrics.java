package com.footballmanager.infrastructure.observability;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Low-overhead operation metrics for the public beta runtime.
 *
 * <p>The registry intentionally stores only aggregate timings and counters.
 * It never records keys, payloads, tokens or personal data.  Every tenth
 * observation is logged so production logs can provide evidence without a
 * line per player or database row.</p>
 */
@Slf4j
public final class RuntimeOperationMetrics {

    private static final Map<String, Aggregate> AGGREGATES = new ConcurrentHashMap<>();

    private RuntimeOperationMetrics() {
    }

    public static <T> Mono<T> measure(String operation, Mono<T> publisher) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return publisher
                .doOnSuccess(value -> record(operation, started, true))
                .doOnError(error -> record(operation, started, false));
        });
    }

    public static void record(String operation, long startedNanos, boolean success) {
        long elapsedMillis = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        Aggregate aggregate = AGGREGATES.computeIfAbsent(operation, ignored -> new Aggregate());
        aggregate.count.increment();
        aggregate.totalMillis.add(elapsedMillis);
        if (success) {
            aggregate.success.increment();
        } else {
            aggregate.errors.increment();
        }
        long count = aggregate.count.sum();
        if (count % 10 == 0 || !success) {
            log.info("[RUNTIME-METRICS] operation={}, count={}, success={}, errors={}, avgMs={}",
                operation, count, aggregate.success.sum(), aggregate.errors.sum(),
                aggregate.totalMillis.sum() / (double) count);
        }
    }

    public static Map<String, Snapshot> snapshot() {
        Map<String, Snapshot> result = new java.util.TreeMap<>();
        AGGREGATES.forEach((operation, aggregate) -> {
            long count = aggregate.count.sum();
            result.put(operation, new Snapshot(
                count,
                aggregate.success.sum(),
                aggregate.errors.sum(),
                count == 0 ? 0d : aggregate.totalMillis.sum() / (double) count));
        });
        return Map.copyOf(result);
    }

    public static void reset() {
        AGGREGATES.clear();
    }

    public record Snapshot(long count, long success, long errors, double averageMillis) {
    }

    private static final class Aggregate {
        private final LongAdder count = new LongAdder();
        private final LongAdder success = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalMillis = new LongAdder();
    }
}

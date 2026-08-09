package com.footballmanager.application.observability;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Low-cardinality timings for public runtime operations. */
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
        aggregate.samples.add(elapsedMillis);
        while (aggregate.samples.size() > 512) {
            aggregate.samples.poll();
        }
        aggregate.maxMillis.accumulate(elapsedMillis);
        if (success) {
            aggregate.success.increment();
        } else {
            aggregate.errors.increment();
        }
        long count = aggregate.count.sum();
        if (count % 10 == 0 || !success) {
            log.info("[RUNTIME-METRICS] operation={}, count={}, success={}, errors={}, avgMs={}, p95Ms={}, maxMs={}",
                operation, count, aggregate.success.sum(), aggregate.errors.sum(),
                aggregate.totalMillis.sum() / (double) count, percentile(aggregate.samples, .95),
                aggregate.maxMillis.get());
        }
    }

    public static Map<String, Snapshot> snapshot() {
        Map<String, Snapshot> result = new java.util.TreeMap<>();
        AGGREGATES.forEach((operation, aggregate) -> {
            long count = aggregate.count.sum();
            result.put(operation, new Snapshot(count, aggregate.success.sum(), aggregate.errors.sum(),
                count == 0 ? 0d : aggregate.totalMillis.sum() / (double) count,
                percentile(aggregate.samples, .50), percentile(aggregate.samples, .95),
                aggregate.maxMillis.get()));
        });
        return Map.copyOf(result);
    }

    public static void reset() {
        AGGREGATES.clear();
    }

    public record Snapshot(long count, long success, long errors, double averageMillis,
                           double p50Millis, double p95Millis, long maxMillis) {
    }

    private static final class Aggregate {
        private final LongAdder count = new LongAdder();
        private final LongAdder success = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalMillis = new LongAdder();
        private final ConcurrentLinkedQueue<Long> samples = new ConcurrentLinkedQueue<>();
        private final LongAccumulator maxMillis = new LongAccumulator(Long::max, 0L);
    }

    private static double percentile(ConcurrentLinkedQueue<Long> samples, double percentile) {
        List<Long> values = new ArrayList<>(samples);
        if (values.isEmpty()) {
            return 0d;
        }
        Collections.sort(values);
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }
}

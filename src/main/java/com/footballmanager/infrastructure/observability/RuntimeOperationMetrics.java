package com.footballmanager.infrastructure.observability;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Compatibility facade for infrastructure tests and integrations.
 * Runtime ownership lives in the application observability package so
 * application services do not depend on infrastructure.
 */
public final class RuntimeOperationMetrics {

    private RuntimeOperationMetrics() {
    }

    public static <T> Mono<T> measure(String operation, Mono<T> publisher) {
        return com.footballmanager.application.observability.RuntimeOperationMetrics.measure(operation, publisher);
    }

    public static void record(String operation, long startedNanos, boolean success) {
        com.footballmanager.application.observability.RuntimeOperationMetrics.record(operation, startedNanos, success);
    }

    public static Map<String, Snapshot> snapshot() {
        return com.footballmanager.application.observability.RuntimeOperationMetrics.snapshot().entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> Snapshot.from(entry.getValue())));
    }

    public static void reset() {
        com.footballmanager.application.observability.RuntimeOperationMetrics.reset();
    }

    public record Snapshot(long count, long success, long errors, double averageMillis,
                           double p50Millis, double p95Millis, long maxMillis) {
        private static Snapshot from(
                com.footballmanager.application.observability.RuntimeOperationMetrics.Snapshot source) {
            return new Snapshot(source.count(), source.success(), source.errors(), source.averageMillis(),
                source.p50Millis(), source.p95Millis(), source.maxMillis());
        }
    }
}

package com.footballmanager.application.observability;

import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** Request-local durations only. No keys, identities or payloads are exposed. */
public final class WorldSummaryTiming {
    public static final String CONTEXT_KEY = WorldSummaryTiming.class.getName();
    private final Map<String, LongAdder> durations = new ConcurrentHashMap<>();
    private final long started = System.nanoTime();

    public static <T> Mono<T> measure(String stage, Mono<T> publisher) {
        return Mono.deferContextual(context -> {
            if (!context.hasKey(CONTEXT_KEY)) return publisher;
            WorldSummaryTiming timing = context.get(CONTEXT_KEY);
            long before = System.nanoTime();
            return publisher.doOnSuccess(ignored -> timing.record(stage, before))
                    .doOnError(ignored -> timing.record(stage, before));
        });
    }

    private void record(String stage, long before) {
        durations.computeIfAbsent(stage, ignored -> new LongAdder()).add(System.nanoTime() - before);
    }

    public void writeHeaders(ServerHttpResponse response) {
        String stages = durations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ";dur=" + (entry.getValue().sum() / 1_000_000L))
                .collect(Collectors.joining(", "));
        response.getHeaders().set("Server-Timing", "world-summary;dur="
                + (System.nanoTime() - started) / 1_000_000L + (stages.isEmpty() ? "" : ", " + stages));
    }
}

package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.aggregate.User;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Read reuse scoped to one subscribed HTTP request, never to a session. */
public final class RequestUserLookup {
    private static final Object KEY = new Object();
    private RequestUserLookup() {}

    public static Context enable(Context context) {
        return context.put(KEY, new ConcurrentHashMap<UUID, Mono<User>>());
    }

    public static Mono<User> find(UUID id, Supplier<Mono<User>> loader) {
        return Mono.deferContextual(context -> {
            if (!context.hasKey(KEY)) return loader.get();
            Map<UUID, Mono<User>> reads = context.get(KEY);
            return reads.computeIfAbsent(id, ignored -> Mono.defer(loader).cache());
        });
    }
}

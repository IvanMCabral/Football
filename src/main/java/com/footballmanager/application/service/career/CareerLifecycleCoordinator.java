package com.footballmanager.application.service.career;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serializes Redis persistence operations for one career owner.
 *
 * <p>This is deliberately an in-process coordinator. It protects the single
 * staging instance without pretending to be a distributed Redis lock. Each
 * queued operation releases its slot on success, error or cancellation, and
 * idle owner entries are removed so the map cannot grow with every account.</p>
 */
@Component
public final class CareerLifecycleCoordinator {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String COORDINATED_OWNER_CONTEXT =
            CareerLifecycleCoordinator.class.getName() + ".owner";

    private final ConcurrentMap<String, OwnerQueue> queues = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> resettingOwners = new ConcurrentHashMap<>();
    private final Duration timeout;

    public CareerLifecycleCoordinator(
            @Value("${app.redis.lifecycle-coordination-timeout:30s}") Duration timeout) {
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? DEFAULT_TIMEOUT
                : timeout;
    }

    public <T> Mono<T> serialize(UUID ownerId, Mono<T> operation) {
        return enqueue(ownerId, operation, false);
    }

    public <T> Mono<T> serializeReset(UUID ownerId, Mono<T> operation) {
        return enqueue(ownerId, operation, true);
    }

    /**
     * Serializes all writes for one career identity.  Owner coordination alone
     * is not enough: a late callback for an old career must wait behind reset
     * cleanup for that exact career before it can validate ownership.
     */
    public <T> Mono<T> serializeCareer(String careerId, Mono<T> operation) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        return enqueue("career:" + careerId, operation, false);
    }

    public boolean isBusy(UUID ownerId) {
        return ownerId != null && queues.containsKey(ownerId.toString());
    }

    public boolean isResetting(UUID ownerId) {
        return ownerId != null && resettingOwners.containsKey(ownerId.toString());
    }

    int activeOwnerCount() {
        return queues.size();
    }

    private <T> Mono<T> enqueue(UUID ownerId, Mono<T> operation, boolean reset) {
        if (ownerId == null) {
            return Mono.error(new IllegalArgumentException("ownerId must not be null"));
        }
        return enqueue(ownerId.toString(), operation, reset);
    }

    private <T> Mono<T> enqueue(String ownerKey, Mono<T> operation, boolean reset) {
        if (operation == null) {
            return Mono.error(new IllegalArgumentException("operation must not be null"));
        }
        return Mono.deferContextual(context -> {
            if (ownerKey.equals(context.getOrDefault(COORDINATED_OWNER_CONTEXT, ""))) {
                // Re-entrant calls are part of the same owner transaction
                // (for example, a world refresh triggered while starting a
                // career). Enqueueing them again would wait for the outer
                // operation and deadlock that operation.
                return operation;
            }
            Object resetMarker = reset ? new Object() : null;
            if (resetMarker != null) {
                resettingOwners.put(ownerKey, resetMarker);
            }
            OwnerQueue queue = queues.computeIfAbsent(ownerKey, ignored -> new OwnerQueue());
            Sinks.One<Void> released = Sinks.one();
            Mono<Void> marker = released.asMono().cache();
            Mono<Void> predecessor = queue.tail.getAndSet(marker);
            Mono<T> work = predecessor
                    .onErrorResume(ignored -> Mono.empty())
                    .then(Mono.defer(() -> operation));
            return work
                    .timeout(timeout)
                    .contextWrite(nested -> nested.put(COORDINATED_OWNER_CONTEXT, ownerKey))
                    .doFinally(signal -> {
                        released.tryEmitEmpty();
                        if (resetMarker != null) {
                            resettingOwners.remove(ownerKey, resetMarker);
                        }
                        Mono<Void> idle = Mono.empty();
                        queue.tail.compareAndSet(marker, idle);
                        if (queue.tail.get() == idle) {
                            queues.remove(ownerKey, queue);
                        }
                    });
        });
    }

    private static final class OwnerQueue {
        private final AtomicReference<Mono<Void>> tail = new AtomicReference<>(Mono.empty());
    }
}

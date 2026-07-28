package com.footballmanager.application.service.reactive;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes reactive work that is triggered by engine lifecycle callbacks rather
 * than by an HTTP request publisher.
 *
 * <p>This is the single intentional fire-and-forget boundary in production
 * code. Callers use it for lifecycle side effects that cannot be returned to an
 * HTTP pipeline (for example, match engine callbacks emitted from synchronous
 * simulation code). The executor owns subscription, error logging and shutdown
 * disposal so those concerns do not leak into application services.
 */
@Component
@Slf4j
public class ReactiveLifecycleExecutor {
    private final Set<Disposable> inFlight = ConcurrentHashMap.newKeySet();

    public void execute(String operationName, Mono<Void> work) {
        if (work == null) {
            return;
        }
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        Disposable subscription = work
            .doOnSubscribe(ignored -> log.debug("{} started", operationName))
            .doOnSuccess(ignored -> log.debug("{} completed", operationName))
            .doOnError(error -> log.warn("{} failed: {}", operationName, error.getMessage(), error))
            .doFinally(ignored -> {
                Disposable current = subscriptionRef.get();
                if (current != null) {
                    inFlight.remove(current);
                }
            })
            .onErrorResume(error -> Mono.empty())
            .subscribe();
        subscriptionRef.set(subscription);
        if (!subscription.isDisposed()) {
            inFlight.add(subscription);
        }
    }

    @PreDestroy
    void disposeInFlightWork() {
        inFlight.forEach(Disposable::dispose);
        inFlight.clear();
    }
}

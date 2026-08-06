package com.footballmanager.application.service.career;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerLifecycleCoordinatorTest {

    private final CareerLifecycleCoordinator coordinator = new CareerLifecycleCoordinator(Duration.ofSeconds(5));

    @Test
    void serializesSameOwnerAndKeepsDifferentOwnersIndependent() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

        Mono<Void> a1 = coordinator.serialize(ownerA, Mono.fromRunnable(() -> events.add("a1")));
        Mono<Void> a2 = coordinator.serialize(ownerA, Mono.fromRunnable(() -> events.add("a2")));
        Mono<Void> b1 = coordinator.serialize(ownerB, Mono.fromRunnable(() -> events.add("b1")));

        Flux.merge(a1, a2, b1).blockLast(Duration.ofSeconds(5));

        assertTrue(events.indexOf("a1") < events.indexOf("a2"));
        assertTrue(events.contains("b1"));
        assertEquals(0, coordinator.activeOwnerCount());
    }

    @Test
    void resetReleasesAfterSuccessErrorAndCancellation() {
        UUID owner = UUID.randomUUID();

        coordinator.serializeReset(owner, Mono.empty()).block(Duration.ofSeconds(5));
        assertFalse(coordinator.isBusy(owner));
        assertFalse(coordinator.isResetting(owner));

        coordinator.serializeReset(owner, Mono.error(new IllegalStateException("expected")))
                .onErrorResume(ignored -> Mono.empty())
                .block(Duration.ofSeconds(5));
        assertFalse(coordinator.isBusy(owner));
        assertFalse(coordinator.isResetting(owner));

        coordinator.serializeReset(owner, Mono.never()).subscribe().dispose();
        assertEquals(0, coordinator.activeOwnerCount());
        assertFalse(coordinator.isResetting(owner));
    }
}

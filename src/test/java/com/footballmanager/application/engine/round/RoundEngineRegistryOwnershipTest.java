package com.footballmanager.application.engine.round;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RoundEngineRegistryOwnershipTest {

    @Test
    void stoppingOwnerADoesNotTouchOwnerB() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        RoundEngineRegistry registry = new RoundEngineRegistry();
        RoundEngine engineA = new RoundEngine(UUID.randomUUID());
        RoundEngine engineB = new RoundEngine(UUID.randomUUID());
        engineA.setOwner(ownerA, "career-a");
        engineB.setOwner(ownerB, "career-b");
        registry.register(UUID.randomUUID(), engineA);
        UUID roundB = UUID.randomUUID();
        registry.register(roundB, engineB);

        assertEquals(1, registry.stopEnginesForOwner(ownerA, "career-a"));
        assertEquals(1, registry.getActiveCount());
        assertSame(engineB, registry.get(roundB));
    }

    @Test
    void unownedEngineCannotBeStoppedByOwnerScopedRequest() {
        RoundEngineRegistry registry = new RoundEngineRegistry();
        UUID roundId = UUID.randomUUID();
        RoundEngine engine = new RoundEngine(roundId);
        registry.register(roundId, engine);

        assertEquals(0, registry.stopEnginesForOwner(UUID.randomUUID(), null));
        assertTrue(registry.exists(roundId));
    }

    @Test
    void concurrentResetsConvergeForAllOwnerARoundsAndKeepOwnerB() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        RoundEngineRegistry registry = new RoundEngineRegistry();
        UUID roundA1 = UUID.randomUUID();
        UUID roundA2 = UUID.randomUUID();
        UUID roundB = UUID.randomUUID();
        RoundEngine engineA1 = new RoundEngine(roundA1);
        RoundEngine engineA2 = new RoundEngine(roundA2);
        RoundEngine engineB = new RoundEngine(roundB);
        engineA1.setOwner(ownerA, "career-a");
        engineA2.setOwner(ownerA, "career-a");
        engineB.setOwner(ownerB, "career-b");
        registry.register(roundA1, engineA1);
        registry.register(roundA2, engineA2);
        registry.register(roundB, engineB);

        int stopped = IntStream.range(0, 2)
                .parallel()
                .map(ignored -> registry.stopEnginesForOwner(ownerA, "career-a"))
                .sum();

        assertEquals(2, stopped);
        assertEquals(1, registry.getActiveCount());
        assertSame(engineB, registry.get(roundB));
        assertNull(registry.get(roundA1));
        assertNull(registry.get(roundA2));
    }
}

package com.footballmanager.application.engine.round;

import org.junit.jupiter.api.Test;

import java.util.UUID;

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
}

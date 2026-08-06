package com.footballmanager.application.service.match.session;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchSessionRegistryOwnershipTest {

    @Test
    void clearingOwnerADoesNotClearOwnerB() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        MatchSessionRegistry registry = new MatchSessionRegistry(Mockito.mock(MatchTickHandler.class));
        UUID matchA = UUID.randomUUID();
        UUID matchB = UUID.randomUUID();
        registry.getOrCreateSession(ownerA, matchA, UUID.randomUUID(), UUID.randomUUID());
        registry.getOrCreateSession(ownerB, matchB, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(1, registry.clearSessionsForOwner(ownerA, null));
        assertEquals(1, registry.getActiveSessionCount());
        assertEquals(true, registry.hasSession(ownerB, matchB));
    }

    @Test
    void concurrentResetsRemoveAllOwnerASessionsAndKeepOwnerB() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        MatchSessionRegistry registry = new MatchSessionRegistry(Mockito.mock(MatchTickHandler.class));
        UUID matchA1 = UUID.randomUUID();
        UUID matchA2 = UUID.randomUUID();
        UUID matchB = UUID.randomUUID();
        registry.getOrCreateSession(ownerA, matchA1, UUID.randomUUID(), UUID.randomUUID(), "career-a");
        registry.getOrCreateSession(ownerA, matchA2, UUID.randomUUID(), UUID.randomUUID(), "career-a");
        registry.getOrCreateSession(ownerB, matchB, UUID.randomUUID(), UUID.randomUUID(), "career-b");

        int cleared = IntStream.range(0, 2)
                .parallel()
                .map(ignored -> registry.clearSessionsForOwner(ownerA, "career-a"))
                .sum();

        assertEquals(2, cleared);
        assertEquals(1, registry.getActiveSessionCount());
        assertEquals(true, registry.hasSession(ownerB, matchB));
        assertEquals(false, registry.hasSession(ownerA, matchA1));
        assertEquals(false, registry.hasSession(ownerA, matchA2));
    }
}

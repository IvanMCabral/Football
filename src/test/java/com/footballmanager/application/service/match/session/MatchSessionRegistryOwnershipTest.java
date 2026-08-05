package com.footballmanager.application.service.match.session;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

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
}

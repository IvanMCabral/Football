package com.footballmanager.application.engine.round;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RoundEngineRegistryOwnershipTest {

    @Test
    void unregisterRemovesMappingsEvenAfterTheCompletedEngineReleasedItsMatches() {
        var registry = new RoundEngineRegistry();
        UUID roundId = UUID.randomUUID(), matchId = UUID.randomUUID();
        var engine = new RoundEngine(roundId);
        engine.registerMatch(matchId, mock(com.footballmanager.application.engine.match.MatchEngine.class));
        registry.register(roundId, engine);
        engine.stop();
        registry.unregister(roundId);
        assertNull(registry.getRoundIdByMatchId(matchId));
        assertNull(registry.getByMatchId(matchId));
    }

    @Test
    void replacingAnAlreadyStoppedRoundAlsoRemovesItsRetainedMappings() {
        var registry = new RoundEngineRegistry();
        UUID roundId = UUID.randomUUID(), matchId = UUID.randomUUID();
        var old = new RoundEngine(roundId);
        old.registerMatch(matchId, mock(com.footballmanager.application.engine.match.MatchEngine.class));
        registry.register(roundId, old);
        old.stop();
        registry.register(roundId, new RoundEngine(roundId));
        assertNull(registry.getRoundIdByMatchId(matchId));
    }

    @Test
    void stoppingAnOwnerRemovesClearedMappingsAndPreservesOtherOwnersMatches() {
        var registry = new RoundEngineRegistry();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        UUID roundA = UUID.randomUUID(), roundB = UUID.randomUUID();
        UUID matchA = UUID.randomUUID(), matchB = UUID.randomUUID();
        var a = new RoundEngine(roundA); a.setOwner(owner, "career-a");
        var b = new RoundEngine(roundB); b.setOwner(other, "career-b");
        a.registerMatch(matchA, mock(com.footballmanager.application.engine.match.MatchEngine.class));
        b.registerMatch(matchB, mock(com.footballmanager.application.engine.match.MatchEngine.class));
        registry.register(roundA, a); registry.register(roundB, b); a.stop();
        registry.stopEnginesForOwner(owner, "career-a");
        assertNull(registry.getRoundIdByMatchId(matchA));
        assertEquals(roundB, registry.getRoundIdByMatchId(matchB));
        assertSame(b, registry.getByMatchId(matchB));
    }

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

    @Test
    void replacingRoundRemovesAllMappingsOwnedByTheReplacedEngine() {
        RoundEngineRegistry registry = new RoundEngineRegistry();
        UUID roundId = UUID.randomUUID();
        UUID oldMatchId = UUID.randomUUID();
        UUID newMatchId = UUID.randomUUID();
        RoundEngine engineA = new RoundEngine(roundId);
        RoundEngine engineB = new RoundEngine(roundId);
        engineA.registerMatch(oldMatchId, mock(com.footballmanager.application.engine.match.MatchEngine.class));
        engineB.registerMatch(newMatchId, mock(com.footballmanager.application.engine.match.MatchEngine.class));

        registry.register(roundId, engineA);
        assertSame(engineA, registry.getByMatchId(oldMatchId));

        registry.register(roundId, engineB);

        assertNull(registry.getByMatchId(oldMatchId));
        assertNull(registry.getRoundIdByMatchId(oldMatchId));
        assertSame(engineB, registry.getByMatchId(newMatchId));
        assertEquals(roundId, registry.getRoundIdByMatchId(newMatchId));
    }
}

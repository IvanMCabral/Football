package com.footballmanager.domain.model.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPlayerIdentityTest {

    @Test
    void canonicalIdentityIsStableAndOwnerScopedAcrossTenRebuilds() {
        UUID owner = UUID.randomUUID();
        UUID realPlayer = UUID.randomUUID();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, realPlayer,
                    "team", "Player", 24, "ATT", 80, 40, 75, 79, 82, 77,
                    BigDecimal.valueOf(10));
            ids.add(player.getWorldPlayerId());
        }
        assertEquals(1, ids.size());
        assertEquals(WorldPlayer.stableCanonicalWorldPlayerId(owner, realPlayer), ids.iterator().next());
        assertEquals(WorldPlayer.stableCanonicalWorldPlayerId(owner, realPlayer),
                WorldPlayer.stableCanonicalWorldPlayerId(UUID.randomUUID(), realPlayer));
        assertNotEquals(WorldPlayer.stableCanonicalWorldPlayerId(owner, realPlayer),
                WorldPlayer.stableCanonicalWorldPlayerId(owner, UUID.randomUUID()));
        assertTrue(ids.iterator().next().matches("[0-9a-f-]{36}"));
    }

    @Test
    void oneThousandCanonicalInputsAreStableAndCollisionFree() {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UUID real = UUID.nameUUIDFromBytes(("canonical-player-" + i)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String first = WorldPlayer.stableCanonicalWorldPlayerId(owner, real);
            assertEquals(first, WorldPlayer.stableCanonicalWorldPlayerId(UUID.randomUUID(), real));
            ids.add(first);
        }
        assertEquals(1000, ids.size());
    }
}

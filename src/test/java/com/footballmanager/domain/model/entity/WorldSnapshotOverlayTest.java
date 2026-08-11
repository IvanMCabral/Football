package com.footballmanager.domain.model.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldSnapshotOverlayTest {

    @Test
    void roundTripPreservesCustomStateAndCanonicalReferences() {
        UUID owner = UUID.randomUUID();
        UUID realPlayerId = UUID.randomUUID();
        UUID realTeamId = UUID.randomUUID();
        UUID realLeagueId = UUID.randomUUID();
        WorldTeam realTeam = WorldTeam.fromRealTeam(realTeamId, realLeagueId, "Canonical", "AR", "BA",
                BigDecimal.TEN, "4-4-2", null);
        WorldPlayer realPlayer = WorldPlayer.fromCanonicalPlayer(owner, realPlayerId, realTeam.getWorldTeamId(),
                "Canonical Player", 25, "ATT", 80, 40, 70, 75, 80, 78, BigDecimal.TEN);
        WorldTeam customTeam = WorldTeam.createCustom("Custom", "AR", BigDecimal.ONE, "4-3-3");
        WorldPlayer customPlayer = WorldPlayer.createCustom("Custom Player", 19, "MID", 60, 60, 60, 60, 60, 60,
                BigDecimal.ONE);

        WorldSnapshot original = new WorldSnapshot();
        original.setUserId(owner);
        original.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        original.setLastUpdated(Instant.parse("2026-01-02T00:00:00Z"));
        original.getWorldTeams().put(realTeam.getWorldTeamId(), realTeam);
        original.getWorldTeams().put(customTeam.getWorldTeamId(), customTeam);
        original.getWorldPlayers().put(realPlayer.getWorldPlayerId(), realPlayer);
        original.getWorldPlayers().put(customPlayer.getWorldPlayerId(), customPlayer);

        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setWorldTeams(java.util.Map.of(realTeam.getWorldTeamId(), realTeam));
        canonical.setWorldPlayers(java.util.Map.of(realPlayer.getWorldPlayerId(), realPlayer));
        WorldSnapshot restored = WorldSnapshotOverlay.fromSnapshot(original).applyTo(canonical);

        assertEquals(owner, restored.getUserId());
        assertEquals(2, restored.getWorldTeams().size());
        assertEquals(2, restored.getWorldPlayers().size());
        assertNotNull(restored.getWorldTeam(customTeam.getWorldTeamId()));
        assertNotNull(restored.getWorldPlayer(customPlayer.getWorldPlayerId()));
        assertSame(realPlayer, restored.getWorldPlayer(realPlayer.getWorldPlayerId()));
        assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        assertEquals(original.getLastUpdated(), restored.getLastUpdated());
    }

    @Test
    void legacyRealPlayerIdResolvesWithoutReplacingCanonicalIdentity() {
        UUID owner = UUID.randomUUID();
        UUID realPlayerId = UUID.randomUUID();
        String legacyId = UUID.randomUUID().toString();
        WorldPlayer legacy = WorldPlayer.fromRealPlayer(realPlayerId, "team", "Legacy", 25,
                "ATT", 80, 40, 70, 75, 80, 78, BigDecimal.TEN);
        legacy.setWorldPlayerId(legacyId);
        WorldSnapshot original = new WorldSnapshot();
        original.setUserId(owner);
        original.getWorldPlayers().put(legacyId, legacy);
        WorldPlayer canonicalPlayer = WorldPlayer.fromCanonicalPlayer(owner, realPlayerId, "team", "Legacy", 25,
                "ATT", 80, 40, 70, 75, 80, 78, BigDecimal.TEN);
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.getWorldPlayers().put(canonicalPlayer.getWorldPlayerId(), canonicalPlayer);

        WorldSnapshot restored = WorldSnapshotOverlay.fromSnapshot(original, canonical).applyTo(canonical);

        assertSame(canonicalPlayer, restored.getWorldPlayer(canonicalPlayer.getWorldPlayerId()));
        assertSame(canonicalPlayer, restored.getWorldPlayer(legacyId));
        assertEquals(1, restored.getAllWorldPlayers().size());
    }

    @Test
    void aliasCollisionFailsClosed() {
        UUID owner = UUID.randomUUID();
        UUID realA = UUID.randomUUID();
        UUID realB = UUID.randomUUID();
        WorldPlayer a = WorldPlayer.fromCanonicalPlayer(owner, realA, "team", "A", 25,
                "ATT", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
        WorldPlayer b = WorldPlayer.fromCanonicalPlayer(owner, realB, "team", "B", 25,
                "ATT", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.getWorldPlayers().put(a.getWorldPlayerId(), a);
        canonical.getWorldPlayers().put(b.getWorldPlayerId(), b);
        WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
        overlay.setOwnerId(owner);
        overlay.setLegacyPlayerAliases(java.util.Map.of(a.getWorldPlayerId(), b.getWorldPlayerId()));
        assertThrows(IllegalStateException.class, () -> overlay.applyTo(canonical));
    }
}

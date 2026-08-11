package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.out.redis.CanonicalWorldCatalogFingerprint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageRepresentationTest {

    @Test
    void measuredRepresentativeWorldShowsCatalogOverlayReduction() throws Exception {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        WorldSnapshot full = new WorldSnapshot();
        full.setUserId(owner);
        full.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        full.setLastUpdated(Instant.parse("2026-01-02T00:00:00Z"));
        for (int teamIndex = 0; teamIndex < 60; teamIndex++) {
            UUID realTeam = UUID.nameUUIDFromBytes(("team:" + teamIndex).getBytes(StandardCharsets.UTF_8));
            WorldTeam team = WorldTeam.fromRealTeam(realTeam, UUID.nameUUIDFromBytes("league".getBytes(StandardCharsets.UTF_8)),
                    "Team " + teamIndex, "AR", "City", BigDecimal.TEN, "4-4-2", null);
            full.getWorldTeams().put(team.getWorldTeamId(), team);
            for (int playerIndex = 0; playerIndex < 20; playerIndex++) {
                UUID realPlayer = UUID.nameUUIDFromBytes(("player:" + teamIndex + ":" + playerIndex)
                        .getBytes(StandardCharsets.UTF_8));
                WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, realPlayer, team.getWorldTeamId(),
                        "Player " + teamIndex + "-" + playerIndex, 24, "MID", 70, 70, 70, 70, 70, 70,
                        BigDecimal.TEN);
                full.getWorldPlayers().put(player.getWorldPlayerId(), player);
            }
        }
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setLeagues(full.getLeagues());
        canonical.setWorldTeams(new LinkedHashMap<>(full.getWorldTeams()));
        canonical.setWorldPlayers(new LinkedHashMap<>(full.getWorldPlayers()));
        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(full, canonical);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String fingerprint = new CanonicalWorldCatalogFingerprint(mapper).fingerprint(canonical);
        RedisEnvelope envelope = new RedisEnvelope(WorldSnapshotOverlay.STORAGE_VERSION, "COMMITTED", owner,
                "world-catalog:v2:" + fingerprint, fingerprint, "0".repeat(64), overlay);
        long beforePerOwner = mapper.writeValueAsBytes(full).length;
        long catalog = mapper.writeValueAsBytes(canonical).length;
        long ownerOverlay = mapper.writeValueAsBytes(envelope).length;
        int owners = 10;
        long before = beforePerOwner * owners;
        long after = catalog + ownerOverlay * owners;
        double reduction = (before - after) * 100.0 / before;
        System.out.printf("[WORLD-SIZE] beforeBytes=%d catalogBytes=%d overlayBytes=%d afterBytes=%d reductionPct=%.2f%n",
                before, catalog, ownerOverlay, after, reduction);
        assertTrue(after < before, "V2 representation must be smaller than duplicated full world");
    }

    private record RedisEnvelope(int storageVersion, String state, UUID ownerId, String catalogKey,
                                 String catalogFingerprint, String overlayChecksum,
                                 WorldSnapshotOverlay overlay) {}
}

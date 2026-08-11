package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldSnapshotOverlay;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldOverlayContractTest {

    private static final UUID OWNER = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID LEAGUE = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID TEAM = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("74000000-0000-0000-0000-000000000001");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void explicitNullSnapshotTimestampsAreDistinctFromAbsentDelta() throws Exception {
        WorldSnapshot canonical = snapshot(Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"));
        WorldSnapshot current = copy(canonical);
        current.setCreatedAt(null);
        current.setLastUpdated(null);

        WorldSnapshotOverlay stored = mapper.readValue(mapper.writeValueAsBytes(
                WorldSnapshotOverlay.fromSnapshot(current, canonical)), WorldSnapshotOverlay.class);
        WorldSnapshot reconstructed = stored.applyTo(copy(canonical));

        assertEquals(Set.of(WorldSnapshotOverlay.SnapshotField.CREATED_AT,
                WorldSnapshotOverlay.SnapshotField.LAST_UPDATED), stored.getChangedSnapshotFields());
        assertNull(reconstructed.getCreatedAt());
        assertNull(reconstructed.getLastUpdated());
        assertTrue(new WorldSemanticComparator().compare(current, reconstructed).equivalent());
    }

    @Test
    void absentTimestampDeltaPreservesCanonicalValues() throws Exception {
        WorldSnapshot canonical = snapshot(Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"));
        WorldSnapshotOverlay stored = mapper.readValue(mapper.writeValueAsBytes(
                WorldSnapshotOverlay.fromSnapshot(copy(canonical), canonical)), WorldSnapshotOverlay.class);

        assertTrue(stored.getChangedSnapshotFields().isEmpty());
        WorldSnapshot reconstructed = stored.applyTo(copy(canonical));
        assertEquals(canonical.getCreatedAt(), reconstructed.getCreatedAt());
        assertEquals(canonical.getLastUpdated(), reconstructed.getLastUpdated());
    }

    @Test
    void jacksonAuthorityIncludesDerivedViewsButDoesNotPersistTheirDuplicatePayload() throws Exception {
        WorldEntityFieldAuthority authority = new WorldEntityFieldAuthority(mapper);
        authority.requireComplete();

        Map<String, WorldEntityFieldAuthority.SerializationAuthority> snapshot =
                authority.jacksonClassifications().get(WorldSnapshot.class);
        assertEquals(9, snapshot.size());
        assertEquals(WorldEntityFieldAuthority.SerializationAuthority.DERIVED_VIEW_IGNORED,
                snapshot.get("allWorldTeams"));
        assertEquals(WorldEntityFieldAuthority.SerializationAuthority.DERIVED_VIEW_IGNORED,
                snapshot.get("allWorldPlayers"));
        String json = mapper.writeValueAsString(snapshot(null, null));
        assertFalse(json.contains("allWorldTeams"));
        assertFalse(json.contains("allWorldPlayers"));
        assertTrue(authority.uncoveredJacksonProperties().isEmpty());
    }

    @Test
    void nullSemanticsAuthorityCoversEveryOverlayValue() {
        WorldOverlayNullSemanticsAuthority authority = new WorldOverlayNullSemanticsAuthority();
        assertEquals(26, authority.fields().size());
        assertTrue(authority.require("WorldSnapshot.createdAt").ownerExplicitNullAllowed());
        assertTrue(authority.require("WorldPlayer.name").ownerExplicitNullAllowed());
        assertFalse(authority.require("WorldPlayer.skillLevels").ownerExplicitNullAllowed());
    }

    @Test
    void comparatorRejectsUnexpectedSelfAliasAndWrongAliasGraphs() throws Exception {
        WorldSnapshot expected = snapshot(null, null);
        String playerId = expected.getAllWorldPlayers().getFirst().getWorldPlayerId();

        WorldSnapshot selfAlias = copy(expected);
        selfAlias.setWorldPlayerAliases(Map.of(playerId, playerId));
        assertFalse(new WorldSemanticComparator().compare(expected, selfAlias).equivalent());

        WorldSnapshot wrongTarget = copy(expected);
        wrongTarget.setWorldPlayerAliases(Map.of("legacy", "missing"));
        assertFalse(new WorldSemanticComparator().compare(expected, wrongTarget).equivalent());

        WorldSnapshot cycle = copy(expected);
        cycle.setWorldPlayerAliases(Map.of("a", "b", "b", "a"));
        assertFalse(new WorldSemanticComparator().compare(expected, cycle).equivalent());
    }

    private WorldSnapshot copy(WorldSnapshot source) throws Exception {
        return mapper.readValue(mapper.writeValueAsBytes(source), WorldSnapshot.class);
    }

    private static WorldSnapshot snapshot(Instant createdAt, Instant lastUpdated) {
        WorldSnapshot value = new WorldSnapshot();
        value.setUserId(OWNER);
        value.setCreatedAt(createdAt);
        value.setLastUpdated(lastUpdated);
        value.setLeagues(java.util.List.of(new WorldLeague(LEAGUE, "League", "AR", 1)));
        WorldTeam team = WorldTeam.fromRealTeam(TEAM, LEAGUE, "Team", "AR", "City",
                BigDecimal.TEN, "4-4-2");
        value.setWorldTeams(new LinkedHashMap<>(Map.of(team.getWorldTeamId(), team)));
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(OWNER, PLAYER, team.getWorldTeamId(),
                "Player", 22, "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN);
        value.setWorldPlayers(new LinkedHashMap<>(Map.of(player.getWorldPlayerId(), player)));
        value.setWorldPlayerAliases(Map.of());
        return value;
    }
}

package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageMigrationLimitsTest {

    @Test
    void acceptsABoundedOwner() {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.getWorldTeams().put("team", WorldTeam.createCustom("Team", "AR", BigDecimal.ONE, "4-4-2"));
        snapshot.getWorldPlayers().put("player", WorldPlayer.createCustom("Player", 20, "MID",
                60, 60, 60, 60, 60, 60, BigDecimal.ONE));
        assertTrue(WorldStorageMigrationLimits.validate(snapshot, 1_024).valid());
    }

    @Test
    void rejectsTooManyCustomTeams() {
        WorldSnapshot snapshot = new WorldSnapshot();
        for (int index = 0; index <= WorldStorageMigrationLimits.MAX_CUSTOM_TEAMS; index++) {
            WorldTeam team = WorldTeam.createCustom("T" + index, "AR", BigDecimal.ONE, "4-4-2");
            snapshot.getWorldTeams().put(team.getWorldTeamId(), team);
        }
        assertFalse(WorldStorageMigrationLimits.validate(snapshot, 1_024).valid());
    }

    @Test
    void rejectsTooManyAliasesAndOversizeSerializedSource() {
        WorldSnapshot snapshot = new WorldSnapshot();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (int index = 0; index <= WorldStorageMigrationLimits.MAX_ALIASES; index++) {
            aliases.put("legacy-" + index, "canonical-" + index);
        }
        snapshot.setWorldPlayerAliases(aliases);
        assertFalse(WorldStorageMigrationLimits.validate(snapshot, 1_024).valid());

        snapshot.setWorldPlayerAliases(Map.of());
        assertFalse(WorldStorageMigrationLimits.validate(snapshot,
                WorldStorageMigrationLimits.MAX_SERIALIZED_LEGACY_BYTES + 1).valid());
    }

    @Test
    void rejectsUnboundedTextAndIdentifiers() {
        WorldSnapshot snapshot = new WorldSnapshot();
        WorldTeam team = WorldTeam.createCustom("x".repeat(WorldStorageMigrationLimits.MAX_NAME_CHARS + 1),
                "AR", BigDecimal.ONE, "4-4-2");
        snapshot.getWorldTeams().put(team.getWorldTeamId(), team);
        assertFalse(WorldStorageMigrationLimits.validate(snapshot, 1_024).valid());
    }

    @Test
    void completeMaxAndMaxPlusOneBoundaryMatrixIsFailClosed() {
        assertBoundary(customTeams(WorldStorageMigrationLimits.MAX_CUSTOM_TEAMS),
                customTeams(WorldStorageMigrationLimits.MAX_CUSTOM_TEAMS + 1), 1_024);
        assertBoundary(customPlayers(WorldStorageMigrationLimits.MAX_CUSTOM_PLAYERS),
                customPlayers(WorldStorageMigrationLimits.MAX_CUSTOM_PLAYERS + 1), 1_024);
        assertBoundary(realTeams(WorldStorageMigrationLimits.MAX_TOTAL_TEAMS),
                realTeams(WorldStorageMigrationLimits.MAX_TOTAL_TEAMS + 1), 1_024);
        assertBoundary(realPlayers(WorldStorageMigrationLimits.MAX_TOTAL_PLAYERS),
                realPlayers(WorldStorageMigrationLimits.MAX_TOTAL_PLAYERS + 1), 1_024);
        assertBoundary(aliases(WorldStorageMigrationLimits.MAX_ALIASES),
                aliases(WorldStorageMigrationLimits.MAX_ALIASES + 1), 1_024);
        assertBoundary(leagues(WorldStorageMigrationLimits.MAX_LEAGUES),
                leagues(WorldStorageMigrationLimits.MAX_LEAGUES + 1), 1_024);
        assertBoundary(traits(WorldStorageMigrationLimits.MAX_SPECIAL_TRAITS_PER_PLAYER),
                traits(WorldStorageMigrationLimits.MAX_SPECIAL_TRAITS_PER_PLAYER + 1), 1_024);
        assertEquals(PlayerSkill.values().length, WorldStorageMigrationLimits.MAX_SKILLS_PER_PLAYER,
                "the enum itself makes max+1 structurally unrepresentable");
        assertTrue(WorldStorageMigrationLimits.validate(skillsAtTypeMaximum(), 1_024).valid());
        assertBoundary(text(WorldStorageMigrationLimits.MAX_NAME_CHARS),
                text(WorldStorageMigrationLimits.MAX_NAME_CHARS + 1), 1_024);
        assertBoundary(identifier(WorldStorageMigrationLimits.MAX_IDENTIFIER_CHARS),
                identifier(WorldStorageMigrationLimits.MAX_IDENTIFIER_CHARS + 1), 1_024);
        WorldSnapshot empty = new WorldSnapshot();
        assertTrue(WorldStorageMigrationLimits.validate(empty,
                WorldStorageMigrationLimits.MAX_SERIALIZED_LEGACY_BYTES).valid());
        assertFalse(WorldStorageMigrationLimits.validate(empty,
                WorldStorageMigrationLimits.MAX_SERIALIZED_LEGACY_BYTES + 1).valid());
    }

    private static void assertBoundary(WorldSnapshot maximum, WorldSnapshot overflow, int bytes) {
        assertTrue(WorldStorageMigrationLimits.validate(maximum, bytes).valid());
        assertFalse(WorldStorageMigrationLimits.validate(overflow, bytes).valid());
    }

    private static WorldSnapshot customTeams(int count) {
        WorldSnapshot snapshot = new WorldSnapshot();
        for (int i = 0; i < count; i++) {
            WorldTeam team = WorldTeam.createCustom("t", "AR", BigDecimal.ONE, "4-4-2");
            snapshot.getWorldTeams().put(team.getWorldTeamId(), team);
        }
        return snapshot;
    }

    private static WorldSnapshot customPlayers(int count) {
        WorldSnapshot snapshot = new WorldSnapshot();
        for (int i = 0; i < count; i++) {
            WorldPlayer player = WorldPlayer.createCustom("p", 20, "MID", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return snapshot;
    }

    private static WorldSnapshot realTeams(int count) {
        WorldSnapshot snapshot = new WorldSnapshot();
        UUID league = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.nameUUIDFromBytes(("team" + i).getBytes());
            WorldTeam team = WorldTeam.fromRealTeam(id, league, "t", "AR", "c", BigDecimal.ONE, "4-4-2");
            snapshot.getWorldTeams().put(team.getWorldTeamId(), team);
        }
        return snapshot;
    }

    private static WorldSnapshot realPlayers(int count) {
        WorldSnapshot snapshot = new WorldSnapshot();
        for (int i = 0; i < count; i++) {
            WorldPlayer player = new WorldPlayer();
            player.setWorldPlayerId("p" + i);
            player.setRealPlayerId(UUID.nameUUIDFromBytes(("player" + i).getBytes()));
            player.setOrigin(WorldPlayer.WorldPlayerOrigin.REAL);
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return snapshot;
    }

    private static WorldSnapshot aliases(int count) {
        WorldSnapshot snapshot = new WorldSnapshot();
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) values.put("a" + i, "b" + i);
        snapshot.setWorldPlayerAliases(values);
        return snapshot;
    }

    private static WorldSnapshot leagues(int count) {
        WorldSnapshot snapshot = new WorldSnapshot();
        java.util.ArrayList<WorldLeague> values = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) values.add(new WorldLeague(UUID.nameUUIDFromBytes(("l" + i).getBytes()), "l", "AR", 1));
        snapshot.setLeagues(values);
        return snapshot;
    }

    private static WorldSnapshot traits(int count) {
        WorldSnapshot snapshot = customPlayers(1);
        WorldPlayer player = snapshot.getAllWorldPlayers().getFirst();
        java.util.ArrayList<PlayerSpecialTrait> values = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) values.add(new PlayerSpecialTrait(UUID.randomUUID(), "c" + i, "n", "d"));
        player.setSpecialTraits(values);
        return snapshot;
    }

    private static WorldSnapshot skillsAtTypeMaximum() {
        WorldSnapshot snapshot = customPlayers(1);
        Map<PlayerSkill, Integer> values = new java.util.EnumMap<>(PlayerSkill.class);
        for (PlayerSkill skill : PlayerSkill.values()) values.put(skill, 1);
        snapshot.getAllWorldPlayers().getFirst().setSkillLevels(values);
        return snapshot;
    }

    private static WorldSnapshot text(int length) {
        WorldSnapshot snapshot = new WorldSnapshot();
        WorldTeam team = WorldTeam.createCustom("x".repeat(length), "AR", BigDecimal.ONE, "4-4-2");
        snapshot.getWorldTeams().put(team.getWorldTeamId(), team);
        return snapshot;
    }

    private static WorldSnapshot identifier(int length) {
        WorldSnapshot snapshot = new WorldSnapshot();
        WorldPlayer player = WorldPlayer.createCustom("p", 20, "MID", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
        player.setWorldPlayerId("x".repeat(length));
        snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        return snapshot;
    }
}

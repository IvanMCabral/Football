package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldSnapshotOverlay;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSemanticOverlayCompletenessTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEAGUE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TEAM = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void auditP0AndEveryOverlayEligibleFieldSurviveRoundTrip() {
        WorldSnapshot canonicalForDiff = canonical();
        WorldSnapshot current = mutatedLegacy();

        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(current, canonicalForDiff);
        WorldSnapshot reconstructed = overlay.applyTo(canonical());

        new WorldSemanticComparator().requireEquivalent(current, reconstructed);
        WorldPlayer reloadedPlayer = reconstructed.getAllWorldPlayers().getFirst();
        WorldTeam reloadedTeam = reconstructed.getAllWorldTeams().getFirst();
        assertEquals(99, reloadedPlayer.getBaseAttack(), "audit P0 player regression");
        assertEquals("3-5-2", reloadedTeam.getBaseFormation(), "audit P0 team regression");
        assertEquals(13, overlay.getRealPlayerDeltas().get(PLAYER).getChangedFields().size());
        assertEquals(7, overlay.getRealTeamDeltas().get(TEAM).getChangedFields().size());
        assertEquals(3, overlay.getRealLeagueDeltas().get(LEAGUE).getChangedFields().size());
    }

    @Test
    void unchangedCanonicalFieldsAreNotDuplicatedInDelta() {
        WorldSnapshot canonical = canonical();
        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(canonical, canonical());

        assertTrue(overlay.getRealPlayerDeltas().isEmpty());
        assertTrue(overlay.getRealTeamDeltas().isEmpty());
        assertTrue(overlay.getRealLeagueDeltas().isEmpty());
        assertTrue(overlay.getCustomPlayers().isEmpty());
        assertTrue(overlay.getCustomTeams().isEmpty());
    }

    @Test
    void removalsAndAdditionalEntitiesRemainSemanticallyExact() {
        WorldSnapshot current = mutatedLegacy();
        current.setLeagues(List.of(new WorldLeague(null, "Owner League", "AR", 4)));
        current.setWorldTeams(new LinkedHashMap<>());
        current.setWorldPlayers(new LinkedHashMap<>());
        current.setWorldPlayerAliases(Map.of());

        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(current, canonical());
        WorldSnapshot reconstructed = overlay.applyTo(canonical());

        new WorldSemanticComparator().requireEquivalent(current, reconstructed);
        assertEquals(1, overlay.getRemovedCanonicalLeagueIds().size());
        assertEquals(1, overlay.getRemovedCanonicalTeamIds().size());
        assertEquals(1, overlay.getRemovedCanonicalPlayerIds().size());
    }

    @Test
    void fieldAuthorityCoversEveryPersistedWorldField() {
        WorldEntityFieldAuthority authority = new WorldEntityFieldAuthority();
        authority.requireComplete();
        assertTrue(authority.uncoveredFields().isEmpty());
        assertEquals(38, authority.classifications().values().stream().mapToInt(Map::size).sum());
    }

    @Test
    void semanticComparatorDetectsAFieldThatWasNotReconstructed() {
        WorldSnapshot expected = mutatedLegacy();
        WorldSnapshot actual = mutatedLegacy();
        actual.getAllWorldPlayers().getFirst().setBaseDefense(1);

        WorldSemanticComparator.Comparison comparison = new WorldSemanticComparator().compare(expected, actual);

        assertFalse(comparison.equivalent());
        assertTrue(comparison.differences().stream().anyMatch(path -> path.endsWith(".baseDefense")));
    }

    @TestFactory
    Stream<DynamicTest> everyOverlayFieldHasAnIndependentNegativeControl() {
        List<FieldMutation> mutations = new ArrayList<>();
        mutations.add(team("realLeagueId", value -> value.setRealLeagueId(UUID.randomUUID())));
        mutations.add(team("name", value -> value.setName("changed")));
        mutations.add(team("country", value -> value.setCountry("BR")));
        mutations.add(team("city", value -> value.setCity("changed")));
        mutations.add(team("baseBudget", value -> value.setBaseBudget(BigDecimal.valueOf(77))));
        mutations.add(team("baseFormation", value -> value.setBaseFormation("3-4-3")));
        mutations.add(team("division", value -> value.setDivision(Division.TERCERA)));
        mutations.add(player("worldTeamId", value -> value.setWorldTeamId("other-team")));
        mutations.add(player("name", value -> value.setName("changed")));
        mutations.add(player("age", value -> value.setAge(39)));
        mutations.add(player("position", value -> value.setPosition("ATT")));
        mutations.add(player("baseAttack", value -> value.setBaseAttack(99)));
        mutations.add(player("baseDefense", value -> value.setBaseDefense(98)));
        mutations.add(player("baseTechnique", value -> value.setBaseTechnique(97)));
        mutations.add(player("baseSpeed", value -> value.setBaseSpeed(96)));
        mutations.add(player("baseStamina", value -> value.setBaseStamina(95)));
        mutations.add(player("baseMentality", value -> value.setBaseMentality(94)));
        mutations.add(player("baseMarketValue", value -> value.setBaseMarketValue(BigDecimal.valueOf(93))));
        mutations.add(player("heightCm", value -> value.setHeightCm(192)));
        mutations.add(player("skillLevels", value -> value.setSkillLevels(Map.of(PlayerSkill.SHOOTER, 5))));
        mutations.add(player("specialTraits", value -> value.setSpecialTraits(List.of(
                new PlayerSpecialTrait(PLAYER, "CHANGED", "Changed", "Changed")))));
        mutations.add(league("name", value -> value.setName("changed")));
        mutations.add(league("country", value -> value.setCountry("BR")));
        mutations.add(league("tier", value -> value.setTier(3)));
        return mutations.stream().map(mutation -> DynamicTest.dynamicTest(mutation.name(), () -> {
            WorldSnapshot current = canonical();
            mutation.apply(current);
            WorldSnapshot reconstructed = WorldSnapshotOverlay.fromSnapshot(current, canonical())
                    .applyTo(canonical());
            new WorldSemanticComparator().requireEquivalent(current, reconstructed);
        }));
    }

    private static FieldMutation team(String field, Consumer<WorldTeam> mutation) {
        return new FieldMutation("WorldTeam." + field, snapshot -> mutation.accept(snapshot.getAllWorldTeams().getFirst()));
    }

    private static FieldMutation player(String field, Consumer<WorldPlayer> mutation) {
        return new FieldMutation("WorldPlayer." + field, snapshot -> mutation.accept(snapshot.getAllWorldPlayers().getFirst()));
    }

    private static FieldMutation league(String field, Consumer<WorldLeague> mutation) {
        return new FieldMutation("WorldLeague." + field, snapshot -> mutation.accept(snapshot.getLeagues().getFirst()));
    }

    private record FieldMutation(String name, Consumer<WorldSnapshot> mutation) {
        void apply(WorldSnapshot snapshot) { mutation.accept(snapshot); }
    }

    private static WorldSnapshot canonical() {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(OWNER);
        snapshot.setCreatedAt(Instant.parse("2026-08-01T00:00:00Z"));
        snapshot.setLastUpdated(Instant.parse("2026-08-02T00:00:00Z"));
        snapshot.setLeagues(List.of(new WorldLeague(LEAGUE, "Canonical League", "ES", 1)));
        WorldTeam team = WorldTeam.fromRealTeam(TEAM, LEAGUE, "Canonical Team", "ES", "Madrid",
                BigDecimal.valueOf(1_000_000), "4-4-2", Division.PRIMERA);
        snapshot.setWorldTeams(new LinkedHashMap<>(Map.of(team.getWorldTeamId(), team)));
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(OWNER, PLAYER, team.getWorldTeamId(),
                "Canonical Player", 24, "MID", 70, 71, 72, 73, 74, 75, BigDecimal.valueOf(5_000_000));
        player.setHeightCm(180);
        player.setSkillLevels(Map.of(PlayerSkill.PASSER, 2));
        player.setSpecialTraits(List.of(new PlayerSpecialTrait(PLAYER, "BASE", "Base", "Base trait")));
        snapshot.setWorldPlayers(new LinkedHashMap<>(Map.of(player.getWorldPlayerId(), player)));
        snapshot.setWorldPlayerAliases(Map.of());
        return snapshot;
    }

    private static WorldSnapshot mutatedLegacy() {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(OWNER);
        snapshot.setCreatedAt(Instant.parse("2026-08-01T00:00:00Z"));
        snapshot.setLastUpdated(Instant.parse("2026-08-02T00:00:00Z"));
        snapshot.setLeagues(List.of(new WorldLeague(LEAGUE, "Owner League", "AR", 2)));
        WorldTeam team = WorldTeam.fromRealTeam(TEAM,
                UUID.fromString("20000000-0000-0000-0000-000000000002"), "Owner Team", "AR", "Rosario",
                BigDecimal.valueOf(2_000_000), "3-5-2", Division.SEGUNDA);
        snapshot.setWorldTeams(new LinkedHashMap<>(Map.of(team.getWorldTeamId(), team)));
        WorldPlayer player = WorldPlayer.fromRealPlayer(PLAYER, team.getWorldTeamId(),
                "Owner Player", 31, "ATT", 99, 88, 87, 86, 85, 84, BigDecimal.valueOf(9_000_000));
        player.setHeightCm(191);
        player.setSkillLevels(Map.of(PlayerSkill.SHOOTER, 5, PlayerSkill.HEADER, 4));
        player.setSpecialTraits(List.of(new PlayerSpecialTrait(PLAYER, "OWNER", "Owner", "Owner trait")));
        snapshot.setWorldPlayers(new LinkedHashMap<>(Map.of(player.getWorldPlayerId(), player)));
        snapshot.setWorldPlayerAliases(Map.of("old-player-id", player.getWorldPlayerId()));
        return snapshot;
    }
}

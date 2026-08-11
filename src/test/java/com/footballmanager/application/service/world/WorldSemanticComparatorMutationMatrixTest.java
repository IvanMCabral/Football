package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WorldSemanticComparatorMutationMatrixTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEAGUE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TEAM = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @TestFactory
    Stream<DynamicTest> everyPersistedMaterialPropertyHasAnIndependentComparatorMutation() {
        List<Mutation> mutations = new ArrayList<>();
        mutations.add(snapshot("userId", value -> value.setUserId(UUID.randomUUID())));
        mutations.add(snapshot("createdAt", value -> value.setCreatedAt(Instant.EPOCH)));
        mutations.add(snapshot("lastUpdated", value -> value.setLastUpdated(Instant.EPOCH.plusSeconds(1))));
        mutations.add(snapshot("leagues", value -> value.setLeagues(List.of())));
        mutations.add(snapshot("worldTeams", value -> value.setWorldTeams(Map.of())));
        mutations.add(snapshot("worldPlayers", value -> value.setWorldPlayers(Map.of())));
        mutations.add(snapshot("worldPlayerAliases", value -> value.setWorldPlayerAliases(
                Map.of("unexpected-legacy-id", value.getAllWorldPlayers().getFirst().getWorldPlayerId()))));

        mutations.add(team("worldTeamId", value -> value.setWorldTeamId("changed-team")));
        mutations.add(team("realTeamId", value -> value.setRealTeamId(UUID.randomUUID())));
        mutations.add(team("realLeagueId", value -> value.setRealLeagueId(UUID.randomUUID())));
        mutations.add(team("name", value -> value.setName("Changed")));
        mutations.add(team("country", value -> value.setCountry("BR")));
        mutations.add(team("city", value -> value.setCity("Changed")));
        mutations.add(team("baseBudget", value -> value.setBaseBudget(BigDecimal.TEN)));
        mutations.add(team("baseFormation", value -> value.setBaseFormation("3-5-2")));
        mutations.add(team("origin", value -> value.setOrigin(WorldTeam.WorldTeamOrigin.CUSTOM)));
        mutations.add(team("division", value -> value.setDivision(Division.SEGUNDA)));

        mutations.add(player("worldPlayerId", value -> value.setWorldPlayerId("changed-player")));
        mutations.add(player("realPlayerId", value -> value.setRealPlayerId(UUID.randomUUID())));
        mutations.add(player("worldTeamId", value -> value.setWorldTeamId("changed-team")));
        mutations.add(player("name", value -> value.setName("Changed")));
        mutations.add(player("age", value -> value.setAge(35)));
        mutations.add(player("position", value -> value.setPosition("ATT")));
        mutations.add(player("baseAttack", value -> value.setBaseAttack(99)));
        mutations.add(player("baseDefense", value -> value.setBaseDefense(98)));
        mutations.add(player("baseTechnique", value -> value.setBaseTechnique(97)));
        mutations.add(player("baseSpeed", value -> value.setBaseSpeed(96)));
        mutations.add(player("baseStamina", value -> value.setBaseStamina(95)));
        mutations.add(player("baseMentality", value -> value.setBaseMentality(94)));
        mutations.add(player("baseMarketValue", value -> value.setBaseMarketValue(BigDecimal.TEN)));
        mutations.add(player("origin", value -> value.setOrigin(WorldPlayer.WorldPlayerOrigin.CUSTOM)));
        mutations.add(player("heightCm", value -> value.setHeightCm(199)));
        mutations.add(player("skillLevels", value -> value.setSkillLevels(Map.of(PlayerSkill.SHOOTER, 5))));
        mutations.add(player("specialTraits", value -> value.setSpecialTraits(List.of(
                new PlayerSpecialTrait(PLAYER, "MUTATED", "Mutated", "Mutated")))));

        mutations.add(league("realLeagueId", value -> value.setRealLeagueId(UUID.randomUUID())));
        mutations.add(league("name", value -> value.setName("Changed")));
        mutations.add(league("country", value -> value.setCountry("BR")));
        mutations.add(league("tier", value -> value.setTier(3)));

        return mutations.stream().map(mutation -> DynamicTest.dynamicTest(mutation.path(), () -> {
            WorldSnapshot expected = fixture();
            WorldSnapshot actual = copy(expected);
            mutation.change().accept(actual);
            WorldSemanticComparator.Comparison comparison = new WorldSemanticComparator().compare(expected, actual);
            assertFalse(comparison.equivalent(), "false pass for " + mutation.path());
        }));
    }

    private Mutation snapshot(String field, Consumer<WorldSnapshot> change) {
        return new Mutation("WorldSnapshot." + field, change);
    }

    private Mutation team(String field, Consumer<WorldTeam> change) {
        return snapshot("WorldTeam." + field, world -> change.accept(world.getAllWorldTeams().getFirst()));
    }

    private Mutation player(String field, Consumer<WorldPlayer> change) {
        return snapshot("WorldPlayer." + field, world -> change.accept(world.getAllWorldPlayers().getFirst()));
    }

    private Mutation league(String field, Consumer<WorldLeague> change) {
        return snapshot("WorldLeague." + field, world -> change.accept(world.getLeagues().getFirst()));
    }

    private WorldSnapshot copy(WorldSnapshot source) throws Exception {
        return mapper.readValue(mapper.writeValueAsBytes(source), WorldSnapshot.class);
    }

    private static WorldSnapshot fixture() {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(OWNER);
        snapshot.setCreatedAt(Instant.parse("2026-08-01T00:00:00Z"));
        snapshot.setLastUpdated(Instant.parse("2026-08-02T00:00:00Z"));
        snapshot.setLeagues(List.of(new WorldLeague(LEAGUE, "League", "AR", 1)));
        WorldTeam team = WorldTeam.fromRealTeam(TEAM, LEAGUE, "Team", "AR", "City",
                BigDecimal.ONE, "4-4-2", Division.PRIMERA);
        snapshot.setWorldTeams(new LinkedHashMap<>(Map.of(team.getWorldTeamId(), team)));
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(OWNER, PLAYER, team.getWorldTeamId(),
                "Player", 24, "MID", 70, 71, 72, 73, 74, 75, BigDecimal.ONE);
        player.setHeightCm(180);
        player.setSkillLevels(Map.of(PlayerSkill.PASSER, 2));
        player.setSpecialTraits(List.of(new PlayerSpecialTrait(PLAYER, "BASE", "Base", "Base")));
        snapshot.setWorldPlayers(new LinkedHashMap<>(Map.of(player.getWorldPlayerId(), player)));
        snapshot.setWorldPlayerAliases(Map.of());
        return snapshot;
    }

    private record Mutation(String path, Consumer<WorldSnapshot> change) { }
}

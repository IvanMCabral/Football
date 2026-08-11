package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldSemanticComparator;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every owner delta is exercised through the complete physical Redis migration pipeline. */
class WorldStorageV2PhysicalSemanticMatrixIntegrationTest extends AbstractIntegrationTest {

    @TestFactory
    Stream<DynamicTest> everyOverlayEligibleFieldSurvivesPhysicalPipeline() {
        List<Mutation> mutations = new ArrayList<>();
        mutations.add(team("realLeagueId", value -> value.setRealLeagueId(UUID.randomUUID())));
        mutations.add(team("name", value -> value.setName("Owner team")));
        mutations.add(team("country", value -> value.setCountry("BR")));
        mutations.add(team("city", value -> value.setCity("Owner city")));
        mutations.add(team("baseBudget", value -> value.setBaseBudget(BigDecimal.valueOf(987654321))));
        mutations.add(team("baseFormation", value -> value.setBaseFormation("3-5-2")));
        mutations.add(team("division", value -> value.setDivision(Division.TERCERA)));
        mutations.add(player("worldTeamId", value -> value.setWorldTeamId("owner-team")));
        mutations.add(player("name", value -> value.setName("Owner player")));
        mutations.add(player("age", value -> value.setAge(39)));
        mutations.add(player("position", value -> value.setPosition("ATT")));
        mutations.add(player("baseAttack", value -> value.setBaseAttack(99)));
        mutations.add(player("baseDefense", value -> value.setBaseDefense(98)));
        mutations.add(player("baseTechnique", value -> value.setBaseTechnique(97)));
        mutations.add(player("baseSpeed", value -> value.setBaseSpeed(96)));
        mutations.add(player("baseStamina", value -> value.setBaseStamina(95)));
        mutations.add(player("baseMentality", value -> value.setBaseMentality(94)));
        mutations.add(player("baseMarketValue", value -> value.setBaseMarketValue(BigDecimal.valueOf(93000000))));
        mutations.add(player("heightCm", value -> value.setHeightCm(199)));
        mutations.add(player("skillLevels", value -> value.setSkillLevels(Map.of(PlayerSkill.SHOOTER, 5))));
        mutations.add(player("specialTraits", value -> value.setSpecialTraits(List.of(
                new PlayerSpecialTrait(value.getRealPlayerId(), "OWNER", "Owner", "Owner trait")))));
        mutations.add(league("name", value -> value.setName("Owner league")));
        mutations.add(league("country", value -> value.setCountry("BR")));
        mutations.add(league("tier", value -> value.setTier(3)));

        return mutations.stream().map(mutation -> DynamicTest.dynamicTest(mutation.path(), () -> {
            UUID owner = UUID.nameUUIDFromBytes(("physical-delta-" + mutation.path())
                    .getBytes(StandardCharsets.UTF_8));
            WorldSnapshot canonical = canonical(owner);
            WorldSnapshot legacy = copy(canonical);
            mutation.apply(legacy);
            WorldSnapshot reloaded = migrateAndReload(owner, canonical, legacy);
            new WorldSemanticComparator().requireEquivalent(legacy, reloaded);
        }));
    }

    @Test
    void physicalNullAndEmptyMatrixPreservesPresenceSemantics() throws Exception {
        assertPhysicalCase("canonical-value-legacy-null", canonical -> { }, legacy -> {
            legacy.setCreatedAt(null);
            legacy.setLastUpdated(null);
        });
        assertPhysicalCase("canonical-null-legacy-value", canonical -> {
            canonical.setCreatedAt(null);
            canonical.setLastUpdated(null);
        }, legacy -> {
            legacy.setCreatedAt(Instant.parse("2026-08-03T00:00:00Z"));
            legacy.setLastUpdated(Instant.parse("2026-08-04T00:00:00Z"));
        });
        assertPhysicalCase("canonical-null-legacy-null", canonical -> {
            canonical.setCreatedAt(null);
            canonical.setLastUpdated(null);
        }, legacy -> { });
        assertPhysicalCase("no-timestamp-delta", canonical -> { }, legacy -> { });
        assertPhysicalCase("explicit-empty-string", canonical -> { }, legacy ->
                legacy.getAllWorldPlayers().getFirst().setName(""));
        assertPhysicalCase("explicit-empty-map", canonical -> { }, legacy ->
                legacy.getAllWorldPlayers().getFirst().setSkillLevels(Map.of()));
    }

    @TestFactory
    Stream<DynamicTest> everyNullableOverlayFieldPreservesExplicitNullPhysically() {
        List<Mutation> nullableMutations = new ArrayList<>();
        nullableMutations.add(snapshot("createdAt=null", value -> value.setCreatedAt(null)));
        nullableMutations.add(snapshot("lastUpdated=null", value -> value.setLastUpdated(null)));
        nullableMutations.add(team("realLeagueId=null", value -> value.setRealLeagueId(null)));
        nullableMutations.add(team("name=null", value -> value.setName(null)));
        nullableMutations.add(team("country=null", value -> value.setCountry(null)));
        nullableMutations.add(team("city=null", value -> value.setCity(null)));
        nullableMutations.add(team("baseBudget=null", value -> value.setBaseBudget(null)));
        nullableMutations.add(team("baseFormation=null", value -> value.setBaseFormation(null)));
        nullableMutations.add(team("division=null", value -> value.setDivision(null)));
        nullableMutations.add(player("worldTeamId=null", value -> value.setWorldTeamId(null)));
        nullableMutations.add(player("name=null", value -> value.setName(null)));
        nullableMutations.add(player("age=null", value -> value.setAge(null)));
        nullableMutations.add(player("position=null", value -> value.setPosition(null)));
        nullableMutations.add(player("baseAttack=null", value -> value.setBaseAttack(null)));
        nullableMutations.add(player("baseDefense=null", value -> value.setBaseDefense(null)));
        nullableMutations.add(player("baseTechnique=null", value -> value.setBaseTechnique(null)));
        nullableMutations.add(player("baseSpeed=null", value -> value.setBaseSpeed(null)));
        nullableMutations.add(player("baseStamina=null", value -> value.setBaseStamina(null)));
        nullableMutations.add(player("baseMentality=null", value -> value.setBaseMentality(null)));
        nullableMutations.add(player("baseMarketValue=null", value -> value.setBaseMarketValue(null)));
        nullableMutations.add(player("heightCm=null", value -> value.setHeightCm(null)));
        nullableMutations.add(league("name=null", value -> value.setName(null)));
        nullableMutations.add(league("country=null", value -> value.setCountry(null)));
        nullableMutations.add(league("tier=null", value -> value.setTier(null)));

        return nullableMutations.stream().map(mutation -> DynamicTest.dynamicTest(mutation.path(), () -> {
            UUID owner = UUID.nameUUIDFromBytes(("physical-null-field-" + mutation.path())
                    .getBytes(StandardCharsets.UTF_8));
            WorldSnapshot canonical = canonical(owner);
            WorldSnapshot legacy = copy(canonical);
            mutation.apply(legacy);
            WorldSnapshot reloaded = migrateAndReload(owner, canonical, legacy);
            new WorldSemanticComparator().requireEquivalent(legacy, reloaded);
        }));
    }

    private void assertPhysicalCase(String name, Consumer<WorldSnapshot> canonicalMutation,
                                    Consumer<WorldSnapshot> legacyMutation) throws Exception {
        UUID owner = UUID.nameUUIDFromBytes(("physical-null-" + name).getBytes(StandardCharsets.UTF_8));
        WorldSnapshot canonical = canonical(owner);
        canonicalMutation.accept(canonical);
        WorldSnapshot legacy = copy(canonical);
        legacyMutation.accept(legacy);
        WorldSnapshot reloaded = migrateAndReload(owner, canonical, legacy);
        new WorldSemanticComparator().requireEquivalent(legacy, reloaded);
        assertEquals(legacy.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(legacy.getLastUpdated(), reloaded.getLastUpdated());
    }

    private WorldSnapshot migrateAndReload(UUID owner, WorldSnapshot canonical, WorldSnapshot legacy) throws Exception {
        String raw = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, raw, Duration.ofDays(30)).block();
        RedisWorldRepository writer = repository();
        WorldStorageMigrationOrchestrator.Outcome outcome = WorldStorageMigrationTestDriver.migrate(
                writer, ignored -> Mono.just(copyUnchecked(canonical)), owner,
                1_000_000L, 32_000_000L, 32_768L);
        assertNotNull(outcome);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, outcome.status());
        writer = null;
        RedisWorldRepository reader = repository();
        WorldSnapshot reloaded = reader.findByUserId(owner).block(Duration.ofSeconds(15));
        assertNotNull(reloaded);
        String committed = reactiveRedisTemplate.opsForValue().get("world:" + owner).block();
        assertTrue(objectMapper.readTree(committed).path("state").asText().equals("COMMITTED"));
        return reloaded;
    }

    private RedisWorldRepository repository() {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, objectMapper);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private WorldSnapshot copy(WorldSnapshot source) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsBytes(source), WorldSnapshot.class);
    }

    private WorldSnapshot copyUnchecked(WorldSnapshot source) {
        try {
            return copy(source);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static Mutation team(String field, Consumer<WorldTeam> mutation) {
        return new Mutation("WorldTeam." + field, snapshot -> mutation.accept(snapshot.getAllWorldTeams().getFirst()));
    }

    private static Mutation snapshot(String field, Consumer<WorldSnapshot> mutation) {
        return new Mutation("WorldSnapshot." + field, mutation);
    }

    private static Mutation player(String field, Consumer<WorldPlayer> mutation) {
        return new Mutation("WorldPlayer." + field,
                snapshot -> mutation.accept(snapshot.getAllWorldPlayers().getFirst()));
    }

    private static Mutation league(String field, Consumer<WorldLeague> mutation) {
        return new Mutation("WorldLeague." + field, snapshot -> mutation.accept(snapshot.getLeagues().getFirst()));
    }

    private record Mutation(String path, Consumer<WorldSnapshot> action) {
        void apply(WorldSnapshot snapshot) { action.accept(snapshot); }
    }

    private static WorldSnapshot canonical(UUID owner) {
        UUID leagueId = UUID.nameUUIDFromBytes(("league-" + owner).getBytes(StandardCharsets.UTF_8));
        UUID teamId = UUID.nameUUIDFromBytes(("team-" + owner).getBytes(StandardCharsets.UTF_8));
        UUID playerId = UUID.nameUUIDFromBytes(("player-" + owner).getBytes(StandardCharsets.UTF_8));
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        snapshot.setCreatedAt(Instant.parse("2026-08-01T00:00:00Z"));
        snapshot.setLastUpdated(Instant.parse("2026-08-02T00:00:00Z"));
        snapshot.setLeagues(List.of(new WorldLeague(leagueId, "League", "AR", 1)));
        WorldTeam team = WorldTeam.fromRealTeam(teamId, leagueId, "Team", "AR", "City",
                BigDecimal.TEN, "4-4-2", Division.PRIMERA);
        snapshot.setWorldTeams(new LinkedHashMap<>(Map.of(team.getWorldTeamId(), team)));
        WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, playerId, team.getWorldTeamId(),
                "Player", 22, "MID", 70, 71, 72, 73, 74, 75, BigDecimal.TEN);
        player.setHeightCm(180);
        player.setSkillLevels(Map.of(PlayerSkill.PASSER, 2));
        player.setSpecialTraits(List.of(new PlayerSpecialTrait(playerId, "BASE", "Base", "Base trait")));
        snapshot.setWorldPlayers(new LinkedHashMap<>(Map.of(player.getWorldPlayerId(), player)));
        snapshot.setWorldPlayerAliases(Map.of());
        return snapshot;
    }
}

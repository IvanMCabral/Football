package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldStorageMigrationLimits;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageMigrationPhysicalBoundsIntegrationTest extends AbstractIntegrationTest {

    @TestFactory
    Stream<DynamicTest> everyRepresentableMaxPlusOneIsRejectedBeforePrepared() {
        List<Boundary> boundaries = List.of(
                new Boundary("customTeams", () -> customTeams(WorldStorageMigrationLimits.MAX_CUSTOM_TEAMS + 1)),
                new Boundary("customPlayers", () -> customPlayers(WorldStorageMigrationLimits.MAX_CUSTOM_PLAYERS + 1)),
                new Boundary("totalTeams", () -> realTeams(WorldStorageMigrationLimits.MAX_TOTAL_TEAMS + 1)),
                new Boundary("totalPlayers", () -> realPlayers(WorldStorageMigrationLimits.MAX_TOTAL_PLAYERS + 1)),
                new Boundary("aliases", () -> aliases(WorldStorageMigrationLimits.MAX_ALIASES + 1)),
                new Boundary("leagues", () -> leagues(WorldStorageMigrationLimits.MAX_LEAGUES + 1)),
                new Boundary("traits", () -> traits(WorldStorageMigrationLimits.MAX_SPECIAL_TRAITS_PER_PLAYER + 1)),
                new Boundary("text", () -> text(WorldStorageMigrationLimits.MAX_NAME_CHARS + 1)),
                new Boundary("identifier", () -> identifier(WorldStorageMigrationLimits.MAX_IDENTIFIER_CHARS + 1)),
                new Boundary("serializedBytes", WorldStorageMigrationPhysicalBoundsIntegrationTest::oversizedSerializedWorld)
        );
        return boundaries.stream().map(boundary -> DynamicTest.dynamicTest(boundary.name(), () -> {
            UUID owner = UUID.nameUUIDFromBytes(("physical-boundary-" + boundary.name()).getBytes(StandardCharsets.UTF_8));
            WorldSnapshot value = boundary.fixture().get();
            value.setUserId(owner);
            String raw = objectMapper.writeValueAsString(value);
            if (boundary.name().equals("serializedBytes")) {
                assertTrue(raw.getBytes(StandardCharsets.UTF_8).length
                        > WorldStorageMigrationLimits.MAX_SERIALIZED_LEGACY_BYTES);
            }
            reactiveRedisTemplate.opsForValue().set("world:" + owner, raw, Duration.ofMinutes(5)).block();
            var result = WorldStorageMigrationTestDriver.migrate(repository(), ignored -> reactor.core.publisher.Mono.just(empty(owner)),
                    owner, 1_000_000, 128_000_000, 32_768, 65_536);
            assertEquals(WorldStorageMigrationOrchestrator.Status.INVALID_LEGACY, result.status(), boundary.name());
            String retained = reactiveRedisTemplate.opsForValue().get("world:" + owner).block();
            assertEquals(raw, retained, boundary.name());
            assertFalse(objectMapper.readTree(retained).has("migrationState"), boundary.name());
        }));
    }

    @Test
    void skillDimensionAtEnumMaximumMigratesAndNoMaxPlusOneCanBeRepresented() throws Exception {
        assertEquals(PlayerSkill.values().length, WorldStorageMigrationLimits.MAX_SKILLS_PER_PLAYER);
        UUID owner = UUID.nameUUIDFromBytes("physical-boundary-skills".getBytes(StandardCharsets.UTF_8));
        WorldSnapshot legacy = customPlayers(1);
        legacy.setUserId(owner);
        EnumMap<PlayerSkill, Integer> skills = new EnumMap<>(PlayerSkill.class);
        for (PlayerSkill skill : PlayerSkill.values()) skills.put(skill, 5);
        legacy.getAllWorldPlayers().getFirst().setSkillLevels(skills);
        String raw = objectMapper.writeValueAsString(legacy);
        reactiveRedisTemplate.opsForValue().set("world:" + owner, raw, Duration.ofMinutes(5)).block();
        var result = WorldStorageMigrationTestDriver.migrate(repository(), ignored -> reactor.core.publisher.Mono.just(empty(owner)),
                owner, 1_000_000, 128_000_000, 32_768, 65_536);
        assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, result.status(), result.reason());
    }

    private RedisWorldRepository repository() {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private static WorldSnapshot empty(UUID owner) {
        WorldSnapshot value = new WorldSnapshot();
        value.setUserId(owner);
        return value;
    }

    private static WorldSnapshot customTeams(int count) {
        WorldSnapshot value = new WorldSnapshot();
        for (int i = 0; i < count; i++) {
            WorldTeam team = WorldTeam.createCustom("t" + i, "AR", BigDecimal.ONE, "4-4-2");
            value.getWorldTeams().put(team.getWorldTeamId(), team);
        }
        return value;
    }

    private static WorldSnapshot customPlayers(int count) {
        WorldSnapshot value = new WorldSnapshot();
        for (int i = 0; i < count; i++) {
            WorldPlayer player = WorldPlayer.createCustom("p" + i, 20, "MID", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
            value.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return value;
    }

    private static WorldSnapshot realTeams(int count) {
        WorldSnapshot value = new WorldSnapshot();
        UUID league = UUID.nameUUIDFromBytes("physical-league".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < count; i++) {
            UUID id = UUID.nameUUIDFromBytes(("physical-team-" + i).getBytes(StandardCharsets.UTF_8));
            WorldTeam team = WorldTeam.fromRealTeam(id, league, "t", "AR", "c", BigDecimal.ONE, "4-4-2");
            value.getWorldTeams().put(team.getWorldTeamId(), team);
        }
        return value;
    }

    private static WorldSnapshot realPlayers(int count) {
        WorldSnapshot value = new WorldSnapshot();
        for (int i = 0; i < count; i++) {
            WorldPlayer player = new WorldPlayer();
            player.setWorldPlayerId("p" + i);
            player.setRealPlayerId(UUID.nameUUIDFromBytes(("physical-player-" + i).getBytes(StandardCharsets.UTF_8)));
            player.setOrigin(WorldPlayer.WorldPlayerOrigin.REAL);
            value.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        return value;
    }

    private static WorldSnapshot aliases(int count) {
        WorldSnapshot value = new WorldSnapshot();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) aliases.put("a" + i, "b" + i);
        value.setWorldPlayerAliases(aliases);
        return value;
    }

    private static WorldSnapshot leagues(int count) {
        WorldSnapshot value = new WorldSnapshot();
        List<WorldLeague> leagues = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            leagues.add(new WorldLeague(UUID.nameUUIDFromBytes(("physical-league-" + i).getBytes(StandardCharsets.UTF_8)),
                    "l", "AR", 1));
        }
        value.setLeagues(leagues);
        return value;
    }

    private static WorldSnapshot traits(int count) {
        WorldSnapshot value = customPlayers(1);
        List<PlayerSpecialTrait> traits = new ArrayList<>();
        for (int i = 0; i < count; i++) traits.add(new PlayerSpecialTrait(UUID.randomUUID(), "c" + i, "n", "d"));
        value.getAllWorldPlayers().getFirst().setSpecialTraits(traits);
        return value;
    }

    private static WorldSnapshot text(int length) {
        WorldSnapshot value = new WorldSnapshot();
        WorldTeam team = WorldTeam.createCustom("x".repeat(length), "AR", BigDecimal.ONE, "4-4-2");
        value.getWorldTeams().put(team.getWorldTeamId(), team);
        return value;
    }

    private static WorldSnapshot identifier(int length) {
        WorldSnapshot value = customPlayers(1);
        WorldPlayer player = value.getAllWorldPlayers().getFirst();
        value.getWorldPlayers().clear();
        player.setWorldPlayerId("x".repeat(length));
        value.getWorldPlayers().put(player.getWorldPlayerId(), player);
        return value;
    }

    private static WorldSnapshot oversizedSerializedWorld() {
        WorldSnapshot value = customPlayers(WorldStorageMigrationLimits.MAX_CUSTOM_PLAYERS);
        String payload = "Á⚽".repeat(170);
        for (WorldPlayer player : value.getAllWorldPlayers()) {
            player.setName(payload);
            List<PlayerSpecialTrait> traits = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                traits.add(new PlayerSpecialTrait(UUID.randomUUID(), "t" + i, payload, payload));
            }
            player.setSpecialTraits(traits);
        }
        return value;
    }

    private record Boundary(String name, Supplier<WorldSnapshot> fixture) { }
}

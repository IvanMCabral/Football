package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldReferenceGraph;
import com.footballmanager.application.service.world.WorldMigrationReferenceInventory;
import com.footballmanager.application.service.world.WorldStorageMigrationPlanner;
import com.footballmanager.application.service.world.WorldSemanticComparator;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageV2VariedMatrixIntegrationTest extends AbstractIntegrationTest {

    @Test
    void tenVariedOwnersAreLosslessAndReferenceSafeAcrossRedisReloads() {
        WorldStorageMigrationPlanner planner = new WorldStorageMigrationPlanner();
        int lossless = 0;
        int referencesValidated = 0;
        Set<String> catalogKeys = new LinkedHashSet<>();
        for (int variant = 0; variant < 10; variant++) {
            RedisWorldRepository writer = repository();
            int caseIndex = variant;
            UUID owner = UUID.nameUUIDFromBytes(("varied-owner-" + variant)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            WorldSnapshot legacy = legacyWorld(owner, variant);
            WorldSnapshot canonical = canonicalWorld(owner, legacy);
            CareerSave career = activeCareer(owner, legacy, variant >= 8);
            WorldReferenceGraph references = career == null ? WorldReferenceGraph.empty()
                    : new WorldMigrationReferenceInventory().discover(career);
            WorldStorageMigrationPlanner.MigrationPlan plan = planner.plan(legacy, canonical, references);
            assertTrue(plan.ready(), () -> "variant " + caseIndex + ": " + plan.reason());
            referencesValidated++;

            try {
                String legacyJson = objectMapper.writeValueAsString(legacy);
                reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyJson, Duration.ofDays(30))
                        .block(Duration.ofSeconds(5));
            } catch (Exception error) {
                throw new AssertionError("variant " + variant + " legacy seed failed", error);
            }
            WorldStorageMigrationOrchestrator orchestrator = WorldStorageMigrationTestDriver.create(
                    writer, ignored -> reactor.core.publisher.Mono.just(canonical), career);
            WorldStorageMigrationOrchestrator.Outcome outcome = orchestrator.migrate(owner,
                    new WorldStorageMigrationOrchestrator.CapacitySnapshot(
                            1_000_000, 32_000_000, 32_768)).block(Duration.ofSeconds(30));
            assertNotNull(outcome);
            assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, outcome.status());
            try {
                String envelope = reactiveRedisTemplate.opsForValue().get("world:" + owner)
                        .block(Duration.ofSeconds(5));
                catalogKeys.add(objectMapper.readTree(envelope).path("catalogKey").asText());
            } catch (Exception error) {
                throw new AssertionError("variant " + variant + " catalog envelope is unreadable", error);
            }
            writer = null; // destroy the writer-side component graph before recovery
            RedisWorldRepository restarted = recoveryRepository();
            WorldSnapshot first = restarted.findByUserId(owner).block(Duration.ofSeconds(15));
            restarted = null;
            WorldSnapshot second = recoveryRepository().findByUserId(owner).block(Duration.ofSeconds(15));
            new WorldSemanticComparator().requireEquivalent(legacy, first);
            new WorldSemanticComparator().requireEquivalent(legacy, second);
            assertTrue(planner.plan(legacy, canonical,
                    new WorldMigrationReferenceInventory().discover(career)).ready());
            assertTrue(career.getTournamentState().canStartMatchDay());
            career.getTournamentState().startMatchDay();
            career.getTournamentState().finishMatchDay();
            career.getTournamentState().enterWaitingUserPhase();
            lossless++;
        }
        assertEquals(10, lossless);
        assertEquals(10, referencesValidated);
        assertEquals(10, catalogKeys.size(), "distinct canonical catalogs must coexist without collision");
        System.out.printf("[WORLD-MATRIX] cases=%d lossless=%d referencesValidated=%d distinctCatalogs=%d%n",
                10, lossless, referencesValidated, catalogKeys.size());
    }

    private RedisWorldRepository repository() {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, objectMapper, null,
                new CanonicalWorldCatalogFingerprint(objectMapper), null);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private RedisWorldRepository recoveryRepository() {
        com.fasterxml.jackson.databind.ObjectMapper isolatedMapper = objectMapper.copy();
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, isolatedMapper, null,
                new CanonicalWorldCatalogFingerprint(isolatedMapper), null);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private WorldSnapshot legacyWorld(UUID owner, int variant) {
        UUID leagueA = UUID.nameUUIDFromBytes(("league-a-" + variant).getBytes());
        UUID leagueB = UUID.nameUUIDFromBytes(("league-b-" + variant).getBytes());
        UUID teamA = UUID.nameUUIDFromBytes(("team-a-" + variant).getBytes());
        UUID teamB = UUID.nameUUIDFromBytes(("team-b-" + variant).getBytes());
        WorldSnapshot world = new WorldSnapshot();
        world.setUserId(owner);
        world.setLeagues(variant >= 8
                ? List.of(WorldLeague.fromRealLeague(leagueA, "A", "AR", 1),
                          WorldLeague.fromRealLeague(leagueB, "B", "BR", 1))
                : List.of(WorldLeague.fromRealLeague(leagueA, "A", "AR", 1)));
        WorldTeam a = WorldTeam.fromRealTeam(teamA, leagueA, "A", "AR", "A", BigDecimal.TEN, "4-4-2");
        WorldTeam b = WorldTeam.fromRealTeam(teamB, variant == 6 ? leagueB : leagueA,
                "B", "AR", "B", BigDecimal.TEN, "4-3-3");
        world.getWorldTeams().put(a.getWorldTeamId(), a);
        world.getWorldTeams().put(b.getWorldTeamId(), b);
        for (int i = 0; i < 4; i++) {
            UUID real = UUID.nameUUIDFromBytes(("player-" + variant + "-" + i).getBytes());
            WorldPlayer player = variant == 0
                    ? WorldPlayer.fromCanonicalPlayer(owner, real, i < 2 ? a.getWorldTeamId() : b.getWorldTeamId(),
                    "P" + i, 20 + i, "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN)
                    : WorldPlayer.fromRealPlayer(real, i < 2 ? a.getWorldTeamId() : b.getWorldTeamId(),
                    "P" + i, 20 + i, "MID", 70, 70, 70, 70, 70, 70, BigDecimal.TEN);
            world.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
        List<WorldPlayer> realPlayers = world.getAllWorldPlayers();
        if (variant == 1 || variant == 9) realPlayers.get(0).setBaseAttack(99);
        if (variant == 2 || variant == 9) a.setBaseFormation("3-5-2");
        if (variant == 3 || variant == 9) {
            realPlayers.get(0).setBaseDefense(88);
            realPlayers.get(1).setBaseSpeed(92);
            realPlayers.get(2).setBaseTechnique(91);
        }
        if (variant == 6 || variant == 9) b.setRealLeagueId(leagueB);
        if (variant == 4 || variant == 9) {
            WorldTeam custom = WorldTeam.createCustom("Custom", "AR", BigDecimal.ONE, "3-5-2");
            world.getWorldTeams().put(custom.getWorldTeamId(), custom);
        }
        if (variant == 5 || variant == 9) {
            WorldPlayer custom = WorldPlayer.createCustom("Custom", 19, "ATT", 60, 50, 60, 60, 60, 60,
                    BigDecimal.ONE);
            world.getWorldPlayers().put(custom.getWorldPlayerId(), custom);
        }
        if (variant == 7) {
            WorldPlayer first = realPlayers.get(0);
            world.setWorldPlayerAliases(Map.of("retired-world-id", first.getWorldPlayerId()));
        }
        return world;
    }

    private WorldSnapshot canonicalWorld(UUID owner, WorldSnapshot legacy) {
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setUserId(owner);
        canonical.setLeagues(legacy.getLeagues().stream()
                .map(league -> WorldLeague.fromRealLeague(league.getRealLeagueId(), league.getName(),
                        league.getCountry(), league.getTier())).toList());
        UUID primaryLeague = canonical.getLeagues().getFirst().getRealLeagueId();
        legacy.getWorldTeams().values().stream()
                .filter(team -> team.getOrigin() == WorldTeam.WorldTeamOrigin.REAL)
                .forEach(team -> canonical.getWorldTeams().put(team.getRealTeamId().toString(),
                        WorldTeam.fromRealTeam(team.getRealTeamId(), team.getRealLeagueId(), team.getName(),
                                team.getCountry(), team.getCity(), team.getBaseBudget(),
                                "A".equals(team.getName()) ? "4-4-2" : "4-3-3",
                                team.getDivision())));
        canonical.getWorldTeams().values().stream().filter(team -> "B".equals(team.getName()))
                .forEach(team -> team.setRealLeagueId(primaryLeague));
        legacy.getWorldPlayers().values().stream()
                .filter(player -> player.getOrigin() == WorldPlayer.WorldPlayerOrigin.REAL)
                .forEach(player -> {
                    WorldPlayer copy = WorldPlayer.fromCanonicalPlayer(owner, player.getRealPlayerId(),
                            player.getWorldTeamId(), player.getName(), player.getAge(), player.getPosition(),
                            70, 70, 70,
                            70, 70, 70,
                            player.getBaseMarketValue());
                    canonical.getWorldPlayers().put(copy.getWorldPlayerId(), copy);
                });
        return canonical;
    }

    private CareerSave activeCareer(UUID owner, WorldSnapshot world, boolean completedFixture) {
        CareerSave career = new CareerSave();
        career.setUserId(owner);
        List<WorldTeam> teams = world.getAllWorldTeams().stream()
                .filter(team -> team.getOrigin() == WorldTeam.WorldTeamOrigin.REAL).limit(2).toList();
        SessionTeam home = SessionTeam.fromRealTeam(teams.get(0).getRealTeamId(), teams.get(0).getWorldTeamId(),
                teams.get(0).getName(), teams.get(0).getCountry(), BigDecimal.TEN, "4-4-2", null);
        SessionTeam away = SessionTeam.fromRealTeam(teams.get(1).getRealTeamId(), teams.get(1).getWorldTeamId(),
                teams.get(1).getName(), teams.get(1).getCountry(), BigDecimal.TEN, "4-4-2", null);
        career.addSessionTeam(home);
        career.addSessionTeam(away);
        career.setUserSessionTeamId(home.getSessionTeamId());
        List<WorldPlayer> players = world.getAllWorldPlayers().stream().limit(2).toList();
        Map<String, String> sessionIdsByWorldId = new LinkedHashMap<>();
        for (WorldPlayer player : players) {
            SessionPlayer session = SessionPlayer.cloneFromWorldPlayer(player.getWorldPlayerId(), player.getName(),
                    player.getPosition(), player.getAge(), player.calculateOverall(), home.getSessionTeamId());
            career.addSessionPlayer(session);
            career.assignPlayerToTeam(session.getSessionPlayerId(), home.getSessionTeamId());
            sessionIdsByWorldId.put(player.getWorldPlayerId(), session.getSessionPlayerId());
        }
        String starterId = sessionIdsByWorldId.get(players.get(0).getWorldPlayerId());
        career.setTeamStarting11(Map.of(home.getSessionTeamId(), List.of(starterId)));
        career.setTeamStarting11SubdivisionSlots(Map.of(home.getSessionTeamId(),
                Map.of("S16-2", new LineupSlot(starterId, "S16-2"))));
        career.markPlayerAsRemoved(home.getWorldTeamId(), players.get(0).getWorldPlayerId());
        MatchFixture fixture = new MatchFixture("match", home.getSessionTeamId(), away.getSessionTeamId(), 1);
        if (completedFixture) fixture.complete(new MatchFixture.MatchResultData(1, 0, 1, 0, 1, 0));
        career.getTournamentState().setFixtures(List.of(fixture));
        career.getTournamentState().initializeStandings(List.of(home, away));
        career.getTournamentState().setTotalRounds(2);
        return career;
    }

}

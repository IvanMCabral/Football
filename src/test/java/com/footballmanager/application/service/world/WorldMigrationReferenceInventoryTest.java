package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMigrationReferenceInventoryTest {

    @Test
    void reflectionVerifiedRegistryCoversEveryDurableIdentityModelField() {
        WorldMigrationDurableReferenceRegistry registry = new WorldMigrationDurableReferenceRegistry();
        registry.requireComplete();

        WorldMigrationReferenceInventory inventory = new WorldMigrationReferenceInventory(registry);
        assertTrue(registry.uncoveredModelFields().isEmpty());
        assertEquals(registry.referencePaths(), inventory.declaredIdFields());
        assertTrue(inventory.coverage().keySet().containsAll(registry.referencePaths()));
        assertTrue(registry.referencePaths().contains("WorldSnapshot.worldPlayerAliases@MAP_VALUE"));
        assertTrue(registry.referencePaths().contains("CareerSave.teamStarting11Subdivision@MAP_VALUE/MAP_VALUE"));

        var authority = registry.rootAuthority();
        var graph = registry.persistedGraph();
        assertTrue(authority.boundaries().stream().anyMatch(value ->
                value.technology() == DurablePersistenceBoundary.StorageTechnology.POSTGRESQL));
        assertTrue(authority.boundaries().stream().allMatch(DurablePersistenceBoundary::isClassified));
        System.out.printf("[WORLD-REFERENCE-AUTHORITY-V4] writers=%d roots=%d models=%d fields=%d "
                        + "inherited=%d containers=%d identityLeaves=%d references=%d validators=%d "
                        + "unresolved=%d unclassifiedWriters=%d unvalidated=%d extraValidators=%d%n",
                authority.writers().size(), graph.roots().size(), graph.models().size(), graph.fields().size(),
                graph.inheritedFields().size(), graph.containers().size(), graph.identityLeaves().size(),
                registry.referencePaths().size(), registry.validators().size(), graph.unresolvedGenericPaths().size(),
                authority.unclassifiedWriters().size(), registry.unvalidatedReferences().size(),
                registry.extraValidators().size());
    }

    @Test
    void removedPlayersArePartOfTheAuthoritativeReferenceGraph() {
        CareerSave career = new CareerSave();
        career.markPlayerAsRemoved("world-team-removed", "world-player-removed");

        WorldReferenceGraph graph = new WorldMigrationReferenceInventory().discover(career);

        assertTrue(graph.teamReferences().get("career.removedPlayers.team")
                .contains("world-team-removed"));
        assertTrue(graph.playerReferences().get("career.removedPlayers.player")
                .contains("world-player-removed"));
    }

    @Test
    void discoversEveryDurableCareerSurfaceWithoutCallerSuppliedReferences() {
        CareerSave career = new CareerSave();
        SessionTeam home = SessionTeam.custom("world-team-home", "Home", "AR",
                BigDecimal.TEN, "4-4-2");
        SessionTeam away = SessionTeam.custom("world-team-away", "Away", "AR",
                BigDecimal.TEN, "4-3-3");
        career.addSessionTeam(home);
        career.addSessionTeam(away);
        career.setUserSessionTeamId(home.getSessionTeamId());

        SessionPlayer starter = SessionPlayer.cloneFromWorldPlayer(
                "world-player-starter", "Starter", "MID", 22, 70, home.getSessionTeamId());
        SessionPlayer bench = SessionPlayer.cloneFromWorldPlayer(
                "world-player-bench", "Bench", "ATT", 21, 68, home.getSessionTeamId());
        career.addSessionPlayer(starter);
        career.addSessionPlayer(bench);
        career.assignPlayerToTeam(starter.getSessionPlayerId(), home.getSessionTeamId());
        career.assignPlayerToTeam(bench.getSessionPlayerId(), home.getSessionTeamId());
        career.setTeamStarting11(Map.of(home.getSessionTeamId(), List.of(starter.getSessionPlayerId())));
        career.setTeamStarting11SubdivisionSlots(Map.of(home.getSessionTeamId(),
                Map.of("S16-2", new LineupSlot(starter.getSessionPlayerId(), "S16-2"))));
        career.markPlayerAsRemoved(home.getWorldTeamId(), bench.getWorldPlayerId());
        career.getTournamentState().setFixtures(List.of(new MatchFixture("fixture", home.getSessionTeamId(),
                away.getSessionTeamId(), 1)));
        career.getTournamentState().initializeStandings(List.of(home, away));

        WorldReferenceGraph graph = new WorldMigrationReferenceInventory().discover(career);

        assertEquals(Set.of(home.getWorldTeamId(), away.getWorldTeamId()),
                union(graph.teamReferences()));
        assertEquals(Set.of(starter.getWorldPlayerId(), bench.getWorldPlayerId()),
                union(graph.playerReferences()));
        assertTrue(graph.teamReferences().containsKey("career.selectedTeam"));
        assertTrue(graph.teamReferences().containsKey("career.fixtures.home"));
        assertTrue(graph.teamReferences().containsKey("career.fixtures.away"));
        assertTrue(graph.teamReferences().containsKey("career.standings"));
        assertTrue(graph.playerReferences().containsKey("career.squad"));
        assertTrue(graph.playerReferences().containsKey("career.startingXI"));
        assertTrue(graph.playerReferences().containsKey("career.lineupSlots"));
        assertTrue(graph.playerReferences().containsKey("career.removedPlayers.player"));
    }

    @Test
    void unsetSelectedTeamIsValidAndDoesNotInventAReference() {
        WorldReferenceGraph graph = new WorldMigrationReferenceInventory().discover(new CareerSave());

        assertFalse(graph.teamReferences().containsKey("career.selectedTeam"));
    }

    @Test
    void migrationAdmissionCannotBeForgedWithACallerBoolean() throws IOException {
        String repositorySource = Files.readString(Path.of(
                "src/main/java/com/footballmanager/adapters/out/redis/RedisWorldRepository.java"));
        String plannerSource = Files.readString(Path.of(
                "src/main/java/com/footballmanager/application/service/world/WorldStorageMigrationPlanner.java"));
        assertFalse(repositorySource.contains("QuotaBoundMigrationRequest"));
        assertFalse(repositorySource.contains("referenceSafe()"));
        assertFalse(plannerSource.contains("referenceSafe"));
    }

    private static Set<String> union(Map<String, Set<String>> references) {
        Set<String> values = new LinkedHashSet<>();
        references.values().forEach(values::addAll);
        return values;
    }
}

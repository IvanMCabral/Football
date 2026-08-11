package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldStorageMigrationPlannerTest {

    @Test
    void blocksWhenAReferencedLegacyPlayerCannotBeResolved() {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = new WorldSnapshot();
        legacy.setUserId(owner);
        WorldPlayer player = WorldPlayer.fromRealPlayer(UUID.randomUUID(), "team", "legacy", 20,
                "MID", 50, 50, 50, 50, 50, 50, BigDecimal.ONE);
        legacy.getWorldPlayers().put(player.getWorldPlayerId(), player);

        WorldSnapshot proposed = new WorldSnapshot();
        proposed.setUserId(owner);
        WorldStorageMigrationPlanner.MigrationPlan result = new WorldStorageMigrationPlanner().plan(legacy, proposed);

        assertFalse(result.ready());
        assertTrue(result.reason().contains("not resolvable"));
    }

    @Test
    void acceptsAnOwnerScopedRepresentationWithAllReferences() {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = new WorldSnapshot();
        legacy.setUserId(owner);
        WorldPlayer player = WorldPlayer.createCustom("legacy", 20, "MID", 50, 50, 50, 50, 50, 50,
                BigDecimal.ONE);
        legacy.getWorldPlayers().put(player.getWorldPlayerId(), player);
        WorldSnapshot proposed = new WorldSnapshot();
        proposed.setUserId(owner);
        proposed.getWorldPlayers().put(player.getWorldPlayerId(), player);

        assertTrue(new WorldStorageMigrationPlanner().plan(legacy, proposed).ready());
    }

    @Test
    void preservesLegacyRealPlayerAliasAndValidatesActiveCareerSurfaces() {
        UUID owner = UUID.randomUUID();
        UUID realPlayer = UUID.randomUUID();
        String legacyId = UUID.randomUUID().toString();
        WorldPlayer legacyPlayer = WorldPlayer.fromRealPlayer(realPlayer, "team", "Legacy", 20,
                "MID", 50, 50, 50, 50, 50, 50, BigDecimal.ONE);
        legacyPlayer.setWorldPlayerId(legacyId);
        WorldSnapshot legacy = new WorldSnapshot();
        legacy.setUserId(owner);
        legacy.getWorldPlayers().put(legacyId, legacyPlayer);
        WorldPlayer canonical = WorldPlayer.fromCanonicalPlayer(owner, realPlayer, "team", "Legacy", 20,
                "MID", 50, 50, 50, 50, 50, 50, BigDecimal.ONE);
        WorldSnapshot proposed = new WorldSnapshot();
        proposed.setUserId(owner);
        proposed.getWorldPlayers().put(canonical.getWorldPlayerId(), canonical);
        WorldReferenceGraph graph = new WorldReferenceGraph(java.util.Map.of(), java.util.Map.of(
                "career.startingXI", java.util.Set.of(legacyId),
                "runtime", java.util.Set.of(canonical.getWorldPlayerId())));

        WorldStorageMigrationPlanner.MigrationPlan result =
                new WorldStorageMigrationPlanner().plan(legacy, proposed, graph);

        assertTrue(result.ready());
        assertEquals(canonical.getWorldPlayerId(), result.legacyPlayerAliases().get(legacyId));
    }

    @Test
    void blocksAnUnresolvedFixtureOrLineupReference() {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = new WorldSnapshot();
        legacy.setUserId(owner);
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setUserId(owner);
        WorldReferenceGraph graph = new WorldReferenceGraph(
                java.util.Map.of("fixtures", java.util.Set.of("missing-team")),
                java.util.Map.of("lineup", java.util.Set.of("missing-player")));
        WorldStorageMigrationPlanner.MigrationPlan result =
                new WorldStorageMigrationPlanner().plan(legacy, canonical, graph);
        assertFalse(result.ready());
        assertEquals(WorldStorageMigrationPlanner.Status.BLOCKED_REFERENCE_INCOMPATIBILITY, result.status());
        assertEquals(2, result.referenceFailures().size());
    }

    @Test
    void quotaDryRunUsesPreparedSameKeyCompactionAndFailsClosed() {
        WorldStorageMigrationPlanner planner = new WorldStorageMigrationPlanner();
        WorldSnapshot source = new WorldSnapshot();
        UUID ownerId = UUID.randomUUID();
        source.setUserId(ownerId);
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setUserId(ownerId);
        WorldStorageMigrationPlanner.MigrationPlan referencePlan = planner.plan(source, canonical);
        WorldStorageMigrationPlanner.DryRunPlan feasible = planner.dryRun(
                new WorldStorageMigrationPlanner.DryRunInput("owner", 1, "hash",
                        1_200_000, 500, 800, 1_051_366, 100_000,
                        false, 268_137_301, 268_435_456, 65_536), referencePlan);
        assertTrue(feasible.migrationFeasible());
        assertTrue(feasible.projectedPeakBytes() < 268_435_456 - 65_536);

        WorldStorageMigrationPlanner.DryRunPlan blocked = planner.dryRun(
                new WorldStorageMigrationPlanner.DryRunInput("owner", 1, "hash",
                        100_000, 500, 800, 1_051_366, 100_000,
                        false, 268_137_301, 268_435_456, 65_536), referencePlan);
        assertFalse(blocked.migrationFeasible());
    }
}

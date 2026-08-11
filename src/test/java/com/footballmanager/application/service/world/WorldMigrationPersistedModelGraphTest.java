package com.footballmanager.application.service.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMigrationPersistedModelGraphTest {

    @Test
    void discoversEveryConfiguredPersistedRootAndNestedContainerWithoutRawGaps() {
        WorldMigrationPersistedModelGraph.Graph graph = new WorldMigrationPersistedModelGraph().discover();

        assertEquals(8, graph.roots().size());
        assertTrue(graph.modelCount() >= 20, () -> "models=" + graph.models());
        assertTrue(graph.containerCount() >= 20, () -> "containers=" + graph.containers());
        assertTrue(graph.unresolvedGenericPaths().isEmpty(),
                () -> "unresolved=" + graph.unresolvedGenericPaths());
        assertTrue(graph.models().stream().anyMatch(type -> type.getSimpleName().equals("CareerSave")));
        assertTrue(graph.models().stream().anyMatch(type -> type.getSimpleName().equals("CareerTeamManager")));
        assertTrue(graph.models().stream().anyMatch(type -> type.getSimpleName().equals("TournamentState")));
        System.out.printf("[WORLD-REFERENCE-GRAPH] roots=%d models=%d fields=%d containers=%d references=%d unresolved=%d%n",
                graph.roots().size(), graph.models().size(), graph.fields().size(), graph.containers().size(),
                new WorldMigrationDurableReferenceRegistry().referencePaths().size(),
                graph.unresolvedGenericPaths().size());
    }

    @Test
    void selfDestructionFindsReferencesInEveryContainerShape() {
        WorldMigrationDurableReferenceRegistry registry = new WorldMigrationDurableReferenceRegistry();
        List<String> uncovered = registry.uncoveredAnnotatedReferences(InjectedRoot.class);

        assertEquals(7, uncovered.size(), uncovered::toString);
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("direct")));
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("list")));
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("set")));
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("map") && value.contains("MAP_KEY")));
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("map") && value.contains("MAP_VALUE")));
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("nested")));
        assertTrue(uncovered.stream().anyMatch(value -> value.contains("removedPlayersLike")));
        assertFalse(uncovered.isEmpty());
    }

    private static final class InjectedRoot {
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER)
        private String direct;
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER,
                locations = WorldIdentityReference.Location.ELEMENT)
        private List<String> list;
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER,
                locations = WorldIdentityReference.Location.ELEMENT)
        private Set<String> set;
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER,
                locations = {WorldIdentityReference.Location.MAP_KEY, WorldIdentityReference.Location.MAP_VALUE})
        private Map<String, String> map;
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER,
                locations = WorldIdentityReference.Location.ELEMENT)
        private List<List<String>> nested;
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER,
                locations = WorldIdentityReference.Location.MAP_VALUE)
        private Map<String, Set<String>> removedPlayersLike;
    }
}

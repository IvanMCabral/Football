package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMigrationPersistedModelGraphTest {

    @Test
    void discoversWriterDerivedRootsAndCompleteClassifiedGraph() {
        WorldMigrationDurableReferenceRegistry registry = new WorldMigrationDurableReferenceRegistry();
        registry.requireComplete();
        WorldMigrationPersistedModelGraph.Graph graph = registry.persistedGraph();

        assertFalse(registry.rootAuthority().writers().isEmpty());
        assertTrue(registry.rootAuthority().unclassifiedWriters().isEmpty());
        assertTrue(graph.roots().stream().anyMatch(type -> type.getSimpleName().equals("CareerSave")));
        assertTrue(graph.roots().stream().anyMatch(type -> type.getSimpleName().equals("WorldSnapshot")));
        assertTrue(graph.models().stream().anyMatch(type -> type.getSimpleName().equals("CareerTeamManager")));
        assertTrue(graph.unresolvedGenericPaths().isEmpty(), graph.unresolvedGenericPaths()::toString);
        assertEquals(graph.identityLeaves(), graph.classifiedIdentityPaths());
        assertEquals(registry.referencePaths(), registry.validators().keySet());
        assertTrue(registry.unvalidatedReferences().isEmpty());
        assertTrue(registry.extraValidators().isEmpty());
    }

    @Test
    void inheritedReferenceIsDiscoveredInsteadOfSilentlyPassing() {
        WorldMigrationPersistedModelGraph.Graph graph = inspect(InheritedChild.class);

        assertTrue(legacyDeclaredFieldGuardWouldPass(InheritedChild.class));
        assertTrue(graph.inheritedFields().stream().anyMatch(path -> path.contains("inherited")));
        assertTrue(graph.annotatedReferences().stream().anyMatch(path -> path.contains("inherited")));
        assertTrue(graph.identityLeaves().contains("InheritedChild.inherited@VALUE"));
    }

    @Test
    void externalHolderCannotPassWithoutClassification() {
        WorldMigrationPersistedModelGraph.Graph graph = inspect(UnclassifiedExternalHolder.class);
        Set<String> missing = new java.util.LinkedHashSet<>(graph.identityLeaves());
        missing.removeAll(graph.classifiedIdentityPaths());

        assertEquals(Set.of("UnclassifiedExternalHolder.futureReference@VALUE"), missing);
        assertTrue(legacyDefaultRootGuardWouldPass(UnclassifiedExternalHolder.class));
        assertFalse(WorldReferenceAuthorityValidator.validate(graph, Map.of()).isEmpty());
    }

    @Test
    void resolvesGenericSuperclassAndFailsClosedForUnboundedVariable() {
        WorldMigrationPersistedModelGraph.Graph bound = inspect(BoundGenericChild.class);
        assertTrue(bound.identityLeaves().contains("BoundGenericChild.references@ELEMENT"));
        assertTrue(bound.annotatedReferences().stream().anyMatch(path -> path.contains("references@ELEMENT")));

        WorldMigrationPersistedModelGraph.Graph unresolved = inspect(UnboundedGeneric.class);
        assertTrue(unresolved.unresolvedGenericPaths().stream()
                .anyMatch(path -> path.contains("unresolved-type-variable")));
    }

    @Test
    void discoversEveryContainerShapeAndRoute() {
        WorldMigrationPersistedModelGraph.Graph graph = inspect(ContainerMatrix.class);
        Set<String> expected = Set.of(
                "ContainerMatrix.scalar@VALUE",
                "ContainerMatrix.list@ELEMENT",
                "ContainerMatrix.set@ELEMENT",
                "ContainerMatrix.array@ELEMENT",
                "ContainerMatrix.optional@ELEMENT",
                "ContainerMatrix.map@MAP_KEY",
                "ContainerMatrix.map@MAP_VALUE",
                "ContainerMatrix.nested@ELEMENT/MAP_VALUE");
        assertTrue(graph.identityLeaves().containsAll(expected), graph.identityLeaves()::toString);
        assertEquals(expected.size(), graph.annotatedReferences().size());
    }

    @Test
    void emptyOrBrokenDiscoveryCannotBeAcceptedByCompleteRegistry() {
        WorldMigrationPersistedModelGraph.Graph graph = inspect(UnclassifiedExternalHolder.class);
        assertFalse(graph.identityLeaves().isEmpty());
        assertFalse(graph.identityLeaves().equals(graph.classifiedIdentityPaths()));
        assertThrows(IllegalStateException.class, () -> requireGraphComplete(graph));
    }

    @Test
    void removedValidatorAndRegistryOnlyPathBothFailTheMetaGate() {
        WorldMigrationPersistedModelGraph.Graph graph = inspect(ContainerMatrix.class);
        Map<String, String> complete = new java.util.LinkedHashMap<>();
        graph.annotatedReferences().forEach(value -> complete.put(
                WorldReferenceAuthorityValidator.pathPart(value), "test-validator"));
        assertTrue(WorldReferenceAuthorityValidator.validate(graph, complete).isEmpty());

        Map<String, String> missing = new java.util.LinkedHashMap<>(complete);
        missing.remove("ContainerMatrix.scalar@VALUE");
        assertTrue(WorldReferenceAuthorityValidator.validate(graph, missing).stream()
                .anyMatch(issue -> issue.contains("missing-validator")));

        Map<String, String> stale = new java.util.LinkedHashMap<>(complete);
        stale.put("NoLongerPersisted.field@VALUE", "stale-validator");
        assertTrue(WorldReferenceAuthorityValidator.validate(graph, stale).stream()
                .anyMatch(issue -> issue.contains("validator-without-path")));
    }

    private static WorldMigrationPersistedModelGraph.Graph inspect(Class<?> root) {
        return new WorldMigrationPersistedModelGraph().inspect(Set.of(root), Map.of());
    }

    private static void requireGraphComplete(WorldMigrationPersistedModelGraph.Graph graph) {
        Set<String> missing = new java.util.LinkedHashSet<>(graph.identityLeaves());
        missing.removeAll(graph.classifiedIdentityPaths());
        if (!missing.isEmpty() || !graph.unresolvedGenericPaths().isEmpty()) {
            throw new IllegalStateException("incomplete graph");
        }
    }

    private static boolean legacyDeclaredFieldGuardWouldPass(Class<?> root) {
        return java.util.Arrays.stream(root.getDeclaredFields())
                .noneMatch(field -> field.isAnnotationPresent(WorldIdentityReference.class));
    }

    private static boolean legacyDefaultRootGuardWouldPass(Class<?> detached) {
        WorldMigrationDurableReferenceRegistry current = new WorldMigrationDurableReferenceRegistry();
        current.requireComplete();
        return !current.persistedGraph().models().contains(detached);
    }

    private static class InheritedBase {
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER)
        private String inherited;
    }

    private static final class InheritedChild extends InheritedBase {
        private int ordinary;
    }

    private static final class UnclassifiedExternalHolder {
        private String futureReference;
    }

    private static class GenericBase<T> {
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "ELEMENT")
        private List<T> references;
    }

    private static final class BoundGenericChild extends GenericBase<String> { }

    private static final class UnboundedGeneric<T> {
        private List<T> values;
    }

    private static final class ContainerMatrix {
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER) private String scalar;
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "ELEMENT")
        private List<String> list;
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "ELEMENT")
        private Set<String> set;
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "ELEMENT")
        private String[] array;
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "ELEMENT")
        private Optional<String> optional;
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_TEAM, route = "MAP_KEY")
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "MAP_VALUE")
        private Map<String, String> map;
        @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER, route = "ELEMENT/MAP_VALUE")
        private List<Map<Integer, String>> nested;
    }
}

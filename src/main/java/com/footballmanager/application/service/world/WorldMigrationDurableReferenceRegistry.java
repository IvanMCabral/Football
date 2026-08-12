package com.footballmanager.application.service.world;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed, annotation-backed authority for durable world identity paths. */
@Component
public final class WorldMigrationDurableReferenceRegistry {

    private final WorldMigrationPersistedRootAuthority.Authority rootAuthority;
    private final WorldMigrationPersistedModelGraph.Graph persistedGraph;
    private final Map<String, String> validators;

    public WorldMigrationDurableReferenceRegistry() {
        WorldMigrationPersistedRootAuthority roots = new WorldMigrationPersistedRootAuthority();
        this.rootAuthority = roots.discover();
        this.persistedGraph = new WorldMigrationPersistedModelGraph(roots).discover();
        this.validators = buildValidators(persistedGraph.annotatedReferences());
    }

    public Set<String> referencePaths() {
        return Set.copyOf(validators.keySet());
    }

    public Map<String, String> validators() {
        return Map.copyOf(validators);
    }

    public Set<String> unvalidatedReferences() {
        Set<String> result = discoveredReferencePaths();
        result.removeAll(validators.keySet());
        return Set.copyOf(result);
    }

    public Set<String> extraValidators() {
        Set<String> result = new LinkedHashSet<>(validators.keySet());
        result.removeAll(discoveredReferencePaths());
        return Set.copyOf(result);
    }

    public List<String> uncoveredModelFields() {
        List<String> result = new ArrayList<>(WorldReferenceAuthorityValidator.validate(
                persistedGraph, validators));
        rootAuthority.unclassifiedWriters().forEach(writer ->
                result.add(writer.adapter().getName() + ":unclassified-persisted-writer"));
        return List.copyOf(result);
    }

    public void requireComplete() {
        rootAuthority.requireComplete();
        List<String> uncovered = uncoveredModelFields();
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("Incomplete durable world-reference authority: "
                    + String.join(", ", uncovered));
        }
    }

    public WorldMigrationPersistedModelGraph.Graph persistedGraph() {
        return persistedGraph;
    }

    public WorldMigrationPersistedRootAuthority.Authority rootAuthority() {
        return rootAuthority;
    }

    public List<String> uncoveredAnnotatedReferences(Class<?> root) {
        WorldMigrationPersistedModelGraph.Graph graph = new WorldMigrationPersistedModelGraph()
                .inspect(Set.of(root), Map.of());
        return graph.annotatedReferences().stream().sorted().toList();
    }

    private Set<String> discoveredReferencePaths() {
        Set<String> result = new LinkedHashSet<>();
        persistedGraph.annotatedReferences().forEach(value ->
                result.add(WorldReferenceAuthorityValidator.pathPart(value)));
        return result;
    }

    private static Map<String, String> buildValidators(Set<String> references) {
        Map<String, String> result = new LinkedHashMap<>();
        references.stream().sorted().forEach(value -> {
            String path = WorldReferenceAuthorityValidator.pathPart(value);
            String domain = value.substring(value.lastIndexOf(':') + 1);
            String validator = switch (domain) {
                case "WORLD_TEAM" -> "world-team-catalog";
                case "WORLD_PLAYER" -> "world-player-catalog";
                case "REAL_TEAM" -> "canonical-team-catalog";
                case "REAL_PLAYER" -> "canonical-player-catalog";
                case "SESSION_TEAM" -> "career-session-team-to-world-team";
                case "SESSION_PLAYER" -> "career-session-player-to-world-player";
                default -> throw new IllegalStateException("Reference domain has no validator: " + domain);
            };
            String previous = result.put(path, validator);
            if (previous != null && !previous.equals(validator)) {
                throw new IllegalStateException("Competing validators for " + path);
            }
        });
        return Map.copyOf(result);
    }

}

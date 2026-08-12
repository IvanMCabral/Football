package com.footballmanager.application.service.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure fail-closed validator used by runtime startup and self-destruction tests. */
public final class WorldReferenceAuthorityValidator {

    private WorldReferenceAuthorityValidator() { }

    public static List<String> validate(WorldMigrationPersistedModelGraph.Graph graph,
                                        Map<String, String> validators) {
        List<String> result = new ArrayList<>(graph.unresolvedGenericPaths());
        Set<String> unclassified = new LinkedHashSet<>(graph.identityLeaves());
        unclassified.removeAll(graph.classifiedIdentityPaths());
        unclassified.forEach(path -> result.add(path + ":unclassified-durable-identity"));

        Set<String> references = new LinkedHashSet<>();
        graph.annotatedReferences().forEach(value -> references.add(pathPart(value)));
        Set<String> missingValidators = new LinkedHashSet<>(references);
        missingValidators.removeAll(validators.keySet());
        missingValidators.forEach(path -> result.add(path + ":missing-validator"));
        Set<String> extraValidators = new LinkedHashSet<>(validators.keySet());
        extraValidators.removeAll(references);
        extraValidators.forEach(path -> result.add(path + ":validator-without-path"));

        if (graph.identityLeaves().isEmpty()) result.add("discovery:empty-identity-authority");
        if (references.isEmpty()) result.add("discovery:empty-reference-authority");
        return List.copyOf(result);
    }

    static String pathPart(String value) {
        int separator = value.lastIndexOf(':');
        if (separator < 0) throw new IllegalStateException("Malformed reference descriptor: " + value);
        return value.substring(0, separator);
    }
}

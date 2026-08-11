package com.footballmanager.application.service.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Named world-level references that must survive a storage migration. */
public record WorldReferenceGraph(Map<String, Set<String>> teamReferences,
                                  Map<String, Set<String>> playerReferences) {

    public WorldReferenceGraph {
        teamReferences = immutable(teamReferences);
        playerReferences = immutable(playerReferences);
    }

    public static WorldReferenceGraph empty() {
        return new WorldReferenceGraph(Map.of(), Map.of());
    }

    private static Map<String, Set<String>> immutable(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((key, values) -> copy.put(key,
                values == null ? Set.of() : Set.copyOf(values)));
        return Map.copyOf(copy);
    }
}

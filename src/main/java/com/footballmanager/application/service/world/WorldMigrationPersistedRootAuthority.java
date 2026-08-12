package com.footballmanager.application.service.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives World V2 roots only after an independent durable-boundary discovery.
 * {@link WorldPersistedWriter} is classification metadata, never discovery
 * metadata. An unannotated boundary therefore remains visible and fails the
 * authority gate instead of disappearing from it.
 */
public final class WorldMigrationPersistedRootAuthority {

    public Authority discover() {
        return fromDiscovery(new DurablePersistenceBoundaryDiscovery().discover());
    }

    public Authority inspect(Set<Class<?>> adapterTypes) {
        DurablePersistenceBoundaryDiscovery.Discovery discovery =
                new DurablePersistenceBoundaryDiscovery().inspect(adapterTypes);
        if (discovery.boundaries().isEmpty()) {
            throw new IllegalStateException("Persisted writer discovery returned no declarations");
        }
        if (discovery.boundaries().stream().noneMatch(value -> !value.declarations().isEmpty())) {
            throw new IllegalStateException("Persisted writer discovery returned no declarations");
        }
        return fromDiscovery(discovery);
    }

    private Authority fromDiscovery(DurablePersistenceBoundaryDiscovery.Discovery discovery) {
        List<WriterRoot> writers = new ArrayList<>();
        for (DurablePersistenceBoundary boundary : discovery.boundaries()) {
            for (DurablePersistenceBoundary.Declaration declaration : boundary.declarations()) {
                if (declaration.persistedType() == Object.class) continue;
                WorldPersistedWriter[] metadata = boundary.boundaryType()
                        .getAnnotationsByType(WorldPersistedWriter.class);
                WorldPersistedWriter matching = java.util.Arrays.stream(metadata)
                        .filter(value -> value.writeMethod().equals(declaration.method())
                                && value.root().equals(declaration.persistedType()))
                        .findFirst().orElse(null);
                if (matching != null) {
                    writers.add(new WriterRoot(boundary.boundaryType(), matching.writeMethod(),
                            matching.root(), matching.storageFamily(), matching.role()));
                }
            }
        }
        writers.sort(Comparator.comparing((WriterRoot value) -> value.adapter().getName())
                .thenComparing(WriterRoot::writeMethod)
                .thenComparing(value -> value.root().getName()));
        if (writers.isEmpty()) {
            throw new IllegalStateException("Persisted writer discovery returned no declarations");
        }
        Set<Class<?>> graphRoots = new LinkedHashSet<>();
        writers.stream()
                .filter(writer -> writer.role() == WorldPersistedWriter.DurabilityRole.WORLD_REFERENCE_GRAPH)
                .map(WriterRoot::root)
                .forEach(graphRoots::add);
        if (graphRoots.isEmpty()) {
            throw new IllegalStateException("Persisted writer discovery returned no world-reference roots");
        }
        List<UnclassifiedWriter> unclassified = discovery.unclassified().stream()
                .map(value -> new UnclassifiedWriter(value.boundaryType(), value.technology(),
                        value.operationType(), value.persistedTypes(), value.exclusionReason()))
                .toList();
        return new Authority(List.copyOf(writers), Set.copyOf(graphRoots),
                discovery.boundaries(), unclassified);
    }

    public record WriterRoot(Class<?> adapter, String writeMethod, Class<?> root,
                             String storageFamily, WorldPersistedWriter.DurabilityRole role) { }

    public record UnclassifiedWriter(Class<?> adapter,
                                     DurablePersistenceBoundary.StorageTechnology technology,
                                     DurablePersistenceBoundary.OperationType operationType,
                                     Set<Class<?>> persistedTypes,
                                     String reason) { }

    public record Authority(List<WriterRoot> writers, Set<Class<?>> graphRoots,
                            List<DurablePersistenceBoundary> boundaries,
                            List<UnclassifiedWriter> unclassifiedWriters) {
        public Authority(List<WriterRoot> writers, Set<Class<?>> graphRoots) {
            this(writers, graphRoots, List.of(), List.of());
        }

        public List<DurablePersistenceBoundary> classifiedBoundaries() {
            return boundaries.stream().filter(DurablePersistenceBoundary::isClassified).toList();
        }

        public void requireComplete() {
            if (!unclassifiedWriters.isEmpty()) {
                String names = unclassifiedWriters.stream().map(value -> value.adapter().getName())
                        .sorted().collect(java.util.stream.Collectors.joining(", "));
                throw new IllegalStateException("Unclassified durable persistence boundaries: " + names);
            }
        }
    }
}

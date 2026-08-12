package com.footballmanager.application.service.world;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers durable roots from persistence-writer declarations. The source of
 * roots is the adapter boundary, not a migration-owned root list.
 */
public final class WorldMigrationPersistedRootAuthority {

    private static final String PRODUCT_BASE_PACKAGE = "com.footballmanager";

    public Authority discover() {
        Set<Class<?>> adapterTypes = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        TypeFilter everyType = (metadata, factory) -> true;
        scanner.addIncludeFilter(everyType);
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        scanner.findCandidateComponents(PRODUCT_BASE_PACKAGE).forEach(candidate -> {
            try {
                Class<?> type = Class.forName(candidate.getBeanClassName(), false, loader);
                if (isProductClass(type)
                        && type.getAnnotationsByType(WorldPersistedWriter.class).length > 0) {
                    adapterTypes.add(type);
                }
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("Cannot inspect persisted writer "
                        + candidate.getBeanClassName(), error);
            }
        });
        return inspect(adapterTypes);
    }

    private static boolean isProductClass(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) return true;
        return !source.getLocation().toExternalForm().replace('\\', '/').contains("/test-classes/");
    }

    public Authority inspect(Set<Class<?>> adapterTypes) {
        List<WriterRoot> writers = new ArrayList<>();
        for (Class<?> adapter : adapterTypes) {
            for (WorldPersistedWriter declaration : adapter.getAnnotationsByType(WorldPersistedWriter.class)) {
                writers.add(new WriterRoot(adapter, declaration.writeMethod(), declaration.root(),
                        declaration.storageFamily(), declaration.role()));
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
        return new Authority(List.copyOf(writers), Set.copyOf(graphRoots));
    }

    public record WriterRoot(Class<?> adapter, String writeMethod, Class<?> root,
                             String storageFamily, WorldPersistedWriter.DurabilityRole role) { }

    public record Authority(List<WriterRoot> writers, Set<Class<?>> graphRoots) {
        public List<WriterRoot> unclassifiedWriters() {
            return List.of();
        }
    }
}

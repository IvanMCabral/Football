package com.footballmanager.application.service.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMigrationPersistedWriterAuthorityTest {

    private static final Pattern PACKAGE = Pattern.compile("package\\s+([\\w.]+);");
    private static final Pattern TYPE = Pattern.compile("public\\s+(?:final\\s+)?class\\s+(\\w+)");

    @Test
    void everyProductiveRedisWriterIsExplicitlyClassified() throws Exception {
        Set<Class<?>> discoveredWriterClasses = redisWriterClassesFromSource();
        assertFalse(discoveredWriterClasses.isEmpty());

        List<Class<?>> unclassified = discoveredWriterClasses.stream()
                .filter(type -> type.getAnnotationsByType(WorldPersistedWriter.class).length == 0)
                .sorted(java.util.Comparator.comparing(Class::getName))
                .toList();
        assertTrue(unclassified.isEmpty(), () -> "UNCLASSIFIED_PERSISTED_WRITERS=" + unclassified);

        WorldMigrationPersistedRootAuthority.Authority authority =
                new WorldMigrationPersistedRootAuthority().discover();
        assertTrue(authority.writers().stream().map(WorldMigrationPersistedRootAuthority.WriterRoot::adapter)
                .collect(java.util.stream.Collectors.toSet()).containsAll(discoveredWriterClasses));
    }

    @Test
    void unannotatedExternalWriterCannotSilentlyBecomeARoot() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new WorldMigrationPersistedRootAuthority().inspect(Set.of(UnclassifiedWriter.class)));
        assertTrue(error.getMessage().contains("no declarations"));
    }

    @Test
    void annotatedExternalWriterIsDiscoveredWithoutChangingRootAuthorityCode() {
        WorldMigrationPersistedRootAuthority.Authority authority =
                new WorldMigrationPersistedRootAuthority().inspect(Set.of(ClassifiedWriter.class));
        assertEquals(Set.of(ExternalHolder.class), authority.graphRoots());
    }

    private static Set<Class<?>> redisWriterClassesFromSource() throws Exception {
        Set<Class<?>> result = new LinkedHashSet<>();
        try (var paths = Files.walk(Path.of("src/main/java/com/footballmanager"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (!(normalized.contains("/adapters/out/redis/")
                        || normalized.contains("/infrastructure/persistence/redis/")
                        || normalized.contains("/infrastructure/adapter/out/redis/"))) continue;
                String source = Files.readString(path);
                if (!writesRedis(source)) continue;
                Matcher packageMatcher = PACKAGE.matcher(source);
                Matcher typeMatcher = TYPE.matcher(source);
                if (!packageMatcher.find() || !typeMatcher.find()) {
                    throw new IOException("Cannot identify Redis writer type " + path);
                }
                result.add(Class.forName(packageMatcher.group(1) + "." + typeMatcher.group(1)));
            }
        }
        return result;
    }

    private static boolean writesRedis(String source) {
        return source.contains("opsForValue().set")
                || source.contains("opsForSet().add")
                || source.contains("redis.call('SET'")
                || source.contains("redis.call(\"SET\"")
                || source.contains("RedisScript.of");
    }

    private static final class UnclassifiedWriter { }

    @WorldPersistedWriter(root = ExternalHolder.class, writeMethod = "save",
            storageFamily = "test", role = WorldPersistedWriter.DurabilityRole.WORLD_REFERENCE_GRAPH)
    private static final class ClassifiedWriter { }

    private static final class ExternalHolder {
        @com.footballmanager.domain.model.metadata.WorldIdentityReference(
                domain = com.footballmanager.domain.model.metadata.WorldIdentityDomain.WORLD_PLAYER)
        private String player;
    }
}

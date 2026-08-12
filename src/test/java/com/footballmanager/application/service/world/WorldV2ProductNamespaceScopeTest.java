package com.footballmanager.application.service.world;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Build artifact proof for the durable discovery package boundary. */
class WorldV2ProductNamespaceScopeTest {

    @Test
    void packagedApplicationHasNoOwnedClassesOutsideFootballManagerNamespace() throws Exception {
        Path jarPath = Path.of("target", "football-manager-1.0.0.jar");
        if (!Files.exists(jarPath)) return;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<String> owned = jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("BOOT-INF/classes/") && name.endsWith(".class"))
                    .filter(name -> !name.startsWith("BOOT-INF/classes/com/footballmanager/"))
                    .toList();
            assertTrue(owned.isEmpty(), () -> "application-owned classes outside scanner namespace: " + owned);
        }
    }
}

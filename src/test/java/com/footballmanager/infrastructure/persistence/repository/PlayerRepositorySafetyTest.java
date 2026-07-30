package com.footballmanager.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerRepositorySafetyTest {

    private static final Path REPOSITORY_SOURCE =
            Path.of("src/main/java/com/footballmanager/infrastructure/persistence/repository");

    @Test
    void repositoriesDoNotExposeRawGlobalPlayerInsertBypass() throws IOException {
        List<String> violations;
        try (var files = Files.walk(REPOSITORY_SOURCE)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(PlayerRepositorySafetyTest::containsRawPlayerInsert)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(violations)
                .as("Global catalog player writes must go through the validated MVP1 importer/writer path")
                .isEmpty();
    }

    private static boolean containsRawPlayerInsert(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("INSERT INTO players")
                    || source.contains("INSERT INTO players (")
                    || source.contains("insertPlayer(");
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect " + path, e);
        }
    }
}

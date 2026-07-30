package com.footballmanager.application.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLayerBoundaryTest {

    private static final Path APPLICATION_SOURCE = Path.of("src/main/java/com/footballmanager/application");

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "JdbcTemplate",
            "ClassPathResource",
            "DataSource",
            "DataSourceTransactionManager",
            "DatabaseClient",
            "java.sql",
            "com.footballmanager.infrastructure",
            "com.footballmanager.adapters",
            "SELECT ",
            "INSERT ",
            "UPDATE ",
            "DELETE "
    );

    @Test
    void applicationLayerDoesNotDependOnAdaptersInfrastructureOrConcreteIo() throws IOException {
        List<String> violations;
        try (var files = Files.walk(APPLICATION_SOURCE)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> forbiddenTokensIn(path).stream())
                    .toList();
        }

        assertThat(violations).isEmpty();
    }

    private static List<String> forbiddenTokensIn(Path path) {
        try {
            String source = Files.readString(path);
            return FORBIDDEN_TOKENS.stream()
                    .filter(source::contains)
                    .map(token -> path + " contains " + token)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect " + path, e);
        }
    }
}

package com.footballmanager.application.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationArchitectureBoundaryTest {

    private static final Path SIMULATION_SOURCE =
            Path.of("src/main/java/com/footballmanager/application/service/simulation");
    private static final Path DETAILED_ENGINE =
            Path.of("src/main/java/com/footballmanager/application/service/simulation/detailed/DetailedMatchEngine.java");
    private static final Path LEAGUE_SIMULATOR =
            Path.of("src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java");

    @Test
    void simulationCoreDoesNotDependOnWebDtosRedisJdbcOrInfrastructure() throws IOException {
        List<String> violations;
        try (var files = Files.walk(SIMULATION_SOURCE)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith("Dto.java"))
                    .flatMap(path -> forbiddenTokensIn(path).stream())
                    .toList();
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void detailedMatchEngineRemainsFacadeOverFlow() throws IOException {
        String source = Files.readString(DETAILED_ENGINE);

        assertThat(lineCount(DETAILED_ENGINE)).isLessThan(130);
        assertThat(source).contains("DetailedMatchEngineFlow");
        assertThat(countOccurrences(source, "new DetailedMatchEngineFlow")).isLessThanOrEqualTo(3);
        assertThat(source).doesNotContain("ShotAttemptService");
        assertThat(source).doesNotContain("while (clock.isRunning())");
    }

    @Test
    void leagueSimulatorDelegatesPersistenceAndMutationResponsibilities() throws IOException {
        String source = Files.readString(LEAGUE_SIMULATOR);

        assertThat(source).contains("MatchDetailPersistenceCoordinator");
        assertThat(source).contains("CareerMutationCoordinator");
        assertThat(source).doesNotContain("DetailedMatchData.fromResult");
        assertThat(source).doesNotContain("CareerMutationResult mutationResult");
        assertThat(source).doesNotContain(".onErrorResume(e -> Mono.empty())");
    }

    private static List<String> forbiddenTokensIn(Path path) {
        try {
            String source = Files.readString(path);
            return List.of(
                            "com.footballmanager.adapters.in.web",
                            "com.footballmanager.adapters.out",
                            "com.footballmanager.infrastructure",
                            "ReactiveRedisTemplate",
                            "JdbcTemplate",
                            "DatabaseClient",
                            "java.sql",
                            "SELECT ",
                            "INSERT ",
                            "UPDATE ",
                            "DELETE ")
                    .stream()
                    .filter(source::contains)
                    .map(token -> path + " contains " + token)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect " + path, e);
        }
    }

    private static long lineCount(Path path) throws IOException {
        try (var lines = Files.lines(path)) {
            return lines.count();
        }
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = source.indexOf(token, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + token.length();
        }
    }
}

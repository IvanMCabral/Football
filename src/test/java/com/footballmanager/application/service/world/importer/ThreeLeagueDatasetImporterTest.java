package com.footballmanager.application.service.world.importer;

import com.footballmanager.infrastructure.world.importer.ThreeLeagueDatasetImporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Three-league MVP 1 dataset importer")
class ThreeLeagueDatasetImporterTest {

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "Mgr2026Rot!Secure#");
    private static final String DB_NAME = "manager_three_league_import_" + UUID.randomUUID().toString().replace("-", "");

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static ThreeLeagueDatasetImporter importer;

    @BeforeAll
    static void createDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(adminUrl(), DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DB_NAME);
        }

        dataSource = new SingleConnectionDataSource(dbUrl(), DB_USER, DB_PASSWORD, true);
        jdbcTemplate = new JdbcTemplate(dataSource);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        importer = new ThreeLeagueDatasetImporter(jdbcTemplate, new ObjectMapper());
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        if (dataSource != null) {
            dataSource.destroy();
        }
        try (var connection = DriverManager.getConnection(adminUrl(), DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("""
                SELECT pg_terminate_backend(pid)
                FROM pg_stat_activity
                WHERE datname = '%s' AND pid <> pg_backend_pid()
                """.formatted(DB_NAME));
            statement.execute("DROP DATABASE IF EXISTS " + DB_NAME);
        }
    }

    @Test
    @DisplayName("imports the complete explicit dataset and validates exactly two traits per player")
    void importsCompleteDatasetAndValidatesTraits() {
        ThreeLeagueImportReport report = importer.importDataset();

        assertThat(report.countries()).isEqualTo(3);
        assertThat(report.leagues()).isEqualTo(3);
        assertThat(report.clubs()).isEqualTo(70);
        assertThat(report.teams()).isEqualTo(70);
        assertThat(report.players()).isEqualTo(1680);
        assertThat(report.playerSpecialAttributes()).isEqualTo(3360);

        assertThat(count("countries", "code IN ('ESP','ARG','BRA')")).isEqualTo(3);
        assertThat(count("leagues", "code IN ('ESP-PRIMERA','ARG-PRIMERA','BRA-SERIE-A')")).isEqualTo(3);
        assertThat(count("clubs", "source_system = 'manager-mvp1-explicit'")).isEqualTo(70);
        assertThat(count("players", "source_system = 'manager-mvp1-explicit'")).isEqualTo(1680);
        assertThat(invalidSpecialAttributePlayerCount()).isZero();
        assertThat(orphanSpecialAttributeCount()).isZero();
    }

    @Test
    @DisplayName("second import is idempotent")
    void secondImportIsIdempotent() {
        importer.importDataset();
        DatasetCounts first = counts();

        importer.importDataset();
        DatasetCounts second = counts();

        assertThat(second).isEqualTo(first);
        assertThat(invalidSpecialAttributePlayerCount()).isZero();
    }

    @Test
    @DisplayName("global validation detects broken trait coverage")
    void validationDetectsBrokenTraitCoverage() {
        importer.importDataset();
        jdbcTemplate.update("""
            DELETE FROM player_special_attributes
            WHERE id = (
                SELECT psa.id
                FROM player_special_attributes psa
                JOIN players p ON p.id = psa.player_id
                WHERE p.source_system = 'manager-mvp1-explicit'
                LIMIT 1
            )
            """);

        assertThatThrownBy(importer::validateGlobal)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exactly two special attributes");
    }

    private DatasetCounts counts() {
        return new DatasetCounts(
            count("clubs", "source_system = 'manager-mvp1-explicit'"),
            count("teams", "club_id IN (SELECT id FROM clubs WHERE source_system = 'manager-mvp1-explicit')"),
            count("players", "source_system = 'manager-mvp1-explicit'"),
            count("player_special_attributes", "player_id IN (SELECT id FROM players WHERE source_system = 'manager-mvp1-explicit')")
        );
    }

    private int count(String table, String where) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
    }

    private int invalidSpecialAttributePlayerCount() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM (
                SELECT p.id
                FROM players p
                LEFT JOIN player_special_attributes psa ON psa.player_id = p.id
                WHERE p.source_system = 'manager-mvp1-explicit'
                GROUP BY p.id
                HAVING COUNT(psa.id) <> 2
            ) invalid
            """, Integer.class);
    }

    private int orphanSpecialAttributeCount() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM player_special_attributes psa
            LEFT JOIN players p ON p.id = psa.player_id
            LEFT JOIN special_attributes sa ON sa.id = psa.special_attribute_id
            WHERE p.id IS NULL OR sa.id IS NULL
            """, Integer.class);
    }

    private static String adminUrl() {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    }

    private static String dbUrl() {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    }

    private record DatasetCounts(int clubs, int teams, int players, int traits) {}
}

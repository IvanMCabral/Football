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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private static final String DB_PASSWORD = requiredEnv("DB_PASSWORD");
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
        assertThat(clubsWithLessThanTwoGoalkeepers()).isZero();
    }

    @Test
    @DisplayName("second import is idempotent across entities, relationships and stable player ids")
    void secondImportIsIdempotentAcrossLogicalSnapshot() {
        importer.importDataset();
        DatasetCounts first = counts();
        DatasetFingerprint firstFingerprint = fingerprint();

        importer.importDataset();
        DatasetCounts second = counts();
        DatasetFingerprint secondFingerprint = fingerprint();

        assertThat(second).isEqualTo(first);
        assertThat(secondFingerprint).isEqualTo(firstFingerprint);
        assertThat(invalidSpecialAttributePlayerCount()).isZero();
        assertThat(clubsWithLessThanTwoGoalkeepers()).isZero();
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

    @Test
    @DisplayName("failed validation inside the import transaction rolls back partial dataset writes")
    void failedValidationRollsBackPartialDatasetWrites() {
        importer.importDataset();
        DatasetFingerprint before = fingerprint();
        DatasetCounts beforeCounts = counts();

        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
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
            importer.validateGlobal();
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exactly two special attributes");

        assertThat(counts()).isEqualTo(beforeCounts);
        assertThat(fingerprint()).isEqualTo(before);
        assertThat(invalidSpecialAttributePlayerCount()).isZero();
    }

    @Test
    @DisplayName("stable public player identities keep club-independent ids and corrected positions")
    void stablePlayerIdentitiesKeepClubIndependentIdsAndCorrectedPositions() {
        importer.importDataset();

        assertPlayerIdentity("public-player:alejandro-balde:2003-04-15:esp", "Balde", "LB");
        assertPlayerIdentity("public-player:lamine-yamal:1999-05-02:esp", "Yamal", "RW");
        assertPlayerIdentity("public-player:aitor-fernandez:1991-07-13:esp", "Fernández", "GK");
        assertPlayerIdentity("public-player:cristhian-stuani:2000-01-13:esp", "Stuani", "ST");
        assertPlayerIdentity("public-player:carlos-palacios:2005-06-08:arg", "Palacios", "CAM");
        assertPlayerIdentity("public-player:federico-mancuello:1994-12-12:arg", "Mancuello", "CM");
        assertPlayerIdentity("public-player:gonzalo-montiel:2003-02-04:arg", "Montiel", "RB");
        assertPlayerIdentity("public-player:gabriel-barbosa:1991-03-05:bra", "Barbosa", "ST");
    }

    @Test
    @DisplayName("re-import repairs non-identity mutations without changing stable player ids")
    void reImportRepairsMutablePlayerFieldsAndRelationshipsWithoutChangingStableIds() {
        importer.importDataset();
        DatasetFingerprint before = fingerprint();
        String sourceId = "public-player:aitor-fernandez:1991-07-13:esp";
        String playerId = playerId(sourceId);
        String originalTeamId = playerTeamId(sourceId);
        String otherTeamId = jdbcTemplate.queryForObject("""
            SELECT t.id::text
            FROM teams t
            WHERE t.id::text <> ?
            ORDER BY t.id
            LIMIT 1
            """, String.class, originalTeamId);

        jdbcTemplate.update("""
            UPDATE players
            SET display_name = 'Aitor Broken',
                shirt_number = 99,
                position = 'ST',
                attack = 1,
                defense = 1,
                technique = 1,
                speed = 1,
                stamina = 1,
                mentality = 1,
                source_entity_id = 'broken-source-entity',
                identity_source_ref = 'broken-identity-ref',
                position_source_ref = 'broken-position-ref'
            WHERE id = ?::uuid
            """, playerId);
        jdbcTemplate.update("DELETE FROM team_squad WHERE player_id = ?::uuid", playerId);
        jdbcTemplate.update("INSERT INTO team_squad (team_id, player_id) VALUES (?::uuid, ?::uuid)", otherTeamId, playerId);
        jdbcTemplate.update("DELETE FROM player_special_attributes WHERE player_id = ?::uuid", playerId);

        importer.importDataset();

        assertThat(playerId(sourceId)).isEqualTo(playerId);
        assertThat(playerTeamId(sourceId)).isEqualTo(originalTeamId);
        assertThat(fingerprint()).isEqualTo(before);
        assertThat(invalidSpecialAttributePlayerCount()).isZero();
        assertThat(orphanSpecialAttributeCount()).isZero();
    }

    @Test
    @DisplayName("rollback matrix keeps snapshot intact for representative importer validation failures")
    void rollbackMatrixKeepsSnapshotIntactForRepresentativeValidationFailures() {
        importer.importDataset();
        DatasetFingerprint before = fingerprint();
        DatasetCounts beforeCounts = counts();

        assertRollback("missing source ref", () -> jdbcTemplate.update("""
            UPDATE players
            SET identity_source_ref = ''
            WHERE id = (SELECT id FROM players WHERE source_system = 'manager-mvp1-explicit' ORDER BY source_id LIMIT 1)
            """));
        assertRollback("missing source entity", () -> jdbcTemplate.update("""
            UPDATE players
            SET source_entity_id = ''
            WHERE id = (SELECT id FROM players WHERE source_system = 'manager-mvp1-explicit' ORDER BY source_id LIMIT 1)
            """));
        assertRollback("invalid position", () -> jdbcTemplate.update("""
            UPDATE players
            SET position = 'BAD'
            WHERE id = (SELECT id FROM players WHERE source_system = 'manager-mvp1-explicit' ORDER BY source_id LIMIT 1)
            """));
        assertRollback("0 traits", () -> jdbcTemplate.update("""
            DELETE FROM player_special_attributes
            WHERE player_id = (SELECT id FROM players WHERE source_system = 'manager-mvp1-explicit' ORDER BY source_id LIMIT 1)
            """));
        assertRollback("1 trait", () -> jdbcTemplate.update("""
            DELETE FROM player_special_attributes
            WHERE id = (
                SELECT psa.id
                FROM player_special_attributes psa
                JOIN players p ON p.id = psa.player_id
                WHERE p.source_system = 'manager-mvp1-explicit'
                ORDER BY p.source_id, psa.slot
                LIMIT 1
            )
            """));

        assertThat(counts()).isEqualTo(beforeCounts);
        assertThat(fingerprint()).isEqualTo(before);
        assertThat(invalidSpecialAttributePlayerCount()).isZero();
        assertThat(orphanSpecialAttributeCount()).isZero();
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

    private int clubsWithLessThanTwoGoalkeepers() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM (
                SELECT c.id
                FROM clubs c
                JOIN teams t ON t.club_id = c.id
                JOIN team_squad ts ON ts.team_id = t.id
                JOIN players p ON p.id = ts.player_id
                WHERE c.source_system = 'manager-mvp1-explicit'
                GROUP BY c.id
                HAVING COUNT(*) FILTER (WHERE p.position = 'GK') < 2
            ) invalid
            """, Integer.class);
    }

    private DatasetFingerprint fingerprint() {
        return new DatasetFingerprint(
            hash("""
                SELECT source_id || '|' || id || '|' || display_name || '|' || position || '|' || shirt_number
                    || '|' || attack || '|' || defense || '|' || technique || '|' || speed
                    || '|' || stamina || '|' || mentality || '|' || COALESCE(height_cm::text, '')
                    || '|' || COALESCE(source_entity_id, '') || '|' || COALESCE(identity_source_ref, '')
                    || '|' || COALESCE(position_source_ref, '')
                FROM players
                WHERE source_system = 'manager-mvp1-explicit'
                ORDER BY source_id
                """),
            hash("""
                SELECT t.id || '|' || p.source_id
                FROM team_squad ts
                JOIN teams t ON t.id = ts.team_id
                JOIN players p ON p.id = ts.player_id
                WHERE p.source_system = 'manager-mvp1-explicit'
                ORDER BY t.id, p.source_id
                """),
            hash("""
                SELECT p.source_id || '|' || sa.code || '|' || psa.slot
                FROM player_special_attributes psa
                JOIN players p ON p.id = psa.player_id
                JOIN special_attributes sa ON sa.id = psa.special_attribute_id
                WHERE p.source_system = 'manager-mvp1-explicit'
                ORDER BY p.source_id, psa.slot
                """)
        );
    }

    private void assertRollback(String scenario, Runnable mutation) {
        DatasetFingerprint before = fingerprint();
        DatasetCounts beforeCounts = counts();
        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            mutation.run();
            importer.validateGlobal();
        }))
            .as(scenario)
            .isInstanceOf(RuntimeException.class);

        assertThat(counts()).as(scenario + " counts").isEqualTo(beforeCounts);
        assertThat(fingerprint()).as(scenario + " fingerprint").isEqualTo(before);
        assertThat(orphanSpecialAttributeCount()).as(scenario + " orphan traits").isZero();
    }

    private String playerId(String sourceId) {
        return jdbcTemplate.queryForObject("""
            SELECT id::text
            FROM players
            WHERE source_system = 'manager-mvp1-explicit'
              AND source_id = ?
            """, String.class, sourceId);
    }

    private String playerTeamId(String sourceId) {
        return jdbcTemplate.queryForObject("""
            SELECT ts.team_id::text
            FROM team_squad ts
            JOIN players p ON p.id = ts.player_id
            WHERE p.source_system = 'manager-mvp1-explicit'
              AND p.source_id = ?
            ORDER BY ts.team_id
            LIMIT 1
            """, String.class, sourceId);
    }

    private void assertPlayerIdentity(String externalId, String displayName, String position) {
        String playerId = jdbcTemplate.queryForObject("""
            SELECT id::text
            FROM players
            WHERE source_system = 'manager-mvp1-explicit'
              AND source_id = ?
              AND display_name = ?
              AND position = ?
              AND source_entity_id IS NOT NULL
              AND position_source_ref IS NOT NULL
              AND position_source_ref <> ''
            """, String.class, externalId, displayName, position);

        assertThat(playerId).isNotBlank();
        assertThat(externalId).doesNotContain(":barcelona:", ":osasuna:", ":girona:", ":boca-juniors:",
            ":independiente:", ":river-plate:", ":santos:");
    }

    private String hash(String query) {
        return jdbcTemplate.queryForObject("""
            SELECT md5(COALESCE(string_agg(row_value, E'\\n'), ''))
            FROM (
            """ + query + """
            ) snapshot(row_value)
            """, String.class);
    }

    private static String adminUrl() {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    }

    private static String dbUrl() {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be provided by the test environment");
        }
        return value;
    }

    private record DatasetCounts(int clubs, int teams, int players, int traits) {}

    private record DatasetFingerprint(String players, String squads, String traits) {}
}

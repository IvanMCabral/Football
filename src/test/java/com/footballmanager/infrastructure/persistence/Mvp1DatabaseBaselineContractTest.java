package com.footballmanager.infrastructure.persistence;

import com.footballmanager.application.service.world.PlayerSpecialAttributeSelectionValidator;
import com.footballmanager.testinfra.PostgresTestEnvironmentPostProcessor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mvp1DatabaseBaselineContractTest {

    private static final PostgresTestEnvironmentPostProcessor.PostgresProcess POSTGRES =
        PostgresTestEnvironmentPostProcessor.ensureRunning();
    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = String.valueOf(POSTGRES.port());
    private static final String DB_USER = POSTGRES.username();
    private static final String DB_PASSWORD = POSTGRES.password();
    private static final String DATABASE =
        "manager_mvp1_baseline_contract_" + UUID.randomUUID().toString().replace("-", "");

    private static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + database;
    }

    @BeforeAll
    static void createDatabaseAndMigrate() throws Exception {
        try (Connection admin = DriverManager.getConnection(jdbcUrl("postgres"), DB_USER, DB_PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE);
        }

        Flyway.configure()
            .dataSource(jdbcUrl(DATABASE), DB_USER, DB_PASSWORD)
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        try (Connection admin = DriverManager.getConnection(jdbcUrl("postgres"), DB_USER, DB_PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
        }
    }

    @Test
    void flywayAppliesSingleCleanBaselineToEmptyPostgresDatabase() throws Exception {
        try (Connection connection = connect()) {
            assertThat(singleString(connection,
                "SELECT version || '|' || description || '|' || success " +
                    "FROM flyway_schema_history ORDER BY installed_rank"))
                .isEqualTo("1|create manager schema|true");

            assertThat(singleInt(connection,
                "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"))
                .isEqualTo(26);
        }
    }

    @Test
    void persistentEntitiesPointToExistingTables() throws Exception {
        try (Connection connection = connect()) {
            assertTableExists(connection, "users");
            assertTableExists(connection, "leagues");
            assertTableExists(connection, "seasons");
            assertTableExists(connection, "teams");
            assertTableExists(connection, "players");
            assertTableExists(connection, "matches");
            assertNoTable(connection, "tournament");
            assertNoTable(connection, "tournaments");
        }
    }

    @Test
    void seasonRoundTripUsesSingleSeasonYearColumn() throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            UUID countryId = UUID.randomUUID();
            UUID leagueId = UUID.randomUUID();

            statement.executeUpdate("""
                INSERT INTO countries (id, code, name)
                VALUES ('%s', 'ARG', 'Argentina')
                """.formatted(countryId));
            statement.executeUpdate("""
                INSERT INTO leagues (id, country_id, code, name, country)
                VALUES ('%s', '%s', 'ARG-1', 'Argentina Primera', 'Argentina')
                """.formatted(leagueId, countryId));
            statement.executeUpdate("""
                INSERT INTO seasons (season_year, league_id, status, starts_at, ends_at)
                VALUES (2026, '%s', 'ACTIVE', '2026-01-01', '2026-12-31')
                """.formatted(leagueId));

            try (ResultSet rs = statement.executeQuery("""
                SELECT season_year, league_id, status, starts_at, ends_at
                FROM seasons
                WHERE league_id = '%s'
                """.formatted(leagueId))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("season_year")).isEqualTo(2026);
                assertThat(UUID.fromString(rs.getString("league_id"))).isEqualTo(leagueId);
                assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                assertThat(rs.getDate("starts_at").toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
                assertThat(rs.getDate("ends_at").toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            }

            assertColumnExists(connection, "seasons", "season_year", "integer");
            assertColumnDoesNotExist(connection, "seasons", "year");
        }
    }

    @Test
    void playerHeightPolicyAllowsUnknownAndValidHeightsButRejectsInvalidValues() throws Exception {
        try (Connection connection = connect()) {
            SeedIds ids = seedUserLeagueAndTeam(connection);

            insertPlayer(connection, ids, "Unknown Height", null);
            insertPlayer(connection, ids, "Valid Height", 180);

            assertThatThrownBy(() -> insertPlayer(connection, ids, "Zero Height", 0))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlayer(connection, ids, "Too Short", 159))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlayer(connection, ids, "Too Tall", 211))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void specialAttributeDatabaseConstraintsRejectInvalidRelations() throws Exception {
        try (Connection connection = connect()) {
            SeedIds ids = seedUserLeagueAndTeam(connection);
            UUID playerId = insertPlayer(connection, ids, "Two Traits Player", 181);
            UUID firstTrait = specialAttributeId(connection, "leader");
            UUID secondTrait = specialAttributeId(connection, "workhorse");

            insertPlayerSpecialAttribute(connection, playerId, firstTrait, 1);
            insertPlayerSpecialAttribute(connection, playerId, secondTrait, 2);

            assertThat(singleInt(connection,
                "SELECT COUNT(*) FROM player_special_attributes WHERE player_id = '" + playerId + "'"))
                .isEqualTo(2);

            assertThatThrownBy(() -> insertPlayerSpecialAttribute(connection, playerId, firstTrait, 2))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlayerSpecialAttribute(connection, playerId, secondTrait, 1))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlayerSpecialAttribute(connection, playerId, secondTrait, 3))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlayerSpecialAttribute(connection, UUID.randomUUID(), firstTrait, 1))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlayerSpecialAttribute(connection, playerId, UUID.randomUUID(), 1))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void applicationValidatorRequiresExactlyTwoExistingDifferentSpecialAttributes() {
        PlayerSpecialAttributeSelectionValidator validator = new PlayerSpecialAttributeSelectionValidator();
        Set<String> catalog = Set.of("leader", "workhorse", "line_breaker");

        assertThat(validator.validate(List.of("leader", "workhorse"), catalog).codes())
            .containsExactlyInAnyOrder("leader", "workhorse");

        assertThatThrownBy(() -> validator.validate(List.of(), catalog))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of("leader"), catalog))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of("leader", "workhorse", "line_breaker"), catalog))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of("leader", "leader"), catalog))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of("leader", "unknown_trait"), catalog))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baselineExposesCriticalKeysAndIndexesForThreeLeagueImports() throws Exception {
        try (Connection connection = connect()) {
            Map<String, List<String>> uniqueConstraints = Map.of(
                "countries", List.of("countries_code_key"),
                "clubs", List.of("uq_clubs_source"),
                "players", List.of("uq_players_source"),
                "team_squad", List.of("uq_team_squad"),
                "player_special_attributes", List.of(
                    "uq_player_special_attribute",
                    "uq_player_special_attribute_slot")
            );

            for (Map.Entry<String, List<String>> entry : uniqueConstraints.entrySet()) {
                for (String constraint : entry.getValue()) {
                    assertConstraintExists(connection, entry.getKey(), constraint);
                }
            }

            assertIndexExists(connection, "idx_players_country_id");
            assertIndexExists(connection, "idx_player_special_attributes_player");
            assertIndexExists(connection, "idx_matches_game_round");
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(DATABASE), DB_USER, DB_PASSWORD);
    }

    private static void assertTableExists(Connection connection, String table) throws SQLException {
        assertThat(singleInt(connection,
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + table + "'"))
            .as("table %s exists", table)
            .isEqualTo(1);
    }

    private static void assertNoTable(Connection connection, String table) throws SQLException {
        assertThat(singleInt(connection,
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + table + "'"))
            .as("table %s does not exist", table)
            .isZero();
    }

    private static void assertColumnExists(Connection connection, String table, String column, String type) throws SQLException {
        assertThat(singleInt(connection,
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                "AND table_name = '" + table + "' AND column_name = '" + column + "' AND data_type = '" + type + "'"))
            .as("%s.%s has type %s", table, column, type)
            .isEqualTo(1);
    }

    private static void assertColumnDoesNotExist(Connection connection, String table, String column) throws SQLException {
        assertThat(singleInt(connection,
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                "AND table_name = '" + table + "' AND column_name = '" + column + "'"))
            .as("%s.%s is absent", table, column)
            .isZero();
    }

    private static void assertConstraintExists(Connection connection, String table, String constraint) throws SQLException {
        assertThat(singleInt(connection,
            "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' " +
                "AND table_name = '" + table + "' AND constraint_name = '" + constraint + "'"))
            .as("constraint %s on %s", constraint, table)
            .isEqualTo(1);
    }

    private static void assertIndexExists(Connection connection, String indexName) throws SQLException {
        assertThat(singleInt(connection,
            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = '" + indexName + "'"))
            .as("index %s", indexName)
            .isEqualTo(1);
    }

    private static int singleInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static String singleString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private static SeedIds seedUserLeagueAndTeam(Connection connection) throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (id, email, username, password_hash)
                VALUES ('%s', '%s@example.test', 'user_%s', 'hash')
                """.formatted(userId, userId, userId.toString().substring(0, 8)));
            statement.executeUpdate("""
                INSERT INTO leagues (id, code, name, country)
                VALUES ('%s', 'L_%s', 'Contract League', 'Testland')
                """.formatted(leagueId, leagueId.toString().substring(0, 8)));
            statement.executeUpdate("""
                INSERT INTO teams (id, manager_id, league_id, name, country)
                VALUES ('%s', '%s', '%s', 'Contract Team', 'Testland')
                """.formatted(teamId, userId, leagueId));
        }
        return new SeedIds(userId, leagueId, teamId);
    }

    private static UUID insertPlayer(Connection connection, SeedIds ids, String name, Integer heightCm) throws SQLException {
        UUID playerId = UUID.randomUUID();
        String heightSql = heightCm == null ? "NULL" : heightCm.toString();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO players (
                    id, source_system, source_id, name, age, position,
                    attack, defense, technique, speed, stamina, mentality,
                    market_value, energy, injured, height_cm
                )
                VALUES (
                    '%s', 'contract-test', '%s', '%s', 24, 'CM',
                    60, 60, 60, 60, 60, 60,
                    %s, 100, FALSE, %s
                )
                """.formatted(playerId, playerId, name, BigDecimal.ZERO, heightSql));
            statement.executeUpdate("""
                INSERT INTO team_squad (team_id, player_id)
                VALUES ('%s', '%s')
                """.formatted(ids.teamId(), playerId));
        }
        return playerId;
    }

    private static UUID specialAttributeId(Connection connection, String code) throws SQLException {
        return UUID.fromString(singleString(connection,
            "SELECT id::text FROM special_attributes WHERE code = '" + code + "'"));
    }

    private static void insertPlayerSpecialAttribute(
        Connection connection,
        UUID playerId,
        UUID specialAttributeId,
        int slot
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO player_special_attributes (player_id, special_attribute_id, slot)
                VALUES ('%s', '%s', %d)
                """.formatted(playerId, specialAttributeId, slot));
        }
    }

    private record SeedIds(UUID userId, UUID leagueId, UUID teamId) {
    }
}

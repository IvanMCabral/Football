package com.footballmanager.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Mvp1DatabaseBaselineContractTest {

    private static final Path BASELINE =
        Path.of("src/main/resources/db/migration/V1__create_manager_schema.sql");

    @Test
    void baselineDefinesMvp1CatalogCareerAndMatchTables() throws IOException {
        String sql = Files.readString(BASELINE);

        assertThat(sql)
            .contains("CREATE TABLE countries")
            .contains("CREATE TABLE leagues")
            .contains("CREATE TABLE divisions")
            .contains("CREATE TABLE clubs")
            .contains("CREATE TABLE teams")
            .contains("CREATE TABLE players")
            .contains("CREATE TABLE team_squad")
            .contains("CREATE TABLE games")
            .contains("CREATE TABLE matches")
            .contains("CREATE TABLE match_events")
            .contains("CREATE TABLE standings");
    }

    @Test
    void baselineDefinesPlayerAttributesAndTwoSpecialAttributeSlots() throws IOException {
        String sql = Files.readString(BASELINE);

        assertThat(sql)
            .contains("CREATE TABLE player_attribute_catalog")
            .contains("('attack', 'Attack'")
            .contains("('defense', 'Defense'")
            .contains("('technique', 'Technique'")
            .contains("('speed', 'Speed'")
            .contains("('stamina', 'Stamina'")
            .contains("('mentality', 'Mentality'")
            .contains("CREATE TABLE special_attributes")
            .contains("CREATE TABLE player_special_attributes")
            .contains("slot SMALLINT NOT NULL CHECK (slot IN (1, 2))")
            .contains("CONSTRAINT uq_player_special_attribute UNIQUE (player_id, special_attribute_id)")
            .contains("CONSTRAINT uq_player_special_attribute_slot UNIQUE (player_id, slot)");
    }

    @Test
    void baselineDefinesCoreConstraintsAndIndexes() throws IOException {
        String sql = Files.readString(BASELINE);

        assertThat(sql)
            .contains("CHECK (attack BETWEEN 1 AND 99)")
            .contains("CHECK (defense BETWEEN 1 AND 99)")
            .contains("CHECK (technique BETWEEN 1 AND 99)")
            .contains("CHECK (speed BETWEEN 1 AND 99)")
            .contains("CHECK (stamina BETWEEN 1 AND 99)")
            .contains("CHECK (mentality BETWEEN 1 AND 99)")
            .contains("CONSTRAINT uq_team_squad UNIQUE (team_id, player_id)")
            .contains("CREATE INDEX idx_matches_game_round ON matches(game_id, round)")
            .contains("CREATE INDEX idx_player_special_attributes_player ON player_special_attributes(player_id)");
    }
}

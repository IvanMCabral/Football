package com.footballmanager.infrastructure.persistence.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.PlayerAttributes;
import com.footballmanager.domain.model.valueobject.PlayerId;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.r2dbc.spi.Row;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("players")
public class PlayerEntity {
    @Id
    private UUID id;
    private String name;
    private int age;
    private String position;
    private int attack;
    private int defense;
    private int technique;
    private int speed;
    private int stamina;
    private int mentality;
    private BigDecimal marketValue;
    private int energy;
    private boolean injured;
    private Instant createdAt;
    private Instant updatedAt;

    // Height and skill metadata are nullable for backward compatibility.
    private Integer heightCm;

    /**
     * Skill levels serialized as JSON text.
     */
    private String skillLevelsJson;

    /** Maps the canonical player columns when the world bootstrap joins team_squad. */
    public static PlayerEntity fromRow(Row row) {
        return new PlayerEntity(
                row.get("id", UUID.class),
                row.get("name", String.class),
                value(row, "age", Integer.class, 0),
                row.get("position", String.class),
                value(row, "attack", Integer.class, 0),
                value(row, "defense", Integer.class, 0),
                value(row, "technique", Integer.class, 0),
                value(row, "speed", Integer.class, 0),
                value(row, "stamina", Integer.class, 0),
                value(row, "mentality", Integer.class, 0),
                value(row, "market_value", BigDecimal.class, BigDecimal.ZERO),
                value(row, "energy", Integer.class, 100),
                value(row, "injured", Boolean.class, false),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                row.get("height_cm", Integer.class),
                row.get("skill_levels_json", String.class));
    }

    private static <T> T value(Row row, String column, Class<T> type, T fallback) {
        T value = row.get(column, type);
        return value == null ? fallback : value;
    }

    // JSON codec.
    //
    // ObjectMapper estatico: thread-safe post-configuration, y la conversion es
    // trivial (Map<PlayerSkill, Integer> ↔ JSON object). Trade-off documentado:
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<PlayerSkill, Integer>> SKILL_MAP_TYPE =
            new TypeReference<Map<PlayerSkill, Integer>>() {};

    /**
     * Serializa skillLevels map a JSON string. Null/empty map → null (no JSONB null literal).
     */
    static String serializeSkillLevels(Map<PlayerSkill, Integer> skillLevels) {
        if (skillLevels == null || skillLevels.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(skillLevels);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize skillLevels to JSON", e);
        }
    }

    /**
     * Deserializa skillLevels JSON string a map. Null/blank → empty map.
     * Falla loud si el JSON es malformed (no se traga excepciones silenciosamente).
     */
    static Map<PlayerSkill, Integer> deserializeSkillLevels(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<PlayerSkill, Integer> result = MAPPER.readValue(json, SKILL_MAP_TYPE);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to deserialize skillLevels JSON: " + json, e);
        }
    }

    // Mapping helpers.

    public static PlayerEntity fromDomain(Player player) {
        PlayerAttributes attrs = player.getAttributes();
        return new PlayerEntity(
            player.getId().getValue(),
            player.getName(),
            player.getAge(),
            player.getPosition().name(), // Position now matches GK, LB, CB, etc.
            attrs.getAttack(),
            attrs.getDefense(),
            attrs.getTechnique(),
            attrs.getSpeed(),
            attrs.getStamina(),
            attrs.getMentality(),
            player.getMarketValue(),
            player.getEnergy(),
            player.isInjured(),
            player.getCreatedAt(),
            player.getUpdatedAt(),
            player.getHeightCm(),
            serializeSkillLevels(player.getSkillLevels())
        );
    }

    public static PlayerEntity fromDomainForInsert(Player player) {
        PlayerAttributes attrs = player.getAttributes();
        return new PlayerEntity(
            player.getId() != null ? player.getId().getValue() : null,
            player.getName(),
            player.getAge(),
            player.getPosition().name(), // Position now matches GK, LB, CB, etc.
            attrs.getAttack(),
            attrs.getDefense(),
            attrs.getTechnique(),
            attrs.getSpeed(),
            attrs.getStamina(),
            attrs.getMentality(),
            player.getMarketValue(),
            player.getEnergy(),
            player.isInjured(),
            player.getCreatedAt(),
            player.getUpdatedAt(),
            player.getHeightCm(),
            serializeSkillLevels(player.getSkillLevels())
        );
    }

    public Player toDomain() {
        PlayerAttributes attributes = PlayerAttributes.of(
            attack, defense, technique, speed, stamina, mentality
        );
        // injuryState basado en el boolean injured
        Player.InjuryState injuryState = injured
            ? Player.InjuryState.INJURED_SERIOUS
            : Player.InjuryState.HEALTHY;

        // Null height and empty skill maps are valid for legacy records.
        Map<PlayerSkill, Integer> skills = deserializeSkillLevels(skillLevelsJson);

        return Player.reconstruct(
            PlayerId.of(id),
            name,
            age,
            Player.Position.valueOf(position),
            attributes,
            marketValue,
            heightCm,
            skills,
            energy,
            injuryState,
            injured,
            createdAt,
            updatedAt
        );
    }

    /**
     * Read-only view of the deserialized skill levels.
     * Lazy: parses JSON on first call. Returns empty map if skillLevelsJson is null.
     */
    public Map<PlayerSkill, Integer> getSkillLevels() {
        return Collections.unmodifiableMap(deserializeSkillLevels(skillLevelsJson));
    }
}

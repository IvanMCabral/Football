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

    // V25D32-F2: height + skills metadata (source of truth: Player directo).
    // Columnas NULLABLE para backward-compat con players pre-V25D32.
    // Engine en V25D33 usara defaults si son null/empty.
    private Integer heightCm;

    /**
     * V25D32-F2: skills serializados como JSON string (no JSONB — hypersistence-utils
     * no esta en pom). Engine deserializa on-read via {@link #deserializeSkillLevels(String)}.
     * Formato esperado: {@code {"SHOOTER":88,"DRIBBLER":75,...}} (Map<PlayerSkill, Integer>).
     */
    private String skillLevelsJson;

    // ========== JSON codec (V25D32-F2) ==========
    //
    // ObjectMapper estatico: thread-safe post-configuration, y la conversion es
    // trivial (Map<PlayerSkill, Integer> ↔ JSON object). Trade-off documentado:
    // si V25D33 migra a JSONB columna, este codec se reemplaza por una annotation
    // @JdbcTypeCode(SqlTypes.JSON) o @Convert(converter=...).
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

    // ========== Mapping helpers (V25D32-F2 updated) ==========

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
            player.getHeightCm(),                            // V25D32-F2
            serializeSkillLevels(player.getSkillLevels())    // V25D32-F2
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
            player.getHeightCm(),                            // V25D32-F2
            serializeSkillLevels(player.getSkillLevels())    // V25D32-F2
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

        // V25D32-F2: leer height + skills del entity y reconstruir Player completo.
        // Backward-compat: si heightCm/skillLevelsJson son null, se mantienen null/empty
        // (engine en V25D33 aplica defaults).
        Map<PlayerSkill, Integer> skills = deserializeSkillLevels(skillLevelsJson);

        return Player.reconstruct(
            PlayerId.of(id),
            name,
            age,
            Player.Position.valueOf(position),
            attributes,
            marketValue,
            heightCm,                     // V25D32-F2 (Integer, nullable)
            skills,                       // V25D32-F2 (Map, never null per codec)
            energy,
            injuryState,
            injured,
            createdAt,
            updatedAt
        );
    }

    /**
     * V25D32-F2: read-only view of the deserialized skill levels.
     * Lazy: parses JSON on first call. Returns empty map if skillLevelsJson is null.
     */
    public Map<PlayerSkill, Integer> getSkillLevels() {
        return Collections.unmodifiableMap(deserializeSkillLevels(skillLevelsJson));
    }
}

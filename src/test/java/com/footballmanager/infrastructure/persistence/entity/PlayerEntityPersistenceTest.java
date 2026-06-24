package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.PlayerAttributes;
import com.footballmanager.domain.model.valueobject.PlayerId;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D32-F2: PlayerEntity persistence — height + skills columns.
 *
 * <p>Tests del mapping domain → entity → domain. Sin Spring/DB: unit test puro sobre
 * {@link PlayerEntity#fromDomain(Player)}, {@link PlayerEntity#fromDomainForInsert(Player)}
 * y {@link PlayerEntity#toDomain()}.
 *
 * <p>El round-trip con datos reales se hace via Postgres en runtime; aca cubrimos:
 * <ul>
 *   <li>Round-trip con height + skills</li>
 *   <li>Backward-compat: pre-V25D32 rows (height=null, skillLevelsJson=null) → domain con null/empty</li>
 *   <li>Malformed JSON lanza IllegalStateException (fail-loud, no silent swallow)</li>
 *   <li>Sparse map: skill con level 0 se omite (getSkillLevel retorna 0)</li>
 * </ul>
 */
class PlayerEntityPersistenceTest {

    @Test
    void fromDomain_includesHeightAndSkillLevels() {
        Player player = buildPlayerWithHeightAndSkills();

        PlayerEntity entity = PlayerEntity.fromDomain(player);

        assertEquals(178, entity.getHeightCm(),
                "fromDomain debe copiar heightCm de Player a entity");
        assertNotNull(entity.getSkillLevelsJson(),
                "fromDomain debe serializar skillLevels a JSON");
        assertTrue(entity.getSkillLevelsJson().contains("SHOOTER"),
                "JSON debe contener el skill SHOOTER");
        assertTrue(entity.getSkillLevelsJson().contains("88"),
                "JSON debe contener el level 88");
    }

    @Test
    void fromDomainForInsert_includesHeightAndSkillLevels() {
        Player player = buildPlayerWithHeightAndSkills();

        PlayerEntity entity = PlayerEntity.fromDomainForInsert(player);

        assertEquals(178, entity.getHeightCm());
        assertNotNull(entity.getSkillLevelsJson());
    }

    @Test
    void fromDomain_nullHeightAndEmptySkills_yieldsNullColumns() {
        // Player sin height ni skills (caso normal para players random del seed)
        Player player = buildMinimalPlayer();

        PlayerEntity entity = PlayerEntity.fromDomain(player);

        assertNull(entity.getHeightCm(), "height null en domain → null en entity");
        assertNull(entity.getSkillLevelsJson(),
                "skillLevels vacio en domain → null en entity (no string vacia ni 'null' literal)");
    }

    @Test
    void roundTrip_preservesHeightAndSkills() {
        Player original = buildPlayerWithHeightAndSkills();

        PlayerEntity entity = PlayerEntity.fromDomain(original);
        Player restored = entity.toDomain();

        assertEquals(original.getHeightCm(), restored.getHeightCm(),
                "Round-trip debe preservar heightCm");
        assertEquals(original.getSkillLevel(PlayerSkill.SHOOTER),
                restored.getSkillLevel(PlayerSkill.SHOOTER),
                "Round-trip debe preservar SHOOTER level");
        assertEquals(original.getSkillLevel(PlayerSkill.DRIBBLER),
                restored.getSkillLevel(PlayerSkill.DRIBBLER),
                "Round-trip debe preservar DRIBBLER level");
        assertEquals(original.getSkillLevel(PlayerSkill.SPEEDSTER),
                restored.getSkillLevel(PlayerSkill.SPEEDSTER),
                "Round-trip debe preservar SPEEDSTER level");
        // Skills no seteados → 0 (sparse)
        assertEquals(0, restored.getSkillLevel(PlayerSkill.MARKER),
                "Skills no seteados deben ser 0 despues del round-trip");
    }

    @Test
    void toDomain_backwardCompat_nullColumns_yieldNullHeightEmptySkills() {
        // V25D32-F2: pre-V25D32 rows en Postgres tienen height=null y skillLevelsJson=null.
        // El engine en V25D33 usara defaults si height es null o skills es empty.
        PlayerEntity legacy = new PlayerEntity(
            UUID.randomUUID(), "Legacy Player", 30, "CM",
            70, 70, 70, 70, 70, 70,
            BigDecimal.valueOf(1_000_000L), 100, false,
            Instant.now(), Instant.now(),
            null,   // heightCm: null (pre-V25D32)
            null    // skillLevelsJson: null (pre-V25D32)
        );

        Player restored = legacy.toDomain();

        assertNull(restored.getHeightCm(),
                "Legacy row sin height → Player.getHeightCm() retorna null");
        assertTrue(restored.getSkillLevels().isEmpty(),
                "Legacy row sin skills → Player.getSkillLevels() retorna map vacio");
        assertEquals(0, restored.getSkillLevel(PlayerSkill.SHOOTER),
                "Legacy row sin skills → SHOOTER level es 0");
    }

    @Test
    void toDomain_emptyJsonString_yieldsEmptySkills() {
        // Edge case: skillLevelsJson = "" (string vacia, no null)
        PlayerEntity edge = new PlayerEntity(
            UUID.randomUUID(), "Edge Player", 25, "CF",
            80, 60, 85, 90, 80, 75,
            BigDecimal.valueOf(50_000_000L), 100, false,
            Instant.now(), Instant.now(),
            185, ""
        );

        Player restored = edge.toDomain();

        assertEquals(185, restored.getHeightCm());
        assertTrue(restored.getSkillLevels().isEmpty());
    }

    @Test
    void toDomain_malformedJson_throwsIllegalState() {
        PlayerEntity broken = new PlayerEntity(
            UUID.randomUUID(), "Broken", 25, "CF",
            80, 60, 85, 90, 80, 75,
            BigDecimal.ZERO, 100, false,
            Instant.now(), Instant.now(),
            180, "{this is not valid JSON"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                broken::toDomain,
                "toDomain debe fallar loud si el JSON es malformed (no swallow)");
        assertTrue(ex.getMessage().contains("Failed to deserialize"),
                "El mensaje debe mencionar el deserialize failure: " + ex.getMessage());
    }

    @Test
    void getSkillLevels_isUnmodifiable() {
        // Convenience getter: read-only view del map deserializado.
        PlayerEntity entity = PlayerEntity.fromDomain(buildPlayerWithHeightAndSkills());
        Map<PlayerSkill, Integer> view = entity.getSkillLevels();
        assertEquals(88, view.get(PlayerSkill.SHOOTER));
        assertThrows(UnsupportedOperationException.class,
                () -> view.put(PlayerSkill.SHOOTER, 99),
                "getSkillLevels() debe retornar view read-only");
    }

    // ========== Test fixtures ==========

    private Player buildPlayerWithHeightAndSkills() {
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        skills.put(PlayerSkill.SHOOTER, 88);
        skills.put(PlayerSkill.DRIBBLER, 75);
        skills.put(PlayerSkill.SPEEDSTER, 92);

        PlayerAttributes attrs = PlayerAttributes.of(94, 40, 89, 96, 82, 80);
        return Player.create(
            PlayerId.of(UUID.randomUUID()),
            "Mbappe", 25, Player.Position.ST,
            attrs, BigDecimal.valueOf(180_000_000L),
            178, skills
        );
    }

    private Player buildMinimalPlayer() {
        PlayerAttributes attrs = PlayerAttributes.of(70, 70, 70, 70, 70, 70);
        return Player.create(
            PlayerId.of(UUID.randomUUID()),
            "Generic Player", 25, Player.Position.CM,
            attrs, BigDecimal.valueOf(1_000_000L)
            // height null, skills vacio
        );
    }
}

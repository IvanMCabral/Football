package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D31: SessionPlayer - height + skills metadata tests.
 * Cubre: bounds-check de skill levels [0,99], bounds-check de height [160,210],
 * getters/setters, sparse map behavior, Jackson round-trip.
 */
class SessionPlayerSkillsTest {

    @Test
    void newSessionPlayer_skillLevelsEmpty() {
        SessionPlayer p = new SessionPlayer();
        assertNotNull(p.getSkillLevels(), "skillLevels map debe estar inicializado");
        assertTrue(p.getSkillLevels().isEmpty(), "skillLevels map debe estar vacio por default");
        for (PlayerSkill s : PlayerSkill.values()) {
            assertEquals(0, p.getSkillLevel(s),
                    "Skill " + s + " debe ser 0 en SessionPlayer nuevo");
        }
    }

    @Test
    void setSkillLevel_persistsInMap() {
        SessionPlayer p = new SessionPlayer();
        p.setSkillLevel(PlayerSkill.SHOOTER, 85);
        p.setSkillLevel(PlayerSkill.PASSER, 70);

        assertEquals(85, p.getSkillLevel(PlayerSkill.SHOOTER));
        assertEquals(70, p.getSkillLevel(PlayerSkill.PASSER));
        assertEquals(2, p.getSkillLevels().size());
    }

    @Test
    void setSkillLevel_clampsTo0to99() {
        SessionPlayer p = new SessionPlayer();
        // Los limites exactos (0 y 99) son validos — no clamping per se, los aceptamos
        assertDoesNotThrow(() -> p.setSkillLevel(PlayerSkill.MARKER, 0));
        assertDoesNotThrow(() -> p.setSkillLevel(PlayerSkill.TACKLER, 99));
        assertEquals(99, p.getSkillLevel(PlayerSkill.TACKLER));
    }

    @Test
    void setSkillLevel_over99_throwsIAE() {
        SessionPlayer p = new SessionPlayer();
        assertThrows(IllegalArgumentException.class,
                () -> p.setSkillLevel(PlayerSkill.SHOOTER, 100));
    }

    @Test
    void setSkillLevel_negative_throwsIAE() {
        SessionPlayer p = new SessionPlayer();
        assertThrows(IllegalArgumentException.class,
                () -> p.setSkillLevel(PlayerSkill.SHOOTER, -1));
    }

    @Test
    void getSkillLevel_absentSkill_returns0() {
        SessionPlayer p = new SessionPlayer();
        p.setSkillLevel(PlayerSkill.SHOOTER, 50);
        // DRIBBLER nunca se setea
        assertEquals(0, p.getSkillLevel(PlayerSkill.DRIBBLER));
    }

    @Test
    void setHeightCm_persists() {
        SessionPlayer p = new SessionPlayer();
        p.setHeightCm(180);
        assertEquals(180, p.getHeightCm());

        // Limites
        SessionPlayer p2 = new SessionPlayer();
        p2.setHeightCm(160);
        assertEquals(160, p2.getHeightCm());

        SessionPlayer p3 = new SessionPlayer();
        p3.setHeightCm(210);
        assertEquals(210, p3.getHeightCm());

        // null es valido (no seteado)
        SessionPlayer p4 = new SessionPlayer();
        p4.setHeightCm(null);
        assertNull(p4.getHeightCm());
    }

    @Test
    void setHeightCm_negativeOrOver210_throwsIAE() {
        SessionPlayer p = new SessionPlayer();
        assertThrows(IllegalArgumentException.class, () -> p.setHeightCm(159));
        assertThrows(IllegalArgumentException.class, () -> p.setHeightCm(211));
        assertThrows(IllegalArgumentException.class, () -> p.setHeightCm(-1));
        assertThrows(IllegalArgumentException.class, () -> p.setHeightCm(0));
        assertThrows(IllegalArgumentException.class, () -> p.setHeightCm(1000));
    }

    @Test
    void serialization_roundTrip() throws Exception {
        SessionPlayer original = new SessionPlayer();
        original.setSessionPlayerId(UUID.randomUUID().toString());
        original.setWorldPlayerId("wp-skill-rt");
        original.setName("Skill Round Trip");
        original.setPosition("ATT");
        original.setAge(27);
        original.setHeightCm(182);
        original.setSkillLevel(PlayerSkill.SHOOTER, 88);
        original.setSkillLevel(PlayerSkill.DRIBBLER, 75);
        original.setSkillLevel(PlayerSkill.PASSER, 60);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("\"skillLevels\""), "JSON debe contener campo skillLevels");

        SessionPlayer restored = mapper.readValue(json, SessionPlayer.class);
        assertEquals(original.getSessionPlayerId(), restored.getSessionPlayerId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getHeightCm(), restored.getHeightCm());
        assertEquals(original.getSkillLevel(PlayerSkill.SHOOTER),
                restored.getSkillLevel(PlayerSkill.SHOOTER));
        assertEquals(original.getSkillLevel(PlayerSkill.DRIBBLER),
                restored.getSkillLevel(PlayerSkill.DRIBBLER));
        assertEquals(original.getSkillLevel(PlayerSkill.PASSER),
                restored.getSkillLevel(PlayerSkill.PASSER));
        // Skills no seteados
        assertEquals(0, restored.getSkillLevel(PlayerSkill.MARKER));
        assertEquals(0, restored.getSkillLevel(PlayerSkill.WALL));
    }
}

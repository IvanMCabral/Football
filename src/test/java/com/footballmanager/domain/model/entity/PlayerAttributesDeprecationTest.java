package com.footballmanager.domain.model.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D32: PlayerAttributes deprecation regression test.
 *
 * <p>Verifica que la deprecation de V25D31-F1 (heightCm + skillLevels duplicados
 * en PlayerAttributes) esta completa. Source of truth unico: {@link Player}.
 *
 * <p>Este test es deliberadamente reflectivo porque la deprecation es de SHAPE:
 * los campos/metodos no deben existir. Si alguien los re-introduce por copy-paste
 * de V25D31, este test lo flaggea.
 */
class PlayerAttributesDeprecationTest {

    @Test
    void noHeightCmField() {
        for (Field f : PlayerAttributes.class.getDeclaredFields()) {
            assertNotEquals("heightCm", f.getName(),
                    "PlayerAttributes no debe tener field 'heightCm' (V25D32 deprecation). "
                    + "El field vive en Player.");
        }
    }

    @Test
    void noSkillLevelsField() {
        for (Field f : PlayerAttributes.class.getDeclaredFields()) {
            assertNotEquals("skillLevels", f.getName(),
                    "PlayerAttributes no debe tener field 'skillLevels' (V25D32 deprecation). "
                    + "El field vive en Player.");
        }
    }

    @Test
    void noHeightOrSkillGettersOrSetters() {
        String[] forbiddenMethods = {
            "getHeightCm", "setHeightCm",
            "getSkillLevels", "getSkillLevel", "setSkillLevel"
        };
        for (Method m : PlayerAttributes.class.getDeclaredMethods()) {
            for (String forbidden : forbiddenMethods) {
                assertNotEquals(forbidden, m.getName(),
                        "PlayerAttributes no debe exponer metodo '" + forbidden
                        + "' (V25D32 deprecation). Acceder via Player.getHeightCm / Player.getSkillLevel.");
            }
        }
    }

    @Test
    void noWithHeightAndSkillsFactory() throws NoSuchMethodException {
        // factory was a static method 'withHeightAndSkills' with 8 args
        // after deprecation, such a method must not exist
        for (Method m : PlayerAttributes.class.getDeclaredMethods()) {
            assertNotEquals("withHeightAndSkills", m.getName(),
                    "PlayerAttributes no debe tener factory 'withHeightAndSkills' (V25D32 deprecation).");
        }
    }

    @Test
    void has6BaseStatsOnly() {
        // The 6 stat fields must still exist (we didn't accidentally remove them).
        String[] expectedFields = {"attack", "defense", "technique", "speed", "stamina", "mentality"};
        for (String expected : expectedFields) {
            boolean found = false;
            for (Field f : PlayerAttributes.class.getDeclaredFields()) {
                if (f.getName().equals(expected)) { found = true; break; }
            }
            assertTrue(found, "PlayerAttributes debe conservar field '" + expected + "'");
        }
        // Exactly 6 fields (no surprises — no leaked helper fields).
        int fieldCount = PlayerAttributes.class.getDeclaredFields().length;
        assertEquals(6, fieldCount,
                "PlayerAttributes debe tener EXACTAMENTE 6 fields (las 6 stats base). Got: "
                + fieldCount);
    }

    @Test
    void calculateOverall_stillWorks() {
        PlayerAttributes attrs = PlayerAttributes.of(80, 70, 90, 85, 75, 88);
        int overall = attrs.calculateOverall();
        int expected = (80 + 70 + 90 + 85 + 75 + 88) / 6; // = 81
        assertEquals(expected, overall);
    }

    @Test
    void constructorAndFactory_stillWork() {
        PlayerAttributes ctor = new PlayerAttributes(60, 60, 60, 60, 60, 60);
        assertEquals(60, ctor.getAttack());
        assertEquals(60, ctor.getMentality());

        PlayerAttributes factory = PlayerAttributes.of(70, 70, 70, 70, 70, 70);
        assertEquals(70, factory.calculateOverall());
    }

    @Test
    void noHeightOrSkillBoundsConstants() {
        // Bounds constants from V25D31 (MIN_HEIGHT_CM, MAX_HEIGHT_CM, MIN_SKILL_LEVEL,
        // MAX_SKILL_LEVEL) were only used by the deprecated setters — they must be gone.
        String[] forbiddenConstants = {
            "MIN_HEIGHT_CM", "MAX_HEIGHT_CM", "MIN_SKILL_LEVEL", "MAX_SKILL_LEVEL"
        };
        for (Field f : PlayerAttributes.class.getDeclaredFields()) {
            for (String forbidden : forbiddenConstants) {
                assertNotEquals(forbidden, f.getName(),
                        "PlayerAttributes no debe tener constante '" + forbidden
                        + "' (V25D32 deprecation — los bounds viven en Player).");
            }
        }
    }
}

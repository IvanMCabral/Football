package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D32-F3: PlayerAttributesGenerator tests.
 *
 * <p>Verifica el generador deterministico de heights + skill defaults.
 * Reproducibilidad: mismo seed → mismo resultado.
 */
class PlayerAttributesGeneratorTest {

    @Test
    void defaultConstructor_usesV25D32Seed() {
        PlayerAttributesGenerator g1 = new PlayerAttributesGenerator();
        PlayerAttributesGenerator g2 = new PlayerAttributesGenerator();
        // Mismo seed por default → mismo primer height
        assertEquals(g1.generateHeightCm(), g2.generateHeightCm(),
                "Default constructor debe usar el mismo seed (20240624) → mismo primer height");
    }

    @Test
    void explicitSeed_yieldsReproducibleSequence() {
        PlayerAttributesGenerator g1 = new PlayerAttributesGenerator(42L);
        PlayerAttributesGenerator g2 = new PlayerAttributesGenerator(42L);
        // Mismo seed → misma secuencia
        for (int i = 0; i < 10; i++) {
            assertEquals(g1.generateHeightCm(), g2.generateHeightCm(),
                    "Mismo seed debe dar misma secuencia (iter " + i + ")");
        }
    }

    @Test
    void differentSeeds_yieldDifferentSequences() {
        PlayerAttributesGenerator g1 = new PlayerAttributesGenerator(1L);
        PlayerAttributesGenerator g2 = new PlayerAttributesGenerator(2L);
        int h1 = g1.generateHeightCm();
        int h2 = g2.generateHeightCm();
        // No es garantizado que sean distintos en la primera iteracion (probabilidad
        // muy baja ~3% dado que la stddev es 7 en [160, 210]). Probamos 100 para
        // asegurar al menos 1 diferencia.
        boolean foundDiff = (h1 != h2);
        for (int i = 0; i < 100 && !foundDiff; i++) {
            foundDiff = (g1.generateHeightCm() != g2.generateHeightCm());
        }
        assertTrue(foundDiff, "Seeds distintos deben producir secuencias distintas (eventualmente)");
    }

    @Test
    void generateHeightCm_respectsBounds() {
        PlayerAttributesGenerator g = new PlayerAttributesGenerator();
        for (int i = 0; i < 1000; i++) {
            int h = g.generateHeightCm();
            assertTrue(h >= PlayerAttributesGenerator.MIN_HEIGHT_CM,
                    "Height " + h + " (iter " + i + ") < MIN " + PlayerAttributesGenerator.MIN_HEIGHT_CM);
            assertTrue(h <= PlayerAttributesGenerator.MAX_HEIGHT_CM,
                    "Height " + h + " (iter " + i + ") > MAX " + PlayerAttributesGenerator.MAX_HEIGHT_CM);
        }
    }

    @Test
    void generateDefaultSkillLevels_isEmpty() {
        PlayerAttributesGenerator g = new PlayerAttributesGenerator();
        Map<PlayerSkill, Integer> skills = g.generateDefaultSkillLevels();
        assertNotNull(skills, "generateDefaultSkillLevels debe retornar map no-null");
        assertTrue(skills.isEmpty(),
                "generateDefaultSkillLevels retorna map vacio (V25D32 no infla con skills random — "
                + "eso seria ruido en el smoke canonico, prompt de V25D32 explicito)");
    }

    @Test
    void oneShotHelpers_useDistinctSeeds() {
        // generateHeightCmOneShot() sin args usa DEFAULT_SEED.
        // generateHeightCmOneShot(seed) usa el seed pasado.
        // Distintos calls a oneShot() con el mismo default seed dan MISMO resultado
        // (idempotente).
        int h1 = PlayerAttributesGenerator.generateHeightCmOneShot();
        int h2 = PlayerAttributesGenerator.generateHeightCmOneShot();
        assertEquals(h1, h2, "oneShot() sin args usa DEFAULT_SEED — debe ser idempotente");

        int h3 = PlayerAttributesGenerator.generateHeightCmOneShot(999L);
        int h4 = PlayerAttributesGenerator.generateHeightCmOneShot(999L);
        assertEquals(h3, h4, "oneShot(seed) con mismo seed debe ser idempotente");
    }
}

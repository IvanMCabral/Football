package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * V25D32-F3: Generador de height + skill levels para players del seed que no
 * tienen datos hardcoded en el JSON.
 *
 * <p><b>Por que existe:</b> el JSON {@code laliga-2024-25.json} tiene heightCm
 * y skillLevels solo para el top-5 (curated) + heightCm para los restantes del
 * top-20. Los otros ~386 players del seed no tienen height/skills en el JSON —
 * necesitamos generarlos de forma realista y deterministica (mismo seed → mismo
 * resultado, para tests reproducibles y para que la persistencia en Postgres
 * sea idempotente en cuanto a datos generados).
 *
 * <p><b>Algoritmo de height:</b> distribucion normal truncada en [160, 210] cm,
 * media 178, stddev 7. Reproducible via {@link #withSeed(long)}. Default seed
 * = 20240624 (fecha del sprint).
 *
 * <p><b>Por que no random skills para los 386 restantes:</b> el prompt de V25D32
 * lo desaconseja explicitamente — "introduciria ruido en el smoke canonico".
 * Engine en V25D33 aplica defaults si skillLevels es empty. Por eso este
 * generador SOLO produce heights, no skills.
 */
public final class PlayerAttributesGenerator {

    /** Height bounds (deben matchear con Player.setHeightCm domain validation). */
    public static final int MIN_HEIGHT_CM = 160;
    public static final int MAX_HEIGHT_CM = 210;
    public static final int HEIGHT_MEAN_CM = 178;
    public static final int HEIGHT_STDDEV_CM = 7;
    public static final long DEFAULT_SEED = 20240624L;

    private final Random random;

    public PlayerAttributesGenerator() {
        this(DEFAULT_SEED);
    }

    public PlayerAttributesGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Genera una altura aleatoria (distribucion normal truncada en [160, 210]).
     * Determinista para un seed dado.
     */
    public int generateHeightCm() {
        // Box-Muller transform para distribucion normal
        double u1 = Math.max(Double.MIN_VALUE, random.nextDouble());
        double u2 = random.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        int height = (int) Math.round(HEIGHT_MEAN_CM + z * HEIGHT_STDDEV_CM);
        // Truncar a los bounds
        if (height < MIN_HEIGHT_CM) height = MIN_HEIGHT_CM;
        if (height > MAX_HEIGHT_CM) height = MAX_HEIGHT_CM;
        return height;
    }

    /**
     * Para los 386 players no-top-20, devuelve un map de skills vacio. Engine
     * en V25D33 aplicara defaults. Mantenemos este metodo explicito en vez de
     * hacer que la call-site retorne Map.of() directamente, para que la
     * "intencion" quede documentada.
     */
    public Map<PlayerSkill, Integer> generateDefaultSkillLevels() {
        return Map.of();
    }

    /**
     * Generador one-shot para tests / ad-hoc queries. NO usar en el seed path
     * (usar la instancia con seed fijo para reproducibilidad).
     */
    public static int generateHeightCmOneShot() {
        return new PlayerAttributesGenerator().generateHeightCm();
    }

    /**
     * Test helper: generador one-shot con un seed explicito. NO usar en el
     * seed path.
     */
    public static int generateHeightCmOneShot(long seed) {
        return new PlayerAttributesGenerator(seed).generateHeightCm();
    }
}

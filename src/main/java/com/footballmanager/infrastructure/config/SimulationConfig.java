package com.footballmanager.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Random;

/**
 * Configuración de componentes para el motor de simulación.
 *
 * <p>V24D20-TESTHARNESS: la semilla del {@code Random} ahora es
 * configurable vía property {@code app.simulation.random-seed}.
 * <ul>
 *   <li>{@code 0} (default) → {@code new Random()} no determinístico,
 *       mismo comportamiento que antes en dev/prod.</li>
 *   <li>Cualquier otro valor (ej. {@code 42}) → {@code new Random(seed)}
 *       determinístico, útil para reproducibilidad de tests E2E y
 *       smokes Bloque B (mismo rival × N corridas).</li>
 * </ul>
 * <p>El profile {@code test} setea explícitamente {@code 42} vía
 * {@code application-test.yml} para preservar el comportamiento de los
 * tests existentes que ya asumen esa semilla.
 */
@Configuration("infrastructureSimulationConfig")
public class SimulationConfig {

    /**
     * Semilla configurable — 0 significa "sin semilla" (random real).
     * Default 0 para mantener comportamiento histórico en dev/prod.
     */
    @Value("${app.simulation.random-seed:0}")
    private long randomSeed;

    /**
     * Bean de Random para producción / dev / local.
     * Si randomSeed == 0 → {@code new Random()} (no determinístico).
     * Si randomSeed != 0 → {@code new Random(seed)} (reproducible).
     */
    @Bean
    @Profile("!test")
    public Random randomProduction() {
        return randomSeed == 0L ? new Random() : new Random(randomSeed);
    }

    /**
     * Bean de Random para testing.
     * Usa la semilla de la property (default 42L en application-test.yml)
     * para tests determinísticos y reproducibles.
     */
    @Bean
    @Profile("test")
    public Random randomTest() {
        return randomSeed == 0L ? new Random(42L) : new Random(randomSeed);
    }
}

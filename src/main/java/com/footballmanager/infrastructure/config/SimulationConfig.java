package com.footballmanager.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Random;

/**
 * Configuración de componentes para el motor de simulación.
 */
@Configuration
public class SimulationConfig {
    
    /**
     * Bean de Random para producción.
     * Usa semilla aleatoria para comportamiento no determinístico real.
     */
    @Bean
    @Profile("!test")
    public Random randomProduction() {
        return new Random();
    }
    
    /**
     * Bean de Random para testing.
     * Usa semilla fija para pruebas determinísticas y reproducibles.
     */
    @Bean
    @Profile("test")
    public Random randomTest() {
        return new Random(42L); // Semilla fija para tests determinísticos
    }
}

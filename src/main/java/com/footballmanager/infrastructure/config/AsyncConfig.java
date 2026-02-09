package com.footballmanager.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita procesamiento asíncrono para operaciones como persistencia de resultados.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Configuración por defecto de Spring para @Async
}

package com.footballmanager.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorsConfigTest {

    @Test
    void onlyConfiguredOriginsAreAllowed() {
        CorsConfig corsConfig = new CorsConfig(
            "http://localhost:4200,https://staging.manager.example,https://manager.example");

        assertTrue(corsConfig.isAllowedOrigin("http://localhost:4200"));
        assertTrue(corsConfig.isAllowedOrigin("https://staging.manager.example"));
        assertTrue(corsConfig.isAllowedOrigin("https://manager.example"));
        assertFalse(corsConfig.isAllowedOrigin("https://attacker.example"));
        assertFalse(corsConfig.isAllowedOrigin(null));
    }

    @Test
    void wildcardIsNotImplicitlyAllowed() {
        CorsConfig corsConfig = new CorsConfig("");

        assertFalse(corsConfig.isAllowedOrigin("http://localhost:4200"));
        assertFalse(corsConfig.allowedOrigins().contains("*"));
    }

    @Test
    void requestCorrelationHeaderIsAllowedForTheInstrumentedStartRequest() {
        CorsConfig corsConfig = new CorsConfig("https://manager-4f952.web.app");

        assertTrue(corsConfig.allowedHeaders().contains("X-Request-Id"));
    }
}

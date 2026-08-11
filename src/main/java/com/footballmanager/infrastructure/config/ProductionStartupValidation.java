package com.footballmanager.infrastructure.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ProductionStartupValidation {

    private static final Set<String> INSECURE_VALUES = Set.of(
        "",
        "postgres",
        "admin",
        "password",
        "secret",
        "change-me",
        "change-me-please-use-openssl-rand-base64-64",
        "default-secret-key-please-change-in-production-this-should-be-at-least-512-bits-long"
    );

    private final Environment environment;
    private final List<String> requiredVariables;

    public ProductionStartupValidation(
            Environment environment,
            @Value("${app.security.production.required-variables:}") String requiredVariables) {
        this.environment = environment;
        this.requiredVariables = parseRequiredVariables(requiredVariables);
    }

    @PostConstruct
    public void validate() {
        if (!isProdProfileActive()) {
            return;
        }

        List<String> missingOrUnsafe = requiredVariables.stream()
            .filter(this::isMissingOrUnsafe)
            .toList();

        if (!missingOrUnsafe.isEmpty()) {
            throw new IllegalStateException(
                "Production startup blocked. Missing or unsafe environment variables: "
                    + String.join(", ", missingOrUnsafe));
        }

        validateJwt();
        validateCors();
        validatePositiveLong("JWT_EXPIRATION", 60_000L, 86_400_000L);
        validatePositiveLong("JWT_REFRESH_EXPIRATION", 60_000L, 2_592_000_000L);
        validatePositiveInt("DB_PORT");
        validatePositiveInt("REDIS_PORT");
        validateLifecycleCoordinationMode();
        validateRedisTtl("REDIS_WORLD_TTL", "app.redis.world-ttl", Duration.ofHours(1), Duration.ofDays(90));
        validateRedisTtl("REDIS_WORLD_CATALOG_TTL", "app.redis.world-catalog-ttl", Duration.ofDays(1), Duration.ofDays(365));
        validateRedisTtl("REDIS_MATCH_DETAIL_TTL", "app.redis.match-detail-ttl", Duration.ofDays(1), Duration.ofDays(90));
    }

    private void validateLifecycleCoordinationMode() {
        String mode = environment.getProperty("APP_REDIS_LIFECYCLE_MODE",
                environment.getProperty("app.redis.lifecycle-mode", "single-instance"));
        if (!"single-instance".equalsIgnoreCase(mode.trim())) {
            throw new IllegalStateException(
                    "Production startup blocked. Redis lifecycle mode requires a distributed lock: " + mode);
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private boolean isMissingOrUnsafe(String name) {
        String value = configuredValue(name);
        if (value == null || value.isBlank()) {
            return true;
        }
        return INSECURE_VALUES.contains(value.trim());
    }

    private void validateJwt() {
        String jwtSecret = configuredValue("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isBlank() || !jwtSecret.equals(jwtSecret.trim())) {
            throw new IllegalStateException("Production startup blocked. JWT_SECRET must be a trimmed non-empty value");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException("Production startup blocked. JWT_SECRET must be at least 64 UTF-8 bytes");
        }
    }

    private void validateCors() {
        String rawOrigins = configuredValue("APP_CORS_ALLOWED_ORIGINS");
        if (rawOrigins == null || rawOrigins.isBlank()) {
            throw new IllegalStateException("Production startup blocked. APP_CORS_ALLOWED_ORIGINS is required");
        }
        for (String origin : rawOrigins.split(",")) {
            validateOrigin(origin.trim());
        }
    }

    private void validateOrigin(String origin) {
        if (origin.isBlank()
                || "null".equalsIgnoreCase(origin)
                || origin.contains("*")
                || origin.endsWith("/")) {
            throw new IllegalStateException("Production startup blocked. Invalid CORS origin configured");
        }
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getPath() != null && !uri.getPath().isBlank()
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalStateException("Production startup blocked. Invalid CORS origin configured");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Production startup blocked. Invalid CORS origin configured");
        }
    }

    private void validatePositiveLong(String name, long min, long max) {
        String raw = configuredValue(name);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < min || value > max) {
                throw new IllegalStateException("Production startup blocked. Invalid duration: " + name);
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Production startup blocked. Invalid duration: " + name);
        }
    }

    private void validatePositiveInt(String name) {
        String raw = configuredValue(name);
        try {
            if (raw == null || raw.isBlank() || Integer.parseInt(raw.trim()) <= 0) {
                throw new IllegalStateException("Production startup blocked. Invalid numeric variable: " + name);
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Production startup blocked. Invalid numeric variable: " + name);
        }
    }

    private void validateRedisTtl(String environmentName, String propertyName, Duration min, Duration max) {
        String raw = environment.getProperty(environmentName);
        if (raw == null || raw.isBlank()) {
            raw = environment.getProperty(propertyName, "30d");
        }
        try {
            Duration ttl = org.springframework.boot.convert.DurationStyle.detectAndParse(raw.trim());
            if (ttl.compareTo(min) < 0 || ttl.compareTo(max) > 0) {
                throw new IllegalStateException("Production startup blocked. Invalid Redis retention: " + environmentName);
            }
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException && e.getMessage() != null
                    && e.getMessage().contains("Invalid Redis retention")) {
                throw e;
            }
            throw new IllegalStateException("Production startup blocked. Invalid Redis retention: " + environmentName);
        }
    }

    private String configuredValue(String name) {
        String directValue = environment.getProperty(name);
        if (directValue != null) {
            return directValue;
        }
        String canonicalValue = environment.getProperty(name.toLowerCase().replace('_', '.'));
        if (canonicalValue != null) {
            return canonicalValue;
        }
        String placeholder = environment.resolvePlaceholders("${" + name + ":}");
        return placeholder.isBlank() ? null : placeholder;
    }

    private static List<String> parseRequiredVariables(String rawVariables) {
        if (rawVariables == null || rawVariables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawVariables.split(","))
            .map(String::trim)
            .filter(variable -> !variable.isBlank())
            .distinct()
            .toList();
    }
}

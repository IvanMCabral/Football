package com.footballmanager.infrastructure.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
    }

    private boolean isProdProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private boolean isMissingOrUnsafe(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            return true;
        }
        return INSECURE_VALUES.contains(value.trim());
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

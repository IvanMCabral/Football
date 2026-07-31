package com.footballmanager.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionStartupValidationTest {

    @Test
    void prodProfileFailsFastWhenRequiredSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.profiles.active", "prod")
            .withProperty("DB_HOST", "db.example.com")
            .withProperty("DB_PORT", "5432")
            .withProperty("DB_NAME", "manager")
            .withProperty("DB_USER", "manager")
            .withProperty("DB_PASSWORD", "safe-db-password")
            .withProperty("REDIS_HOST", "redis.example.com")
            .withProperty("REDIS_PORT", "6379")
            .withProperty("REDIS_PASSWORD", "safe-redis-password")
            .withProperty("APP_CORS_ALLOWED_ORIGINS", "https://beta.example.com");
        environment.setActiveProfiles("prod");

        ProductionStartupValidation validation = new ProductionStartupValidation(
            environment,
            "DB_HOST,DB_PORT,DB_NAME,DB_USER,DB_PASSWORD,REDIS_HOST,REDIS_PORT,REDIS_PASSWORD,JWT_SECRET,APP_CORS_ALLOWED_ORIGINS");

        assertThrows(IllegalStateException.class, validation::validate);
    }

    @Test
    void prodProfileRejectsKnownUnsafeSecretValues() {
        MockEnvironment environment = completeProdEnvironment()
            .withProperty("DB_PASSWORD", "postgres");
        environment.setActiveProfiles("prod");

        ProductionStartupValidation validation = new ProductionStartupValidation(
            environment,
            "DB_HOST,DB_PORT,DB_NAME,DB_USER,DB_PASSWORD,REDIS_HOST,REDIS_PORT,REDIS_PASSWORD,JWT_SECRET,APP_CORS_ALLOWED_ORIGINS");

        assertThrows(IllegalStateException.class, validation::validate);
    }

    @Test
    void prodProfileStartsWhenAllRequiredValuesArePresentAndSafe() {
        MockEnvironment environment = completeProdEnvironment();
        environment.setActiveProfiles("prod");

        ProductionStartupValidation validation = new ProductionStartupValidation(
            environment,
            "DB_HOST,DB_PORT,DB_NAME,DB_USER,DB_PASSWORD,REDIS_HOST,REDIS_PORT,REDIS_PASSWORD,JWT_SECRET,APP_CORS_ALLOWED_ORIGINS");

        assertDoesNotThrow(validation::validate);
    }

    @Test
    void nonProdProfileDoesNotRequireProductionSecrets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        ProductionStartupValidation validation = new ProductionStartupValidation(
            environment,
            "DB_HOST,DB_PORT,DB_NAME,DB_USER,DB_PASSWORD,REDIS_HOST,REDIS_PORT,REDIS_PASSWORD,JWT_SECRET,APP_CORS_ALLOWED_ORIGINS");

        assertDoesNotThrow(validation::validate);
    }

    private static MockEnvironment completeProdEnvironment() {
        return new MockEnvironment()
            .withProperty("DB_HOST", "db.example.com")
            .withProperty("DB_PORT", "5432")
            .withProperty("DB_NAME", "manager")
            .withProperty("DB_USER", "manager")
            .withProperty("DB_PASSWORD", "safe-db-password")
            .withProperty("REDIS_HOST", "redis.example.com")
            .withProperty("REDIS_PORT", "6379")
            .withProperty("REDIS_PASSWORD", "safe-redis-password")
            .withProperty("JWT_SECRET", "safe-jwt-secret-with-enough-entropy-for-prod")
            .withProperty("APP_CORS_ALLOWED_ORIGINS", "https://beta.example.com");
    }
}

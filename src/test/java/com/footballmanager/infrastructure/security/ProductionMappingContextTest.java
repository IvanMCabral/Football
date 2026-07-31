package com.footballmanager.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.flyway.enabled=false",
        "DB_HOST=localhost",
        "DB_PORT=5432",
        "DB_NAME=football_manager_test",
        "DB_USER=manager_test",
        "DB_PASSWORD=prod-safe-test-db-password",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "REDIS_PASSWORD=prod-safe-test-redis-password",
        "JWT_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "APP_CORS_ALLOWED_ORIGINS=https://beta.example.com"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("prod")
class ProductionMappingContextTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void productionContextDoesNotRegisterToolingOrSensitiveMutatorMappings() {
        Set<String> mappings = handlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternsCondition().getPatterns().stream())
            .map(Object::toString)
            .collect(Collectors.toSet());

        assertThat(mappings).noneMatch(pattern -> pattern.startsWith("/api/v1/editor"));
        assertThat(mappings).doesNotContain(
            "/api/v1/world/leagues/{leagueId}/add-team",
            "/api/v1/world/leagues/{leagueId}/remove-team/{teamId}",
            "/api/v1/admin/world/seed/laliga",
            "/api/v1/test-harness/career/set-formation");
        assertThat(mappings).contains(
            "/api/v1/auth/login",
            "/api/v1/health",
            "/api/v1/career/lineup/current");
    }
}

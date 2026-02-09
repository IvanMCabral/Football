package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.domain.port.in.fixture.MigrateFixturesUseCase;
import com.footballmanager.domain.port.in.fixture.RegenerateFixturesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * CareerAdminController - Endpoints administrativos/técnicos.
 * Base path: /api/v1/career/admin
 *
 * Responsabilidad: Solo HTTP + auth + delegación.
 * La lógica de negocio vive en los UseCases.
 */
@RestController
@RequestMapping("/api/v1/career/admin")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class CareerAdminController {

    private final MigrateFixturesUseCase migrateFixturesUseCase;
    private final RegenerateFixturesUseCase regenerateFixturesUseCase;

    /**
     * POST /api/v1/career/admin/migrate
     * Delega a MigrateFixturesUseCase
     */
    @PostMapping("/migrate")
    public Mono<Map<String, Object>> migrateFixtures(Authentication authentication) {
        String userId = getUserIdFromAuth(authentication);

        return migrateFixturesUseCase.migrate(userId)
                .<Map<String, Object>>map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "success");
                    response.put("message", "Fixtures migrated successfully");
                    return response;
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("status", "error");
                    error.put("error", e.getMessage());
                    return Mono.just(error);
                });
    }

    /**
     * POST /api/v1/career/admin/regenerate
     * Delega a RegenerateFixturesUseCase
     */
    @PostMapping("/regenerate")
    public Mono<Map<String, Object>> regenerateFixtures(Authentication authentication) {
        String userId = getUserIdFromAuth(authentication);

        return regenerateFixturesUseCase.regenerate(java.util.UUID.fromString(userId))
                .<Map<String, Object>>map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Fixtures regenerated successfully");
                    return response;
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    return Mono.just(error);
                });
    }

    private String getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized: no user id in authentication");
        }
        return authentication.getName();
    }
}

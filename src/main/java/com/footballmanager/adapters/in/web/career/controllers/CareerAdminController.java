package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.domain.port.in.fixture.MigrateFixturesUseCase;
import com.footballmanager.domain.port.in.fixture.RegenerateFixturesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CareerAdminController - Endpoints administrativos/tÃ©cnicos.
 * Base path: /api/v1/career/admin
 *
 * Responsabilidad: Solo HTTP + auth + delegaciÃ³n.
 * La lÃ³gica de negocio vive en los UseCases.
 */
@RestController
@RequestMapping("/api/v1/career/admin")
@Profile("!prod")
@RequiredArgsConstructor
public class CareerAdminController {

    private final MigrateFixturesUseCase migrateFixturesUseCase;
    private final RegenerateFixturesUseCase regenerateFixturesUseCase;
    private final ControllerHelper controllerHelper;

    /**
     * POST /api/v1/career/admin/migrate
     * Delega a MigrateFixturesUseCase
     */
    @PostMapping("/migrate")
    public Mono<Map<String, Object>> migrateFixtures(Authentication authentication) {
        String userId = controllerHelper.getUserId(authentication).toString();

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
                    error.put("error", "Request could not be processed");
                    return Mono.just(error);
                });
    }

    /**
     * POST /api/v1/career/admin/regenerate
     * Delega a RegenerateFixturesUseCase
     */
    @PostMapping("/regenerate")
    public Mono<Map<String, Object>> regenerateFixtures(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);

        return regenerateFixturesUseCase.regenerate(userId)
                .<Map<String, Object>>map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Fixtures regenerated successfully");
                    return response;
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", "Request could not be processed");
                    return Mono.just(error);
                });
    }
}

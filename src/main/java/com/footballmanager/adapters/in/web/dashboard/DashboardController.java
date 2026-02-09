package com.footballmanager.adapters.in.web.dashboard;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.dashboard.dto.UserStatsResponse;
import com.footballmanager.adapters.in.web.dashboard.dto.WorldStatusResponse;
import com.footballmanager.application.service.domain.UserStatsService;
import com.footballmanager.application.service.world.WorldSnapshotService;
import com.footballmanager.application.service.world.WorldStatusQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ControllerHelper controllerHelper;
    private final UserStatsService userStatsService;
    private final WorldStatusQueryService worldStatusQueryService;
    private final WorldSnapshotService worldSnapshotService;

    @GetMapping("/user-stats")
    public Mono<UserStatsResponse> getUserStats(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return userStatsService.getUserStats(userId);
    }

    @GetMapping("/world-status")
    public Mono<WorldStatusResponse> getWorldStatus(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return worldStatusQueryService.getWorldStatus(userId);
    }

    /**
     * POST /api/v1/dashboard/reload-world
     * Fuerza la recarga del WorldSnapshot desde PostgreSQL.
     * Útil cuando el snapshot en Redis tiene datos incompletos.
     */
    @PostMapping("/reload-world")
    public Mono<WorldStatusResponse> reloadWorldSnapshot(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return worldSnapshotService.reloadFromDatabase(userId)
                .map(snapshot -> new WorldStatusResponse(
                        snapshot.getAllWorldTeams() != null ? snapshot.getAllWorldTeams().size() : 0,
                        snapshot.getAllWorldPlayers() != null ? snapshot.getAllWorldPlayers().size() : 0,
                        0
                ));
    }
}

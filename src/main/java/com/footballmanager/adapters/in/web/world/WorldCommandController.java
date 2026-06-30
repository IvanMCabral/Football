package com.footballmanager.adapters.in.web.world;

import com.footballmanager.application.service.world.WorldSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for WorldSnapshot operations.
 * Handles only snapshot-level operations (regenerate, etc.)
 *
 * <p><b>V25D78-C48 security fix:</b> DELETE /snapshot is destructive (regenerates
 * the entire WorldSnapshot from Postgres, overwriting the current state). Without
 * auth validation, a user could DELETE another user's snapshot. Now: if JWT is
 * present, validate JWT.userId == param.userId, return 403 if mismatch.
 */
@RestController
@RequestMapping("/api/v1/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class WorldCommandController {

    private final WorldSnapshotService worldSnapshotService;

    /**
     * DELETE /api/v1/world/snapshot?userId={userId}
     * Regenerates the WorldSnapshot from PostgreSQL, overwriting the existing one.
     *
     * <p><b>Auth:</b> requires JWT (SecurityConfig.java:144 post-C48). If the JWT
     * userId does NOT match the query param userId, returns 403 with
     * code=IMPERSONATION_FORBIDDEN. Admin pre-user setup, if needed, goes through
     * {@code POST /api/v1/admin/world/seed-la-liga} which uses a different code
     * path (admin role check instead of impersonation check).
     */
    @DeleteMapping("/snapshot")
    public Mono<ResponseEntity<Map<String, Object>>> deleteSnapshot(
            @RequestParam UUID userId,
            Authentication authentication) {
        if (authentication != null && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            UUID jwtUserId;
            try {
                jwtUserId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("code", "IMPERSONATION_FORBIDDEN");
                err.put("message", "JWT principal is not a valid userId");
                err.put("status", HttpStatus.FORBIDDEN.value());
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(err));
            }
            if (!jwtUserId.equals(userId)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("code", "IMPERSONATION_FORBIDDEN");
                err.put("message", "Authenticated user is not allowed to delete WorldSnapshot of another user");
                err.put("status", HttpStatus.FORBIDDEN.value());
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(err));
            }
        }

        return worldSnapshotService.reloadFromDatabase(userId)
                .map(snapshot -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "regenerated");
                    response.put("leagues", String.valueOf(snapshot.getLeagues().size()));
                    response.put("teams", String.valueOf(snapshot.getWorldTeams().size()));
                    response.put("players", String.valueOf(snapshot.getWorldPlayers().size()));
                    return ResponseEntity.ok(response);
                });
    }
}

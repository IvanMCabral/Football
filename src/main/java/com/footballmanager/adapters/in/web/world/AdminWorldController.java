package com.footballmanager.adapters.in.web.world;

import com.footballmanager.application.service.world.LaLigaSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V25D78-C48: Admin-only world setup endpoint.
 *
 * <p>Provides an alternative path to seed LaLiga for any user, gated by
 * {@code ROLE_ADMIN}. This is the escape hatch for ops/setup flows that need
 * to seed WorldSnapshot for users BEFORE they authenticate (e.g., data import,
 * pre-populating test users, recovering from data corruption).
 *
 * <p><b>Why a separate endpoint:</b> the main {@code POST /api/v1/world/seed-la-liga}
 * (LaLigaSeedController) is now gated by authenticated + impersonation check
 * (C47 fix: JWT.userId must match param.userId). This protects regular users from
 * being impersonated by attackers. But it also means an admin cannot seed for a
 * user via the same endpoint without first logging in as that user. This admin
 * endpoint solves that without re-introducing the impersonation vector.
 *
 * <p><b>Auth model:</b>
 * <ul>
 *   <li>Path: {@code POST /api/v1/admin/world/seed-la-liga?userId=X}</li>
 *   <li>Auth: SecurityConfig.java requires authenticated (line ~144 post-C48).</li>
 *   <li>Role: manual {@code isAdmin(Authentication)} check inside this controller.
 *       If the user lacks ROLE_ADMIN, returns 403.</li>
 *   <li>No impersonation check: admin can seed for any userId by design.</li>
 * </ul>
 *
 * <p><b>Implementation note:</b> role check is MANUAL inside the controller method
 * (lines below) instead of declarative {@code @PreAuthorize("hasRole('ADMIN')")}
 * because the project does not currently have spring-boot-starter-aop / aspectjweaver
 * on the classpath. The {@code @PreAuthorize} annotation requires AOP proxying which
 * would need that dependency. Manual check is functionally equivalent and avoids
 * the dependency cost for this C48 sprint. See SecurityConfig.java comment.
 *
 * <p><b>Audit log:</b> not yet implemented. Future sprint (out of C48 scope per task)
 * should add audit logging for admin actions. For now, server logs include the admin
 * userId + target userId + timestamp via the standard Spring web logging.
 */
@RestController
@RequestMapping("/api/v1/admin/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class AdminWorldController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final LaLigaSeedService laLigaSeedService;

    /**
     * V25D78-C48: manual admin-role check. Returns true if the Authentication has
     * the ROLE_ADMIN authority. Used by AdminWorldController.adminSeedLaLiga to
     * gate the endpoint without requiring @PreAuthorize (which needs AOP).
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority auth : authentication.getAuthorities()) {
            if (ROLE_ADMIN.equals(auth.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("/seed-la-liga")
    public Mono<ResponseEntity<Map<String, Object>>> adminSeedLaLiga(
            @RequestParam UUID userId,
            Authentication authentication) {
        // V25D78-C48: manual role check (see class-level javadoc for why manual vs @PreAuthorize).
        if (!isAdmin(authentication)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", "ADMIN_REQUIRED");
            err.put("message", "This endpoint requires role=ADMIN");
            err.put("status", HttpStatus.FORBIDDEN.value());
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(err));
        }

        return laLigaSeedService.execute(userId)
                .map(result -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("status", "ok");
                    body.put("userId", userId.toString());
                    body.put("leagueName", result.leagueName());
                    body.put("teamsInserted", result.teamsCount());
                    body.put("playersInserted", result.playersCount());
                    body.put("durationMs", result.durationMs());
                    body.put("adminEndpoint", true);
                    return ResponseEntity.ok(body);
                });
    }
}
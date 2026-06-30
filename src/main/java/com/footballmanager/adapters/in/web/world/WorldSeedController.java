package com.footballmanager.adapters.in.web.world;

import com.footballmanager.application.service.world.WorldSeedService;
import com.footballmanager.application.service.world.LeagueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V25D78-C55.1: Multi-league seed controller. Exposes 10 league-specific
 * endpoints + a {@code seed-all} convenience endpoint, all under
 * {@code /api/v1/world/seed-*}.
 *
 * <p>Auth model: same as {@code POST /world/seed-la-liga}. SecurityConfig
 * (post-C48) requires {@code authenticated}. Admin user can seed any
 * user's snapshot via {@code POST /admin/world/seed-all} (added separately).
 *
 * <p>Each endpoint accepts {@code ?userId=X} (same convention as the C44
 * La Liga seed endpoint). The userId is checked against the JWT (impersonation
 * guard added in C50) — non-matching → 403 IMPERSONATION_FORBIDDEN.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/world")
@RequiredArgsConstructor
public class WorldSeedController {

    private final WorldSeedService worldSeedService;
    private final com.footballmanager.adapters.in.web.common.ControllerHelper controllerHelper;

    /**
     * V25D78-C55.1: Generic per-league seed endpoint.
     * URL pattern: {@code POST /api/v1/world/seed/{slug}?userId=X}
     * Slug: laliga, premier, bundesliga, seria-a, ligue-1, brasileirao,
     *       liga-profesional, mls, eredivisie, championship.
     */
    @PostMapping("/seed/{slug}")
    public Mono<ResponseEntity<Map<String, Object>>> seedLeague(
            @PathVariable String slug,
            @RequestParam UUID userId,
            org.springframework.security.core.Authentication authentication) {
        controllerHelper.requireSelfUserId(authentication, userId);
        LeagueType lt = LeagueType.fromSlug(slug);
        if (lt == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error("UNKNOWN_LEAGUE", "No league with slug '" + slug + "'")));
        }
        return worldSeedService.seedLeague(lt, userId)
                .map(result -> ResponseEntity.ok(success(result)))
                .onErrorResume(e -> {
                    log.error("[WORLD-SEED] seed/{}/{} failed with full stack:", lt.slug(), userId, e);
                    e.printStackTrace();
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(error("SEED_FAILED", e.getMessage())));
                });
    }

    /**
     * V25D78-C55.1: Seed all 10 leagues at once. Idempotent — safe to call
     * multiple times.
     */
    @PostMapping("/seed-all")
    public Mono<ResponseEntity<Map<String, Object>>> seedAll(
            @RequestParam UUID userId,
            org.springframework.security.core.Authentication authentication) {
        controllerHelper.requireSelfUserId(authentication, userId);
        return worldSeedService.seedAllLeagues(userId)
                .map(result -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("status", "ok");
                    body.put("userId", userId.toString());
                    body.put("leaguesSeeded", result.perLeague().size());
                    body.put("perLeague", result.perLeague());
                    return ResponseEntity.ok(body);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(error("SEED_ALL_FAILED", e.getMessage()))));
    }

    private Map<String, Object> success(WorldSeedService.SeedResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("userId", r.leagueName() != null ? r.leagueName() : "");
        body.put("leagueName", r.leagueName());
        body.put("teamsInserted", r.teamsCount());
        body.put("playersInserted", r.playersCount());
        body.put("durationMs", r.durationMs());
        return body;
    }

    private Map<String, Object> error(String code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("code", code);
        body.put("message", msg);
        return body;
    }
}
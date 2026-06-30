package com.footballmanager.adapters.in.web.world;

import com.footballmanager.application.service.world.LaLigaSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V24D6U5: Controller one-shot para ejecutar el seed de La Liga 2024/25.
 *
 * <p><b>Endpoint:</b> {@code POST /api/v1/world/seed-la-liga?userId={userId}}
 * <p><b>Efecto:</b> pobla el WorldSnapshot del usuario (en Redis) con 20 equipos y ~400
 * jugadores de La Liga 2024/25. Es idempotente: re-ejecuciones no duplican data, solo
 * actualizan stats.
 *
 * <p><b>Importante:</b> este endpoint es destructivo en el sentido de que sobreescribe
 * stats de teams/players con el mismo nombre. NO dropea el snapshot del usuario.
 *
 * <p><b>V25D78-C47 (security fix):</b> si la request trae JWT (vía header
 * {@code Authorization: Bearer ...}), el {@code userId} del JWT DEBE coincidir con el
 * {@code userId} del query param. Si no coincide, retorna 403 Forbidden con
 * {@code code=IMPERSONATION_FORBIDDEN}. Esto previene que un user autenticado pueda
 * sembrar/corromper el WorldSnapshot de otro user (privilege escalation / data tampering).
 *
 * <p>Si la request NO trae JWT (anónima), el endpoint sigue accesible (security config
 * tiene {@code /api/v1/world/**} como {@code permitAll()} por design intent — el world
 * debe ser sembrable ANTES de que exista cualquier usuario, durante el setup flow).
 * Esa decisión de design está documentada en
 * {@code SecurityConfig.java} línea ~144 (V24D12-C-3) y queda fuera del scope de este
 * fix.
 */
@RestController
@RequestMapping("/api/v1/world/seed-la-liga")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class LaLigaSeedController {

    private final LaLigaSeedService laLigaSeedService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> seedLaLiga(
            @RequestParam UUID userId,
            Authentication authentication) {
        // V25D78-C47: si la request trae JWT, validar que su userId coincide con el
        // query param. Si no coincide, el JWT user está intentando impersonar a otro
        // user — bloqueamos con 403.
        if (authentication != null && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            UUID jwtUserId;
            try {
                jwtUserId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException e) {
                // JWT con name no-UUID (defensivo, no debería pasar dado el converter
                // del SecurityConfig que setea name=userId). Devolvemos 403 igual.
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("code", "IMPERSONATION_FORBIDDEN");
                err.put("message", "JWT principal is not a valid userId");
                err.put("status", HttpStatus.FORBIDDEN.value());
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(err));
            }
            if (!jwtUserId.equals(userId)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("code", "IMPERSONATION_FORBIDDEN");
                err.put("message", "Authenticated user is not allowed to seed WorldSnapshot for another user");
                err.put("status", HttpStatus.FORBIDDEN.value());
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(err));
            }
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
                    return ResponseEntity.ok(body);
                });
    }
}

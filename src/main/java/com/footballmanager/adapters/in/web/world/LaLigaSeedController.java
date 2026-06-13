package com.footballmanager.adapters.in.web.world;

import com.footballmanager.application.service.world.LaLigaSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
 */
@RestController
@RequestMapping("/api/v1/world/seed-la-liga")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class LaLigaSeedController {

    private final LaLigaSeedService laLigaSeedService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> seedLaLiga(@RequestParam UUID userId) {
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

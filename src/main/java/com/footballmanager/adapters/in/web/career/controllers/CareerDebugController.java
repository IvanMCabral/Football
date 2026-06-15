package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CareerDebugController - Endpoints de debug y diagnóstico.
 * Base path: /api/v1/career/debug
 *
 * Responsabilidad: Endpoints temporales para debug y diagnóstico.
 * SOLO disponible en perfil "dev".
 *
 * Endpoints:
 * - GET /api/v1/career/debug        → Debug de conectividad
 * - GET /api/v1/career/debug/fixtures → Debug de fixtures con resultados
 */
@RestController
@RequestMapping("/api/v1/career/debug")
@Profile("dev")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CareerDebugController {

    private final CareerSessionService sessionService;
    private final ControllerHelper controllerHelper;

    public CareerDebugController(CareerSessionService sessionService, ControllerHelper controllerHelper) {
        this.sessionService = sessionService;
        this.controllerHelper = controllerHelper;
    }

    /**
     * GET /api/v1/career/debug
     * Endpoint de debug para probar conectividad
     */
    @GetMapping("")
    public Mono<Map<String, Object>> debugEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Career controller reachable");
        response.put("timestamp", System.currentTimeMillis());
        return Mono.just(response);
    }

    /**
     * GET /api/v1/career/debug/fixtures
     * Endpoint temporal para debuggear fixtures - muestra todos con resultados
     */
    @GetMapping("/fixtures")
    public Mono<Map<String, Object>> debugFixtures(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);

        return sessionService.continueCareer(userId)
                .flatMap(career -> {
                    Map<String, Object> debug = new HashMap<>();
                    debug.put("careerId", career.getCareerId());
                    debug.put("currentRound", career.getTournamentState().getCurrentRound());
                    debug.put("totalRounds", career.getTournamentState().getTotalRounds());

                    List<Map<String, Object>> fixturesDebug = new ArrayList<>();
                    for (MatchFixture f : career.getTournamentState().getFixtures()) {
                        Map<String, Object> fDebug = new HashMap<>();
                        fDebug.put("matchId", f.getMatchId());
                        fDebug.put("homeTeamId", f.getHomeTeamId());
                        fDebug.put("awayTeamId", f.getAwayTeamId());
                        fDebug.put("round", f.getRound());
                        fDebug.put("status", f.getStatus());
                        if (f.getResult() != null) {
                            fDebug.put("homeGoals", f.getResult().getHomeGoals());
                            fDebug.put("awayGoals", f.getResult().getAwayGoals());
                        } else {
                            fDebug.put("homeGoals", null);
                            fDebug.put("awayGoals", null);
                        }
                        fixturesDebug.add(fDebug);
                    }
                    debug.put("fixtures", fixturesDebug);

                    return Mono.just(debug);
                })
                .switchIfEmpty(Mono.just(Map.of("error", "Career no encontrado")));
    }
}

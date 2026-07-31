package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Development-only career diagnostics.
 *
 * Base path: /api/v1/career/debug
 *
 * These endpoints are intentionally limited to the "dev" profile. They help
 * verify career connectivity and inspect generated fixtures while developing.
 */
@RestController
@RequestMapping("/api/v1/career/debug")
@Profile("dev")
public class CareerDebugController {

    private final CareerSessionService sessionService;
    private final ControllerHelper controllerHelper;

    public CareerDebugController(CareerSessionService sessionService, ControllerHelper controllerHelper) {
        this.sessionService = sessionService;
        this.controllerHelper = controllerHelper;
    }

    /**
     * Health probe for the career diagnostics controller.
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
     * Lists career fixtures with their current result data for local diagnosis.
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
                .switchIfEmpty(Mono.just(Map.of("error", "Career not found")));
    }
}

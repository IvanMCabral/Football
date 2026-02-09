package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.query.*;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.port.in.career.GetCareerStatusUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CareerViewController - Query endpoints para Career (solo lectura).
 *
 * Endpoints que leen datos: GET
 * Ruta base: /api/v1/career
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/career")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CareerViewController {

    private final ControllerHelper controllerHelper;
    private final CareerSessionService sessionService;
    private final FixtureQueryService fixtureQueryService;
    private final StandingQueryService standingQueryService;
    private final CareerQueryService careerQueryService;
    private final CareerPalmaresQueryService palmaresQueryService;
    private final CareerChampionQueryService championQueryService;
    private final GetCareerStatusUseCase getCareerStatusUseCase;

    public CareerViewController(
            ControllerHelper controllerHelper,
            CareerSessionService sessionService,
            FixtureQueryService fixtureQueryService,
            StandingQueryService standingQueryService,
            CareerQueryService careerQueryService,
            CareerPalmaresQueryService palmaresQueryService,
            CareerChampionQueryService championQueryService,
            GetCareerStatusUseCase getCareerStatusUseCase) {
        this.controllerHelper = controllerHelper;
        this.sessionService = sessionService;
        this.fixtureQueryService = fixtureQueryService;
        this.standingQueryService = standingQueryService;
        this.careerQueryService = careerQueryService;
        this.palmaresQueryService = palmaresQueryService;
        this.championQueryService = championQueryService;
        this.getCareerStatusUseCase = getCareerStatusUseCase;
    }

    /**
     * GET /api/v1/career/status
     * Obtiene el estado de la carrera del usuario
     */
    @GetMapping("/status")
    public Mono<GetCareerStatusUseCase.CareerStatusDto> getCareerStatus(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return getCareerStatusUseCase.execute(userId);
    }

    /**
     * GET /api/v1/career/fixtures
     * Obtiene fixtures de la división del usuario para una fecha específica
     */
    @GetMapping("/fixtures")
    public Mono<List<FixtureQueryDtos.MatchInfo>> getUserDivisionFixtures(
            @RequestParam int round,
            Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(career -> fixtureQueryService.getUserDivisionFixturesByRound(career, round))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/fixtures/all
     * Obtiene todos los fixtures de la división del usuario
     */
    @GetMapping("/fixtures/all")
    public Mono<FixtureQueryDtos.AllFixturesResponse> getAllFixtures(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return sessionService.getCareerFromCache(userId)
                .flatMap(fixtureQueryService::getAllUserDivisionFixtures)
                .switchIfEmpty(Mono.just(new FixtureQueryDtos.AllFixturesResponse(
                        "", 0, List.of(), List.of(), Map.of(), new FixtureQueryDtos.DivisionConfig(0, 0, false, 0, 0)
                )));
    }

    /**
     * GET /api/v1/career/fixtures/complete
     * Obtiene todos los fixtures de todas las divisiones
     */
    @GetMapping("/fixtures/complete")
    public Mono<FixtureQueryDtos.CompleteFixturesResponse> getCompleteFixtures(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return sessionService.getCareerFromCache(userId)
                .flatMap(fixtureQueryService::getCompleteFixtures)
                .switchIfEmpty(Mono.just(new FixtureQueryDtos.CompleteFixturesResponse(
                        "", 0, 0, List.of(), List.of()
                )));
    }

    /**
     * GET /api/v1/career/fixtures/league
     * Obtiene fixtures de todas las divisiones para una fecha específica
     */
    @GetMapping("/fixtures/league")
    public Mono<List<FixtureQueryDtos.LeagueDivisionFixtures>> getLeagueFixtures(
            Authentication auth,
            @RequestParam(required = false) Integer round) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(career -> fixtureQueryService.getLeagueFixtures(career, round))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/standings
     * Obtiene standings de la división del usuario
     */
    @GetMapping("/standings")
    public Mono<List<StandingQueryService.StandingEntry>> getUserStandings(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(career -> standingQueryService.getUserDivisionStandings(career, career.getUserDivision()))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/standings/all
     * Obtiene todas las tablas de posiciones de todas las divisiones
     */
    @GetMapping("/standings/all")
    public Mono<StandingQueryService.AllStandingsResponse> getAllStandings(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(career -> {
                    List<Division> divisions = career.getSeasonManager().getDivisions();
                    Division userDivision = career.getUserDivision();
                    return standingQueryService.getAllStandings(career, divisions, userDivision);
                })
                .switchIfEmpty(Mono.just(new StandingQueryService.AllStandingsResponse(List.of())));
    }

    /**
     * GET /api/v1/career/champion
     * Obtiene el campeón del torneo (solo si está terminado)
     */
    @GetMapping("/champion")
    public Mono<CareerChampionQueryService.ChampionInfo> getChampion(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(championQueryService::getChampion)
                .switchIfEmpty(Mono.error(new IllegalStateException("Career no encontrado")));
    }

    /**
     * GET /api/v1/career/palmares
     * Obtiene palmares de la división del usuario
     */
    @GetMapping("/palmares")
    public Mono<List<CareerPalmaresQueryService.PalmaresEntry>> getUserPalmares(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        log.info("[PALMARES-API] GET /palmares - userId={}", userId);
        return sessionService.getCareerFromCache(userId)
                .flatMap(palmaresQueryService::getUserDivisionPalmares)
                .doOnNext(entries -> log.info("[PALMARES-API] GET /palmares - returning {} entries", entries.size()))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/palmares/all
     * Obtiene todo el palmares de todas las temporadas
     */
    @GetMapping("/palmares/all")
    public Mono<List<CareerPalmaresQueryService.PalmaresEntry>> getAllPalmares(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        log.info("[PALMARES-API] GET /palmares/all - userId={}", userId);
        return sessionService.getCareerFromCache(userId)
                .flatMap(palmaresQueryService::getAllPalmares)
                .doOnNext(entries -> log.info("[PALMARES-API] GET /palmares/all - returning {} entries", entries.size()))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/tops
     * Obtiene los equipos más ganadores de todas las divisiones
     */
    @GetMapping("/tops")
    public Mono<List<CareerPalmaresQueryService.TopTeamEntry>> getTopTeams(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(palmaresQueryService::getTopTeams)
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/palmares/division/{divisionId}
     * Obtiene los campeones de una division especifica
     */
    @GetMapping("/palmares/division/{divisionId}")
    public Mono<List<CareerPalmaresQueryService.PalmaresEntry>> getPalmaresByDivision(
            Authentication auth,
            @PathVariable String divisionId) {
        UUID userId = controllerHelper.getUserId(auth);
        log.info("[PALMARES-API] GET /palmares/division/{} - userId={}", divisionId, userId);
        return sessionService.getCareerFromCache(userId)
                .flatMap(career -> palmaresQueryService.getPalmaresByDivision(career, divisionId))
                .doOnNext(entries -> log.info("[PALMARES-API] GET /palmares/division/{} - returning {} entries", divisionId, entries.size()))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/tops/division/{divisionId}
     * Obtiene los equipos con más títulos de una division especifica
     */
    @GetMapping("/tops/division/{divisionId}")
    public Mono<List<CareerPalmaresQueryService.TopTeamEntry>> getTopTeamsByDivision(
            Authentication auth,
            @PathVariable String divisionId) {
        UUID userId = controllerHelper.getUserId(auth);
        log.info("[TOPTEAMS-API] GET /tops/division/{} - userId={}", divisionId, userId);
        return sessionService.getCareerFromCache(userId)
                .flatMap(career -> palmaresQueryService.getTopTeamsByDivision(career, divisionId))
                .doOnNext(entries -> log.info("[TOPTEAMS-API] GET /tops/division/{} - returning {} entries", divisionId, entries.size()))
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/promotions
     * Obtiene las promociones y descensos
     */
    @GetMapping("/promotions")
    public Mono<List<CareerPalmaresQueryService.PromotionEntry>> getPromotions(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(palmaresQueryService::getPromotions)
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * GET /api/v1/career/divisions
     * Obtiene información de las divisiones
     */
    @GetMapping("/divisions")
    public Mono<List<CareerQueryService.DivisionInfo>> getDivisions(Authentication auth) {
        UUID userId = controllerHelper.getUserId(auth);
        return sessionService.getCareerFromCache(userId)
                .flatMap(careerQueryService::getDivisions)
                .switchIfEmpty(Mono.just(List.of()));
    }

}

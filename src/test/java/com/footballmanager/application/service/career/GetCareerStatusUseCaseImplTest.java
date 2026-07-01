package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.entity.career.Promotion;
import com.footballmanager.domain.port.in.career.GetCareerStatusUseCase.CareerStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * V25D78-C55.2 phase 4 UI: tests for {@link GetCareerStatusUseCaseImpl} covering the
 * two new fields exposed to the frontend so the dashboard can render the user's
 * division tier and auto-trigger the promotions dialog.
 *
 * <p>Coverage:
 * <ul>
 *   <li><b>(c)</b> {@code userDivision} populated from CareerSave.getUserDivision() —
 *       mirrors the division tier the user is currently playing in.</li>
 *   <li><b>(c)</b> {@code userDivision} defaults to {@code PRIMERA} when the
 *       legacy career has no division yet (back-compat for pre-C55.2 saves).</li>
 *   <li><b>(d2)</b> {@code promotionsAvailable} is {@code true} when CareerSave
 *       carries a non-empty promotions list (engine just finished a season and
 *       computed movement) and {@code false} otherwise.</li>
 *   <li>Empty-career fallback returns the no-op DTO with sane defaults.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GetCareerStatusUseCaseImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c5");

    @Mock
    private CareerSessionService sessionService;

    private GetCareerStatusUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCareerStatusUseCaseImpl(sessionService);
    }

    /**
     * Build a CareerSave where the user team {@code sessionTeamId} sits in the
     * supplied {@code userDivision} (and that division exists in
     * {@code seasonManager.divisions}). The save also gets a minimal
     * TournamentState and empty PlayerManager/TeamManager so the impl can
     * compute squad/season fields without NPEs.
     */
    private CareerSave makeCareerWithDivision(String sessionTeamId, Division userDivision) {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(sessionTeamId);

        CareerPlayerManager playerManager = new CareerPlayerManager();
        playerManager.setSessionPlayers(new java.util.HashMap<>());
        playerManager.setFreePlayers(java.util.List.of());
        career.setPlayerManager(playerManager);

        CareerTeamManager teamManager = new CareerTeamManager();
        career.setTeamManager(teamManager);

        CareerSeasonManager seasonManager = new CareerSeasonManager();
        if (userDivision != null) {
            Division primera = new Division("PRIMERA", 1);
            Division tercera = new Division("TERCERA", 3);
            // The user's session team must be present in the chosen division's teamIds
            // so seasonManager.findDivisionByTeamId(sessionTeamId) returns it.
            userDivision.addTeam(sessionTeamId);
            seasonManager.setDivisions(List.of(primera, userDivision, tercera));
        }
        career.setSeasonManager(seasonManager);

        TournamentState tournamentState = new TournamentState();
        tournamentState.setCurrentRound(5);
        tournamentState.setTotalRounds(38);
        career.setTournamentState(tournamentState);

        return career;
    }

    /**
     * Plant a non-empty promotions list into CareerSeasonManager.promotionCalculator.
     * We bypass {@code seasonManager.calculatePromotionsAndRelegations(state)} because
     * it requires a fully-populated TournamentState with standings — overkill for
     * the contract under test (we only care that {@code getPromotions().isEmpty()}
     * flips to false).
     */
    private void seedPromotions(CareerSave career) throws Exception {
        Promotion p = new Promotion();
        p.setTeamId("team-x");
        p.setTeamName("Atlético");
        p.setType(Promotion.PromotionType.PROMOTED);
        p.setFromPosition(1);

        Field smField = CareerSave.class.getDeclaredField("seasonManager");
        smField.setAccessible(true);
        CareerSeasonManager sm = (CareerSeasonManager) smField.get(career);
        var calcField = CareerSeasonManager.class.getDeclaredField("promotionCalculator");
        calcField.setAccessible(true);
        Object calcInstance = calcField.get(sm);
        var promotionsListField = calcInstance.getClass().getDeclaredField("promotionList");
        promotionsListField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<Promotion> pl = (java.util.List<Promotion>) promotionsListField.get(calcInstance);
        pl.add(p);
    }

    @Test
    @DisplayName("(c) userDivision populated from CareerSave when team sits in SEGUNDA")
    void execute_userDivisionReturned_whenCareerHasDivision() {
        Division segunda = new Division("SEGUNDA", 2);
        CareerSave career = makeCareerWithDivision("team-1", segunda);
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals("SEGUNDA", dto.userDivision());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("(c) userDivision is null when career has no divisions (legacy)")
    void execute_userDivisionNull_whenCareerLegacy() {
        // No divisions set → seasonManager.findDivisionByTeamId returns null
        CareerSave career = makeCareerWithDivision("team-1", null);
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> {
                    assertEquals(null, dto.userDivision(),
                            "legacy careers with no division should expose null, not a fake default — "
                                    + "the frontend handles the legacy case explicitly");
                    assertFalse(dto.promotionsAvailable(),
                            "no divisions ⇒ engine hasn't computed promotions either");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("(d2) promotionsAvailable true when CareerSave has non-empty promotions list")
    void execute_promotionsAvailable_whenSeasonJustEnded() throws Exception {
        Division primera = new Division("PRIMERA", 1);
        CareerSave career = makeCareerWithDivision("team-1", primera);
        seedPromotions(career);

        // Sanity: confirm the seed worked (otherwise the test is a tautology).
        assertFalse(career.getPromotions().isEmpty(), "seed should populate promotions list");

        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> {
                    assertEquals("PRIMERA", dto.userDivision());
                    assertTrue(dto.promotionsAvailable(),
                            "promotionsAvailable must be true when CareerSave.promotions is non-empty");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("(d2) promotionsAvailable false when no promotions (mid-season)")
    void execute_promotionsAvailableFalse_midSeason() {
        Division primera = new Division("PRIMERA", 1);
        CareerSave career = makeCareerWithDivision("team-1", primera);
        // No seedPromotions call → list is empty
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> assertFalse(dto.promotionsAvailable()))
                .verifyComplete();
    }

    @Test
    @DisplayName("empty cache returns NO_CAREER DTO with safe defaults")
    void execute_emptyCache_returnsNoCareerDefaults() {
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> {
                    assertEquals("NO_CAREER", dto.careerPhase());
                    assertEquals(null, dto.userDivision(),
                            "no-career must surface null so the frontend can hide division UI");
                    assertFalse(dto.promotionsAvailable());
                })
                .verifyComplete();
    }
}
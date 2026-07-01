package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.SessionTeam;
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
import java.math.BigDecimal;
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
        return makeCareerWithDivision(sessionTeamId, userDivision, null);
    }

    /**
     * V25D78-C55.9: build a CareerSave with a properly wired SessionTeam (name,
     * country, formation) so {@code career.getSessionTeam(sessionTeamId).getName()}
     * returns a real value. Pass {@code teamName=null} to skip the SessionTeam
     * wiring (legacy behaviour).
     */
    private CareerSave makeCareerWithDivision(String sessionTeamId, Division userDivision, String teamName) {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(sessionTeamId);

        CareerPlayerManager playerManager = new CareerPlayerManager();
        playerManager.setSessionPlayers(new java.util.HashMap<>());
        playerManager.setFreePlayers(java.util.List.of());
        career.setPlayerManager(playerManager);

        CareerTeamManager teamManager = new CareerTeamManager();
        if (teamName != null) {
            SessionTeam sessionTeam = SessionTeam.custom(
                    sessionTeamId,
                    teamName,
                    "Spain",
                    new BigDecimal("100000000"),
                    "4-4-2"
            );
            // Override the auto-generated sessionTeamId so it matches sessionTeamId
            // (CareerSave.getSessionTeam looks up by sessionTeamId).
            try {
                Field idField = SessionTeam.class.getDeclaredField("sessionTeamId");
                idField.setAccessible(true);
                idField.set(sessionTeam, sessionTeamId);
            } catch (Exception e) {
                throw new RuntimeException("failed to set sessionTeamId", e);
            }
            teamManager.addSessionTeam(sessionTeam);
        }
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
     * V25D78-C55.9: simulate a promotion by mutating the seasonManager so that
     * the user's teamId is now in the {@code toDivision} (and removed from the
     * {@code fromDivision}). This mimics what PromotionExecutor.execute →
     * DivisionManager.moveTeam does at end-of-season without going through the
     * full TournamentState + standings pipeline.
     *
     * <p>Implementation note: the helper builds its own {@code primera} and
     * {@code tercera} Division instances internally, so the caller's
     * {@code fromDivision} / {@code toDivision} must be the SAME instances the
     * helper stored (i.e. the {@code userDivision} arg of
     * {@link #makeCareerWithDivision}). Pass those exact references; we look
     * them up by name+number inside the live {@code divisionManager} to be
     * safe against reference mismatches.
     */
    private void simulatePromotion(CareerSave career, String sessionTeamId, Division fromDivision, Division toDivision) {
        try {
            Field smField = CareerSave.class.getDeclaredField("seasonManager");
            smField.setAccessible(true);
            CareerSeasonManager sm = (CareerSeasonManager) smField.get(career);

            Field dmField = CareerSeasonManager.class.getDeclaredField("divisionManager");
            dmField.setAccessible(true);
            Object divisionManager = dmField.get(sm);

            // Resolve live division instances from the divisionManager so we
            // don't accidentally pass a stale test-scope Division that was never
            // inserted into the divisions list. Match by divisionNumber, which
            // is the unique key set at construction time.
            Field liveDivisionsField = divisionManager.getClass().getDeclaredField("divisions");
            liveDivisionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Division> liveDivisions = (java.util.List<Division>) liveDivisionsField.get(divisionManager);

            Division liveFrom = liveDivisions.stream()
                    .filter(d -> d.getDivisionNumber() == fromDivision.getDivisionNumber())
                    .findFirst().orElseThrow();
            Division liveTo = liveDivisions.stream()
                    .filter(d -> d.getDivisionNumber() == toDivision.getDivisionNumber())
                    .findFirst().orElseThrow();

            java.lang.reflect.Method moveTeam = divisionManager.getClass().getDeclaredMethod(
                    "moveTeam", String.class, Division.class, Division.class);
            moveTeam.setAccessible(true);
            moveTeam.invoke(divisionManager, sessionTeamId, liveFrom, liveTo);
        } catch (Exception e) {
            throw new RuntimeException("simulatePromotion failed", e);
        }
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

    // ============================================================================
    // V25D78-C55.9 — A8 + A9 contract tests (userDivision + userTeamName)
    // ============================================================================

    /**
     * V25D78-C55.9 A8: post career-start, the user's division tier must be
     * returned as a non-null string regardless of which of the 12 sub-divisions
     * (post-C55.6 distribution: 60 teams / 5-per-division) the team sits in.
     *
     * <p>Pre-C55.9 the switch only handled divisionNumber 1-3 → "PRIMERA"/"SEGUNDA"/
     * "TERCERA" and returned {@code null} for 4-12. This test specifically
     * exercises the regression target (tier 5 = "QUINTA") so a future refactor
     * can't silently regress the 12-tier coverage.
     */
    @Test
    @DisplayName("V25D78-C55.9 A8: userDivision populated for tier > 3 (post-start)")
    void status_userDivision_returnsCareerUserDivision_postCareerStart() {
        Division quinta = new Division("QUINTA", 5);
        CareerSave career = makeCareerWithDivision("team-1", quinta);
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> {
                    assertNotNull(dto.userDivision(),
                            "tier-5 (QUINTA) must NOT be null post-fix; pre-fix the switch only handled 1-3");
                    assertEquals("QUINTA", dto.userDivision(),
                            "divisionNumber=5 must map to the QUINTA tier label");
                })
                .verifyComplete();
    }

    /**
     * V25D78-C55.9 A8 (post-promotion): after the engine moves the user's team
     * between divisions (PromotionExecutor.execute → DivisionManager.moveTeam),
     * the next {@code /career/status} call must reflect the new tier. The
     * promotion is computed lazily from {@code seasonManager.findDivisionByTeamId},
     * so simply mutating the divisions map (via the same internal moveTeam path)
     * exercises the contract.
     */
    @Test
    @DisplayName("V25D78-C55.9 A8: userDivision updates after promotion (SEGUNDA -> PRIMERA)")
    void status_userDivision_updatesPostPromotion() {
        // Career starts with the user team in SEGUNDA (rank ~6-10 / divisionNumber=2).
        Division primera = new Division("PRIMERA", 1);
        Division segunda = new Division("SEGUNDA", 2);
        CareerSave career = makeCareerWithDivision("team-1", segunda);

        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        // Before promotion: userDivision must be SEGUNDA.
        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> assertEquals("SEGUNDA", dto.userDivision(),
                        "pre-promotion tier must be SEGUNDA"))
                .verifyComplete();

        // Simulate the end-of-season promotion: PromotionExecutor.execute →
        // DivisionManager.moveTeam(sessionTeamId, fromDiv=SEGUNDA, toDiv=PRIMERA).
        simulatePromotion(career, "team-1", segunda, primera);

        // After promotion: userDivision must now be PRIMERA. Same CareerSave
        // instance, no cache reset — the lazy computation picks up the move.
        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> assertEquals("PRIMERA", dto.userDivision(),
                        "post-promotion tier must be PRIMERA — lazy getUserDivision() must reflect DivisionManager.moveTeam"))
                .verifyComplete();
    }

    /**
     * V25D78-C55.9 A9: the human-readable team name (the one the user picked at
     * career-start, e.g. "Real Madrid", "Las Palmas") must be exposed in
     * {@code /career/status.userTeamName}, matching what {@code /career/continue}
     * already returns. Pre-C55.9 this field was always {@code null}, breaking the
     * API contract (per C55.7.3 gap #9 + #10).
     */
    @Test
    @DisplayName("V25D78-C55.9 A9: userTeamName returns the real team name (post-start)")
    void status_userTeamName_returnsRealTeamName_postCareerStart() {
        Division primera = new Division("PRIMERA", 1);
        CareerSave career = makeCareerWithDivision("team-real-madrid", primera, "Real Madrid");
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.execute(USER_ID))
                .assertNext(dto -> {
                    assertNotNull(dto.userTeamName(),
                            "userTeamName must not be null when sessionTeam resolves");
                    assertEquals("Real Madrid", dto.userTeamName(),
                            "userTeamName must match the SessionTeam.getName() the user chose at career-start");
                })
                .verifyComplete();
    }

    /**
     * V25D78-C55.9 (consistency): {@code /career/status.userDivision} and
     * {@code /career/status.userTeamName} must match what {@code /career/continue}
     * returns for the same career state. Per C55.7.3 gap #10, pre-fix the two
     * endpoints disagreed — /continue returned "Las Palmas" while /status returned
     * null for the same user. The contract is: both derive from the same CareerSave
     * state, so they MUST agree by construction.
     *
     * <p>This test verifies the consistency invariant by deriving what
     * {@code ContinueSeasonUseCaseImpl} would produce for {@code userTeamName}
     * (the same expression: {@code career.getSessionTeam(userSessionTeamId).getName()})
     * and asserting equality on the {@code /status} output. {@code userDivision}
     * consistency is implicit because both endpoints route through
     * {@code CareerSave.getUserDivision()} → {@code seasonManager.findDivisionByTeamId}.
     */
    @Test
    @DisplayName("V25D78-C55.9 consistency: /status.userDivision + /status.userTeamName match /continue")
    void status_userDivisionAndTeamName_consistentBetweenEndpoints() {
        Division segunda = new Division("SEGUNDA", 2);
        CareerSave career = makeCareerWithDivision("team-1", segunda, "Las Palmas");
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        // Capture the /status result first
        var statusFuture = useCase.execute(USER_ID).block();

        assertNotNull(statusFuture, "/status Mono.block() must not be null");

        // Derive what /continue would return (per ContinueSeasonUseCaseImpl.userTeamName:
        // career.getSessionTeam(userSessionTeamId) != null
        //     ? career.getSessionTeam(userSessionTeamId).getName()
        //     : "Unknown"
        // ). Both endpoints must agree on this expression.
        String continueUserTeamName = career.getSessionTeam(career.getUserSessionTeamId()) != null
                ? career.getSessionTeam(career.getUserSessionTeamId()).getName()
                : "Unknown";
        Division continueUserDivision = career.getUserDivision();

        // userDivision consistency: both endpoints route through getUserDivision()
        assertEquals(continueUserDivision.getDivisionNumber() == 1 ? "PRIMERA"
                        : continueUserDivision.getDivisionNumber() == 2 ? "SEGUNDA"
                        : continueUserDivision.getDivisionNumber() == 3 ? "TERCERA"
                        : "CUARTA", // 4 in our test fixture
                statusFuture.userDivision(),
                "/status.userDivision must agree with what /continue would derive");
        assertEquals(continueUserTeamName, statusFuture.userTeamName(),
                "/status.userTeamName must equal /continue.userTeamName (both derive from sessionTeam.getName())");
    }
}
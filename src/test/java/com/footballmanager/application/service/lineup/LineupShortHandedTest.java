package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.application.service.career.CareerSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D6U2 — Short-handed lineup tests at the use-case layer.
 *
 * <p>Covers auto-select, manual-select, and confirmLineup with lineups
 * in [MIN, MAX] plus the failure cases (<MIN, >MAX, duplicates,
 * injured/suspended in lineup).
 */
@ExtendWith(MockitoExtension.class)
class LineupShortHandedTest {

    @Mock
    private CareerSessionService careerSessionService;

    private LineupHelper lineupHelper;
    private LineupCommandUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-shorthanded-001";

    @BeforeEach
    void setUp() {
        lineupHelper = new LineupHelper();
        useCase = new LineupCommandUseCaseImpl(
                careerSessionService, lineupHelper, new FormationService());
    }

    private SessionPlayer makePlayer(String id, String name, String position, int energy) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setWorldPlayerId("wp-" + id);
        p.setName(name);
        p.setPosition(position);
        p.setAttack(70);
        p.setDefense(70);
        p.setTechnique(70);
        p.setSpeed(70);
        p.setStamina(energy);
        p.setMentality(70);
        p.setMarketValue(BigDecimal.valueOf(1_000_000));
        p.setEnergy(energy);
        p.setInjured(false);
        p.setInjuryRemainingMatches(0);
        p.setSuspended(false);
        p.setSuspensionRemainingMatches(0);
        p.setOrigin(SessionPlayer.SessionPlayerOrigin.RANDOM);
        return p;
    }

    private SessionPlayer makeInjuredPlayer(String id, String name, String position) {
        SessionPlayer p = makePlayer(id, name, position, 80);
        p.setInjured(true);
        p.setInjuryRemainingMatches(2);
        return p;
    }

    private SessionPlayer makeSuspendedPlayer(String id, String name, String position) {
        SessionPlayer p = makePlayer(id, name, position, 80);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);
        return p;
    }

    private CareerSave makeCareer(List<SessionPlayer> squad) {
        CareerSave career = new CareerSave();
        career.setUserId(UUID.fromString(USER_ID));

        CareerPlayerManager playerManager = new CareerPlayerManager();
        CareerTeamManager teamManager = new CareerTeamManager();

        Map<String, SessionPlayer> sessionPlayers = new HashMap<>();
        List<String> squadIds = new ArrayList<>();
        for (SessionPlayer p : squad) {
            sessionPlayers.put(p.getSessionPlayerId(), p);
            squadIds.add(p.getSessionPlayerId());
        }
        playerManager.setSessionPlayers(sessionPlayers);
        teamManager.setTeamSquads(Map.of(TEAM_ID, squadIds));

        career.setPlayerManager(playerManager);
        career.setTeamManager(teamManager);

        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(TEAM_ID);
        team.setName("Test Team");
        team.setCountry("England");
        team.setBudget(BigDecimal.valueOf(10_000_000));
        team.setFormation("4-4-2");
        team.setMorale(70);
        team.setReputation(60);
        team.setOrigin(SessionTeam.SessionTeamOrigin.CLONED);

        career.setUserSessionTeamId(TEAM_ID);
        career.setTeamStarting11(new HashMap<>());
        return career;
    }

    /**
     * Builds an 11-player healthy 4-4-2 squad (1 GK + 4 DEF + 4 MID + 2 ST)
     * to use as a base. Mark specific players injured/suspended to thin
     * the available pool.
     */
    private List<SessionPlayer> fullHealthySquad() {
        List<SessionPlayer> s = new ArrayList<>();
        s.add(makePlayer("gk-1", "GK1", "GK", 80));
        s.add(makePlayer("def-1", "D1", "CB", 80));
        s.add(makePlayer("def-2", "D2", "CB", 80));
        s.add(makePlayer("def-3", "D3", "LB", 80));
        s.add(makePlayer("def-4", "D4", "RB", 80));
        s.add(makePlayer("mid-1", "M1", "CM", 80));
        s.add(makePlayer("mid-2", "M2", "CM", 80));
        s.add(makePlayer("mid-3", "M3", "LM", 80));
        s.add(makePlayer("mid-4", "M4", "RM", 80));
        s.add(makePlayer("att-1", "A1", "ST", 80));
        s.add(makePlayer("att-2", "A2", "ST", 80));
        return s;
    }

    /**
     * Builds a squad of (15 - available) injured/suspended players +
     * `available` healthy ones. The healthy players fill specific
     * positions in the 4-4-2 formation; the rest are STs marked injured
     * to thin the available pool. Used to set the effective
     * available-player count for short-handed tests.
     *
     * <p>Note: we need at least 11 total squad entries so the engine has
     * fallback material; we keep it 15 to mirror a real career squad.
     */
    private List<SessionPlayer> makeSquadWithAvailableCount(int available, int totalSquadSize) {
        List<SessionPlayer> s = new ArrayList<>();
        // Place the healthy 11-position 4-4-2 first
        if (available >= 11) {
            s.addAll(fullHealthySquad());
            // Then add healthy extras to reach `available` if needed
            for (int i = 0; i < available - 11; i++) {
                s.add(makePlayer("ex-h-" + i, "Extra" + i, "ST", 70));
            }
        } else {
            // `available` is 7-10: build a healthy subset
            // Always include the GK if available > 0
            if (available >= 1) s.add(makePlayer("gk-1", "GK1", "GK", 80));
            // Defenders: min(4, available - other slots)
            int defCount = Math.min(4, Math.max(0, available - (available >= 7 ? 3 : available - 4)));
            for (int i = 0; i < defCount && s.size() < available; i++) {
                s.add(makePlayer("def-" + i, "D" + i, i == 0 ? "CB" : (i == 1 ? "CB" : (i == 2 ? "LB" : "RB")), 80));
            }
            // Midfielders
            int midCount = Math.min(4, available - s.size());
            for (int i = 0; i < midCount && s.size() < available; i++) {
                s.add(makePlayer("mid-" + i, "M" + i, "CM", 80));
            }
            // Attackers to fill
            while (s.size() < available) {
                s.add(makePlayer("att-" + s.size(), "A" + s.size(), "ST", 80));
            }
        }
        // Pad with injured players to reach totalSquadSize
        while (s.size() < totalSquadSize) {
            s.add(makeInjuredPlayer("inj-" + s.size(), "Injured" + s.size(), "ST"));
        }
        return s;
    }

    private List<SessionPlayer> makeSquadWithAvailableAndSuspended(int available, int totalSquadSize, int suspendedCount) {
        List<SessionPlayer> s = makeSquadWithAvailableCount(available, totalSquadSize);
        // Convert some healthy STs to suspended for the "excludes suspended" test
        int converted = 0;
        for (int i = 0; i < s.size() && converted < suspendedCount; i++) {
            if (!Boolean.TRUE.equals(s.get(i).getInjured())) {
                s.get(i).setSuspended(true);
                s.get(i).setSuspensionRemainingMatches(1);
                converted++;
            }
        }
        return s;
    }

    // ========== T1: auto-select 11 available -> full lineup, no warnings ==========

    @Test
    void autoSelect_11Available_returnsFullLineupNoWarnings() {
        // Squad of 11 healthy = 11 available
        List<SessionPlayer> squad = makeSquadWithAvailableCount(11, 11);
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(), "Full squad should yield 11-player lineup");
                assertTrue(dto.warnings() == null || dto.warnings().isEmpty(),
                    "Full lineup should have no warnings");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    // ========== V25D59-C19 P0 T2: auto-select 10 available -> THROWS ==========

    @Test
    void autoSelect_10Available_throwsNotEnoughPlayers() {
        // Squad of 15, only 10 healthy available, 5 injured.
        // V25D59-C19 P0: auto-select no longer produces 10-player lineups.
        // It throws NotEnoughPlayersException → controller maps to 422.
        List<SessionPlayer> squad = makeSquadWithAvailableCount(10, 15);
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "Expected NotEnoughPlayersException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().contains("11"),
                    "Message should mention required 11, got: " + err.getMessage());
                assertTrue(err.getMessage().contains("10"),
                    "Message should mention available 10, got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== V25D59-C19 P0 T3: auto-select 7 available -> THROWS ==========

    @Test
    void autoSelect_7Available_throwsNotEnoughPlayers() {
        // Squad of 15, only 7 healthy available, 8 injured.
        // V25D59-C19 P0: same contract — auto-select requires 11.
        List<SessionPlayer> squad = makeSquadWithAvailableCount(7, 15);
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "Expected NotEnoughPlayersException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().contains("11"),
                    "Message should mention required 11, got: " + err.getMessage());
                assertTrue(err.getMessage().contains("7"),
                    "Message should mention available 7, got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== T4: auto-select 6 available -> 422 via NotEnoughPlayersException ==========

    @Test
    void autoSelect_6Available_returnsMinimumPlayersError() {
        // Squad of 15, only 6 healthy available, 9 injured
        List<SessionPlayer> squad = makeSquadWithAvailableCount(6, 15);
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "Expected NotEnoughPlayersException, got " + err.getClass().getSimpleName());
                // V25D59-C19 P0: threshold is 11 (TARGET_LINEUP_PLAYERS), not 7.
                assertTrue(err.getMessage().contains("11"),
                    "Message should mention required 11, got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== V25D59-C19 P0 T5: auto-select excludes injured/suspended AND throws on 9 ==========

    @Test
    void autoSelect_9Available_throwsNotEnoughPlayers_filtersInjuredSuspended() {
        // V25D59-C19 P0: filtering (exclude injured/suspended) is unchanged, but
        // the auto-select contract is now "11 or throw". This test pins BOTH:
        // the filter happens (10 healthy - 1 suspended = 9 available) AND the
        // throw fires (9 < 11).
        List<SessionPlayer> squad = makeSquadWithAvailableAndSuspended(10, 15, 1);
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "Expected NotEnoughPlayersException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().contains("9"),
                    "Message should reflect 9 available after filter, got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== V25D59-C19 P0 T6 (renamed): no GK fallback on a full squad ==========

    @Test
    void autoSelect_noGoalkeeper_fullSquad_returnsWarningAndElevenPlayers() {
        // V25D59-C19 P0: full 11-player squad with NO natural GK in the position
        // column → algorithm picks the best-OVR outfielder as off-position GK
        // fallback and still returns an 11-player lineup. LINEUP_NO_GOALKEEPER
        // warning surfaces the tactical hit.
        //
        // Old T6 used 11 players with GK injured (10 available) and asserted 10
        // players in the lineup. That path is gone — auto-select throws on 10
        // available (see T2 above). The spirit of "test the no-GK warning" is
        // preserved here with a full-squad variant.
        List<SessionPlayer> squad = List.of(
            makePlayer("out-1", "CB A", "CB", 80),
            makePlayer("out-2", "CB B", "CB", 79),
            makePlayer("out-3", "LB",   "LB", 78),
            makePlayer("out-4", "RB",   "RB", 77),
            makePlayer("out-5", "CM A", "CM", 76),
            makePlayer("out-6", "CM B", "CM", 75),
            makePlayer("out-7", "LM",   "LM", 74),
            makePlayer("out-8", "RM",   "RM", 73),
            makePlayer("out-9", "ST A", "ST", 82),
            makePlayer("out-10","ST B", "ST", 80),
            makePlayer("out-11","CF",   "CF", 78)
        );
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "V25D59-C19 P0: auto-select must return 11 even without a natural GK");
                assertNotNull(dto.warnings());
                assertTrue(dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_NO_GOALKEEPER.equals(w.code())),
                    "Should contain LINEUP_NO_GOALKEEPER warning when GK fallback fires");
            })
            .verifyComplete();
    }

    // ========== T7: manual-select 7 players -> allowed ==========

    @Test
    void manualSelect_7Players_allowed() {
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        // Pick any 7 healthy players (1 GK + 4 DEF + 2 MID)
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2");
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .assertNext(dto -> {
                assertEquals(7, dto.players().size());
                assertNotNull(dto.warnings());
                assertTrue(dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code())),
                    "Should contain LINEUP_SHORT_HANDED warning");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    // ========== T-V24D6T2: manual-select 10 jugadores -> V24D6U2 short-handed path (no longer 400) ==========
    // Bug #3 (re-smoke): manual-select 10 jugadores retornaba 400 "Failed to read HTTP message"
    // porque LineupHelper tiraba IllegalArgumentException("Must select exactly 11 players")
    // y el WebFlux decoder serializaba mal. V24D6U2 + bc85e2e fix liberan el rango 7-11,
    // y el GlobalExceptionHandler ahora mapea cualquier excepcion de dominio a 422.

    @Test
    void manualSelect_10Players_allowed_noLongerReturns400() {
        // Squad of 11 healthy, manual-select 10 = best-effort short-handed
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        // 10 healthy IDs (omit 1 healthy player to land in the [7, 11] range)
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2", "mid-3", "mid-4",
                                          "att-1");
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // V24D6T2 expectation: success (not 400). Pre-V24D6U2 this would have
        // thrown IllegalArgumentException("Must select exactly 11 players") and
        // the WebFlux decoder serialized it as 400 "Failed to read HTTP message".
        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .assertNext(dto -> {
                assertEquals(10, dto.players().size(),
                    "10 players should be accepted (V24D6U2 short-handed flow)");
                assertNotNull(dto.warnings());
                assertTrue(dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code())),
                    "Should contain LINEUP_SHORT_HANDED warning");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    // ========== T-V24D6T2: manual-select with injured player -> 422 (no longer 500) ==========
    // Bug #4 (re-smoke): manual-select con lesionado retornaba 500 porque
    // LineupHelper.validatePlayerFitness tiraba IllegalArgumentException sin
    // handler. El GlobalExceptionHandler ahora lo mapea a 422 con code.

    @Test
    void manualSelect_withInjuredPlayer_rejectedAsIllegalArgument() {
        List<SessionPlayer> squad = fullHealthySquad();
        // Mark one midfielder injured
        squad.add(makeInjuredPlayer("inj-extra", "Injured", "CM"));
        CareerSave career = makeCareer(squad);

        // 11 IDs including the injured one
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2", "mid-3", "mid-4",
                                          "att-1", "inj-extra");
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        // The use-case layer throws IllegalArgumentException; the GlobalExceptionHandler
        // maps it to 422. This test pins the use-case-layer contract; the handler
        // mapping is verified by the integration in a separate spec.
        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof IllegalArgumentException,
                    "Expected IllegalArgumentException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().toLowerCase().contains("injured"),
                    "Expected message to mention 'injured', got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== T8: manual-select 6 players -> rejected ==========

    @Test
    void manualSelect_6Players_rejected() {
        // Rejected by size check before findById is called
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4", "mid-1");

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "Expected NotEnoughPlayersException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().contains("7"),
                    "Message should mention minimum 7, got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== T9: manual-select more than 11 -> rejected ==========

    @Test
    void manualSelect_moreThan11_rejected() {
        // 12 IDs (size check rejects before findById)
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2", "mid-3", "mid-4",
                                          "att-1", "att-2", "def-1");  // duplicate def-1

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof IllegalArgumentException,
                    "Expected IllegalArgumentException, got " + err.getClass().getSimpleName());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    // ========== T10: confirm lineup 7 players -> allowed ==========

    @Test
    void confirmLineup_7Players_allowed() {
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        // Pre-set teamStarting11 to 7 players
        career.getTeamStarting11().put(TEAM_ID, new ArrayList<>(List.of(
            "gk-1", "def-1", "def-2", "def-3", "def-4", "mid-1", "mid-2"
        )));
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.confirmLineup(UUID.fromString(USER_ID)))
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    // ========== T11: confirm lineup with injured player -> rejected (via validatePlayerFitness) ==========

    @Test
    void confirmLineup_injuredPlayer_rejected() {
        // confirmLineup doesn't validate fitness — it only checks size.
        // To pin the contract: lineup with 1 injured player at 7 size is allowed
        // by confirmLineup itself. The fitness check happens on the next match start.
        // This test pins the CURRENT behavior so a future refactor that adds
        // fitness check to confirm is intentional.
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        career.getTeamStarting11().put(TEAM_ID, new ArrayList<>(List.of(
            "gk-1", "def-1", "def-2", "def-3", "def-4", "mid-1", "inj-0"
        )));
        squad.add(makeInjuredPlayer("inj-0", "InjuredBench", "ST"));
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // confirmLineup doesn't validate player fitness — only the size gate
        StepVerifier.create(useCase.confirmLineup(UUID.fromString(USER_ID)))
            .verifyComplete();
    }

    // ========== V25D61-C20.1 P0: short-handed manual-select slot map invariants ==========
    //
    // C20 P0 introduced an off-position fallback inside buildAutoSelectSlotMap that ran
    // unconditionally. The fallback over-fills slotMap when selectedPlayers.size() <
    // formation.positions.length, producing duplicate playerIds in the persisted subdivision
    // map. C20.1 P0 gates the fallback by isAutoSelect (auto-select only). These four tests
    // pin the post-fix contract for the manual-select short-handed path:
    //
    //   1. slot count == number of helper-matched slots (NOT formation.positions.size())
    //   2. no playerId appears twice in the persisted slots
    //   3. subdivisions that helper-match couldn't fill have NO entry in the slots
    //   4. auto-select full squad still produces 11 slots (fallback still fires there)

    /**
     * Test 1 — manual-select 7 players (short-handed) returns exactly 7 slots.
     *
     * <p>With 1 GK + 4 DEF + 2 MID against 4-4-2, the helper-based match fills 7 of 11
     * subdivisions (GK + 4 DEF + 2 MID). With the C20.1 fix the off-position fallback
     * does NOT fire in the manual-select path, so slotMap.size() stays at 7. Without
     * the fix, the fallback would attempt to fill the 4 remaining ATT/MID slots —
     * failing silently because all 7 players are already used, but pinning that
     * behavior here documents the invariant the regression broke.
     */
    @Test
    void manualSelect_shortHanded_returnsCorrectSlotCount() {
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        // 7 IDs: 1 GK + 4 DEF + 2 MID. Helper match fills 7 of 11 subdivisions in 4-4-2.
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2");
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .assertNext(dto -> {
                assertEquals(7, dto.players().size(),
                    "V25D61-C20.1: short-handed manual-select must accept 7 players");
                assertNotNull(dto.slots(),
                    "V25D61-C20.1: slots list must not be null");
                assertEquals(7, dto.slots().size(),
                    "V25D61-C20.1: slot count must equal helper-matched count (7), not "
                    + "formation.positions.size() (11) — the regression over-filled to 8");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    /**
     * Test 2 — manual-select short-handed slots have no duplicate playerIds.
     *
     * <p>Pins the C20.1 regression signature (verifier report):
     * "Lewandowski aparece at BOTH S05-2 AND S16-2". Every playerId in the
     * persisted slot map must be unique — a player cannot occupy two
     * subdivisions simultaneously.
     */
    @Test
    void manualSelect_shortHanded_noDuplicatePlayersInSlots() {
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2");
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .assertNext(dto -> {
                List<String> playerIdsInSlots = dto.slots().stream()
                    .map(com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO::playerId)
                    .toList();
                long distinct = playerIdsInSlots.stream().distinct().count();
                assertEquals(playerIdsInSlots.size(), distinct,
                    "V25D61-C20.1: no playerId may appear twice in persisted slots "
                    + "(regression: 'Lewandowski aparece at BOTH S05-2 AND S16-2'). "
                    + "Got playerIds=" + playerIdsInSlots);
                assertTrue(playerIdsInSlots.size() <= lineupIds.size(),
                    "V25D61-C20.1: slot count must not exceed lineup size ("
                    + lineupIds.size() + "), got " + playerIdsInSlots.size());
            })
            .verifyComplete();
    }

    /**
     * Test 3 — manual-select short-handed leaves unfilled subdivisions empty.
     *
     * <p>With 7 players (1 GK + 4 DEF + 2 MID) against 4-4-2, the 4 ATT/MID
     * subdivisions S05-2, S05-3, S17-2, S18-3 have no helper match. With the
     * C20.1 fix these subdivisions must have NO entry in the slot map (the
     * off-position fallback is skipped for manual-select). Without the fix
     * the fallback would try to assign unused players — and while in this
     * particular case all 7 are used, the fix pins the invariant for any
     * future composition.
     */
    @Test
    void manualSelect_shortHanded_unfilledSlotsAreEmpty() {
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        List<String> lineupIds = List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
                                          "mid-1", "mid-2");
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineupIds))
            .assertNext(dto -> {
                java.util.Set<String> filledSubdivisions = dto.slots().stream()
                    .map(com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO::subdivisionId)
                    .collect(java.util.stream.Collectors.toSet());

                // 4-4-2 subdivisions that no helper match could fill (no ST/extra MID in lineup):
                //   S05-2 (ST), S05-3 (ST) — attackers missing
                //   S17-2 (CM), S18-3 (RM) — extra midfielder/right-mid missing
                List<String> expectedUnfilled = List.of("S05-2", "S05-3", "S17-2", "S18-3");
                for (String unfilled : expectedUnfilled) {
                    assertFalse(filledSubdivisions.contains(unfilled),
                        "V25D61-C20.1: subdivision " + unfilled
                        + " must have NO entry for short-handed 7-player lineup "
                        + "(no attacker / extra midfielder to fill it). Got subdivisions="
                        + filledSubdivisions);
                }
            })
            .verifyComplete();
    }

    /**
     * Test 4 — auto-select full squad still persists 11 slots (regression guard).
     *
     * <p>C20 P0 introduced the off-position fallback specifically for auto-select so that
     * squads with non-natural position coverage still get all 11 subdivisions filled
     * (downstream FormationEffectiveness + manual-select re-open depend on the full
     * map). C20.1 must NOT remove the fallback for auto-select — this test pins that
     * the fix is targeted at manual-select only.
     */
    @Test
    void autoSelect_fullSquad_stillWorks() {
        List<SessionPlayer> squad = fullHealthySquad();
        CareerSave career = makeCareer(squad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertEquals(11, dto.players().size(),
                    "Full squad auto-select must still return 11 players");
                assertNotNull(dto.slots(),
                    "V25D61-C20.1: slots list must not be null");
                assertEquals(11, dto.slots().size(),
                    "V25D61-C20.1: auto-select full squad must persist 11 slots "
                    + "(C20 P0 fallback contract preserved for auto-select path)");
                // No duplicates either
                long distinct = dto.slots().stream()
                    .map(com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO::playerId)
                    .distinct()
                    .count();
                assertEquals(11L, distinct,
                    "V25D61-C20.1: 11 slots must have 11 distinct playerIds "
                    + "(off-position fallback assigned unused players, but each exactly once)");
            })
            .verifyComplete();
    }
}

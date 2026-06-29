package com.footballmanager.application.service.lineup;

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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * V24D6R T4: Manual lineup selection rejects suspended / injured players.
 *
 * <p>The helper-level rejection is already covered by
 * {@code LineupBlockingTest} (10 tests against
 * {@link LineupHelper#validatePlayerFitness}). This test class exercises the
 * same rejection through {@link LineupCommandUseCaseImpl#manualSelectLineup}
 * so the path career → use case → helper → exception is verified end-to-end.
 *
 * <p>Mockito is used only for {@link CareerRepository} (an interface); the
 * rest is hand-built to keep the test deterministic and avoid Spring context.
 */
@ExtendWith(MockitoExtension.class)
class V24ManualSelectLineupAvailabilityBlockingTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String TEAM_ID = "team-manual-001";

    @Mock
    private CareerSessionService careerSessionService;

    private LineupCommandUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new LineupCommandUseCaseImpl(
                careerSessionService, new LineupHelper(), new FormationService());
    }

    @Test
    void manualSelectLineup_rejectsSuspendedPlayer() {
        List<SessionPlayer> squad = buildSquadWith("sus-1", "Suspended Star", "ST",
                /*suspended*/ true, /*suspendRemaining*/ 1, /*injured*/ false, /*injRemaining*/ 0);
        CareerSave career = makeCareer(squad);

        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        List<String> selection = pickFirst11(squad);
        assertTrue(selection.contains("sus-1"),
                "suspended player must be in the selection to exercise rejection");

        StepVerifier.create(
                        useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", selection))
                .expectErrorSatisfies(ex -> {
                    assertTrue(ex instanceof IllegalArgumentException,
                            "expected IllegalArgumentException, got " + ex.getClass());
                    assertTrue(ex.getMessage().toLowerCase().contains("suspended"),
                            "expected message to mention 'suspended', got: " + ex.getMessage());
                })
                .verify();
    }

    @Test
    void manualSelectLineup_rejectsInjuredPlayer() {
        List<SessionPlayer> squad = buildSquadWith("inj-1", "Injured Star", "ST",
                /*suspended*/ false, /*suspendRemaining*/ 0, /*injured*/ true, /*injRemaining*/ 0);
        CareerSave career = makeCareer(squad);

        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        List<String> selection = pickFirst11(squad);
        assertTrue(selection.contains("inj-1"));

        StepVerifier.create(
                        useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", selection))
                .expectErrorSatisfies(ex -> {
                    assertTrue(ex instanceof IllegalArgumentException);
                    assertTrue(ex.getMessage().toLowerCase().contains("injured"),
                            "expected message to mention 'injured', got: " + ex.getMessage());
                })
                .verify();
    }

    @Test
    void manualSelectLineup_rejectsSuspensionRemainingPositiveEvenIfSuspendedFalse() {
        List<SessionPlayer> squad = buildSquadWith("sr-1", "Stale Suspension", "ST",
                /*suspended*/ false, /*suspendRemaining*/ 1, /*injured*/ false, /*injRemaining*/ 0);
        CareerSave career = makeCareer(squad);

        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        List<String> selection = pickFirst11(squad);
        assertTrue(selection.contains("sr-1"));

        StepVerifier.create(
                        useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", selection))
                .expectErrorSatisfies(ex -> {
                    assertTrue(ex instanceof IllegalArgumentException);
                    assertTrue(ex.getMessage().toLowerCase().contains("suspended"),
                            "expected message to mention 'suspended', got: " + ex.getMessage());
                    assertTrue(ex.getMessage().contains("1"),
                            "expected message to mention the remaining-match count, got: " + ex.getMessage());
                })
                .verify();
    }

    @Test
    void manualSelectLineup_rejectsInjuryRemainingPositiveEvenIfInjuredFalse() {
        List<SessionPlayer> squad = buildSquadWith("ir-1", "Stale Injury", "ST",
                /*suspended*/ false, /*suspendRemaining*/ 0, /*injured*/ false, /*injRemaining*/ 2);
        CareerSave career = makeCareer(squad);

        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        List<String> selection = pickFirst11(squad);
        assertTrue(selection.contains("ir-1"));

        StepVerifier.create(
                        useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", selection))
                .expectErrorSatisfies(ex -> {
                    assertTrue(ex instanceof IllegalArgumentException);
                    assertTrue(ex.getMessage().toLowerCase().contains("injured"),
                            "expected message to mention 'injured', got: " + ex.getMessage());
                })
                .verify();
    }

    // ========== Helpers ==========

    private static List<SessionPlayer> buildSquadWith(
            String flagPlayerId, String flagPlayerName, String flagPlayerPos,
            boolean suspended, int suspendRemaining,
            boolean injured, int injRemaining) {
        List<SessionPlayer> squad = new ArrayList<>();
        squad.add(makeGeneric("g-1", "GK One", "GK"));
        squad.add(makeGeneric("d-1", "Def One", "CB"));
        squad.add(makeGeneric("d-2", "Def Two", "CB"));
        squad.add(makeGeneric("d-3", "Def Three", "LB"));
        squad.add(makeGeneric("d-4", "Def Four", "RB"));
        squad.add(makeGeneric("m-1", "Mid One", "CM"));
        squad.add(makeGeneric("m-2", "Mid Two", "CM"));
        squad.add(makeGeneric("m-3", "Mid Three", "CM"));
        squad.add(makeGeneric("m-4", "Mid Four", "LM"));
        // ATT slot 0 is the flag player (in selection); ATT slot 1 is healthy.
        SessionPlayer flag = makeGeneric(flagPlayerId, flagPlayerName, flagPlayerPos);
        flag.setSuspended(suspended);
        flag.setSuspensionRemainingMatches(suspendRemaining);
        flag.setInjured(injured);
        flag.setInjuryRemainingMatches(injRemaining);
        if (injRemaining > 0) flag.setInjuryType("MATCH_INJURY");
        squad.add(flag);
        squad.add(makeGeneric("a-2", "Att Two", "ST"));
        return squad;
    }

    private static SessionPlayer makeGeneric(String id, String name, String pos) {
        SessionPlayer p = SessionPlayer.custom(
                name, 25, pos, 70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(1_000_000));
        p.setSessionPlayerId(id);
        p.setEnergy(80);
        p.setSuspended(false);
        p.setInjured(false);
        p.setInjuryRemainingMatches(0);
        p.setSuspensionRemainingMatches(0);
        return p;
    }

    private static List<String> pickFirst11(List<SessionPlayer> squad) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 11 && i < squad.size(); i++) {
            ids.add(squad.get(i).getSessionPlayerId());
        }
        return ids;
    }

    private static CareerSave makeCareer(List<SessionPlayer> squad) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_manual_select");
        save.setUserId(UUID.fromString(USER_ID));
        save.setUserSessionTeamId(TEAM_ID);

        CareerPlayerManager pm = new CareerPlayerManager();
        CareerTeamManager tm = new CareerTeamManager();

        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(TEAM_ID);
        team.setName("Test Team");
        team.setCountry("England");
        team.setBudget(BigDecimal.valueOf(10_000_000));
        team.setFormation("4-4-2");
        team.setMorale(70);
        team.setReputation(60);
        team.setOrigin(SessionTeam.SessionTeamOrigin.CLONED);
        tm.addSessionTeam(team);

        List<String> squadIds = new ArrayList<>();
        for (SessionPlayer p : squad) {
            pm.addSessionPlayer(p);
            squadIds.add(p.getSessionPlayerId());
        }
        tm.getTeamSquads().put(TEAM_ID, squadIds);

        save.setPlayerManager(pm);
        save.setTeamManager(tm);
        save.setTeamStarting11(new HashMap<>());
        return save;
    }
}

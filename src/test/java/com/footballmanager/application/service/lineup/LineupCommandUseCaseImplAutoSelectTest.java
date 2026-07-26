package com.footballmanager.application.service.lineup;

import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class LineupCommandUseCaseImplAutoSelectTest {

    @Mock
    private CareerSessionService careerSessionService;

    private LineupHelper lineupHelper;
    private LineupCommandUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-suspend-001";

    @BeforeEach
    void setUp() {
        lineupHelper = new LineupHelper();
        useCase = new LineupCommandUseCaseImpl(
                careerSessionService, lineupHelper, new FormationService());
    }

    private SessionPlayer makePlayer(String id, String name, String position, int overall, int energy, boolean injured, boolean suspended, int suspensionRemainingMatches) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setWorldPlayerId("wp-" + id);
        p.setName(name);
        p.setPosition(position);
        p.setAttack(overall);
        p.setDefense(overall);
        p.setTechnique(overall);
        p.setSpeed(overall);
        p.setStamina(energy);
        p.setMentality(overall);
        p.setMarketValue(BigDecimal.valueOf(1_000_000));
        p.setEnergy(energy);
        p.setInjured(injured);
        p.setSuspended(suspended);
        p.setSuspensionRemainingMatches(suspensionRemainingMatches);
        p.setOrigin(SessionPlayer.SessionPlayerOrigin.RANDOM);
        return p;
    }

    private SessionPlayer makePlayerFull(String id, String name, String position, int overall, int energy,
                                         Boolean injured, Integer injuryRemainingMatches,
                                         boolean suspended, int suspensionRemainingMatches) {
        SessionPlayer p = makePlayer(id, name, position, overall, energy, false, suspended, suspensionRemainingMatches);
        p.setInjured(injured);
        p.setInjuryRemainingMatches(injuryRemainingMatches);
        return p;
    }

    private CareerSave makeCareer(List<SessionPlayer> players) {
        CareerSave career = new CareerSave();
        career.setUserId(UUID.fromString(USER_ID));

        CareerPlayerManager playerManager = new CareerPlayerManager();
        CareerTeamManager teamManager = new CareerTeamManager();

        Map<String, SessionPlayer> sessionPlayers = new HashMap<>();
        List<String> squadIds = new ArrayList<>();
        for (SessionPlayer p : players) {
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
        teamManager.addSessionTeam(team);

        career.setUserSessionTeamId(TEAM_ID);
        career.setTeamStarting11(new HashMap<>());

        return career;
    }

    @Test
    void autoSelect_excludesSuspendedPlayers() {
        List<SessionPlayer> players = List.of(
            makePlayer("sus-1", "Suspended Star", "ST", 90, 80, false, true, 1),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 75, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 74, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Available Striker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0),
            makePlayer("att-3", "Third Striker", "ST", 76, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());

                boolean foundSuspended = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Suspended Star"));
                assertFalse(foundSuspended, "Suspended player should not be in lineup");

                boolean foundAvailable = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Available Striker"));
                assertTrue(foundAvailable, "Available striker should be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    @Test
    void autoSelect_excludesSuspensionRemainingPositivePlayers() {
        List<SessionPlayer> players = List.of(
            makePlayer("sr-1", "Suspended Remaining", "CM", 88, 80, false, false, 1),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 85, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 84, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Attacker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0),
            makePlayer("att-3", "Third Striker", "ST", 76, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());

                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Suspended Remaining"));
                assertFalse(found, "Player with suspensionRemainingMatches=1 should not be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    @Test
    void autoSelect_excludesInjuredTrue() {
        List<SessionPlayer> players = List.of(
            makePlayerFull("inj-1", "Injured Star", "ST", 90, 80, true, null, false, 0),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 75, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 74, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Available Striker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0),
            makePlayer("att-3", "Third Striker", "ST", 76, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Injured Star"));
                assertFalse(found, "Injured player should not be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    @Test
    void autoSelect_excludesInjuryRemainingMatchesPositiveEvenIfInjuredFalse() {
        List<SessionPlayer> players = List.of(
            makePlayerFull("stale-1", "Stale Injury Player", "CM", 88, 80, false, 2, false, 0),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 85, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 84, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Attacker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0),
            makePlayer("att-3", "Third Striker", "ST", 76, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Stale Injury Player"));
                assertFalse(found, "Player with injuryRemainingMatches=2 should not be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    @Test
    void autoSelect_excludesInjuryRemainingMatchesPositiveWhenInjuredNull() {
        List<SessionPlayer> players = List.of(
            makePlayerFull("null-inj-1", "Null Injured Positive Remaining", "ST", 88, 80, null, 1, false, 0),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 75, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 74, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Available Striker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0),
            makePlayer("att-3", "Third Striker", "ST", 76, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Null Injured Positive Remaining"));
                assertFalse(found, "Player with null injured but injuryRemainingMatches=1 should not be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    @Test
    void autoSelect_allowsHealthyPlayerWithInjuredFalseAndRemainingZero() {
        // Use 4-4-2 so the CM can fill a midfielder slot
        List<SessionPlayer> players = List.of(
            makePlayerFull("inj-mid", "Injured Mid", "CM", 88, 80, true, null, false, 0),
            makePlayerFull("healthy-1", "Healthy Player", "CM", 75, 80, false, 0, false, 0),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 74, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 73, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Attacker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean foundHealthy = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Healthy Player"));
                assertTrue(foundHealthy, "Healthy player with injured=false and remaining=0 should be in lineup");
                boolean foundInjured = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Injured Mid"));
                assertFalse(foundInjured, "Injured player should not be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    @Test
    void autoSelect_allowsHealthyPlayerWithInjuredNullAndRemainingNull() {
        // Use 4-4-2 so the CM can fill a midfielder slot
        List<SessionPlayer> players = List.of(
            makePlayerFull("inj-mid", "Injured Mid Null", "CM", 88, 80, true, null, false, 0),
            makePlayerFull("null-healthy-1", "Null Healthy Player", "CM", 75, 80, null, null, false, 0),
            makePlayer("gk-1", "Good GK", "GK", 70, 80, false, false, 0),
            makePlayer("def-1", "Def A", "CB", 72, 80, false, false, 0),
            makePlayer("def-2", "Def B", "CB", 71, 80, false, false, 0),
            makePlayer("def-3", "Def C", "LB", 68, 80, false, false, 0),
            makePlayer("def-4", "Def D", "RB", 67, 80, false, false, 0),
            makePlayer("mid-1", "Mid A", "CM", 74, 80, false, false, 0),
            makePlayer("mid-2", "Mid B", "CM", 73, 80, false, false, 0),
            makePlayer("mid-3", "Mid C", "LM", 69, 80, false, false, 0),
            makePlayer("mid-4", "Mid D", "RM", 68, 80, false, false, 0),
            makePlayer("att-1", "Attacker", "ST", 82, 80, false, false, 0),
            makePlayer("att-2", "Second Striker", "ST", 78, 80, false, false, 0)
        );

        CareerSave career = makeCareer(players);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean foundHealthy = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Null Healthy Player"));
                assertTrue(foundHealthy, "Player with null injured and null remaining should be in lineup");
                boolean foundInjured = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Injured Mid Null"));
                assertFalse(foundInjured, "Injured player should not be in lineup");
            })
            .verifyComplete();

        verify(careerSessionService).saveCareer(any());
    }

    // ========== MVP1-lineup-cancha-1.6: HELPER-BASED match (F2) + formation persistence (F1) ==========

    @Test
    @DisplayName("autoSelect 4-4-2 usa WINGER para banda antes que forzar un ATT como MID")
    void autoSelect_4_4_2_prefersWingerForWideMidfield() {
        List<SessionPlayer> squad442WithWinger = List.of(
            makePlayer("gk-wing", "GK Wing", "GK", 80, 80, false, false, 0),
            makePlayer("cb1-wing", "CB A Wing", "CB", 78, 80, false, false, 0),
            makePlayer("cb2-wing", "CB B Wing", "CB", 77, 80, false, false, 0),
            makePlayer("lb-wing", "LB Wing", "LB", 76, 80, false, false, 0),
            makePlayer("rb-wing", "RB Wing", "RB", 75, 80, false, false, 0),
            makePlayer("cm1-wing", "CM A Wing", "CM", 74, 80, false, false, 0),
            makePlayer("cm2-wing", "CM B Wing", "CM", 73, 80, false, false, 0),
            makePlayer("cm3-wing", "CM C Wing", "CM", 72, 80, false, false, 0),
            makePlayer("wing-wing", "Natural Winger", "WINGER", 71, 80, false, false, 0),
            makePlayer("st1-wing", "ST A Wing", "ST", 84, 80, false, false, 0),
            makePlayer("st2-wing", "ST B Wing", "ST", 83, 80, false, false, 0),
            makePlayer("st3-wing", "Extra Central Forward", "ATT", 90, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad442WithWinger);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                assertTrue(dto.players().stream().anyMatch(p -> "Natural Winger".equals(p.name())),
                    "Si hay WINGER disponible, debe cubrir la banda del 4-4-2 antes que un ATT central");
                assertFalse(dto.players().stream().anyMatch(p -> "ST B Wing".equals(p.name())),
                    "Con tres delanteros centrales y un winger, el tercero de la rotacion debe quedar fuera antes que el winger natural");
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        Map<String, String> teamSlots = captor.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertTrue(
            "wing-wing".equals(teamSlots.get("S16-2")) || "wing-wing".equals(teamSlots.get("S18-2")),
            "El WINGER natural debe quedar en LM/RM del 4-4-2, no gastarse en un CM");
    }

    @Test
    @DisplayName("autoSelect 4-3-3 reserva WINGER para LW/RW, no para CM")
    void autoSelect_4_3_3_prefersWingersForFrontThree() {
        List<SessionPlayer> squad433WithWingers = List.of(
            makePlayer("gk-433wing", "GK 433 Wing", "GK", 80, 80, false, false, 0),
            makePlayer("cb1-433wing", "CB A 433 Wing", "CB", 78, 80, false, false, 0),
            makePlayer("cb2-433wing", "CB B 433 Wing", "CB", 77, 80, false, false, 0),
            makePlayer("lb-433wing", "LB 433 Wing", "LB", 76, 80, false, false, 0),
            makePlayer("rb-433wing", "RB 433 Wing", "RB", 75, 80, false, false, 0),
            makePlayer("cm1-433wing", "CM A 433 Wing", "CM", 74, 80, false, false, 0),
            makePlayer("cm2-433wing", "CM B 433 Wing", "CM", 73, 80, false, false, 0),
            makePlayer("cm3-433wing", "CM C 433 Wing", "CM", 72, 80, false, false, 0),
            makePlayer("lw-433wing", "Natural Left Winger", "WINGER", 71, 80, false, false, 0),
            makePlayer("rw-433wing", "Natural Right Winger", "WINGER", 70, 80, false, false, 0),
            makePlayer("st-433wing", "Central Striker", "ST", 84, 80, false, false, 0),
            makePlayer("att-433wing", "Extra Central Forward 433", "ATT", 83, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad433WithWingers);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                assertTrue(dto.players().stream().anyMatch(p -> "Natural Left Winger".equals(p.name())));
                assertTrue(dto.players().stream().anyMatch(p -> "Natural Right Winger".equals(p.name())));
                assertFalse(dto.players().stream().anyMatch(p -> "Extra Central Forward 433".equals(p.name())),
                    "El segundo ATT central no debe desplazar a un winger natural en un 4-3-3");
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        Map<String, String> teamSlots = captor.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertTrue(
            Set.of("lw-433wing", "rw-433wing").contains(teamSlots.get("S04-1"))
                && Set.of("lw-433wing", "rw-433wing").contains(teamSlots.get("S06-3")),
            "Los WINGER naturales deben quedar en LW/RW del 4-3-3, no en los CM");
    }

    @Test
    @DisplayName("autoSelect reserva WINGER para front-three y usa fallback cercano en CM")
    void autoSelect_midfieldFallback_prefersTacticalFitOverRawOvr() {
        List<SessionPlayer> squadThinMidfield = List.of(
            makePlayer("gk-midfit", "GK MidFit", "GK", 80, 80, false, false, 0),
            makePlayer("cb1-midfit", "CB A MidFit", "CB", 78, 80, false, false, 0),
            makePlayer("cb2-midfit", "CB B MidFit", "CB", 77, 80, false, false, 0),
            makePlayer("lb-midfit", "LB MidFit", "LB", 76, 80, false, false, 0),
            makePlayer("rb-midfit", "RB MidFit", "RB", 75, 80, false, false, 0),
            makePlayer("cm1-midfit", "CM A MidFit", "CM", 74, 80, false, false, 0),
            makePlayer("cm2-midfit", "CM B MidFit", "CM", 73, 80, false, false, 0),
            makePlayer("wing-midfit", "Emergency Winger MidFit", "WINGER", 68, 80, false, false, 0),
            makePlayer("lw-midfit", "Natural Left Winger MidFit", "LW", 72, 80, false, false, 0),
            makePlayer("rw-midfit", "Natural Right Winger MidFit", "RW", 71, 80, false, false, 0),
            makePlayer("st-midfit", "Central Striker MidFit", "ST", 84, 80, false, false, 0),
            makePlayer("att-midfit", "High OVR Pure Forward MidFit", "ATT", 90, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squadThinMidfield);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                assertTrue(dto.players().stream().anyMatch(p -> "Emergency Winger MidFit".equals(p.name())),
                    "El WINGER generico debe entrar en el XI por encaje tactico aunque tenga menor OVR");
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        Map<String, String> teamSlots = captor.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertTrue(
            "wing-midfit".equals(teamSlots.get("S04-1"))
                || "wing-midfit".equals(teamSlots.get("S06-3")),
            "En un 4-3-3 el WINGER generico debe reservarse para LW/RW antes que caer en CM. slots=" + teamSlots);
        assertTrue(
            "rw-midfit".equals(teamSlots.get("S17-1"))
                || "rw-midfit".equals(teamSlots.get("S17-2"))
                || "rw-midfit".equals(teamSlots.get("S17-3")),
            "Si falta un CM, el fallback debe ser un perfil de banda/medio cercano y no un ATT puro. slots=" + teamSlots);
        assertFalse(
            "att-midfit".equals(teamSlots.get("S17-1"))
                || "att-midfit".equals(teamSlots.get("S17-2"))
                || "att-midfit".equals(teamSlots.get("S17-3")),
            "Un ATT puro de mayor OVR puede competir como delantero, pero no debe ocupar el fallback de CM");
    }

    @Test
    @DisplayName("autoSelect distingue carrileros de linea media y extremos mediapunta")
    void autoSelect_respectsFormationLineCountsForWingRoles() {
        List<SessionPlayer> squadWithWingRoles = List.of(
            makePlayer("gk-line", "GK Line", "GK", 80, 80, false, false, 0),
            makePlayer("cb1-line", "CB A Line", "CB", 78, 80, false, false, 0),
            makePlayer("cb2-line", "CB B Line", "CB", 77, 80, false, false, 0),
            makePlayer("cb3-line", "CB C Line", "CB", 76, 80, false, false, 0),
            makePlayer("cb4-line", "Extra CB Line", "CB", 90, 80, false, false, 0),
            makePlayer("lb-line", "LB Line", "LB", 69, 80, false, false, 0),
            makePlayer("rb-line", "RB Line", "RB", 68, 80, false, false, 0),
            makePlayer("cm1-line", "CM A Line", "CM", 74, 80, false, false, 0),
            makePlayer("cm2-line", "CM B Line", "CM", 73, 80, false, false, 0),
            makePlayer("cm3-line", "CM C Line", "CM", 72, 80, false, false, 0),
            makePlayer("wing1-line", "Wing A Line", "WINGER", 71, 80, false, false, 0),
            makePlayer("wing2-line", "Wing B Line", "WINGER", 70, 80, false, false, 0),
            makePlayer("st1-line", "ST A Line", "ST", 84, 80, false, false, 0),
            makePlayer("st2-line", "ST B Line", "ST", 83, 80, false, false, 0)
        );

        CareerSave career352 = makeCareer(squadWithWingRoles);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career352));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "3-5-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor352 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor352.capture());
        Map<String, String> slots352 = captor352.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertTrue(Set.of("wing1-line", "wing2-line").contains(slots352.get("S15-1")));
        assertTrue(Set.of("wing1-line", "wing2-line").contains(slots352.get("S18-3")));
        assertFalse(Set.of("S15-1", "S17-1", "S17-2", "S17-3", "S18-3").stream()
            .map(slots352::get)
            .anyMatch("cb4-line"::equals),
            "El central extra puede ganar un puesto de CB, pero no debe invadir la linea media del 3-5-2");

        clearInvocations(careerSessionService);
        CareerSave career4231 = makeCareer(squadWithWingRoles);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career4231));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-2-3-1"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                assertTrue(dto.players().stream().anyMatch(p -> "Wing A Line".equals(p.name())));
                assertTrue(dto.players().stream().anyMatch(p -> "Wing B Line".equals(p.name())));
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor4231 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor4231.capture());
        Map<String, String> slots4231 = captor4231.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertTrue(Set.of("wing1-line", "wing2-line").contains(slots4231.get("S10-2")));
        assertTrue(Set.of("wing1-line", "wing2-line").contains(slots4231.get("S12-2")));

        clearInvocations(careerSessionService);
        CareerSave career343 = makeCareer(squadWithWingRoles);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career343));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "3-4-3"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                assertTrue(dto.players().stream().anyMatch(p -> "Wing A Line".equals(p.name())));
                assertTrue(dto.players().stream().anyMatch(p -> "Wing B Line".equals(p.name())));
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor343 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor343.capture());
        Map<String, String> slots343 = captor343.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertTrue(Set.of("wing1-line", "wing2-line").contains(slots343.get("S04-1")),
            "En 3-4-3 el WINGER natural debe reservarse para LW antes que carrilero/midfield fallback");
        assertTrue(Set.of("wing1-line", "wing2-line").contains(slots343.get("S06-3")),
            "En 3-4-3 el WINGER natural debe reservarse para RW antes que carrilero/midfield fallback");
        assertFalse(Set.of("st1-line", "st2-line").contains(slots343.get("S15-1")),
            "En 3-4-3 un delantero puro no debe ocupar LWB si hay laterales/carrileros disponibles. slots=" + slots343);
        assertFalse(Set.of("st1-line", "st2-line").contains(slots343.get("S18-3")),
            "En 3-4-3 un delantero puro no debe ocupar RWB si hay laterales/carrileros disponibles. slots=" + slots343);
    }

    @Test
    @DisplayName("autoSelect respeta lado natural LB/RB para carrileros LWB/RWB")
    void autoSelect_3_5_2_keepsFullbacksOnNaturalSideForWingbacks() {
        List<SessionPlayer> squadWithSideSpecificFullbacks = List.of(
            makePlayer("gk-side", "GK Side", "GK", 80, 80, false, false, 0),
            makePlayer("cb1-side", "CB A Side", "CB", 78, 80, false, false, 0),
            makePlayer("cb2-side", "CB B Side", "CB", 77, 80, false, false, 0),
            makePlayer("cb3-side", "CB C Side", "CB", 76, 80, false, false, 0),
            makePlayer("rb-side", "Right Back Side", "RB", 75, 80, false, false, 0),
            makePlayer("lb-side", "Left Back Side", "LB", 75, 80, false, false, 0),
            makePlayer("cm1-side", "CM A Side", "CM", 74, 80, false, false, 0),
            makePlayer("cm2-side", "CM B Side", "CM", 73, 80, false, false, 0),
            makePlayer("cm3-side", "CM C Side", "CM", 72, 80, false, false, 0),
            makePlayer("st1-side", "ST A Side", "ST", 84, 80, false, false, 0),
            makePlayer("st2-side", "ST B Side", "ST", 83, 80, false, false, 0)
        );

        CareerSave career352 = makeCareer(squadWithSideSpecificFullbacks);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career352));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "3-5-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor352 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor352.capture());
        Map<String, String> slots352 = captor352.getValue().getTeamStarting11Subdivision().get(TEAM_ID);

        assertEquals("lb-side", slots352.get("S15-1"), "El carrilero izquierdo debe priorizar LB/LWB/LM/LW, no cruzar un RB");
        assertEquals("rb-side", slots352.get("S18-3"), "El carrilero derecho debe priorizar RB/RWB/RM/RW, no cruzar un LB");
    }

    @Test
    @DisplayName("autoSelect 4-2-3-1 no deja WINGER sanos en banco con MID improvisado en RW")
    void autoSelect_4_2_3_1_realWideProfilesBeatCentralMidAtRw() {
        List<SessionPlayer> lasPalmasLikeSquad = List.of(
            makePlayer("gk-lp", "Aaron Escandell", "GK", 76, 80, false, false, 0),
            makePlayer("rb-lp", "Alex Suarez", "DEF", 76, 80, false, false, 0),
            makePlayer("lb-lp", "Marcos Cardenas", "DEF", 75, 80, false, false, 0),
            makePlayer("cb1-lp", "Scott McKenna", "DEF", 76, 80, false, false, 0),
            makePlayer("cb2-lp", "Sergi Cardona", "DEF", 76, 80, false, false, 0),
            makePlayer("cm1-lp", "Kirian Rodriguez", "MID", 78, 80, false, false, 0),
            makePlayer("cm2-lp", "Enzo Loiodice", "MID", 77, 80, false, false, 0),
            makePlayer("cam-lp", "Alberto Moleiro", "MID", 80, 80, false, false, 0),
            makePlayer("campana-lp", "Campaña", "MID", 79, 80, false, false, 0),
            makePlayer("manu-lp", "Manu Fuster", "WINGER", 76, 80, false, false, 0),
            makePlayer("marvin-lp", "Marvin Park", "WINGER", 72, 80, false, false, 0),
            makePlayer("pejino-lp", "Pejino", "WINGER", 72, 80, false, false, 0),
            makePlayer("st-lp", "Oliver McBurnie", "ATT", 78, 80, false, false, 0),
            makePlayer("st2-lp", "Fábio Silva", "ATT", 78, 80, false, false, 0)
        );

        CareerSave career = makeCareer(lasPalmasLikeSquad);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(careerSessionService).saveCareer(any());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-2-3-1"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        Map<String, String> slots = captor.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(slots);
        assertTrue(Set.of("manu-lp", "marvin-lp", "pejino-lp").contains(slots.get("S10-2")),
            "RW debe ser WINGER natural si hay WINGER sano disponible. slots=" + slots);
        assertTrue(Set.of("manu-lp", "marvin-lp", "pejino-lp").contains(slots.get("S12-2")),
            "LW debe ser WINGER natural si hay WINGER sano disponible. slots=" + slots);
        assertNotEquals("campana-lp", slots.get("S10-2"),
            "Campaña MID no debe quedar de RW mientras Marvin/Pejino WINGER están disponibles. slots=" + slots);
    }

    /**
     * MVP1-lineup-cancha-1.6 (Test 3, F2): HELPER-BASED match asigna los 11 slots
     * de un 4-3-3 con squad de posiciones mixtas (CB/LB/RB/CDM/CAM/CM/LW/ST/RW).
     *
     * <p>Con EXACT match (sprint 1.5), un squad así habría fallado en algunos slots:
     * los CM slots del 4-3-3 requieren "CM" exacto, pero CDM y CAM no matchean.
     * HELPER-BASED matchea CDM/CAM/CM via isMidfielder.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: HELPER-BASED match asigna 11 slots con squad mixto 4-3-3")
    void buildAutoSelectSlotMap_helperBased_assigns11Slots() {
        // Squad 4-3-3 con posiciones mixtas — los 3 MID slots del 4-3-3 requieren
        // CM exacto, pero CDM y CAM no son CM. EXACT fallaría 2-3 slots.
        List<SessionPlayer> squad433Mixed = List.of(
            makePlayer("gk-mix", "GK Mix", "GK", 80, 80, false, false, 0),
            makePlayer("lb-mix", "LB Mix", "LB", 80, 80, false, false, 0),
            makePlayer("cb1-mix", "CB Mix A", "CB", 80, 80, false, false, 0),
            makePlayer("cb2-mix", "CB Mix B", "CB", 80, 80, false, false, 0),
            makePlayer("rb-mix", "RB Mix", "RB", 80, 80, false, false, 0),
            makePlayer("cdm-mix", "CDM Mix", "CDM", 80, 80, false, false, 0),
            makePlayer("cam-mix", "CAM Mix", "CAM", 80, 80, false, false, 0),
            makePlayer("cm-mix", "CM Mix", "CM", 80, 80, false, false, 0),
            makePlayer("lw-mix", "LW Mix", "LW", 80, 80, false, false, 0),
            makePlayer("st-mix", "ST Mix", "ST", 80, 80, false, false, 0),
            makePlayer("rw-mix", "RW Mix", "RW", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad433Mixed);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<String> lineup = List.of("gk-mix", "lb-mix", "cb1-mix", "cb2-mix", "rb-mix",
            "cdm-mix", "cam-mix", "cm-mix", "lw-mix", "st-mix", "rw-mix");

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-3-3", lineup, List.of()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots, "HELPER-BASED debe poblar el subdivision map");
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F2: HELPER-BASED llena los 11 slots aunque el squad tenga CDM/CAM (no-CM midfielders)");

        // Verificar que los slots que EXACT habría fallado (CDM/CAM slots) están asignados.
        assertEquals(11, teamSlots.values().stream().distinct().count(),
            "MVP1-lineup-cancha-1.6 F2: cada jugador seleccionado queda en un unico slot");
        assertTrue(teamSlots.values().containsAll(lineup),
            "MVP1-lineup-cancha-1.6 F2: CDM/CAM/CM y extremos quedan asignados sin depender de IDs visuales antiguos");

        // Verificar formación persistida (F1).
        assertEquals("4-3-3", saved.getTeamStarting11Formation().get(TEAM_ID),
            "MVP1-lineup-cancha-1.6 F1: formación persistida en manualSelect HELPER-BASED");
    }

    /**
     * MVP1-lineup-cancha-1.6 (Test 4, F2): HELPER-BASED es super-set de EXACT.
     *
     * <p>Squad 4-3-3 donde EXACT match solo habría llenado 5-7 slots (sin CB players,
     * sin CM players — solo LB/CAM/LM/etc.). HELPER-BASED llena los 11.
     *
     * <p>Este test demuestra la propiedad central del fix: HELPER usa isDefender /
     * isMidfielder / isAttacker en vez de comparación exacta, así que cualquier
     * jugador con posición compatible (no solo idéntica) llena el slot.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: HELPER-BASED es super-set de EXACT — llena 11 slots que EXACT no podía")
    void buildAutoSelectSlotMap_helperBased_superSetOfExact() {
        // Squad 4-3-3 con posiciones mixtas SIN CB y SIN CM:
        // - DEF: GK, LB, LB, RB, RB (5 defenders, ningún CB)
        // - MID: CDM, CAM, LM, LW (4 midfielders, ningún CM)
        // - ATT: ST, RW (2 attackers, ningún LW extra para ST slot)
        // Con EXACT match, los 2 CB slots del 4-3-3 + 3 CM slots quedarían
        // vacíos (5 slots sin asignar), solo 6 se llenarían.
        // Con HELPER-BASED, todos los 11 slots se llenan.
        List<SessionPlayer> squadNoExact = List.of(
            makePlayer("gk-se", "GK SE", "GK", 80, 80, false, false, 0),
            makePlayer("lb1-se", "LB SE 1", "LB", 80, 80, false, false, 0),
            makePlayer("lb2-se", "LB SE 2", "LB", 80, 80, false, false, 0),
            makePlayer("rb1-se", "RB SE 1", "RB", 80, 80, false, false, 0),
            makePlayer("rb2-se", "RB SE 2", "RB", 80, 80, false, false, 0),
            makePlayer("cdm-se", "CDM SE", "CDM", 80, 80, false, false, 0),
            makePlayer("cam-se", "CAM SE", "CAM", 80, 80, false, false, 0),
            makePlayer("lm-se", "LM SE", "LM", 80, 80, false, false, 0),
            makePlayer("lw-se", "LW SE", "LW", 80, 80, false, false, 0),
            makePlayer("st-se", "ST SE", "ST", 80, 80, false, false, 0),
            makePlayer("rw-se", "RW SE", "RW", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squadNoExact);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Forzar el orden del lineup — manualmente, sin auto-select — para tener
        // determinismo en la prueba (auto-select filtraría por categoría y ordenaría
        // por overall, lo cual haría más difícil asegurar qué jugador llena qué slot).
        List<String> lineup = List.of("gk-se", "lb1-se", "lb2-se", "rb1-se", "rb2-se",
            "cdm-se", "cam-se", "lm-se", "lw-se", "st-se", "rw-se");

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-3-3", lineup, List.of()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F2: HELPER-BASED llena 11 slots aunque EXACT solo habría llenado ~6");

        // Slots que EXACT no habría podido llenar (no hay CB ni CM en el squad):
        // - S22-2 y S23-2 (CB slots) → HELPER matchea con LB/RB via isDefender
        // - S13-2, S14-2, S15-2 (CM slots) → HELPER matchea con CDM/CAM/LM via isMidfielder
        assertEquals(11, teamSlots.values().stream().distinct().count(),
            "MVP1-lineup-cancha-1.6 F2: HELPER lleno 11 slots sin duplicar jugadores");
        assertTrue(teamSlots.values().containsAll(lineup),
            "MVP1-lineup-cancha-1.6 F2: laterales/volantes/extremos cubren la formacion sin depender de IDs visuales antiguos");

        // Verificar formación persistida (F1).
        assertEquals("4-3-3", saved.getTeamStarting11Formation().get(TEAM_ID),
            "MVP1-lineup-cancha-1.6 F1: formación persistida junto con HELPER-BASED super-set");
    }

    /**
     * MVP1-lineup-cancha-1.6 (Test 6, F1): autoSelectLineup persiste el código de
     * formación en career.getTeamStarting11Formation().get(teamId).
     *
     * getCurrentLineup recomputaba la formación contando DEF/MID/ATT de la lineup
     * persistida, devolviendo el código viejo aunque el usuario hubiera cambiado
     * la formación.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: autoSelectLineup persiste formation code en teamStarting11Formation")
    void autoSelect_persistsFormationCode() {
        // Squad completo para 4-3-3: GK + LB + 2 CB + RB + 3 CM + LW + ST + RW
        List<SessionPlayer> squad433 = List.of(
            makePlayer("gk-pf", "GK PersistFormation", "GK", 80, 80, false, false, 0),
            makePlayer("lb-pf", "LB PersistFormation", "LB", 80, 80, false, false, 0),
            makePlayer("cb1-pf", "CB A PersistFormation", "CB", 80, 80, false, false, 0),
            makePlayer("cb2-pf", "CB B PersistFormation", "CB", 80, 80, false, false, 0),
            makePlayer("rb-pf", "RB PersistFormation", "RB", 80, 80, false, false, 0),
            makePlayer("cm1-pf", "CM A PersistFormation", "CM", 80, 80, false, false, 0),
            makePlayer("cm2-pf", "CM B PersistFormation", "CM", 80, 80, false, false, 0),
            makePlayer("cm3-pf", "CM C PersistFormation", "CM", 80, 80, false, false, 0),
            makePlayer("lw-pf", "LW PersistFormation", "LW", 80, 80, false, false, 0),
            makePlayer("st-pf", "ST PersistFormation", "ST", 80, 80, false, false, 0),
            makePlayer("rw-pf", "RW PersistFormation", "RW", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad433);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        // F1: formación persistida por team.
        String persistedFormation = saved.getTeamStarting11Formation().get(TEAM_ID);
        assertNotNull(persistedFormation,
            "MVP1-lineup-cancha-1.6 F1: teamStarting11Formation debe estar poblado para el team");
        assertEquals("4-3-3", persistedFormation,
            "MVP1-lineup-cancha-1.6 F1: formation code persistido = 4-3-3 (no inferido de DEF/MID/ATT counts)");

        assertEquals("4-3-3", saved.getSessionTeam(TEAM_ID).getFormation(),
            "SessionTeam.formation debe quedar sincronizada para que live/modal/fallbacks no vuelvan a 4-4-2");

        // Verificar que también persiste el subdivision map (HELPER-BASED).
        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size());
    }

    /**
     * 11 healthy) → autoSelect produce exactamente 11 slots. Pin del happy path
     * para el contrato "auto-select siempre produce 11 (o tira)".
     *
     * <p>Cubre la propiedad principal del fix: aunque el algoritmo pickee
     * off-position por accidente (no debería pasar con un squad así), debe
     * garantizar 11 antes de persistir.
     */
    @Test
    @DisplayName("autoSelect 4-4-2 con squad completo → 11 slots, sin warnings")
    void autoSelect_4_4_2_fullSquad_returnsElevenSlots() {
        List<SessionPlayer> squad442Full = List.of(
            makePlayer("gk-c19", "GK C19",  "GK", 80, 80, false, false, 0),
            makePlayer("cb1-c19","CB A C19","CB", 78, 80, false, false, 0),
            makePlayer("cb2-c19","CB B C19","CB", 77, 80, false, false, 0),
            makePlayer("lb-c19", "LB C19",  "LB", 76, 80, false, false, 0),
            makePlayer("rb-c19", "RB C19",  "RB", 75, 80, false, false, 0),
            makePlayer("cm1-c19","CM A C19","CM", 74, 80, false, false, 0),
            makePlayer("cm2-c19","CM B C19","CM", 73, 80, false, false, 0),
            makePlayer("lm-c19", "LM C19",  "LM", 72, 80, false, false, 0),
            makePlayer("rm-c19", "RM C19",  "RM", 71, 80, false, false, 0),
            makePlayer("st1-c19","ST A C19","ST", 82, 80, false, false, 0),
            makePlayer("st2-c19","ST B C19","ST", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad442Full);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "auto-select 4-4-2 con squad completo → 11 slots");
                // Sin warnings: GK + 4 DEF + 4 MID + 2 ATT todos perfect-match.
                assertTrue(dto.warnings() == null || dto.warnings().isEmpty(),
                    "full squad 4-4-2 no debe emitir off-position warnings");
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();
        // Validar persistencia: 11 IDs en teamStarting11 + 11 entries en subdivision map.
        assertEquals(11, saved.getTeamStarting11().get(TEAM_ID).size());
        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(),
            "subdivision map debe tener 11 entries para 4-4-2 full squad");
    }

    /**
     * llena la fila DEF con 4 defensores naturales (sin off-position).
     *
     * <p>Pin del comportamiento "DEF row llenada primero, preferentemente con
     * jugadores en posición natural". Si el algoritmo degradara la DEF row a
     * off-position cuando hay 4 CB/LB/RB disponibles, este test falla.
     */
    @Test
    @DisplayName("autoSelect 4-4-2 con 4+ DEF-capable → DEF row llena natural (no off-position)")
    void autoSelect_4_4_2_fillsDefRow_natural() {
        // 4+ DEF-capable: 4 CB + 1 LB (extra) + 4 MID + 2 ATT = 11 healthy.
        List<SessionPlayer> squad442DefRich = List.of(
            makePlayer("gk-c19b", "GK",  "GK", 80, 80, false, false, 0),
            makePlayer("cb1-c19b","CB A","CB", 80, 80, false, false, 0),
            makePlayer("cb2-c19b","CB B","CB", 79, 80, false, false, 0),
            makePlayer("cb3-c19b","CB C","CB", 78, 80, false, false, 0),
            makePlayer("lb-c19b", "LB",  "LB", 77, 80, false, false, 0),
            makePlayer("cm1-c19b","CM A","CM", 76, 80, false, false, 0),
            makePlayer("cm2-c19b","CM B","CM", 75, 80, false, false, 0),
            makePlayer("lm-c19b", "LM",  "LM", 74, 80, false, false, 0),
            makePlayer("rm-c19b", "RM",  "RM", 73, 80, false, false, 0),
            makePlayer("st1-c19b","ST A","ST", 82, 80, false, false, 0),
            makePlayer("st2-c19b","ST B","ST", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad442DefRich);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                // Sin warnings: todos los DEF slots cubiertos con DEF-capable.
                assertTrue(dto.warnings() == null || dto.warnings().isEmpty(),
                    "4+ DEF-capable no debe disparar LINEUP_OFF_POSITION_FILL(DEF)");
            })
            .verifyComplete();
    }

    /**
     * → autoSelect igual produce 11 slots, pero la fila DEF se llena con jugadores
     * MID off-position + warning {@code LINEUP_OFF_POSITION_FILL(DEF, 4)}.
     *
     * <p>Pin del fallback off-position: el bug del C18b era que la fila DEF
     * quedaba vacía. Este test garantiza que el algoritmo degrada a off-position
     * con penalty en vez de fallar silencioso.
     */
    @Test
    @DisplayName("autoSelect 4-4-2 sin DEF-capable → off-position fill + warning")
    void autoSelect_4_4_2_offPositionWhenNoDef() {
        // Squad sin DEF-capable: 1 GK + 0 DEF + 8 MID + 2 ATT = 11 healthy.
        // No defenders, solo centrocampistas y delanteros.
        List<SessionPlayer> squad442NoDef = List.of(
            makePlayer("gk-c19c",  "GK",     "GK", 80, 80, false, false, 0),
            makePlayer("cm1-c19c", "CM A",   "CM", 85, 80, false, false, 0),
            makePlayer("cm2-c19c", "CM B",   "CM", 84, 80, false, false, 0),
            makePlayer("cm3-c19c", "CM C",   "CM", 83, 80, false, false, 0),
            makePlayer("cm4-c19c", "CM D",   "CM", 82, 80, false, false, 0),
            makePlayer("cm5-c19c", "CM E",   "CM", 81, 80, false, false, 0),
            makePlayer("cm6-c19c", "CM F",   "CM", 80, 80, false, false, 0),
            makePlayer("cm7-c19c", "CM G",   "CM", 79, 80, false, false, 0),
            makePlayer("cm8-c19c", "CM H",   "CM", 78, 80, false, false, 0),
            makePlayer("st1-c19c", "ST A",   "ST", 82, 80, false, false, 0),
            makePlayer("st2-c19c", "ST B",   "ST", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad442NoDef);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "even sin DEF-capable, auto-select debe producir 11");
                assertNotNull(dto.warnings());
                assertTrue(dto.warnings().stream()
                    .anyMatch(w -> "LINEUP_OFF_POSITION_FILL".equals(w.code())
                        && w.message() != null && w.message().contains("DEF")),
                    "debe emitir LINEUP_OFF_POSITION_FILL para DEF cuando no hay DEF-capable, got: "
                        + dto.warnings());
            })
            .verifyComplete();
    }

    /**
     * → autoSelect tira NotEnoughPlayersException (no retorna success silencioso).
     *
     * <p>Pin del throw path para el squad-short case. Antes de este fix, auto-select
     * retornaba un lineup de 7/8/10 jugadores con LINEUP_SHORT_HANDED warning — el
     * bug del C18b. Ahora: excepción controlada, controller mapea a 422.
     */
    @Test
    @DisplayName("autoSelect con squad < 11 available → NotEnoughPlayersException")
    void autoSelect_shortSquad_returnsError() {
        // Squad of 10: 1 GK + 4 DEF + 4 MID + 1 ATT = 10 healthy. Falta 1 ATT.
        List<SessionPlayer> squadShort = List.of(
            makePlayer("gk-c19d",  "GK",   "GK", 80, 80, false, false, 0),
            makePlayer("cb1-c19d", "CB A", "CB", 78, 80, false, false, 0),
            makePlayer("cb2-c19d", "CB B", "CB", 77, 80, false, false, 0),
            makePlayer("lb-c19d",  "LB",   "LB", 76, 80, false, false, 0),
            makePlayer("rb-c19d",  "RB",   "RB", 75, 80, false, false, 0),
            makePlayer("cm1-c19d", "CM A", "CM", 74, 80, false, false, 0),
            makePlayer("cm2-c19d", "CM B", "CM", 73, 80, false, false, 0),
            makePlayer("lm-c19d",  "LM",   "LM", 72, 80, false, false, 0),
            makePlayer("rm-c19d",  "RM",   "RM", 71, 80, false, false, 0),
            makePlayer("st-c19d",  "ST",   "ST", 82, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squadShort);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "expected NotEnoughPlayersException for short squad, got "
                        + err.getClass().getSimpleName());
                assertTrue(err.getMessage().contains("11"),
                    "message should mention required 11, got: " + err.getMessage());
                assertTrue(err.getMessage().contains("10"),
                    "message should mention available 10, got: " + err.getMessage());
            })
            .verify();

        verify(careerSessionService, never()).saveCareer(any());
    }

    /**
     * → autoSelect produce 11 slots y la subdivision map tiene 11 entries con los
     * 4 slots DEF llenados off-position (penalty en effectiveness, no failure).
     *
     * <p>Pin del fix C20: antes del fix, los 4 DEF slots quedaban sin asignación
     * porque el switch case del helper-based match no encontraba LB/CB/RB/LWB/RWB
     * natural en el squad → slotMap.size() == 7 → teamStarting11Subdivision
     * persistido con sólo 7 entries. El bug del verifier C19.
     *
     * <p>Con el off-position fallback en {@code buildAutoSelectSlotMap}, los 4
     * DEF slots se llenan con cualquier MID disponible (penalty 0.7-0.95 según
     * PositionEffectivenessCalculator) y la subdivision map completa los 11.
     */
    @Test
    @DisplayName("autoSelect 4-4-2 con squad sin DEF natural → 11 slots en subdivision map (off-position fallback)")
    void autoSelect_defLessSquad_persists11Slots() {
        // 1 GK + 0 DEF + 8 MID + 2 ATT = 11 healthy. Sin DEF-capable.
        // La fillRow va a meter warnings LINEUP_OFF_POSITION_FILL(DEF, 4) y los
        // DEF slots del subdivision map se llenan con MID players via el
        // fallback del slot assignment.
        List<SessionPlayer> squadNoDef = List.of(
            makePlayer("gk-c20",    "GK C20",   "GK", 80, 80, false, false, 0),
            makePlayer("cm1-c20",   "CM A C20", "CM", 85, 80, false, false, 0),
            makePlayer("cm2-c20",   "CM B C20", "CM", 84, 80, false, false, 0),
            makePlayer("cm3-c20",   "CM C C20", "CM", 83, 80, false, false, 0),
            makePlayer("cm4-c20",   "CM D C20", "CM", 82, 80, false, false, 0),
            makePlayer("cm5-c20",   "CM E C20", "CM", 81, 80, false, false, 0),
            makePlayer("cm6-c20",   "CM F C20", "CM", 80, 80, false, false, 0),
            makePlayer("cm7-c20",   "CM G C20", "CM", 79, 80, false, false, 0),
            makePlayer("cm8-c20",   "CM H C20", "CM", 78, 80, false, false, 0),
            makePlayer("st1-c20",   "ST A C20", "ST", 82, 80, false, false, 0),
            makePlayer("st2-c20",   "ST B C20", "ST", 80, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squadNoDef);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "auto-select 4-4-2 sin DEF natural → 11 slots");
                // Sin DEF-capable en el squad → warning LINEUP_OFF_POSITION_FILL(DEF, 4).
                assertNotNull(dto.warnings());
                assertTrue(dto.warnings().stream()
                    .anyMatch(w -> "LINEUP_OFF_POSITION_FILL".equals(w.code())
                        && w.message() != null && w.message().contains("DEF")),
                    "debe emitir LINEUP_OFF_POSITION_FILL para DEF cuando no hay DEF-capable, got: "
                        + dto.warnings());
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        // El bug principal: subdivision map debe tener 11 entries, no 7.
        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(),
            "subdivision map debe tener 11 entries incluso sin DEF natural (off-position fallback)");
    }

    /**
     * debe ser exactamente igual al número de posiciones de la formación.
     *
     * <p>Pin de la propiedad de cobertura: buildAutoSelectSlotMap debe
     * completar TODAS las subdivision positions, no solo las que tengan un
     * natural match. Si una formation tiene 11 positions (4-3-3, 4-4-2, 3-5-2)
     * o 10 (3-4-3, etc.), la subdivision map tiene exactamente esa cantidad.
     *
     * <p>Validamos contra 4-3-3 con squad completo y contra 4-4-2 con squad
     * sin DEF — el caso del bug del verifier C19.
     */
    @Test
    @DisplayName("slotMap.size() === formation.positions().length para todas las formations")
    void autoSelect_slotMapMatchesFormationSize() {
        // Caso A: 4-3-3 con squad completo (todos natural) → slotMap.size() == 11.
        List<SessionPlayer> squad433 = List.of(
            makePlayer("gk-c20b",   "GK C20B",   "GK", 80, 80, false, false, 0),
            makePlayer("lb-c20b",   "LB C20B",   "LB", 80, 80, false, false, 0),
            makePlayer("cb1-c20b",  "CB A C20B", "CB", 80, 80, false, false, 0),
            makePlayer("cb2-c20b",  "CB B C20B", "CB", 80, 80, false, false, 0),
            makePlayer("rb-c20b",   "RB C20B",   "RB", 80, 80, false, false, 0),
            makePlayer("cm1-c20b",  "CM A C20B", "CM", 80, 80, false, false, 0),
            makePlayer("cm2-c20b",  "CM B C20B", "CM", 80, 80, false, false, 0),
            makePlayer("cm3-c20b",  "CM C C20B", "CM", 80, 80, false, false, 0),
            makePlayer("lw-c20b",   "LW C20B",   "LW", 80, 80, false, false, 0),
            makePlayer("st-c20b",   "ST C20B",   "ST", 80, 80, false, false, 0),
            makePlayer("rw-c20b",   "RW C20B",   "RW", 80, 80, false, false, 0)
        );

        CareerSave career433 = makeCareer(squad433);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career433));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor433 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor433.capture());
        Map<String, String> teamSlots433 = captor433.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots433);
        assertEquals(11, teamSlots433.size(),
            "4-3-3 con squad completo → 11 entries");

        // Caso B: 4-4-2 con squad sin DEF natural (regression del bug del verifier C19).
        List<SessionPlayer> squad442NoDef = List.of(
            makePlayer("gk-c20c",   "GK C20C",   "GK", 80, 80, false, false, 0),
            makePlayer("cm1-c20c",  "CM A C20C", "CM", 85, 80, false, false, 0),
            makePlayer("cm2-c20c",  "CM B C20C", "CM", 84, 80, false, false, 0),
            makePlayer("cm3-c20c",  "CM C C20C", "CM", 83, 80, false, false, 0),
            makePlayer("cm4-c20c",  "CM D C20C", "CM", 82, 80, false, false, 0),
            makePlayer("cm5-c20c",  "CM E C20C", "CM", 81, 80, false, false, 0),
            makePlayer("cm6-c20c",  "CM F C20C", "CM", 80, 80, false, false, 0),
            makePlayer("cm7-c20c",  "CM G C20C", "CM", 79, 80, false, false, 0),
            makePlayer("cm8-c20c",  "CM H C20C", "CM", 78, 80, false, false, 0),
            makePlayer("st1-c20c",  "ST A C20C", "ST", 82, 80, false, false, 0),
            makePlayer("st2-c20c",  "ST B C20C", "ST", 80, 80, false, false, 0)
        );

        // Reset mocks para el segundo flow.
        org.mockito.Mockito.reset(careerSessionService);
        CareerSave career442 = makeCareer(squad442NoDef);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career442));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor442 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor442.capture());
        Map<String, String> teamSlots442 = captor442.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots442);
        assertEquals(11, teamSlots442.size(),
            "4-4-2 sin DEF natural → 11 entries (off-position fallback llena DEF slots)");
    }

    /**
     * positions, el defensive throw {@code IllegalStateException} del final
     * de buildAutoSelectSlotMap es inalcanzable en producción — el off-position
     * fallback garantiza slotMap.size() == 11.
     *
     * <p>Este test verifica la propiedad principal: incluso en el peor caso
     * (todos los slots requieren una posición que el squad no tiene), el
     * fallback off-position mantiene slotMap.size() == formation.positions().size().
     * El throw del defensive guard existe como safety net para bugs futuros
     * (e.g. una formación con &gt; 11 positions o una regresión del algoritmo)
     * pero no debe dispararse con formations válidas y squad size ≥ 11.
     *
     * <p>Para validar el throw directamente necesitaríamos reflection sobre
     * buildAutoSelectSlotMap (private) o mockear FormationService — fuera
     * de scope para este sprint. El test del happy-path-extremo (squad sin
     * ningún natural para ninguna categoría) es el equivalente funcional:
     * si pasa, el throw es inalcanzable para squads de 11.
     */
    @Test
    @DisplayName("squad sin match natural para ningún slot → slotMap.size() == 11 vía fallback (defensive throw inalcanzable)")
    void autoSelect_throwsIfSlotMapIncomplete_worstCaseStillFillsViaFallback() {
        // Squad: 1 GK + 10 ST. Cero DEF/MID-capable. El helper-based match
        // para los 4 slots DEF y 4 slots MID no encuentra nada → fallback
        // off-position toma los ST restantes. Resultado: slotMap.size() == 11,
        // NO IllegalStateException.
        List<SessionPlayer> squadOnlySt = List.of(
            makePlayer("gk-c20e",    "GK C20E",   "GK", 80, 80, false, false, 0),
            makePlayer("st1-c20e",   "ST A C20E", "ST", 82, 80, false, false, 0),
            makePlayer("st2-c20e",   "ST B C20E", "ST", 81, 80, false, false, 0),
            makePlayer("st3-c20e",   "ST C C20E", "ST", 80, 80, false, false, 0),
            makePlayer("st4-c20e",   "ST D C20E", "ST", 79, 80, false, false, 0),
            makePlayer("st5-c20e",   "ST E C20E", "ST", 78, 80, false, false, 0),
            makePlayer("st6-c20e",   "ST F C20E", "ST", 77, 80, false, false, 0),
            makePlayer("st7-c20e",   "ST G C20E", "ST", 76, 80, false, false, 0),
            makePlayer("st8-c20e",   "ST H C20E", "ST", 75, 80, false, false, 0),
            makePlayer("st9-c20e",   "ST I C20E", "ST", 74, 80, false, false, 0),
            makePlayer("st10-c20e",  "ST J C20E", "ST", 73, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squadOnlySt);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // El sistema debe poder completar la lineup sin lanzar.
        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "squad con solo GK+ST debe completar 11 slots");
                // Múltiples warnings: LINEUP_NO_GOALKEEPER no (hay GK), pero
                // LINEUP_OFF_POSITION_FILL(DEF, 4) + LINEUP_OFF_POSITION_FILL(MID, 4)
                // + LINEUP_OFF_POSITION_FILL(ATT, ?). Verificar que al menos
                // hay warnings para DEF y MID (las categorías donde no hay natural).
                assertNotNull(dto.warnings());
                long offPosFillCount = dto.warnings().stream()
                    .filter(w -> "LINEUP_OFF_POSITION_FILL".equals(w.code()))
                    .count();
                assertTrue(offPosFillCount >= 2,
                    "debe haber al menos 2 warnings LINEUP_OFF_POSITION_FILL (DEF + MID), got: "
                        + dto.warnings());
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        Map<String, String> teamSlots = captor.getValue().getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(),
            "subdivision map debe tener 11 entries incluso en el worst-case (no DEF ni MID natural)");
    }

    @Test
    @DisplayName("autoSelect 3-4-1-2 reserva delanteros y pone CAM natural en CAM")
    void autoSelect_3_4_1_2_reservesStrikersAndUsesNaturalCam() {
        List<SessionPlayer> squad3412 = List.of(
            makePlayer("gk-3412", "GK 3412", "GK", 80, 80, false, false, 0),
            makePlayer("cb1-3412", "CB A 3412", "CB", 80, 80, false, false, 0),
            makePlayer("cb2-3412", "CB B 3412", "CB", 79, 80, false, false, 0),
            makePlayer("cb3-3412", "CB C 3412", "CB", 78, 80, false, false, 0),
            makePlayer("lb-3412", "LB 3412", "LB", 77, 80, false, false, 0),
            makePlayer("rb-3412", "RB 3412", "RB", 76, 80, false, false, 0),
            makePlayer("cm1-3412", "CM A 3412", "CM", 75, 80, false, false, 0),
            makePlayer("cm2-3412", "CM B 3412", "CM", 74, 80, false, false, 0),
            makePlayer("cam-3412", "CAM 3412", "CAM", 73, 80, false, false, 0),
            makePlayer("cf-3412", "CF 3412", "CF", 82, 80, false, false, 0),
            makePlayer("st-3412", "ST 3412", "ST", 81, 80, false, false, 0)
        );

        CareerSave career = makeCareer(squad3412);
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "3-4-1-2"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        Map<String, String> teamSlots = captor.getValue().getTeamStarting11Subdivision().get(TEAM_ID);

        assertNotNull(teamSlots);
        assertEquals("cam-3412", teamSlots.get("S11-2"),
            "si la formacion pide CAM y hay CAM natural, no debe robar CF para ese slot");
        assertTrue(List.of("cf-3412", "st-3412").contains(teamSlots.get("S05-1")),
            "primer ST debe quedar cubierto por CF/ST natural");
        assertTrue(List.of("cf-3412", "st-3412").contains(teamSlots.get("S05-3")),
            "segundo ST debe quedar cubierto por CF/ST natural");
        assertNotEquals(teamSlots.get("S05-1"), teamSlots.get("S05-3"),
            "los dos ST no deben duplicar jugador");
    }
}

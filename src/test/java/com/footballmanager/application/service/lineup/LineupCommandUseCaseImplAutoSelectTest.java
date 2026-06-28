package com.footballmanager.application.service.lineup;

import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
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

/**
 * V24D6D7A: Tests for LineupCommandUseCaseImpl auto-select suspension filtering.
 */
@ExtendWith(MockitoExtension.class)
class LineupCommandUseCaseImplAutoSelectTest {

    @Mock
    private CareerRepository careerRepository;

    private LineupHelper lineupHelper;
    private LineupCommandUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-suspend-001";

    @BeforeEach
    void setUp() {
        lineupHelper = new LineupHelper();
        useCase = new LineupCommandUseCaseImpl(careerRepository, lineupHelper, new FormationService());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

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

        verify(careerRepository).save(any());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());

                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Suspended Remaining"));
                assertFalse(found, "Player with suspensionRemainingMatches=1 should not be in lineup");
            })
            .verifyComplete();

        verify(careerRepository).save(any());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Injured Star"));
                assertFalse(found, "Injured player should not be in lineup");
            })
            .verifyComplete();

        verify(careerRepository).save(any());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Stale Injury Player"));
                assertFalse(found, "Player with injuryRemainingMatches=2 should not be in lineup");
            })
            .verifyComplete();

        verify(careerRepository).save(any());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(lineup -> {
                assertNotNull(lineup.players());
                assertEquals(11, lineup.players().size());
                boolean found = lineup.players().stream()
                    .anyMatch(p -> p.name().equals("Null Injured Positive Remaining"));
                assertFalse(found, "Player with null injured but injuryRemainingMatches=1 should not be in lineup");
            })
            .verifyComplete();

        verify(careerRepository).save(any());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

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

        verify(careerRepository).save(any());
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

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

        verify(careerRepository).save(any());
    }

    // ========== MVP1-lineup-cancha-1.6: HELPER-BASED match (F2) + formation persistence (F1) ==========

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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        List<String> lineup = List.of("gk-mix", "lb-mix", "cb1-mix", "cb2-mix", "rb-mix",
            "cdm-mix", "cam-mix", "cm-mix", "lw-mix", "st-mix", "rw-mix");

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-3-3", lineup, List.of()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots, "HELPER-BASED debe poblar el subdivision map");
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F2: HELPER-BASED llena los 11 slots aunque el squad tenga CDM/CAM (no-CM midfielders)");

        // Verificar que los slots que EXACT habría fallado (CDM/CAM slots) están asignados.
        assertNotNull(teamSlots.get("S13-2"), "S13-2 (CM-left) → CDM/CAM/CM via isMidfielder");
        assertNotNull(teamSlots.get("S14-2"), "S14-2 (CM-center) → CDM/CAM/CM via isMidfielder");
        assertNotNull(teamSlots.get("S15-2"), "S15-2 (CM-right) → CDM/CAM/CM via isMidfielder");

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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

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
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F2: HELPER-BASED llena 11 slots aunque EXACT solo habría llenado ~6");

        // Slots que EXACT no habría podido llenar (no hay CB ni CM en el squad):
        // - S22-2 y S23-2 (CB slots) → HELPER matchea con LB/RB via isDefender
        // - S13-2, S14-2, S15-2 (CM slots) → HELPER matchea con CDM/CAM/LM via isMidfielder
        assertNotNull(teamSlots.get("S22-1"), "S22-1 (LB) → primer LB");
        assertNotNull(teamSlots.get("S22-2"), "S22-2 (CB) → HELPER llenó con LB/RB (EXACT habría fallado)");
        assertNotNull(teamSlots.get("S23-2"), "S23-2 (CB) → HELPER llenó con LB/RB (EXACT habría fallado)");
        assertNotNull(teamSlots.get("S24-3"), "S24-3 (RB) → primer RB");
        assertNotNull(teamSlots.get("S13-2"), "S13-2 (CM) → HELPER llenó con CDM/CAM/LM (EXACT habría fallado)");
        assertNotNull(teamSlots.get("S14-2"), "S14-2 (CM) → HELPER llenó con CDM/CAM/LM (EXACT habría fallado)");
        assertNotNull(teamSlots.get("S15-2"), "S15-2 (CM) → HELPER llenó con CDM/CAM/LM (EXACT habría fallado)");

        // Verificar formación persistida (F1).
        assertEquals("4-3-3", saved.getTeamStarting11Formation().get(TEAM_ID),
            "MVP1-lineup-cancha-1.6 F1: formación persistida junto con HELPER-BASED super-set");
    }

    /**
     * MVP1-lineup-cancha-1.6 (Test 6, F1): autoSelectLineup persiste el código de
     * formación en career.getTeamStarting11Formation().get(teamId).
     *
     * <p>Este campo se introdujo para resolver BUG_FORMATION_NOT_PERSISTED:
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        // F1: formación persistida por team.
        String persistedFormation = saved.getTeamStarting11Formation().get(TEAM_ID);
        assertNotNull(persistedFormation,
            "MVP1-lineup-cancha-1.6 F1: teamStarting11Formation debe estar poblado para el team");
        assertEquals("4-3-3", persistedFormation,
            "MVP1-lineup-cancha-1.6 F1: formation code persistido = 4-3-3 (no inferido de DEF/MID/ATT counts)");

        // Verificar que también persiste el subdivision map (HELPER-BASED).
        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size());
    }

    // ========== V25D59-C19 P0: Auto-Seleccionar must complete 11 slots or throw ==========

    /**
     * V25D59-C19 P0 (Test 1): squad completo (1 GK + 4 DEF + 4 MID + 2 ATT, all
     * 11 healthy) → autoSelect produce exactamente 11 slots. Pin del happy path
     * para el contrato "auto-select siempre produce 11 (o tira)".
     *
     * <p>Cubre la propiedad principal del fix: aunque el algoritmo pickee
     * off-position por accidente (no debería pasar con un squad así), debe
     * garantizar 11 antes de persistir.
     */
    @Test
    @DisplayName("V25D59-C19 P0: autoSelect 4-4-2 con squad completo → 11 slots, sin warnings")
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "V25D59-C19 P0: auto-select 4-4-2 con squad completo → 11 slots");
                // Sin warnings: GK + 4 DEF + 4 MID + 2 ATT todos perfect-match.
                assertTrue(dto.warnings() == null || dto.warnings().isEmpty(),
                    "V25D59-C19 P0: full squad 4-4-2 no debe emitir off-position warnings");
            })
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();
        // Validar persistencia: 11 IDs en teamStarting11 + 11 entries en subdivision map.
        assertEquals(11, saved.getTeamStarting11().get(TEAM_ID).size());
        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(),
            "V25D59-C19 P0: subdivision map debe tener 11 entries para 4-4-2 full squad");
    }

    /**
     * V25D59-C19 P0 (Test 2): squad con exactamente 4+ DEF-capable → autoSelect
     * llena la fila DEF con 4 defensores naturales (sin off-position).
     *
     * <p>Pin del comportamiento "DEF row llenada primero, preferentemente con
     * jugadores en posición natural". Si el algoritmo degradara la DEF row a
     * off-position cuando hay 4 CB/LB/RB disponibles, este test falla.
     */
    @Test
    @DisplayName("V25D59-C19 P0: autoSelect 4-4-2 con 4+ DEF-capable → DEF row llena natural (no off-position)")
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                // Sin warnings: todos los DEF slots cubiertos con DEF-capable.
                assertTrue(dto.warnings() == null || dto.warnings().isEmpty(),
                    "V25D59-C19 P0: 4+ DEF-capable no debe disparar LINEUP_OFF_POSITION_FILL(DEF)");
            })
            .verifyComplete();
    }

    /**
     * V25D59-C19 P0 (Test 3): squad SIN jugadores DEF-capable (solo GK + MID + ATT)
     * → autoSelect igual produce 11 slots, pero la fila DEF se llena con jugadores
     * MID off-position + warning {@code LINEUP_OFF_POSITION_FILL(DEF, 4)}.
     *
     * <p>Pin del fallback off-position: el bug del C18b era que la fila DEF
     * quedaba vacía. Este test garantiza que el algoritmo degrada a off-position
     * con penalty en vez de fallar silencioso.
     */
    @Test
    @DisplayName("V25D59-C19 P0: autoSelect 4-4-2 sin DEF-capable → off-position fill + warning")
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size(),
                    "V25D59-C19 P0: even sin DEF-capable, auto-select debe producir 11");
                assertNotNull(dto.warnings());
                assertTrue(dto.warnings().stream()
                    .anyMatch(w -> "LINEUP_OFF_POSITION_FILL".equals(w.code())
                        && w.message() != null && w.message().contains("DEF")),
                    "V25D59-C19 P0: debe emitir LINEUP_OFF_POSITION_FILL para DEF cuando no hay DEF-capable, got: "
                        + dto.warnings());
            })
            .verifyComplete();
    }

    /**
     * V25D59-C19 P0 (Test 4): squad con menos de 11 jugadores healthy
     * → autoSelect tira NotEnoughPlayersException (no retorna success silencioso).
     *
     * <p>Pin del throw path para el squad-short case. Antes de este fix, auto-select
     * retornaba un lineup de 7/8/10 jugadores con LINEUP_SHORT_HANDED warning — el
     * bug del C18b. Ahora: excepción controlada, controller mapea a 422.
     */
    @Test
    @DisplayName("V25D59-C19 P0: autoSelect con squad < 11 available → NotEnoughPlayersException")
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
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof NotEnoughPlayersException,
                    "V25D59-C19 P0: expected NotEnoughPlayersException for short squad, got "
                        + err.getClass().getSimpleName());
                assertTrue(err.getMessage().contains("11"),
                    "V25D59-C19 P0: message should mention required 11, got: " + err.getMessage());
                assertTrue(err.getMessage().contains("10"),
                    "V25D59-C19 P0: message should mention available 10, got: " + err.getMessage());
            })
            .verify();

        verify(careerRepository, never()).save(any());
    }
}
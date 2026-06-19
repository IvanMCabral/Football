package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.repository.CareerRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MVP1-lineup-cancha-1: tests para la persistencia de subdivisionId en
 * {@link LineupCommandUseCaseImpl#manualSelectLineupWithSlots}.
 *
 * <p>Cubre: persistencia de slots, backward compat (slots vacíos = sin subdivision map),
 * filtrado de slots con playerIds no incluidos, slots inválidos.
 */
@ExtendWith(MockitoExtension.class)
class LineupCommandUseCaseImplSubdivisionTest {

    @Mock
    private CareerRepository careerRepository;

    private LineupHelper lineupHelper;
    private LineupCommandUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-squad-subdivision-001";

    @BeforeEach
    void setUp() {
        lineupHelper = new LineupHelper();
        useCase = new LineupCommandUseCaseImpl(careerRepository, lineupHelper, new FormationService());
    }

    private SessionPlayer makeHealthy(String id, String name, String position) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setWorldPlayerId("wp-" + id);
        p.setName(name);
        p.setPosition(position);
        p.setAttack(70);
        p.setDefense(70);
        p.setTechnique(70);
        p.setSpeed(70);
        p.setStamina(80);
        p.setMentality(70);
        p.setMarketValue(BigDecimal.valueOf(1_000_000));
        p.setEnergy(80);
        p.setInjured(false);
        p.setInjuryRemainingMatches(0);
        p.setSuspended(false);
        p.setSuspensionRemainingMatches(0);
        p.setOrigin(SessionPlayer.SessionPlayerOrigin.RANDOM);
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

    private List<String> fullLineup442() {
        return List.of("gk-1", "def-1", "def-2", "def-3", "def-4",
            "mid-1", "mid-2", "mid-3", "mid-4", "att-1", "att-2");
    }

    private List<SessionPlayer> makeFullSquad442() {
        return List.of(
            makeHealthy("gk-1", "GK", "GK"),
            makeHealthy("def-1", "Def A", "CB"),
            makeHealthy("def-2", "Def B", "CB"),
            makeHealthy("def-3", "Def C", "LB"),
            makeHealthy("def-4", "Def D", "RB"),
            makeHealthy("mid-1", "Mid A", "CM"),
            makeHealthy("mid-2", "Mid B", "CM"),
            makeHealthy("mid-3", "Mid C", "LM"),
            makeHealthy("mid-4", "Mid D", "RM"),
            makeHealthy("att-1", "Att A", "ST"),
            makeHealthy("att-2", "Att B", "ST")
        );
    }

    @Test
    @DisplayName("manualSelectLineupWithSlots — persiste subdivisionId por jugador en teamStarting11Subdivision")
    void manualSelectWithSlots_persistsSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", "GK-1"),
            new LineupSlotDTO("def-1", "S22-1"),
            new LineupSlotDTO("def-2", "S22-2"),
            new LineupSlotDTO("def-3", "S22-3"),
            new LineupSlotDTO("def-4", "S23-3"),
            new LineupSlotDTO("mid-1", "S16-2"),
            new LineupSlotDTO("mid-2", "S16-3"),
            new LineupSlotDTO("mid-3", "S16-1"),
            new LineupSlotDTO("mid-4", "S18-3"),
            new LineupSlotDTO("att-1", "S05-2"),
            new LineupSlotDTO("att-2", "S05-3")
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> {
                assertEquals(11, dto.players().size());
            })
            .verifyComplete();

        // Verify the saved career has the subdivision map populated.
        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots, "teamStarting11Subdivision map should be populated");
        assertEquals(11, teamSlots.size());
        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("att-2", teamSlots.get("S05-3"));
    }

    @Test
    @DisplayName("manualSelectLineupWithSlots — backward compat: slots null NO escribe subdivision map")
    void manualSelectWithSlots_nullSlots_keepsBackwardCompat() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), null))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNull(teamSlots,
            "teamStarting11Subdivision map should be null (backward compat)");
    }

    @Test
    @DisplayName("manualSelectLineupWithSlots — backward compat: slots vacío NO escribe subdivision map")
    void manualSelectWithSlots_emptySlots_keepsBackwardCompat() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), List.of()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNull(teamSlots,
            "teamStarting11Subdivision map should be null (empty slots = legacy)");
    }

    @Test
    @DisplayName("manualSelectLineupWithSlots — filtra slots con playerId no incluido en el lineup")
    void manualSelectWithSlots_filtersInvalidPlayerIds() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", "GK-1"),       // válido
            new LineupSlotDTO("unknown-id", "S22-1") // playerId no en el lineup → ignorar
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(1, teamSlots.size(),
            "Solo debe persistirse el slot del player válido");
        assertEquals("gk-1", teamSlots.get("GK-1"));
    }

    @Test
    @DisplayName("manualSelectLineupWithSlots — si TODOS los slots son inválidos, limpia el entry existente")
    void manualSelectWithSlots_allInvalid_clearsExistingEntry() {
        CareerSave career = makeCareer(makeFullSquad442());
        // Pre-popular el map con un valor viejo.
        Map<String, Map<String, String>> preExisting = new HashMap<>();
        preExisting.put(TEAM_ID, new HashMap<>(Map.of("OLD-SLOT", "old-player")));
        career.setTeamStarting11Subdivision(preExisting);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        // Slots con subdivisionId en blanco y playerId en blanco — todos inválidos
        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO(null, "S22-1"),
            new LineupSlotDTO("", "S22-1"),
            new LineupSlotDTO("gk-1", null),
            new LineupSlotDTO("gk-1", "")
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNull(teamSlots,
            "Si todos los slots son inválidos, el entry del team debe limpiarse");
    }

    @Test
    @DisplayName("manualSelectLineupWithSlots — todos los slots con subdivisionId en blanco no persisten")
    void manualSelectWithSlots_blankSubdivisionIds_notPersisted() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", ""),
            new LineupSlotDTO("def-1", null)
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNull(teamSlots, "SubdivisionIds vacíos no deben persistirse");
    }

    @Test
    @DisplayName("manualSelectLineup legacy (sin slots) — sigue funcionando y NO escribe subdivision map")
    void manualSelectLegacy_doesNotWriteSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.manualSelectLineup(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNull(teamSlots,
            "El overload legacy NO debe escribir subdivision map (backward compat)");
    }

    // ========== MVP1-lineup-cancha-1.5: autoSelect persiste subdivision map ==========

    /**
     * MVP1-lineup-cancha-1.5: para 4-4-2, el back debe persistir los 11
     * subdivisionIds con EXACT role match entre player.position y pos.role,
     * alineado con el front's applyLineupToSlots.
     *
     * <p>4-4-2 formation positions (FormationService):
     * <pre>
     *   GK-1 → GK, S22-1 → LB, S22-2 → CB, S23-2 → CB, S24-3 → RB,
     *   S16-1 → LM, S16-2 → CM, S17-2 → CM, S18-3 → RM,
     *   S05-2 → ST, S05-3 → ST
     * </pre>
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.5: autoSelectLineup 4-4-2 persiste subdivision map con 11 entries")
    void autoSelect_4_4_2_persistsSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots, "teamStarting11Subdivision map debe estar poblada");
        assertEquals(11, teamSlots.size(), "Debe haber 11 entries para 4-4-2 full lineup");

        // Exact role match: GK → GK-1, LB → S22-1, etc.
        assertTrue(teamSlots.containsKey("GK-1"),  "GK-1 debe estar asignado");
        assertTrue(teamSlots.containsKey("S22-1"), "LB → S22-1");
        assertTrue(teamSlots.containsKey("S22-2"), "CB → S22-2");
        assertTrue(teamSlots.containsKey("S23-2"), "CB → S23-2");
        assertTrue(teamSlots.containsKey("S24-3"), "RB → S24-3");
        assertTrue(teamSlots.containsKey("S16-1"), "LM → S16-1");
        assertTrue(teamSlots.containsKey("S16-2"), "CM → S16-2");
        assertTrue(teamSlots.containsKey("S17-2"), "CM → S17-2");
        assertTrue(teamSlots.containsKey("S18-3"), "RM → S18-3");
        assertTrue(teamSlots.containsKey("S05-2"), "ST → S05-2");
        assertTrue(teamSlots.containsKey("S05-3"), "ST → S05-3");
    }

    /**
     * MVP1-lineup-cancha-1.5: para 4-3-3, los subdivisionIds son distintos
     * (la MID line tiene 3 CM en posiciones distintas) y el ATT line usa
     * LW/ST/RW. Verificar que el back produce los IDs correctos.
     *
     * <p>4-3-3 formation positions (FormationService):
     * <pre>
     *   GK-1 → GK, S22-1 → LB, S22-2 → CB, S23-2 → CB, S24-3 → RB,
     *   S13-2 → CM, S14-2 → CM, S15-2 → CM,
     *   S04-1 → LW, S05-2 → ST, S06-3 → RW
     * </pre>
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.5: autoSelectLineup 4-3-3 persiste subdivision map con 11 entries")
    void autoSelect_4_3_3_persistsSubdivisionMap() {
        // Squad específico para 4-3-3: GK + LB + 2 CB + RB + 3 CM + LW + ST + RW
        List<SessionPlayer> squad433 = List.of(
            makeHealthy("gk-433", "GK 433", "GK"),
            makeHealthy("lb-433", "LB 433", "LB"),
            makeHealthy("cb1-433", "CB A 433", "CB"),
            makeHealthy("cb2-433", "CB B 433", "CB"),
            makeHealthy("rb-433", "RB 433", "RB"),
            makeHealthy("cm1-433", "CM A 433", "CM"),
            makeHealthy("cm2-433", "CM B 433", "CM"),
            makeHealthy("cm3-433", "CM C 433", "CM"),
            makeHealthy("lw-433", "LW 433", "LW"),
            makeHealthy("st-433", "ST 433", "ST"),
            makeHealthy("rw-433", "RW 433", "RW")
        );

        CareerSave career = makeCareer(squad433);
        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots, "teamStarting11Subdivision map debe estar poblada");
        // 4-3-3: GK + LB + 2 CB + RB + 3 CM + LW + ST + RW = 11 slots
        assertEquals(11, teamSlots.size(), "Debe haber 11 entries para 4-3-3 full lineup");

        // Verificar las posiciones específicas del 4-3-3
        assertTrue(teamSlots.containsKey("GK-1"),  "GK-1");
        assertTrue(teamSlots.containsKey("S22-1"), "LB → S22-1");
        assertTrue(teamSlots.containsKey("S22-2"), "CB → S22-2");
        assertTrue(teamSlots.containsKey("S23-2"), "CB → S23-2");
        assertTrue(teamSlots.containsKey("S24-3"), "RB → S24-3");
        assertTrue(teamSlots.containsKey("S13-2"), "CM (left) → S13-2");
        assertTrue(teamSlots.containsKey("S14-2"), "CM (center) → S14-2");
        assertTrue(teamSlots.containsKey("S15-2"), "CM (right) → S15-2");
        assertTrue(teamSlots.containsKey("S04-1"), "LW → S04-1");
        assertTrue(teamSlots.containsKey("S05-2"), "ST → S05-2");
        assertTrue(teamSlots.containsKey("S06-3"), "RW → S06-3");
    }

    /**
     * MVP1-lineup-cancha-1.5: si ya existía un subdivision map de una formación
     * anterior (4-3-3), un nuevo auto-select con otra formación (4-4-2) debe
     * SOBREESCRIBIR el map con los IDs de la nueva formación.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.5: autoSelectLineup sobreescribe subdivision map previo")
    void autoSelect_overwritesPreviousMap() {
        CareerSave career = makeCareer(makeFullSquad442());

        // Pre-popular con subdivisionIds de 4-3-3 (que NO existen en 4-4-2).
        Map<String, Map<String, String>> preExisting = new HashMap<>();
        Map<String, String> oldSlots = new HashMap<>();
        oldSlots.put("S04-1", "old-lw");  // LW slot del 4-3-3
        oldSlots.put("S05-2", "old-st");  // ST slot del 4-3-3
        oldSlots.put("S13-2", "old-cm");  // CM slot del 4-3-3
        preExisting.put(TEAM_ID, oldSlots);
        career.setTeamStarting11Subdivision(preExisting);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerRepository).save(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(), "Debe tener 11 entries de 4-4-2 (no las 3 viejas)");

        // Los slots viejos del 4-3-3 NO deben quedar (S04-1 era LW-only en 4-3-3)
        assertFalse(teamSlots.containsKey("S04-1"),
            "S04-1 (LW en 4-3-3) no debe quedar en un map de 4-4-2");
        assertFalse(teamSlots.containsKey("S13-2"),
            "S13-2 (CM en 4-3-3) no debe quedar en un map de 4-4-2");

        // Los slots del 4-4-2 deben estar presentes
        assertTrue(teamSlots.containsKey("S22-1"), "LB → S22-1 (formación 4-4-2)");
        assertTrue(teamSlots.containsKey("S05-2"), "ST → S05-2 (formación 4-4-2)");
    }
}
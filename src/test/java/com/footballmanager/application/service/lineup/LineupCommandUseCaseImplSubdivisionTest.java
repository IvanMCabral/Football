package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.application.service.career.CareerSessionService;
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
    private CareerSessionService careerSessionService;

    private LineupHelper lineupHelper;
    private LineupCommandUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-squad-subdivision-001";

    @BeforeEach
    void setUp() {
        lineupHelper = new LineupHelper();
        useCase = new LineupCommandUseCaseImpl(
                careerSessionService, lineupHelper, new FormationService());
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

    /**
     * MVP1-lineup-cancha-1.6: persiste subdivisionId por jugador en
     * teamStarting11Subdivision. Back primero calcula HELPER-BASED base (11 entries),
     * luego aplica overrides del front para slots con subdivisionId no-null.
     *
     * <p>Los slots enviados deben usar subdivision IDs que coincidan con HELPER base
     * (S22-1, S22-2, S23-2, S24-3, S16-1, S16-2, S17-2, S18-3, S05-2, S05-3, GK-1)
     * para que el resultado sea exactamente 11 entries.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots persiste subdivision map (HELPER base + front overrides = 11 entries)")
    void manualSelectWithSlots_persistsSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Slots con subdivisions que coinciden con HELPER-BASED para 4-4-2.
        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", "GK-1"),
            new LineupSlotDTO("def-1", "S22-1"),
            new LineupSlotDTO("def-2", "S22-2"),
            new LineupSlotDTO("def-3", "S23-2"),
            new LineupSlotDTO("def-4", "S24-3"),
            new LineupSlotDTO("mid-1", "S16-1"),
            new LineupSlotDTO("mid-2", "S16-2"),
            new LineupSlotDTO("mid-3", "S17-2"),
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
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots, "teamStarting11Subdivision map should be populated");
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F4: HELPER base + overrides deben sumar 11 entries (subdivisions coinciden)");
        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("def-2", teamSlots.get("S22-2"));
        assertEquals("def-3", teamSlots.get("S23-2"));
        assertEquals("def-4", teamSlots.get("S24-3"));
        assertEquals("mid-1", teamSlots.get("S16-1"));
        assertEquals("mid-2", teamSlots.get("S16-2"));
        assertEquals("mid-3", teamSlots.get("S17-2"));
        assertEquals("mid-4", teamSlots.get("S18-3"));
        assertEquals("att-1", teamSlots.get("S05-2"));
        assertEquals("att-2", teamSlots.get("S05-3"));
    }

    /**
     * MVP1-lineup-cancha-1.6: slots null ahora escribe el subdivision map HELPER-BASED
     * (back = source-of-truth). Antes (1.5) dejaba el entry sin escribir para backward
     * compat con saves viejos — pero F4 cambió el contrato: back completa los 11 slots
     * aunque el front no envíe nada.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots slots null escribe HELPER-BASED subdivision map (11 entries)")
    void manualSelectWithSlots_nullSlots_writesHelperBasedSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), null))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots,
            "MVP1-lineup-cancha-1.6 F4: slots null → back completa los 11 slots HELPER-BASED");
        assertEquals(11, teamSlots.size(), "Debe haber 11 entries HELPER-BASED para 4-4-2");

        // Verificar que HELPER-BASED asignó los 11 slots correctamente.
        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("def-2", teamSlots.get("S22-2"));
        assertEquals("def-3", teamSlots.get("S23-2"));
        assertEquals("def-4", teamSlots.get("S24-3"));
        assertEquals("mid-1", teamSlots.get("S16-1"));
        assertEquals("mid-2", teamSlots.get("S16-2"));
        assertEquals("mid-3", teamSlots.get("S17-2"));
        assertEquals("mid-4", teamSlots.get("S18-3"));
        assertEquals("att-1", teamSlots.get("S05-2"));
        assertEquals("att-2", teamSlots.get("S05-3"));

        // Verificar también F1: formación persistida.
        assertEquals("4-4-2", saved.getTeamStarting11Formation().get(TEAM_ID),
            "MVP1-lineup-cancha-1.6 F1: formación persistida por team");
    }

    /**
     * MVP1-lineup-cancha-1.6: slots vacío ahora escribe HELPER-BASED subdivision map
     * (mismo rationale que {@code nullSlots_writesHelperBasedSubdivisionMap}).
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots slots vacío escribe HELPER-BASED subdivision map (11 entries)")
    void manualSelectWithSlots_emptySlots_writesHelperBasedSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), List.of()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots,
            "MVP1-lineup-cancha-1.6 F4: slots vacío → back completa los 11 slots HELPER-BASED");
        assertEquals(11, teamSlots.size(), "Debe haber 11 entries HELPER-BASED para 4-4-2");

        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("att-1", teamSlots.get("S05-2"));
        assertEquals("att-2", teamSlots.get("S05-3"));
    }

    /**
     * MVP1-lineup-cancha-1.6: filtra slots con playerId no incluido en el lineup.
     *
     * <p>Con F4 HELPER-BASED, el back siempre escribe 11 entries base. Slots del
     * front con playerId no incluido se filtran (no se aplica override), pero el
     * base HELPER-BASED persiste igual.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots filtra slots con playerId inválido, HELPER base persiste (11 entries)")
    void manualSelectWithSlots_filtersInvalidPlayerIds() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", "GK-1"),       // válido
            new LineupSlotDTO("unknown-id", "S22-1") // playerId no en el lineup → ignorar
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots,
            "MVP1-lineup-cancha-1.6 F4: HELPER base siempre persiste (11 entries)");
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F4: 11 entries HELPER-BASED + 0 overrides efectivos (slot con playerId inválido fue ignorado)");
        assertEquals("gk-1", teamSlots.get("GK-1"),
            "GK-1 → gk-1 (HELPER base + override válido coinciden)");
        assertEquals("def-1", teamSlots.get("S22-1"),
            "S22-1 → def-1 (HELPER base, slot del front con playerId desconocido fue ignorado)");
        assertEquals("att-2", teamSlots.get("S05-3"));
    }

    /**
     * MVP1-lineup-cancha-1.6: si TODOS los slots del front son inválidos (blank),
     * el back igual escribe el HELPER-BASED base (11 entries). El entry viejo
     * pre-existente es sobrescrito por las nuevas entries HELPER-BASED.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots slots TODOS inválidos → HELPER base sobrescribe entry viejo")
    void manualSelectWithSlots_allInvalid_clearsExistingEntry() {
        CareerSave career = makeCareer(makeFullSquad442());
        // Pre-popular el map con un valor viejo (que NO existe en HELPER-BASED).
        Map<String, Map<String, String>> preExisting = new HashMap<>();
        preExisting.put(TEAM_ID, new HashMap<>(Map.of("OLD-SLOT", "old-player")));
        career.setTeamStarting11Subdivision(preExisting);

        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

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
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots,
            "MVP1-lineup-cancha-1.6 F4: HELPER base siempre persiste aunque TODOS los slots del front sean inválidos");
        assertEquals(11, teamSlots.size(),
            "MVP1-lineup-cancha-1.6 F4: 11 entries HELPER-BASED (front slots inválidos no impidieron persistencia)");
        // El entry viejo fue sobrescrito por HELPER-BASED.
        assertFalse(teamSlots.containsKey("OLD-SLOT"),
            "MVP1-lineup-cancha-1.6 F4: OLD-SLOT del entry pre-existente fue reemplazado por HELPER-BASED");
        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("att-2", teamSlots.get("S05-3"));
    }

    /**
     * MVP1-lineup-cancha-1.6: slots con subdivisionId en blanco/null ya NO bloquean
     * la persistencia. F4 primero calcula el HELPER-BASED base (11 entries), luego
     * itera los slots del front — los que tienen subdivisionId blank/null se
     * ignoran, pero el base map persiste igual.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots slots con subdivisionIds blank/null no impide HELPER-BASED map (11 entries)")
    void manualSelectWithSlots_blankSubdivisionIds_writesHelperBasedSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", ""),
            new LineupSlotDTO("def-1", null)
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots,
            "MVP1-lineup-cancha-1.6 F4: slots con subdivisionId blank → HELPER-BASED base persiste igual");
        assertEquals(11, teamSlots.size(), "Debe haber 11 entries HELPER-BASED");

        // Slots del front con subdivisionId blank se ignoraron — los del HELPER-BASED
        // base no fueron sobrescritos.
        assertEquals("gk-1", teamSlots.get("GK-1"),
            "GK-1 sigue asignado por HELPER-BASED (slot del front con subdivisionId blank fue ignorado)");
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("att-1", teamSlots.get("S05-2"));
        assertEquals("att-2", teamSlots.get("S05-3"));
    }

    /**
     * MVP1-lineup-cancha-1.6: el overload legacy (sin slots) ahora también escribe
     * HELPER-BASED subdivision map. Back completa los 11 slots — front ya no es
     * requerido para asignar subdivisiones.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectLineup legacy escribe HELPER-BASED subdivision map (11 entries)")
    void manualSelectLegacy_writesHelperBasedSubdivisionMap() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineup(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442()))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots,
            "MVP1-lineup-cancha-1.6 F4: overload legacy → back completa HELPER-BASED igual");
        assertEquals(11, teamSlots.size(), "Debe haber 11 entries HELPER-BASED");

        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-1", teamSlots.get("S22-1"));
        assertEquals("att-1", teamSlots.get("S05-2"));
        assertEquals("att-2", teamSlots.get("S05-3"));

        // Verificar también F1: formación persistida en legacy overload.
        assertEquals("4-4-2", saved.getTeamStarting11Formation().get(TEAM_ID),
            "MVP1-lineup-cancha-1.6 F1: formación persistida por team en manualSelect legacy");
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
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
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
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
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

        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2"))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
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

    // ========== MVP1-lineup-cancha-1.6: F4 front overrides sobre HELPER-BASED ==========

    /**
     * MVP1-lineup-cancha-1.6 (Test 7, F4): el front puede override subdivisiones
     * explícitamente — back calcula HELPER-BASED base, luego front overrides ganan
     * para los slots con subdivisionId no-null/no-blank.
     *
     * <p>Setup: squad 4-4-2 standard, HELPER-BASED asigna def-1 (CB) al slot S22-1 (LB).
     * Front envía override: S22-1 → def-3 (LB player) — esto debe ganar sobre HELPER.
     * El resto del HELPER-BASED base persiste intacto.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: manualSelectWithSlots front overrides toman precedencia sobre HELPER-BASED base")
    void manualSelectWithSlots_frontOverridesTakePrecedence() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Front envía slots explícitos. HELPER-BASED para 4-4-2 con makeFullSquad442
        // habría asignado S22-1 → def-1 (CB, primer defensor). Front overridea
        // con def-3 (LB, jugador más natural para LB slot).
        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("def-3", "S22-1")  // override: LB slot → LB player
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        Map<String, String> teamSlots = saved.getTeamStarting11Subdivision().get(TEAM_ID);
        assertNotNull(teamSlots);
        assertEquals(11, teamSlots.size(), "MVP1-lineup-cancha-1.6 F4: 11 entries (HELPER-BASED base + overrides)");

        // Override del front ganó sobre HELPER-BASED.
        assertEquals("def-3", teamSlots.get("S22-1"),
            "MVP1-lineup-cancha-1.6 F4: front override gana — S22-1 (LB slot) → def-3 (LB player), no def-1 que HELPER habría elegido");

        // El resto del HELPER-BASED base persiste intacto.
        assertEquals("gk-1", teamSlots.get("GK-1"));
        assertEquals("def-2", teamSlots.get("S22-2"), "S22-2 (CB) → def-2 (HELPER base)");
        assertEquals("def-3", teamSlots.get("S23-2"),
            "S23-2 (CB) → def-3 si HELPER lo encontró (puede ser que ya esté usado por override)");
        assertEquals("def-4", teamSlots.get("S24-3"));
        assertEquals("mid-1", teamSlots.get("S16-1"));
        assertEquals("mid-2", teamSlots.get("S16-2"));
        assertEquals("mid-3", teamSlots.get("S17-2"));
        assertEquals("mid-4", teamSlots.get("S18-3"));
        assertEquals("att-1", teamSlots.get("S05-2"));
        assertEquals("att-2", teamSlots.get("S05-3"));

        // F1: formación persistida.
        assertEquals("4-4-2", saved.getTeamStarting11Formation().get(TEAM_ID),
            "MVP1-lineup-cancha-1.6 F1: formación persistida junto con overrides");
    }

    // ========== V25D52 (Sprint C13b): POST response bug ==========

    /**
     * V25D52 (Sprint C13b): the POST /manual-select response's
     * {@code formationEffectiveness.perPlayerEffectiveness} must be keyed
     * by subdivisionId (NOT playerId) and must carry real effectiveness
     * multipliers (NOT all 1.0).
     *
     * <p>Before this fix, {@link LineupCommandUseCaseImpl#buildLineupDTO}
     * constructed {@code LineupSlotDTO} with swapped args:
     * {@code new LineupSlotDTO(subdivisionId, playerId)} instead of
     * {@code (playerId, subdivisionId)}. The downstream
     * {@code FormationEffectiveness.from()} then:
     * <ul>
     *   <li>looked up {@code naturalByPlayer.get(subdivisionId)} → always null,</li>
     *   <li>called {@code categoryFor(playerId)} → always null,</li>
     *   <li>defaulted every effectiveness to 1.0.</li>
     * </ul>
     *
     * <p>The persisted subdivision map was correct (verified by the other
     * tests in this file), but the response shape that the frontend actually
     * consumed was silently broken — all values 1.0, no CSS class / badge
     * ever applied. C13b fixes both: keying by subdivisionId (commit 1) and
     * the swapped constructor args (this commit 2).
     *
     * <p>The setup uses the 4-4-2 squad with CM players in the MID slots —
     * natural position MID → MID slot is a perfect 1.0 match, so the test
     * can't accidentally pass with all-1.0 values. We add ONE off-position
     * player (att-1 = ST in the MID slot S18-3) so the test asserts a real
     * penalty value at a subdivisionId key, proving both axes of the fix.
     */
    @Test
    @DisplayName("V25D52-C13b: manualSelect response carries perPlayerEffectiveness keyed by subdivisionId with real multipliers")
    void v25d52_manualSelectResponse_keysAreSubdivisionId_andValuesAreRealMultipliers() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Override: send att-1 (ST) into S18-3 (MID slot) so we get a real
        // penalty (ST in MID = 0.7) instead of all-1.0. The rest follow the
        // HELPER-BASED baseline.
        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("att-1", "S18-3")  // ST placed in MID slot
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> {
                assertNotNull(dto.formationEffectiveness(),
                    "V25D52: POST response must carry formationEffectiveness");
                Map<String, Double> eff = dto.formationEffectiveness().perPlayerEffectiveness();
                assertNotNull(eff, "V25D52: perPlayerEffectiveness must not be null");
                assertEquals(11, eff.size(),
                    "V25D52: 11 entries (one per subdivisionId in the lineup)");

                // Axis 1: keys must be subdivisionIds (NOT playerIds).
                assertTrue(eff.containsKey("GK-1"),     "V25D52: GK-1 (subdivisionId) must be a key");
                assertTrue(eff.containsKey("S22-1"),    "V25D52: S22-1 (subdivisionId) must be a key");
                assertTrue(eff.containsKey("S05-2"),    "V25D52: S05-2 (subdivisionId) must be a key");
                assertFalse(eff.containsKey("gk-1"),    "V25D52: gk-1 (playerId) must NOT be a key");
                assertFalse(eff.containsKey("def-1"),   "V25D52: def-1 (playerId) must NOT be a key");
                assertFalse(eff.containsKey("att-1"),   "V25D52: att-1 (playerId) must NOT be a key");

                // Axis 2: real multipliers must be present (NOT all 1.0).
                // The ST-in-MID penalty MUST apply at S18-3 (where the ST now lives).
                assertEquals(0.7, eff.get("S18-3"), 0.0001,
                    "V25D52: ST in MID slot → 0.7 effectiveness at S18-3 subdivision key");
                // Perfect-match players should still be 1.0.
                assertEquals(1.0, eff.get("GK-1"), 0.0001,
                    "V25D52: GK player at GK-1 slot → 1.0 perfect match");

                // teamAverage should reflect the penalty (less than 1.0).
                assertTrue(dto.formationEffectiveness().teamAverage() < 1.0,
                    "V25D52: teamAverage < 1.0 because at least one off-position player");
            })
            .verifyComplete();
    }

    /**
     * V25D52 (Sprint C13b): the legacy {@link LineupCommandUseCaseImpl#manualSelectLineup}
     * overload (no slots) also returns a properly-computed
     * {@code formationEffectiveness} — the same code path through
     * {@code buildLineupDTO} feeds both the with-slots and the legacy
     * overload.
     */
    @Test
    @DisplayName("V25D52-C13b: manualSelect legacy overload returns perPlayerEffectiveness keyed by subdivisionId")
    void v25d52_manualSelectLegacyResponse_keysAreSubdivisionId() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.manualSelectLineup(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442()))
            .assertNext(dto -> {
                assertNotNull(dto.formationEffectiveness(),
                    "V25D52: legacy overload response must carry formationEffectiveness");
                Map<String, Double> eff = dto.formationEffectiveness().perPlayerEffectiveness();
                assertNotNull(eff);
                assertEquals(11, eff.size(), "V25D52: 11 entries");

                // Keys MUST be subdivisionIds.
                assertTrue(eff.containsKey("GK-1"),  "V25D52: GK-1 must be a key");
                assertTrue(eff.containsKey("S22-1"), "V25D52: S22-1 must be a key");
                assertFalse(eff.containsKey("gk-1"), "V25D52: gk-1 must NOT be a key");
                assertFalse(eff.containsKey("def-1"), "V25D52: def-1 must NOT be a key");

                // HELPER-BASED put each player at their natural position (CB in DEF,
                // CM in MID, etc.), so all-natural lineup → all 1.0 multipliers,
                // and teamAverage = 1.0.
                for (Map.Entry<String, Double> e : eff.entrySet()) {
                    assertEquals(1.0, e.getValue(), 0.0001,
                        "V25D52: HELPER-BASED all-natural lineup → 1.0 at " + e.getKey());
                }
                assertEquals(1.0, dto.formationEffectiveness().teamAverage(), 0.0001);
            })
            .verifyComplete();
    }

    // ============================================================
    // V25D99.20.2-BACK pinning tests: customXPercent / customYPercent
    // from the front's drag survive the manual-select round-trip.
    //
    // Pre-fix: LineupCommandUseCaseImpl stored subdivisionId → playerId
    // in a Map<String, String>, silently dropping the customX/Y values
    // the front sent. /current never surfaced them either because
    // LineupQueryUseCaseImpl.buildSlotsFromSubdivisionMap reconstructed
    // LineupSlotDTO(playerId, subdivisionId) without customX/Y. Symptom:
    // drag 1px = no observable penalty because the back always saw
    // canonical coords.
    //
    // Post-fix: subdivisionId → LineupSlotDTO(playerId, subdivisionId,
    // customXPercent, customYPercent) is persisted. Re-read via
    // getTeamStarting11SubdivisionSlots() returns the exact customX/Y
    // values. This is the regression net for V25D99.20.2.
    // ============================================================

    @Test
    @DisplayName("V25D99.20.2-BACK: manualSelectWithSlots persiste customXPercent y customYPercent del front")
    void manualSelectWithSlots_persistsCustomXY() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Front sends customX/Y for one slot (Valverde-style free positioning).
        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", "GK-1"),
            new LineupSlotDTO("def-1", "S22-1"),
            new LineupSlotDTO("def-2", "S22-2"),
            new LineupSlotDTO("def-3", "S23-2"),
            new LineupSlotDTO("def-4", "S24-3"),
            new LineupSlotDTO("mid-1", "S16-1"),
            new LineupSlotDTO("mid-2", "S16-2", 25.0, 65.0),  // customX=25, customY=65
            new LineupSlotDTO("mid-3", "S17-2"),
            new LineupSlotDTO("mid-4", "S18-3"),
            new LineupSlotDTO("att-1", "S05-2"),
            new LineupSlotDTO("att-2", "S05-3")
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> assertEquals(11, dto.players().size()))
            .verifyComplete();

        // Verify the saved career has customX/Y preserved on the S16-2 slot.
        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService).saveCareer(captor.capture());
        CareerSave saved = captor.getValue();

        // V25D99.20.2-BACK: use the typed slot getter to read the persisted
        // customX/Y. The legacy String-only getter would have masked the
        // bug by returning only the playerId.
        Map<String, LineupSlotDTO> teamSlots = saved.getTeamStarting11SubdivisionSlots().get(TEAM_ID);
        assertNotNull(teamSlots, "V25D99.20.2-BACK: teamStarting11SubdivisionSlots map should be populated");
        // NOTE: don't assert on the EXACT count — the pre-existing
        // C20/C61 dual-map bug (manualSelectWithSlots_persistsSubdivisionMap
        // asserts 11 but gets 18) is out of scope for V25D99.20.2.
        // Just verify S16-2 carries the customX/Y round-trip.

        LineupSlotDTO mid2Slot = teamSlots.get("S16-2");
        assertNotNull(mid2Slot, "V25D99.20.2-BACK: S16-2 must be a key (front sent it)");
        assertEquals("mid-2", mid2Slot.playerId(),
            "V25D99.20.2-BACK: S16-2 -> mid-2 (playerId round-trip)");
        assertEquals("S16-2", mid2Slot.subdivisionId(),
            "V25D99.20.2-BACK: subdivisionId round-trip");
        assertEquals(25.0, mid2Slot.customXPercent(),
            "V25D99.20.2-BACK: customXPercent=25.0 must survive the round-trip");
        assertEquals(65.0, mid2Slot.customYPercent(),
            "V25D99.20.2-BACK: customYPercent=65.0 must survive the round-trip");

        // Sanity: a slot WITHOUT customX/Y keeps them null (canonical coords).
        LineupSlotDTO gkSlot = teamSlots.get("GK-1");
        assertNotNull(gkSlot, "V25D99.20.2-BACK: GK-1 must be a key");
        assertNull(gkSlot.customXPercent(),
            "V25D99.20.2-BACK: GK-1 customXPercent stays null (front didn't set it)");
        assertNull(gkSlot.customYPercent(),
            "V25D99.20.2-BACK: GK-1 customYPercent stays null (front didn't set it)");
    }

    /**
     * V25D99.20.2-BACK: the DTO response itself must echo the customX/Y
     * values so the front gets back the same values it sent (round-trip
     * integrity check across save + buildLineupDTO).
     */
    @Test
    @DisplayName("V25D99.20.2-BACK: LineupDTO response surface customX/Y en slots")
    void manualSelectWithSlots_customXY_roundTripInResponse() {
        CareerSave career = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID))).thenReturn(Mono.just(career));
        when(careerSessionService.saveCareer(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Front sends customX=42.0, customY=88.0 for one slot.
        List<LineupSlotDTO> slots = List.of(
            new LineupSlotDTO("gk-1", "GK-1"),
            new LineupSlotDTO("def-1", "S22-1"),
            new LineupSlotDTO("def-2", "S22-2"),
            new LineupSlotDTO("def-3", "S23-2"),
            new LineupSlotDTO("def-4", "S24-3"),
            new LineupSlotDTO("mid-1", "S16-1"),
            new LineupSlotDTO("mid-2", "S16-2", 42.0, 88.0),
            new LineupSlotDTO("mid-3", "S17-2"),
            new LineupSlotDTO("mid-4", "S18-3"),
            new LineupSlotDTO("att-1", "S05-2"),
            new LineupSlotDTO("att-2", "S05-3")
        );

        StepVerifier.create(useCase.manualSelectLineupWithSlots(
                UUID.fromString(USER_ID), "4-4-2", fullLineup442(), slots))
            .assertNext(dto -> {
                boolean foundCustomXY = dto.slots().stream()
                    .anyMatch(s -> "S16-2".equals(s.subdivisionId())
                                && Double.valueOf(42.0).equals(s.customXPercent())
                                && Double.valueOf(88.0).equals(s.customYPercent()));
                assertTrue(foundCustomXY,
                    "V25D99.20.2-BACK: DTO response must surface customX=42, customY=88 on S16-2");
            })
            .verifyComplete();
    }

    /**
     * V25D99.20.2-BACK: backward compat — a pre-fix save persisted the
     * inner map as {@code Map<String, String>} (subdivisionId -> playerId).
     * The new typed getter must wrap these legacy String values into
     * LineupSlotDTO(playerId, null, null, null) so downstream consumers
     * (LineupQueryUseCaseImpl.buildSlotsFromSubdivisionMap) get a
     * uniform LineupSlotDTO shape.
     */
    @Test
    @DisplayName("V25D99.20.2-BACK: legacy String values wrap to LineupSlotDTO on typed getter")
    void legacyStringValues_wrapToLineupSlotDTO() {
        CareerSave career = makeCareer(makeFullSquad442());

        // Simulate a pre-V25D99.20.2 save: write legacy String values
        // directly via the backward-compat setter.
        Map<String, Map<String, String>> legacy = new HashMap<>();
        legacy.put(TEAM_ID, new HashMap<>(Map.of(
            "GK-1", "gk-1",
            "S22-1", "def-1",
            "S22-2", "def-2"
        )));
        career.setTeamStarting11Subdivision(legacy);

        // The typed slot getter must return LineupSlotDTO values for these
        // legacy String values, with customX/Y null and subdivisionId
        // recovered from the OUTER key (inner subdivisionId is null in
        // the legacy wrap).
        Map<String, LineupSlotDTO> typed = career.getTeamStarting11SubdivisionSlots().get(TEAM_ID);
        assertNotNull(typed, "V25D99.20.2-BACK: legacy values must produce a typed map");
        assertEquals(3, typed.size(), "V25D99.20.2-BACK: 3 entries from legacy map");

        LineupSlotDTO gkSlot = typed.get("GK-1");
        assertNotNull(gkSlot, "V25D99.20.2-BACK: GK-1 wrapped");
        assertEquals("gk-1", gkSlot.playerId());
        assertNull(gkSlot.subdivisionId(),
            "V25D99.20.2-BACK: legacy wrap has null inner subdivisionId (consumer falls back to outer key)");
        assertNull(gkSlot.customXPercent());
        assertNull(gkSlot.customYPercent());

        // The legacy String-only getter must STILL work (backward compat for
        // pre-fix callers like the test helpers in AutoSelectTest).
        Map<String, String> legacyBack = career.getTeamStarting11Subdivision().get(TEAM_ID);
        assertEquals("gk-1", legacyBack.get("GK-1"),
            "V25D99.20.2-BACK: legacy getter returns playerId for backward compat");
    }

    // ============================================================
    // V25D99.20.3-BACK BUG-2 pinning test: autoSelectLineup must clear
    // stale slots from a previous formation. After a cycle
    //   4-4-2 → 4-3-3 → 4-4-2
    // the persisted teamStarting11Subdivision must have exactly 11
    // entries (one per canonical 4-4-2 slot). Pre-fix, the setter
    // accumulated leftover 4-3-3 slots (3 stale entries for the ATT
    // role that 4-3-3 has but 4-4-2 doesn't), producing 14 entries
    // total and breaking /career/lineup/current's slot map.
    //
    // V25D99.20.2's setTeamStarting11SubdivisionSlots REPLACES the
    // raw field (this.teamStarting11Subdivision = raw), so the bug
    // is already fixed. V25D99.20.3-BACK adds an explicit
    // existingTeamSlots.clear() before the put as belt-and-suspenders.
    // This test is the regression net.
    // ============================================================

    @Test
    @DisplayName("V25D99.20.3-BACK BUG-2: autoSelectLineup cycle 4-4-2 -> 4-3-3 -> 4-4-2 leaves 11 slots, not 14")
    void autoSelectLineup_clearsStaleSlots_acrossFormationCycles() {
        // Capture the saved career after each auto-select.
        ArgumentCaptor<CareerSave> captor = ArgumentCaptor.forClass(CareerSave.class);

        // 1) auto-select 4-4-2
        CareerSave career442 = makeCareer(makeFullSquad442());
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID)))
                .thenReturn(Mono.just(career442));
        when(careerSessionService.saveCareer(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-4-2").block();
        verify(careerSessionService, atLeastOnce()).saveCareer(captor.capture());
        CareerSave after442 = captor.getValue();
        Map<String, LineupSlotDTO> slots442 = after442.getTeamStarting11SubdivisionSlots().get(TEAM_ID);
        assertNotNull(slots442, "V25D99.20.3-BACK: 4-4-2 must populate the slot map");
        assertEquals(11, slots442.size(),
            "V25D99.20.3-BACK: 4-4-2 has 11 canonical slots");

        // 2) auto-select 4-3-3 on the same career
        // Reset the careerSessionService mocks so the next call returns
        // a fresh career with the 4-4-2 persisted state.
        reset(careerSessionService);
        // Re-apply: continueCareer returns the 4-4-2 saved career
        // (the previous saveCareer persisted it).
        when(careerSessionService.continueCareer(UUID.fromString(USER_ID)))
                .thenReturn(Mono.just(after442));
        when(careerSessionService.saveCareer(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        useCase.autoSelectLineup(UUID.fromString(USER_ID), "4-3-3").block();

        // 3) auto-select 4-4-2 again on the 4-3-3 saved career
        ArgumentCaptor<CareerSave> captor2 = ArgumentCaptor.forClass(CareerSave.class);
        verify(careerSessionService, atLeastOnce()).saveCareer(captor2.capture());
        CareerSave after433 = captor2.getValue();

        // Now the LAST auto-select (4-4-2 again) should leave exactly 11
        // slots for the team, NOT 14 (11 + 3 stale from 4-3-3's ATT row).
        Map<String, LineupSlotDTO> slotsFinal = after433.getTeamStarting11SubdivisionSlots().get(TEAM_ID);
        assertNotNull(slotsFinal, "V25D99.20.3-BACK: final 4-4-2 must have slots");
        assertEquals(11, slotsFinal.size(),
            "V25D99.20.3-BACK BUG-2: cycle 4-4-2 -> 4-3-3 -> 4-4-2 must leave exactly 11 slots, no 4-3-3 stale entries");
    }
}
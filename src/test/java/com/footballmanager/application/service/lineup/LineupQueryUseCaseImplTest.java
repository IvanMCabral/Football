package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * MVP1-lineup-cancha-1.6: Tests para {@link LineupQueryUseCaseImpl}, en particular
 * la lectura de formación persistida con fallback a inferFormation (F3).
 *
 * <p>Antes del sprint 1.6, {@code buildLineupDTO} computaba formation con
 * {@code lineupHelper.inferFormation(lineup)} (cuenta DEF/MID/ATT de la lineup
 * cambiaba formación sin reasignar jugadores.
 *
 * <p>F3 introdujo {@code teamStarting11Formation} en {@link CareerSave} y un método
 * privado {@code readPersistedFormation} que prefiere el valor persistido y solo
 * hace fallback a {@code inferFormation} para saves viejos (sin el campo).
 */
@ExtendWith(MockitoExtension.class)
class LineupQueryUseCaseImplTest {

    @Mock
    private CareerRepository careerRepository;

    private LineupHelper lineupHelper;
    private LineupQueryUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-query-formation-001";

    @BeforeEach
    void setUp() {
        lineupHelper = new LineupHelper();
        // /yPct. Tests don't care about coords, but the constructor now
        // requires it (real coord lookup happens via the service's
        // FormationDTO cache). Null would NPE; pass a real instance
        // without wiring coords.
        useCase = new LineupQueryUseCaseImpl(careerRepository, lineupHelper, new FormationService());
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

    private CareerSave makeCareerWithLineup(List<SessionPlayer> players, List<String> lineupIds) {
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
        career.setTeamStarting11(new HashMap<>(Map.of(TEAM_ID, lineupIds)));
        return career;
    }

    /**
     * MVP1-lineup-cancha-1.6 (Test 5, F3): si un CareerSave no tiene
     * {@code teamStarting11Formation} (saves viejos de sprint 1.5 o anteriores),
     * {@code getCurrentLineup} debe hacer fallback a {@code lineupHelper.inferFormation}
     * para mantener backward compat — no se requiere migración explícita.
     *
     * <p>Setup: career sin teamStarting11Formation (null) + lineup 4-4-2 (4 DEF + 4 MID + 2 ATT).
     * Expectativa: response.formation == "4-4-2" (inferido del role distribution).
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: getCurrentLineup fallback a inferFormation cuando teamStarting11Formation está ausente (saves viejos)")
    void getCurrentLineup_fallsBackToInfer_whenFormationMissing() {
        // Squad 4-4-2: 4 DEF (GK+3 CB/LB/RB) + 4 MID (CM/CM/LM/RM) + 2 ATT (ST/ST)
        List<SessionPlayer> squad442 = List.of(
            makeHealthy("gk-fb", "GK Fallback", "GK"),
            makeHealthy("def1-fb", "Def A Fallback", "CB"),
            makeHealthy("def2-fb", "Def B Fallback", "CB"),
            makeHealthy("def3-fb", "Def C Fallback", "LB"),
            makeHealthy("def4-fb", "Def D Fallback", "RB"),
            makeHealthy("mid1-fb", "Mid A Fallback", "CM"),
            makeHealthy("mid2-fb", "Mid B Fallback", "CM"),
            makeHealthy("mid3-fb", "Mid C Fallback", "LM"),
            makeHealthy("mid4-fb", "Mid D Fallback", "RM"),
            makeHealthy("att1-fb", "Att A Fallback", "ST"),
            makeHealthy("att2-fb", "Att B Fallback", "ST")
        );

        List<String> lineup442 = List.of("gk-fb", "def1-fb", "def2-fb", "def3-fb", "def4-fb",
            "mid1-fb", "mid2-fb", "mid3-fb", "mid4-fb", "att1-fb", "att2-fb");

        CareerSave career = makeCareerWithLineup(squad442, lineup442);

        // teamStarting11Formation NO está seteado — simula save viejo de 1.5 o anterior.
        // El campo debería ser null o un mapa vacío. Por las dudas verificamos ambos casos:
        assert career.getTeamStarting11Formation() == null
            || career.getTeamStarting11Formation().isEmpty()
            : "CareerSave de save viejo no debería tener teamStarting11Formation poblado";

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertNotNull(dto.formation(),
                    "MVP1-lineup-cancha-1.6 F3 fallback: formation debe ser inferida, no null");
                assertEquals("4-4-2", dto.formation(),
                    "MVP1-lineup-cancha-1.6 F3: fallback a inferFormation para saves viejos → 4-4-2 (4 DEF + 4 MID + 2 ATT)");
                assertEquals(11, dto.players().size());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getCurrentLineup: null user team id returns empty lineup instead of MapN pk null")
    void getCurrentLineup_nullUserTeamId_returnsEmptyLineup() {
        CareerSave career = makeCareerWithLineup(List.of(), List.of());
        career.setUserSessionTeamId(null);
        career.setTeamStarting11(Map.of(TEAM_ID, List.of("ghost-player")));

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(false, dto.confirmed());
                assertTrue(dto.players().isEmpty());
                assertTrue(dto.slots().isEmpty());
                assertEquals(0, dto.chemistryScore());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getCurrentLineup filtra slots stale/duplicados y devuelve maximo 11")
    void getCurrentLineup_filtersStaleAndDuplicatedPersistedSlots() {
        List<SessionPlayer> squad442 = List.of(
            makeHealthy("gk-clean", "GK Clean", "GK"),
            makeHealthy("def1-clean", "Def A Clean", "CB"),
            makeHealthy("def2-clean", "Def B Clean", "CB"),
            makeHealthy("def3-clean", "Def C Clean", "LB"),
            makeHealthy("def4-clean", "Def D Clean", "RB"),
            makeHealthy("mid1-clean", "Mid A Clean", "CM"),
            makeHealthy("mid2-clean", "Mid B Clean", "CM"),
            makeHealthy("mid3-clean", "Mid C Clean", "LM"),
            makeHealthy("mid4-clean", "Mid D Clean", "RM"),
            makeHealthy("att1-clean", "Att A Clean", "ST"),
            makeHealthy("att2-clean", "Att B Clean", "ST"),
            makeHealthy("bench-clean", "Bench Clean", "ST")
        );
        List<String> lineup442 = List.of(
            "gk-clean", "def1-clean", "def2-clean", "def3-clean", "def4-clean",
            "mid1-clean", "mid2-clean", "mid3-clean", "mid4-clean", "att1-clean", "att2-clean");
        CareerSave career = makeCareerWithLineup(squad442, lineup442);
        career.setTeamStarting11Formation(Map.of(TEAM_ID, "4-4-2"));

        Map<String, LineupSlotDTO> dirtySlots = new java.util.LinkedHashMap<>();
        dirtySlots.put("GK-1", new LineupSlotDTO("gk-clean", "GK-1"));
        dirtySlots.put("S22-2", new LineupSlotDTO("def1-clean", "S22-2"));
        dirtySlots.put("S23-1", new LineupSlotDTO("def2-clean", "S23-1"));
        dirtySlots.put("S23-3", new LineupSlotDTO("def3-clean", "S23-3"));
        dirtySlots.put("S24-2", new LineupSlotDTO("def4-clean", "S24-2"));
        dirtySlots.put("S16-2", new LineupSlotDTO("mid1-clean", "S16-2"));
        dirtySlots.put("S17-1", new LineupSlotDTO("mid2-clean", "S17-1"));
        dirtySlots.put("S17-3", new LineupSlotDTO("mid3-clean", "S17-3"));
        dirtySlots.put("S18-2", new LineupSlotDTO("mid4-clean", "S18-2"));
        dirtySlots.put("S05-1", new LineupSlotDTO("att1-clean", "S05-1"));
        dirtySlots.put("S05-3", new LineupSlotDTO("att2-clean", "S05-3"));
        dirtySlots.put("S04-1-stale-bench", new LineupSlotDTO("bench-clean", "S04-1"));
        dirtySlots.put("S06-3-duplicate", new LineupSlotDTO("att2-clean", "S06-3"));
        career.setTeamStarting11SubdivisionSlots(Map.of(TEAM_ID, dirtySlots));

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(11, dto.players().size());
                assertEquals(11, dto.slots().size(),
                    "La respuesta no debe arrastrar slots viejos de banco ni duplicados");
                assertTrue(dto.slots().stream().noneMatch(slot -> "bench-clean".equals(slot.playerId())));
                assertEquals(11, dto.slots().stream().map(LineupSlotDTO::playerId).distinct().count());
            })
            .verifyComplete();
    }

    /**
     * MVP1-lineup-cancha-1.6 (F3): si teamStarting11Formation TIENE valor para el
     * team, getCurrentLineup debe retornar ESA formación, no la inferida.
     *
     * <p>Setup: career con teamStarting11Formation: {TEAM_ID → "4-3-3"} + lineup con
     * 4 DEF + 3 MID + 3 ATT. Aunque inferFormation daría "4-3-3" también, el test
     * valida que el read path prefiere el valor persistido.
     */
    @Test
    @DisplayName("MVP1-lineup-cancha-1.6: getCurrentLineup retorna formación persistida (no inferida)")
    void getCurrentLineup_returnsPersistedFormation() {
        // Squad 4-3-3
        List<SessionPlayer> squad433 = List.of(
            makeHealthy("gk-pf", "GK Persist", "GK"),
            makeHealthy("lb-pf", "LB Persist", "LB"),
            makeHealthy("cb1-pf", "CB A Persist", "CB"),
            makeHealthy("cb2-pf", "CB B Persist", "CB"),
            makeHealthy("rb-pf", "RB Persist", "RB"),
            makeHealthy("cm1-pf", "CM A Persist", "CM"),
            makeHealthy("cm2-pf", "CM B Persist", "CM"),
            makeHealthy("cm3-pf", "CM C Persist", "CM"),
            makeHealthy("lw-pf", "LW Persist", "LW"),
            makeHealthy("st-pf", "ST Persist", "ST"),
            makeHealthy("rw-pf", "RW Persist", "RW")
        );

        List<String> lineup433 = List.of("gk-pf", "lb-pf", "cb1-pf", "cb2-pf", "rb-pf",
            "cm1-pf", "cm2-pf", "cm3-pf", "lw-pf", "st-pf", "rw-pf");

        CareerSave career = makeCareerWithLineup(squad433, lineup433);

        // Setear formación persistida. Probamos con "4-4-2" para validar que
        // el read path prefiere la persistida sobre la inferida.
        Map<String, String> formationMap = new HashMap<>();
        formationMap.put(TEAM_ID, "4-4-2");
        career.setTeamStarting11Formation(formationMap);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals("4-4-2", dto.formation(),
                    "MVP1-lineup-cancha-1.6 F3: formación persistida (4-4-2) gana sobre inferFormation (que daría 4-3-3)");
                assertEquals(11, dto.players().size());
            })
            .verifyComplete();
    }

    /**
     * (manual-select short-handed mode, 7-10 players), getCurrentLineup debe
     * retornar un warning LINEUP_SHORT_HANDED en el response.
     *
     * <p>Pre-C25: warnings era List.of() en este path, por lo que el banner
     * "Lineup short-handed" desaparecía al recargar /squad.
     */
    @Test
    @DisplayName("getCurrentLineup retorna LINEUP_SHORT_HANDED si lineup tiene < 11 players")
    void getCurrentLineup_returnsShortHandedWarning_whenLineupLessThan11() {
        // Squad 4-4-2 reducido a 8 players (short-handed manual mode)
        List<SessionPlayer> squadSh = new ArrayList<>(List.of(
            makeHealthy("gk-sh", "GK ShortHand", "GK"),
            makeHealthy("def1-sh", "Def A SH", "CB"),
            makeHealthy("def2-sh", "Def B SH", "CB"),
            makeHealthy("def3-sh", "Def C SH", "LB"),
            makeHealthy("mid1-sh", "Mid A SH", "CM"),
            makeHealthy("mid2-sh", "Mid B SH", "CM"),
            makeHealthy("mid3-sh", "Mid C SH", "LM"),
            makeHealthy("att1-sh", "Att A SH", "ST")
        ));

        List<String> lineupSh = List.of("gk-sh", "def1-sh", "def2-sh", "def3-sh",
            "mid1-sh", "mid2-sh", "mid3-sh", "att1-sh");

        CareerSave career = makeCareerWithLineup(squadSh, lineupSh);
        Map<String, String> formationMap = new HashMap<>();
        formationMap.put(TEAM_ID, "4-4-2");
        career.setTeamStarting11Formation(formationMap);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertNotNull(dto.warnings(), "warnings no debe ser null en el response");
                assertEquals(8, dto.players().size(), "lineup persistido tiene 8 players");
                // Verificar que LINEUP_SHORT_HANDED está presente
                boolean hasShortHanded = dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code()));
                assertTrue(hasShortHanded,
                    "lineup con 8 players debe emitir LINEUP_SHORT_HANDED warning, "
                        + "warnings=" + dto.warnings());
                // Verificar available=8 en el warning
                LineupWarningDTO shortHanded = dto.warnings().stream()
                    .filter(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code()))
                    .findFirst().orElseThrow();
                assertEquals(8, shortHanded.available(),
                    "LINEUP_SHORT_HANDED.available debe reflejar el count del lineup");
            })
            .verifyComplete();
    }

    /**
     * suspendido (red card o suspensionRemainingMatches &gt; 0), {@code getCurrentLineup}
     * debe excluirlo del response. La persistencia interna puede mantenerlo
     * (otros paths dependen), pero la API no debe surfacearlo como parte del
     * "current starting XI".
     *
     * <p>Pre-C40 el filtro no existía — un player suspendido por roja aparecía
     * el filtro en {@link LineupQueryUseCaseImpl} líneas 74-85. Este test es la
     * red de seguridad contra regresiones futuras.
     */
    @Test
    @DisplayName("getCurrentLineup filtra players suspendidos del response")
    void getCurrentLineup_filtersOutSuspendedPlayers() {
        // Squad 4-4-2 full strength, marcamos 1 player como suspended.
        List<SessionPlayer> squadSus = List.of(
            makeHealthy("gk-sus", "GK Suspended", "GK"),
            makeHealthy("def1-sus", "Def A Sus", "CB"),
            makeHealthy("def2-sus", "Def B Sus", "CB"),
            makeHealthy("def3-sus", "Def C Sus", "LB"),
            makeHealthy("def4-sus", "Def D Sus", "RB"),
            makeHealthy("mid1-sus", "Mid A Sus", "CM"),
            makeHealthy("mid2-sus", "Mid B Sus", "CM"),
            makeHealthy("mid3-sus", "Mid C Sus", "LM"),
            makeHealthy("mid4-sus", "Mid D Sus", "RM"),
            makeHealthy("att1-sus", "Att A Sus", "ST"),
            makeHealthy("att2-sus", "Att B Sus", "ST")
        );

        // Marcamos "mid3-sus" como suspended por roja (suspended=true + remaining=1).
        SessionPlayer suspendedPlayer = squadSus.stream()
            .filter(p -> "mid3-sus".equals(p.getSessionPlayerId()))
            .findFirst().orElseThrow();
        suspendedPlayer.setSuspended(true);
        suspendedPlayer.setSuspensionRemainingMatches(1);

        List<String> lineupSus = List.of("gk-sus", "def1-sus", "def2-sus", "def3-sus", "def4-sus",
            "mid1-sus", "mid2-sus", "mid3-sus", "mid4-sus", "att1-sus", "att2-sus");

        CareerSave career = makeCareerWithLineup(squadSus, lineupSus);
        Map<String, String> formationMap = new HashMap<>();
        formationMap.put(TEAM_ID, "4-4-2");
        career.setTeamStarting11Formation(formationMap);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                // El player suspendido debe estar excluido del response
                assertEquals(10, dto.players().size(),
                    "lineup de 11 con 1 suspendido debe devolver 10 players");
                boolean suspendedInResponse = dto.players().stream()
                    .anyMatch(p -> "mid3-sus".equals(p.playerId()));
                assertTrue(!suspendedInResponse,
                    "el player con suspended=true NO debe aparecer en el response");
                // Players sanos sí deben estar
                boolean mid1InResponse = dto.players().stream()
                    .anyMatch(p -> "mid1-sus".equals(p.playerId()));
                assertTrue(mid1InResponse,
                    "players sanos deben seguir en el response");
            })
            .verifyComplete();
    }

    /**
     * {@code suspensionRemainingMatches > 0} pero {@code suspended=false} (caso
     * edge: estado inconsistente entre flags) también debe filtrarse. El código
     * en {@link LineupQueryUseCaseImpl} línea 83-84 aplica ambos filtros
     * independientemente — el segundo filtro es la red de seguridad.
     */
    @Test
    @DisplayName("getCurrentLineup filtra players con suspensionRemainingMatches > 0 aunque suspended=false")
    void getCurrentLineup_filtersOutPlayersWithRemainingSuspension() {
        List<SessionPlayer> squadRem = List.of(
            makeHealthy("gk-rem", "GK Rem", "GK"),
            makeHealthy("def1-rem", "Def A Rem", "CB"),
            makeHealthy("def2-rem", "Def B Rem", "CB"),
            makeHealthy("def3-rem", "Def C Rem", "LB"),
            makeHealthy("def4-rem", "Def D Rem", "RB"),
            makeHealthy("mid1-rem", "Mid A Rem", "CM"),
            makeHealthy("mid2-rem", "Mid B Rem", "CM"),
            makeHealthy("mid3-rem", "Mid C Rem", "LM"),
            makeHealthy("mid4-rem", "Mid D Rem", "RM"),
            makeHealthy("att1-rem", "Att A Rem", "ST"),
            makeHealthy("att2-rem", "Att B Rem", "ST")
        );

        // Marcamos "att1-rem" con suspensionRemainingMatches=2 pero suspended=false
        // (estado defensivo: si el flag suspended fue reseteado por algún path
        // pero el remaining match no, debe seguir filtrado).
        SessionPlayer remainingSusp = squadRem.stream()
            .filter(p -> "att1-rem".equals(p.getSessionPlayerId()))
            .findFirst().orElseThrow();
        remainingSusp.setSuspended(false);
        remainingSusp.setSuspensionRemainingMatches(2);

        List<String> lineupRem = List.of("gk-rem", "def1-rem", "def2-rem", "def3-rem", "def4-rem",
            "mid1-rem", "mid2-rem", "mid3-rem", "mid4-rem", "att1-rem", "att2-rem");

        CareerSave career = makeCareerWithLineup(squadRem, lineupRem);
        Map<String, String> formationMap = new HashMap<>();
        formationMap.put(TEAM_ID, "4-4-2");
        career.setTeamStarting11Formation(formationMap);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertEquals(10, dto.players().size(),
                    "lineup con 1 player con suspensionRemainingMatches>0 debe devolver 10 players");
                boolean remainingInResponse = dto.players().stream()
                    .anyMatch(p -> "att1-rem".equals(p.playerId()));
                assertTrue(!remainingInResponse,
                    "player con suspensionRemainingMatches>0 NO debe aparecer en el response");
            })
            .verifyComplete();
    }

    /**
     * pero la squad NO tiene GK natural (caso edge), getCurrentLineup debe
     * omitir LINEUP_NO_GOALKEEPER cuando sí hay un player con position="GK"
     * en la lineup.
     *
     * <p>Este test verifica que el helper detectShortHandedWarnings funciona
     * correctamente cuando la lineup incluye un GK. (Es el happy path; el caso
     * de NO GK está cubierto en LineupCommandUseCaseImplTest.)
     */
    @Test
    @DisplayName("getCurrentLineup no emite LINEUP_NO_GOALKEEPER si lineup tiene GK")
    void getCurrentLineup_omitsNoGoalkeeper_whenLineupHasGK() {
        // Squad completa 4-4-2 (11 players, full strength)
        List<SessionPlayer> squadFull = List.of(
            makeHealthy("gk-full", "GK Full", "GK"),
            makeHealthy("def1-full", "Def A Full", "CB"),
            makeHealthy("def2-full", "Def B Full", "CB"),
            makeHealthy("def3-full", "Def C Full", "LB"),
            makeHealthy("def4-full", "Def D Full", "RB"),
            makeHealthy("mid1-full", "Mid A Full", "CM"),
            makeHealthy("mid2-full", "Mid B Full", "CM"),
            makeHealthy("mid3-full", "Mid C Full", "LM"),
            makeHealthy("mid4-full", "Mid D Full", "RM"),
            makeHealthy("att1-full", "Att A Full", "ST"),
            makeHealthy("att2-full", "Att B Full", "ST")
        );

        List<String> lineupFull = List.of("gk-full", "def1-full", "def2-full", "def3-full", "def4-full",
            "mid1-full", "mid2-full", "mid3-full", "mid4-full", "att1-full", "att2-full");

        CareerSave career = makeCareerWithLineup(squadFull, lineupFull);
        Map<String, String> formationMap = new HashMap<>();
        formationMap.put(TEAM_ID, "4-4-2");
        career.setTeamStarting11Formation(formationMap);

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getCurrentLineup(UUID.fromString(USER_ID)))
            .assertNext(dto -> {
                assertNotNull(dto);
                assertNotNull(dto.warnings());
                // No debe haber LINEUP_NO_GOALKEEPER ni LINEUP_SHORT_HANDED
                boolean hasNoGK = dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_NO_GOALKEEPER.equals(w.code()));
                boolean hasShortHanded = dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code()));
                assertTrue(!hasNoGK,
                    "lineup con GK no debe emitir LINEUP_NO_GOALKEEPER, "
                        + "warnings=" + dto.warnings());
                assertTrue(!hasShortHanded,
                    "lineup full-strength (11 players) no debe emitir LINEUP_SHORT_HANDED, "
                        + "warnings=" + dto.warnings());
            })
            .verifyComplete();
    }
}

package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
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
 * persistida). Esto causaba BUG_FORMATION_NOT_PERSISTED cuando el usuario
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
        useCase = new LineupQueryUseCaseImpl(careerRepository, lineupHelper);
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
     * V25D65-C25 P0 (Test 3): si el lineup persistido tiene menos de 11 players
     * (manual-select short-handed mode, 7-10 players), getCurrentLineup debe
     * retornar un warning LINEUP_SHORT_HANDED en el response.
     *
     * <p>Pre-C25: warnings era List.of() en este path, por lo que el banner
     * "Lineup short-handed" desaparecía al recargar /squad.
     */
    @Test
    @DisplayName("V25D65-C25 P0: getCurrentLineup retorna LINEUP_SHORT_HANDED si lineup tiene < 11 players")
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
                assertNotNull(dto.warnings(), "V25D65-C25: warnings no debe ser null en el response");
                assertEquals(8, dto.players().size(), "lineup persistido tiene 8 players");
                // Verificar que LINEUP_SHORT_HANDED está presente
                boolean hasShortHanded = dto.warnings().stream()
                    .anyMatch(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code()));
                assertTrue(hasShortHanded,
                    "V25D65-C25: lineup con 8 players debe emitir LINEUP_SHORT_HANDED warning, "
                        + "warnings=" + dto.warnings());
                // Verificar available=8 en el warning
                LineupWarningDTO shortHanded = dto.warnings().stream()
                    .filter(w -> LineupWarningDTO.CODE_SHORT_HANDED.equals(w.code()))
                    .findFirst().orElseThrow();
                assertEquals(8, shortHanded.available(),
                    "V25D65-C25: LINEUP_SHORT_HANDED.available debe reflejar el count del lineup");
            })
            .verifyComplete();
    }

    /**
     * V25D65-C25 P0 (Test 4): si el lineup persistido tiene un GK en la lineup
     * pero la squad NO tiene GK natural (caso edge), getCurrentLineup debe
     * omitir LINEUP_NO_GOALKEEPER cuando sí hay un player con position="GK"
     * en la lineup.
     *
     * <p>Este test verifica que el helper detectShortHandedWarnings funciona
     * correctamente cuando la lineup incluye un GK. (Es el happy path; el caso
     * de NO GK está cubierto en LineupCommandUseCaseImplTest.)
     */
    @Test
    @DisplayName("V25D65-C25 P0: getCurrentLineup no emite LINEUP_NO_GOALKEEPER si lineup tiene GK")
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
                    "V25D65-C25: lineup con GK no debe emitir LINEUP_NO_GOALKEEPER, "
                        + "warnings=" + dto.warnings());
                assertTrue(!hasShortHanded,
                    "V25D65-C25: lineup full-strength (11 players) no debe emitir LINEUP_SHORT_HANDED, "
                        + "warnings=" + dto.warnings());
            })
            .verifyComplete();
    }
}

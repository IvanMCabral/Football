package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
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
        useCase = new LineupCommandUseCaseImpl(careerRepository, lineupHelper);
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
}
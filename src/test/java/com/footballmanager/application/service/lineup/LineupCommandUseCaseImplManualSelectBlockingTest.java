package com.footballmanager.application.service.lineup;

import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.repository.CareerRepository;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D6T — Lock the manual-select blocking contract at the use-case layer.
 *
 * <p>{@link LineupCommandUseCaseImpl#manualSelectLineup} delegates to
 * {@link LineupHelper#validatePlayerFitness} which already rejects
 * injured and suspended players. These tests pin that contract at the
 * use-case boundary so future refactors cannot silently drop the
 * blocking.
 */
@ExtendWith(MockitoExtension.class)
class LineupCommandUseCaseImplManualSelectBlockingTest {

    @Mock
    private CareerRepository careerRepository;

    private LineupHelper lineupHelper;
    private LineupCommandUseCaseImpl useCase;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TEAM_ID = "team-manual-block-001";

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

    private SessionPlayer makeSuspended(String id, String name, String position, int remaining) {
        SessionPlayer p = makeHealthy(id, name, position);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(remaining);
        return p;
    }

    private SessionPlayer makeInjured(String id, String name, String position) {
        SessionPlayer p = makeHealthy(id, name, position);
        p.setInjured(true);
        p.setInjuryRemainingMatches(2);
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

    /**
     * Builds a 4-4-2 lineup (1 GK + 4 DEF + 4 MID + 2 ATT = 11) from the healthy
     * players, then replaces the FIRST healthy attacker with the reserved player.
     * The reserved player must be a ST (for 4-4-2) — the tests construct squads
     * accordingly. After replacement, the lineup still has 11 players.
     */
    private List<String> buildLineupWithReserved(List<SessionPlayer> healthy, String reservedId) {
        List<String> lineup = new ArrayList<>();
        // GK
        lineup.add(healthy.stream().filter(p -> "GK".equals(p.getPosition())).findFirst()
            .orElseThrow().getSessionPlayerId());
        // 4 defenders
        healthy.stream().filter(p -> "CB".equals(p.getPosition()) || "LB".equals(p.getPosition())
                || "RB".equals(p.getPosition()))
            .limit(4).map(SessionPlayer::getSessionPlayerId).forEach(lineup::add);
        // 4 midfielders
        healthy.stream().filter(p -> "CM".equals(p.getPosition()) || "LM".equals(p.getPosition())
                || "RM".equals(p.getPosition()))
            .limit(4).map(SessionPlayer::getSessionPlayerId).forEach(lineup::add);
        // 2 attackers
        healthy.stream().filter(p -> "ST".equals(p.getPosition()))
            .limit(2).map(SessionPlayer::getSessionPlayerId).forEach(lineup::add);
        if (lineup.size() != 11) {
            throw new IllegalStateException("Expected 11 healthy players, got " + lineup.size()
                + " — squad construction is broken for this test");
        }
        // Replace the FIRST healthy attacker with the reserved one (still 11 players)
        lineup.set(10, reservedId);
        return lineup;
    }

    @Test
    void manualSelect_rejectsSuspendedTruePlayer() {
        // Squad: 1 suspended ST + 1 GK + 4 DEF + 4 MID + 2 ST = 12 players.
        // We attempt a 4-4-2 lineup that swaps one of the healthy STs for the
        // suspended one. The use case MUST reject with a "suspended" message.
        List<SessionPlayer> players = List.of(
            makeSuspended("sus-1", "Suspended Star", "ST", 1),
            makeHealthy("gk-1", "Good GK", "GK"),
            makeHealthy("def-1", "Def A", "CB"),
            makeHealthy("def-2", "Def B", "CB"),
            makeHealthy("def-3", "Def C", "LB"),
            makeHealthy("def-4", "Def D", "RB"),
            makeHealthy("mid-1", "Mid A", "CM"),
            makeHealthy("mid-2", "Mid B", "CM"),
            makeHealthy("mid-3", "Mid C", "LM"),
            makeHealthy("mid-4", "Mid D", "RM"),
            makeHealthy("att-1", "Attacker A", "ST"),
            makeHealthy("att-2", "Attacker B", "ST")
        );
        CareerSave career = makeCareer(players);

        List<SessionPlayer> healthyPlayers = players.stream()
            .filter(p -> !"sus-1".equals(p.getSessionPlayerId())).toList();
        List<String> lineup = buildLineupWithReserved(healthyPlayers, "sus-1");

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineup))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof IllegalArgumentException,
                    "Expected IllegalArgumentException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().toLowerCase().contains("suspended"),
                    "Expected message to mention 'suspended', got: " + err.getMessage());
            })
            .verify();

        // Should NOT have saved the career — the request was rejected before save
        verify(careerRepository, never()).save(any());
    }

    @Test
    void manualSelect_rejectsSuspendedRemainingPositive() {
        // Player "st-1" has suspended=false but suspensionRemainingMatches=2
        // (a "stale" state). The use case MUST still reject.
        List<SessionPlayer> players = new ArrayList<>(List.of(
            makeHealthy("st-1", "Stale Susp", "ST"),
            makeHealthy("gk-1", "Good GK", "GK"),
            makeHealthy("def-1", "Def A", "CB"),
            makeHealthy("def-2", "Def B", "CB"),
            makeHealthy("def-3", "Def C", "LB"),
            makeHealthy("def-4", "Def D", "RB"),
            makeHealthy("mid-1", "Mid A", "CM"),
            makeHealthy("mid-2", "Mid B", "CM"),
            makeHealthy("mid-3", "Mid C", "LM"),
            makeHealthy("mid-4", "Mid D", "RM"),
            makeHealthy("att-1", "Attacker A", "ST"),
            makeHealthy("att-2", "Attacker B", "ST")
        ));
        // Inject the stale state on st-1
        players.get(0).setSuspensionRemainingMatches(2);
        // We need 2 STs in the healthy list to keep formation valid when we
        // swap st-1 in. att-1 will be in the lineup, st-1 will be swapped in.
        CareerSave career = makeCareer(players);

        List<SessionPlayer> healthyPlayers = players.stream()
            .filter(p -> !"st-1".equals(p.getSessionPlayerId())).toList();
        List<String> lineup = buildLineupWithReserved(healthyPlayers, "st-1");

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineup))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof IllegalArgumentException,
                    "Expected IllegalArgumentException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().toLowerCase().contains("suspended"),
                    "Expected message to mention 'suspended', got: " + err.getMessage());
            })
            .verify();

        verify(careerRepository, never()).save(any());
    }

    @Test
    void manualSelect_rejectsInjuredTruePlayer() {
        // Same shape as the suspended test, but the reserved player is injured.
        List<SessionPlayer> players = List.of(
            makeInjured("inj-1", "Injured Star", "ST"),
            makeHealthy("gk-1", "Good GK", "GK"),
            makeHealthy("def-1", "Def A", "CB"),
            makeHealthy("def-2", "Def B", "CB"),
            makeHealthy("def-3", "Def C", "LB"),
            makeHealthy("def-4", "Def D", "RB"),
            makeHealthy("mid-1", "Mid A", "CM"),
            makeHealthy("mid-2", "Mid B", "CM"),
            makeHealthy("mid-3", "Mid C", "LM"),
            makeHealthy("mid-4", "Mid D", "RM"),
            makeHealthy("att-1", "Attacker A", "ST"),
            makeHealthy("att-2", "Attacker B", "ST")
        );
        CareerSave career = makeCareer(players);

        List<SessionPlayer> healthyPlayers = players.stream()
            .filter(p -> !"inj-1".equals(p.getSessionPlayerId())).toList();
        List<String> lineup = buildLineupWithReserved(healthyPlayers, "inj-1");

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineup))
            .expectErrorSatisfies(err -> {
                assertTrue(err instanceof IllegalArgumentException,
                    "Expected IllegalArgumentException, got " + err.getClass().getSimpleName());
                assertTrue(err.getMessage().toLowerCase().contains("injured"),
                    "Expected message to mention 'injured', got: " + err.getMessage());
            })
            .verify();

        verify(careerRepository, never()).save(any());
    }

    @Test
    void manualSelect_acceptsAllHealthyLineup() {
        List<SessionPlayer> players = List.of(
            makeHealthy("gk-1", "Good GK", "GK"),
            makeHealthy("def-1", "Def A", "CB"),
            makeHealthy("def-2", "Def B", "CB"),
            makeHealthy("def-3", "Def C", "LB"),
            makeHealthy("def-4", "Def D", "RB"),
            makeHealthy("mid-1", "Mid A", "CM"),
            makeHealthy("mid-2", "Mid B", "CM"),
            makeHealthy("mid-3", "Mid C", "LM"),
            makeHealthy("mid-4", "Mid D", "RM"),
            makeHealthy("att-1", "Attacker A", "ST"),
            makeHealthy("att-2", "Attacker B", "ST")
        );
        CareerSave career = makeCareer(players);
        List<String> lineup = players.stream().map(SessionPlayer::getSessionPlayerId).toList();

        when(careerRepository.findById(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.manualSelectLineup(UUID.fromString(USER_ID), "4-4-2", lineup))
            .assertNext(dto -> {
                assertTrue(dto.players().size() == 11);
            })
            .verifyComplete();

        verify(careerRepository).save(any());
    }
}

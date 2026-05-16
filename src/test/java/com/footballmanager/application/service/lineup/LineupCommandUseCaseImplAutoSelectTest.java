package com.footballmanager.application.service.lineup;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        useCase = new LineupCommandUseCaseImpl(careerRepository, lineupHelper);
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
}
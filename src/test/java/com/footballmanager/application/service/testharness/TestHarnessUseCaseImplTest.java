package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase.CustomFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D20-TESTHARNESS — Unit tests for {@link TestHarnessUseCaseImpl}.
 *
 * <p>Strategy: Mockito-only (no Spring context). Verifies the
 * state-mutation contract on {@link CareerSave} and the delegation
 * to {@link CareerRepository} / {@link CareerSessionService}.
 *
 * <p>The {@code setFormation} test is the critical regression guard for
 * the sprint 1.7 bug (BUG_FORMATION_PERSIST_IGNORED) — it asserts that
 * the formation is persisted to BOTH {@code SessionTeam.formation} AND
 * {@code teamStarting11Formation} map (the V24 engine reads from the
 * latter).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestHarnessUseCaseImpl — unit tests")
class TestHarnessUseCaseImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;

    @InjectMocks private TestHarnessUseCaseImpl useCase;

    private CareerSave career;

    @BeforeEach
    void setUp() {
        career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId("user-team-id");

        // Add 3 players to the user team with various flags to test resetInjuries
        SessionPlayer p1 = playerWithInjury("p1");
        SessionPlayer p2 = playerWithSuspension("p2");
        SessionPlayer p3 = healthyPlayer("p3");
        SessionTeam userTeam = new SessionTeam();
        userTeam.setSessionTeamId("user-team-id");
        userTeam.setFormation("4-3-3");

        // Wire player to team via reflection (since the squad wiring is
        // complex and we want to test the resetInjuries logic in isolation).
        wireSquad(career, "user-team-id", List.of(p1, p2, p3));
    }

    // ========== replaceFixtures ==========

    @Test
    @DisplayName("replaceFixtures: valid list replaces fixtures and resets currentRound=1")
    void replaceFixtures_withValidList_replacesAndSaves() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        List<CustomFixture> fixtures = List.of(
            new CustomFixture("user-team-id", "rival-1", 1, null),
            new CustomFixture("user-team-id", "rival-2", 2, null)
        );

        useCase.replaceFixtures(USER_ID, fixtures)
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(career.getTournamentState().getFixtures()).hasSize(2);
        assertThat(career.getTournamentState().getCurrentRound()).isEqualTo(1);
        assertThat(career.getTournamentState().getTotalRounds()).isEqualTo(2);
        assertThat(career.getTournamentState().getFinished()).isFalse();
        verify(careerRepository, times(1)).save(career);
    }

    @Test
    @DisplayName("replaceFixtures: empty list returns Mono.error")
    void replaceFixtures_emptyList_returnsError() {
        useCase.replaceFixtures(USER_ID, List.of())
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        verify(careerRepository, never()).findById(anyString());
        verify(careerRepository, never()).save(any());
    }

    // ========== resetInjuries ==========

    @Test
    @DisplayName("resetInjuries: clears injured/suspended/yellow/red across the whole squad")
    void resetInjuries_clearsAllFlags() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.resetInjuries(USER_ID)
            .as(StepVerifier::create)
            .verifyComplete();

        // Re-fetch the squad from career
        List<SessionPlayer> squad = career.getTeamSquad("user-team-id");
        for (SessionPlayer p : squad) {
            assertThat(p.getInjured()).isFalse();
            assertThat(p.getInjuryType()).isNull();
            assertThat(p.getInjuryRemainingMatches()).isZero();
            assertThat(p.getSuspended()).isFalse();
            assertThat(p.getSuspensionRemainingMatches()).isZero();
            assertThat(p.getYellowCards()).isZero();
            assertThat(p.getRedCards()).isZero();
        }
        verify(careerRepository, times(1)).save(career);
    }

    // ========== setFormation (CRITICAL — sprint 1.7 regression guard) ==========

    @Test
    @DisplayName("setFormation: persists to BOTH SessionTeam.formation AND teamStarting11Formation map (bug 1.7 fix)")
    void setFormation_persistsInBothSessionTeamAndFormationMap() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.setFormation(USER_ID, "3-5-2")
            .as(StepVerifier::create)
            .verifyComplete();

        SessionTeam userTeam = career.getSessionTeam("user-team-id");
        assertThat(userTeam.getFormation())
            .as("SessionTeam.formation must be updated")
            .isEqualTo("3-5-2");

        assertThat(career.getTeamStarting11Formation().get("user-team-id"))
            .as("teamStarting11Formation map (the one the V24 engine reads) MUST be updated")
            .isEqualTo("3-5-2");

        verify(careerRepository, times(1)).save(career);
    }

    @Test
    @DisplayName("setFormation: blank formation returns Mono.error")
    void setFormation_blankFormation_returnsError() {
        useCase.setFormation(USER_ID, "  ")
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        verify(careerRepository, never()).save(any());
    }

    // ========== createCustom ==========

    @Test
    @DisplayName("createCustom: deletes existing career, starts fresh, then resets injuries")
    void createCustom_deletesThenStartsThenResets() {
        // The user already has a career (the @BeforeEach one). createCustom
        // should: deleteCareer → startNewCareer → resetInjuries.
        when(careerSessionService.deleteCareer(USER_ID))
            .thenReturn(Mono.empty());
        when(careerSessionService.startNewCareer(
                eq(USER_ID), eq("league-1"), eq("team-1"),
                eq("EASY"), eq("NORMAL"), eq(3)))
            .thenReturn(Mono.just(career));
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.createCustom(USER_ID, "league-1", "team-1", "EASY", "NORMAL", 3)
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        verify(careerSessionService, times(1)).deleteCareer(USER_ID);
        verify(careerSessionService, times(1)).startNewCareer(
            eq(USER_ID), eq("league-1"), eq("team-1"),
            eq("EASY"), eq("NORMAL"), eq(3));
        // save called twice: once for resetInjuries, once implicit from start
        verify(careerRepository, times(1)).save(any(CareerSave.class));
    }

    @Test
    @DisplayName("createCustom: teamsPerDivision < 2 returns Mono.error")
    void createCustom_invalidTeamsPerDivision_returnsError() {
        useCase.createCustom(USER_ID, "league-1", "team-1", "EASY", "NORMAL", 1)
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        verify(careerSessionService, never()).deleteCareer(any());
        verify(careerSessionService, never()).startNewCareer(
            any(), any(), any(), any(), any(), anyInt());
    }

    // ========== CustomFixture validation ==========

    @Test
    @DisplayName("CustomFixture: same homeTeamId and awayTeamId throws")
    void customFixture_sameHomeAway_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new CustomFixture("same", "same", 1, null));
    }

    @Test
    @DisplayName("CustomFixture: round < 1 throws")
    void customFixture_invalidRound_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new CustomFixture("home", "away", 0, null));
    }

    // ========== helpers ==========

    private SessionPlayer playerWithInjury(String id) {
        SessionPlayer p = healthyPlayer(id);
        p.setInjured(true);
        p.setInjuryType("HAMSTRING");
        p.setInjuryRemainingMatches(2);
        p.setYellowCards(1);
        return p;
    }

    private SessionPlayer playerWithSuspension(String id) {
        SessionPlayer p = healthyPlayer(id);
        p.setSuspended(true);
        p.setSuspensionRemainingMatches(1);
        p.setRedCards(1);
        return p;
    }

    private SessionPlayer healthyPlayer(String id) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setName("Player " + id);
        p.setAge(25);
        p.setPosition("MID");
        p.setAttack(70);
        p.setDefense(70);
        p.setTechnique(70);
        p.setSpeed(70);
        p.setStamina(70);
        p.setMentality(70);
        // initDefaults() is private — we replicate its effect here so
        // Boolean flags are non-null (required by AssertJ's isFalse/isZero).
        p.setInjured(false);
        p.setInjuryType(null);
        p.setInjuryRemainingMatches(0);
        p.setYellowCards(0);
        p.setRedCards(0);
        p.setSuspended(false);
        p.setSuspensionRemainingMatches(0);
        return p;
    }

    /**
     * Wires the SessionTeam + players into the career's managers using
     * reflection. We can't go through {@code CareerTeamManager.addSessionTeam}
     * / {@code CareerPlayerManager.addSessionPlayer} / {@code assignPlayerToSquad}
     * cleanly without exposing internals, so we call them reflectively.
     */
    @SuppressWarnings("unchecked")
    private void wireSquad(CareerSave career, String teamId, List<SessionPlayer> players) {
        try {
            // Get the teamManager and playerManager via reflection
            Field tmField = CareerSave.class.getDeclaredField("teamManager");
            tmField.setAccessible(true);
            Object teamManager = tmField.get(career);

            Field pmField = CareerSave.class.getDeclaredField("playerManager");
            pmField.setAccessible(true);
            Object playerManager = pmField.get(career);

            // Build and register the SessionTeam so career.getSessionTeam(teamId)
            // and career.getAllSessionTeams() both work.
            SessionTeam team = new SessionTeam();
            team.setSessionTeamId(teamId);
            team.setFormation("4-3-3");
            java.lang.reflect.Method addSessionTeam =
                teamManager.getClass().getMethod("addSessionTeam", SessionTeam.class);
            addSessionTeam.invoke(teamManager, team);

            // Register each player + assign to squad
            java.lang.reflect.Method addSessionPlayer =
                playerManager.getClass().getMethod("addSessionPlayer", SessionPlayer.class);
            java.lang.reflect.Method assign =
                teamManager.getClass().getMethod(
                    "assignPlayerToSquad", String.class, String.class);

            for (SessionPlayer p : players) {
                addSessionPlayer.invoke(playerManager, p);
                assign.invoke(teamManager, p.getSessionPlayerId(), teamId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to wire squad via reflection", e);
        }
    }
}

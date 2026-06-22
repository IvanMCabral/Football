package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchStatus;
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
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private V24DetailedMatchStoragePort v24StoragePort;
    // V24D20-SANDBOX-V2-MVP F5: use the REAL V24MatchContextFactory so
    // build() produces a context with valid homeTeam/awayTeam. A mocked
    // factory would return a context with null teams, which causes
    // V24TeamMatchState.create to throw NPE("team must not be null").
    // We construct useCase manually in setUp() to inject the real
    // factory (Mockito's @InjectMocks doesn't play well with non-mock
    // fields in this JUnit/Mockito version).
    private V24MatchContextFactory v24ContextFactory;
    private TestHarnessUseCaseImpl useCase;

    private CareerSave career;

    @BeforeEach
    void setUp() {
        v24ContextFactory = new V24MatchContextFactory();
        useCase = new TestHarnessUseCaseImpl(
            careerRepository, careerSessionService,
            v24ContextFactory, v24StoragePort);

        career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId("user-team-id");

        // Add 11 players to the user team — first 3 have injury/suspension
        // flags to test resetInjuries, the rest are healthy. The V24
        // engine requires MIN_AVAILABLE_PLAYERS=7 in the starting list,
        // so we need 11 total to match the engine's expectation.
        List<SessionPlayer> userPlayers = new java.util.ArrayList<>();
        userPlayers.add(playerWithInjury("u-p1"));
        userPlayers.add(playerWithSuspension("u-p2"));
        for (int i = 3; i <= 11; i++) {
            userPlayers.add(healthyPlayer("u-p" + i));
        }
        SessionTeam userTeam = new SessionTeam();
        userTeam.setSessionTeamId("user-team-id");
        userTeam.setFormation("4-3-3");

        // Add 11 players to the rival team (all healthy). The V24 engine
        // needs MIN_AVAILABLE_PLAYERS=7 to start a match.
        List<SessionPlayer> rivalPlayers = new java.util.ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            rivalPlayers.add(healthyPlayer("r-p" + i));
        }
        SessionTeam rivalTeam = new SessionTeam();
        rivalTeam.setSessionTeamId("rival-1");
        rivalTeam.setFormation("4-4-2");

        // Wire players to teams via reflection.
        wireSquad(career, "user-team-id", userPlayers);
        wireSquad(career, "rival-1", rivalPlayers);

        // Seed the tournament with a single completed fixture so replayMatch
        // has something to operate on. The fixture is COMPLETED with a
        // placeholder result; replayMatch should reset it to PENDING,
        // re-simulate, and set the new result.
        MatchFixture completed = new MatchFixture(
            "match-001", "user-team-id", "rival-1", 1);
        completed.complete(new MatchFixture.MatchResultData(0, 0, 0, 0, 0, 0));
        career.getTournamentState().setFixtures(List.of(completed));
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
    @DisplayName("replaceFixtures: empty list returns Mono.empty() (no-op)")
    void replaceFixtures_emptyList_returnsNoOp() {
        useCase.replaceFixtures(USER_ID, List.of())
            .as(StepVerifier::create)
            .verifyComplete();

        // V24D24.1: empty list = no-op, NO tocar repo ni cache
        verify(careerRepository, never()).findById(anyString());
        verify(careerRepository, never()).save(any());
        verifyNoInteractions(careerSessionService);
    }

    // V24D24.1 — null guard regression. The empty-list path is now a
    // no-op, but null must still be rejected (it is a genuine client
    // error and should produce a 400, not a silent skip).
    @Test
    @DisplayName("replaceFixtures: null list returns Mono.error (V24D24.1 null guard)")
    void replaceFixtures_nullList_returnsError() {
        useCase.replaceFixtures(USER_ID, null)
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        verify(careerRepository, never()).findById(anyString());
        verify(careerRepository, never()).save(any());
    }

    // ========== BUG #2: totalRounds must equal max(fixtures.round) after replaceFixtures
    //
    // Even though the in-memory state is correct today (setTotalRounds
    // is called inside executeReplaceFixtures), the previous order put
    // setTotalRounds EARLY in the sequence — any future side-effect in
    // setFixtures / initializeStandings / setCareerPhase would clobber
    // it. The fix moves setTotalRounds to be the LAST call so the
    // invariant totalRounds == max(fixtures.round) holds even if a new
    // side-effect is added to an intermediate call.
    //
    // Test: 4 fixtures across 4 rounds → totalRounds must be 4.
    @Test
    @DisplayName("replaceFixtures: totalRounds == fixtures.size() (BUG #2 regression guard)")
    void replaceFixtures_totalRoundsEqualsFixtureCount() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        List<CustomFixture> fixtures = List.of(
            new CustomFixture("user-team-id", "rival-1", 1, null),
            new CustomFixture("user-team-id", "rival-2", 2, null),
            new CustomFixture("user-team-id", "rival-3", 3, null),
            new CustomFixture("user-team-id", "rival-4", 4, null)
        );

        useCase.replaceFixtures(USER_ID, fixtures)
            .as(StepVerifier::create)
            .verifyComplete();

        assertThat(career.getTournamentState().getTotalRounds())
            .as("totalRounds MUST equal max(round) after replaceFixtures (BUG #2)")
            .isEqualTo(4);
        assertThat(career.getTournamentState().getFixtures()).hasSize(4);
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

    // ========== cache invalidation (V24D20-SANDBOX-V2-MVP BUG #1) ==========
    //
    // Root cause: TestHarnessUseCaseImpl.executeSetFormation / executeReplaceFixtures /
    // executeResetInjuries write to Redis via careerRepository.save(career) but
    // NEVER invalidate CareerSessionService.careerCache. The next
    // careerSessionService.getCareerFromCache(userId) returns the stale
    // CareerSave from the in-memory cache, so the V24 engine sees the OLD
    // formation / fixtures / injury state, not the new one.
    //
    // Fix: every save in the test harness must invalidate the cache.
    // Regression guard: these tests fail (red) BEFORE the fix is applied.

    @Test
    @DisplayName("setFormation: invalidates CareerSessionService cache after save (BUG #1)")
    void setFormation_invalidatesCache() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.setFormation(USER_ID, "5-3-2")
            .as(StepVerifier::create)
            .verifyComplete();

        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
    }

    @Test
    @DisplayName("replaceFixtures: invalidates CareerSessionService cache after save (BUG #1)")
    void replaceFixtures_invalidatesCache() {
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

        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
    }

    @Test
    @DisplayName("resetInjuries: invalidates CareerSessionService cache after save (BUG #1)")
    void resetInjuries_invalidatesCache() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.resetInjuries(USER_ID)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
    }

    @Test
    @DisplayName("createCustom: invalidates cache (deleteCareer does it internally, "
        + "plus resetInjuries after fix) (BUG #1)")
    void createCustom_invalidatesCache() {
        when(careerSessionService.deleteCareer(USER_ID))
            .thenReturn(Mono.empty());
        when(careerSessionService.startNewCareer(
                eq(USER_ID), anyString(), anyString(),
                anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(career));
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.createCustom(USER_ID, "league-1", "team-1", "EASY", "NORMAL", 3)
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        // deleteCareer internally invalidates; after the fix, the inner
        // executeResetInjuries will invalidate again. Either way, the
        // contract "createCustom leaves the cache invalidated" is held.
        verify(careerSessionService, atLeastOnce()).invalidateCache(USER_ID);
    }

    // ========== replayMatch (V24D20-SANDBOX-V2-MVP F5) ==========

    @Test
    @DisplayName("replayMatch: null matchId returns Mono.error")
    void replayMatch_nullMatchId_returnsError() {
        useCase.replayMatch(USER_ID, null, 42L)
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        verify(careerRepository, never()).save(any());
    }

    @Test
    @DisplayName("replayMatch: unknown matchId returns Mono.error")
    void replayMatch_unknownMatchId_returnsError() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));

        useCase.replayMatch(USER_ID, "match-doesnt-exist", 42L)
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        verify(careerRepository, never()).save(any());
    }

    @Test
    @DisplayName("replayMatch: with seed override, fixture is reset, re-simulated, "
        + "result updated, saved + cache invalidated (BUG #1 follow-through)")
    void replayMatch_withSeedOverride_resetsAndResimulates() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        // v24ContextFactory is the REAL factory (not mocked) — see setUp().
        // It builds a valid V24MatchContext from the career + fixture + teams,
        // which lets V24DetailedMatchEngine.simulate() run end-to-end.

        // UseCase doesn't see the result of the simulation directly (the
        // engine runs internally). The match we control: the fixture's
        // status goes PENDING → COMPLETED with a non-zero result.
        useCase.replayMatch(USER_ID, "match-001", 42L)
            .as(StepVerifier::create)
            .assertNext(f -> {
                // Fixture is the same object (mutated in place)
                assertThat(f.getMatchId()).isEqualTo("match-001");
                assertThat(f.getStatus())
                    .as("after replay, fixture is COMPLETED again")
                    .isEqualTo(MatchStatus.COMPLETED);
                assertThat(f.getResult())
                    .as("after replay, fixture has a new MatchResultData")
                    .isNotNull();
                // The internal engine may or may not produce goals with the
                // stubbed context — we just verify the status is COMPLETED
                // and the result is non-null. The homeGoals/awayGoals are
                // engine-dependent.
            })
            .verifyComplete();

        // Cache invalidated (BUG #1 follow-through)
        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
        // Save called
        verify(careerRepository, times(1)).save(career);
        // Old V24 detail cleared
        verify(v24StoragePort, times(1)).deleteByMatchId(
            org.mockito.ArgumentMatchers.anyString(), eq("match-001"));
    }

    @Test
    @DisplayName("replayMatch: null seed uses System.currentTimeMillis (no override)")
    void replayMatch_nullSeed_stillCompletes() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.replayMatch(USER_ID, "match-001", null)
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        // Save was called (proves the flow reached save + cache invalidation)
        verify(careerRepository, times(1)).save(career);
        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
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

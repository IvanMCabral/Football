package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V24D21-SANDBOX-V2-MVP-F7: Regression guard for BUG_REPLAY_NO_PERSIST.
 *
 * <p>Bug: {@link TestHarnessUseCaseImpl#executeReplayMatch} updated the
 * {@link MatchFixture} result but NEVER called
 * {@link V24DetailedMatchStoragePort#save} for the new
 * {@link V24DetailedMatchData}. {@code GET /api/v1/careers/{careerId}/matches/{matchId}/detail}
 * therefore returned 404 after a replay (only the old detail existed; the
 * best-effort {@code deleteByMatchId} wiped even that, leaving the API
 * with nothing to return).
 *
 * <p>Impact: blocked Iván's "what-if" smoke — replaying a match with a
 * changed formation couldn't be compared against the original because
 * the timeline / shot map / xG of the new run never reached Redis.
 *
 * <p>Strategy: full use-case test (no Spring context). Wire a real
 * {@link CareerSave} with 11-man squads for both teams (the V24 engine
 * requires MIN_AVAILABLE_PLAYERS=7 in the starting list), call
 * {@code useCase.replayMatch(...)} which runs the real
 * {@code V24DetailedMatchEngine} end-to-end, then verify the storage
 * port was called with a non-null {@link V24DetailedMatchData} for the
 * replayed matchId.
 *
 * <p>The assertion is RED before the fix (no {@code save(...)} call
 * site exists in {@code executeReplayMatch}) and GREEN after.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestHarnessUseCaseImpl — replayMatch persists V24 detail (BUG_REPLAY_NO_PERSIST)")
class TestHarnessReplayPersistsDetailE2ETest {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MATCH_ID = "match-001";

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;
    @Mock private V24DetailedMatchStoragePort v24StoragePort;

    // Real factory so V24MatchContext has valid teams (a mock would return
    // null teams and the engine's V24TeamMatchState.create would NPE).
    // See TestHarnessUseCaseImplTest.setUp() comment for the same rationale.
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

        // 11 healthy players per team so the V24 engine's
        // MIN_AVAILABLE_PLAYERS=7 invariant is met.
        List<SessionPlayer> userPlayers = new java.util.ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            userPlayers.add(healthyPlayer("u-p" + i));
        }
        List<SessionPlayer> rivalPlayers = new java.util.ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            rivalPlayers.add(healthyPlayer("r-p" + i));
        }
        wireSquad(career, "user-team-id", userPlayers);
        wireSquad(career, "rival-1", rivalPlayers);

        // Seed the tournament with one COMPLETED fixture so replayMatch
        // has something to operate on.
        MatchFixture completed = new MatchFixture(
            MATCH_ID, "user-team-id", "rival-1", 1);
        completed.complete(new MatchFixture.MatchResultData(0, 0, 0, 0, 0, 0));
        career.getTournamentState().setFixtures(List.of(completed));
    }

    // ========== BUG_REPLAY_NO_PERSIST regression guard ==========

    @Test
    @DisplayName("replayMatch: persists new V24DetailedMatchData to storage port (BUG_REPLAY_NO_PERSIST)")
    void replayMatch_persistsV24DetailToStoragePort() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.replayMatch(USER_ID, MATCH_ID, 42L)
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        // Capture the saved V24DetailedMatchData so we can verify shape too.
        ArgumentCaptor<String> careerIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<V24DetailedMatchData> detailCaptor =
            ArgumentCaptor.forClass(V24DetailedMatchData.class);

        verify(v24StoragePort, times(1)).save(careerIdCaptor.capture(), detailCaptor.capture());

        String savedCareerId = careerIdCaptor.getValue();
        V24DetailedMatchData savedDetail = detailCaptor.getValue();

        // The saved detail must be for the same careerId that the
        // controller passes to GET /detail — if these diverge the API
        // returns 404 even with the fix in place.
        assertThat(savedCareerId)
            .as("save() careerId must equal career.getData().getCareerId()")
            .isEqualTo(career.getData().getCareerId())
            .isNotBlank();

        // The detail must reference the replayed matchId, not a stale one.
        assertThat(savedDetail)
            .as("V24DetailedMatchData must not be null — BUG_REPLAY_NO_PERSIST means a null save() would silently 404 the detail API")
            .isNotNull();
        assertThat(savedDetail.matchId())
            .as("saved detail.matchId must match the replayed matchId")
            .isEqualTo(MATCH_ID);
        assertThat(savedDetail.careerId())
            .as("saved detail.careerId must match the careerId used in the Redis key")
            .isEqualTo(savedCareerId);
        assertThat(savedDetail.engineVersion())
            .as("engineVersion must be V24 (the engine that produced this data)")
            .isEqualTo("V24");
        assertThat(savedDetail.schemaVersion())
            .as("schemaVersion must be 1")
            .isEqualTo(1);
        assertThat(savedDetail.timeline())
            .as("saved detail must carry the new timeline (this is what /detail exposes)")
            .isNotNull();
        assertThat(savedDetail.homeTeamId())
            .as("saved detail.homeTeamId must match the fixture's home team")
            .isEqualTo("user-team-id");
        assertThat(savedDetail.awayTeamId())
            .as("saved detail.awayTeamId must match the fixture's away team")
            .isEqualTo("rival-1");

        // Sanity: the existing best-effort deleteByMatchId is still called
        // (kept for backward compat — the new save() is idempotent, so the
        // delete is now harmless but not harmful either).
        verify(v24StoragePort, times(1)).deleteByMatchId(eq(savedCareerId), eq(MATCH_ID));
    }

    @Test
    @DisplayName("replayMatch: even when save() fails, career + fixture are still persisted (replay is best-effort for V24 detail)")
    void replayMatch_saveFailure_doesNotPropagate() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());
        // Storage port save throws — replay must not fail the whole flow.
        org.mockito.Mockito.doThrow(new RuntimeException("Redis down (simulated)"))
            .when(v24StoragePort).save(anyString(), any(V24DetailedMatchData.class));

        useCase.replayMatch(USER_ID, MATCH_ID, 42L)
            .as(StepVerifier::create)
            // The fixture save must still complete even if V24 detail save blew up.
            .expectNextCount(1)
            .verifyComplete();

        // Career save and cache invalidation still ran.
        verify(careerRepository, times(1)).save(career);
        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
        // And we DID attempt to persist the new detail (the bug was the opposite).
        verify(v24StoragePort, times(1)).save(anyString(), any(V24DetailedMatchData.class));
    }

    @Test
    @DisplayName("replayMatch: unknown matchId does NOT call save() (validation guard)")
    void replayMatch_unknownMatchId_doesNotPersist() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));

        useCase.replayMatch(USER_ID, "match-doesnt-exist", 42L)
            .as(StepVerifier::create)
            .expectErrorMatches(t -> t instanceof IllegalArgumentException)
            .verify();

        // Critical guard: never persist detail for a fixture that doesn't exist.
        verify(v24StoragePort, never()).save(anyString(), any(V24DetailedMatchData.class));
        verify(careerRepository, never()).save(any());
    }

    // ========== helpers (mirrors TestHarnessUseCaseImplTest) ==========

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
        // initDefaults() is private — replicate its effect so Boolean flags
        // are non-null (required by AssertJ's isFalse/isZero).
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
     * Wires SessionTeam + players into the career's managers via reflection.
     * Mirrors TestHarnessUseCaseImplTest.wireSquad() — keeps this test
     * hermetic (no CareerSessionService.startNewCareer round-trip needed).
     */
    @SuppressWarnings("unchecked")
    private void wireSquad(CareerSave career, String teamId, List<SessionPlayer> players) {
        try {
            Field tmField = CareerSave.class.getDeclaredField("teamManager");
            tmField.setAccessible(true);
            Object teamManager = tmField.get(career);

            Field pmField = CareerSave.class.getDeclaredField("playerManager");
            pmField.setAccessible(true);
            Object playerManager = pmField.get(career);

            SessionTeam team = new SessionTeam();
            team.setSessionTeamId(teamId);
            team.setFormation("4-3-3");
            java.lang.reflect.Method addSessionTeam =
                teamManager.getClass().getMethod("addSessionTeam", SessionTeam.class);
            addSessionTeam.invoke(teamManager, team);

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

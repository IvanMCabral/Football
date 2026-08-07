package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
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
import reactor.core.publisher.Flux;
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
import static org.mockito.Mockito.lenient;

/**
 *
 * <p>Bug: {@link TestHarnessUseCaseImpl#executeReplayMatch} updated the
 * {@link MatchFixture} result but NEVER called
 * therefore returned 404 after a replay (only the old detail existed; the
 * best-effort {@code deleteByMatchId} wiped even that, leaving the API
 * with nothing to return).
 *
 * <p>Impact: blocked IvÃ¡n's "what-if" smoke â€” replaying a match with a
 * changed formation couldn't be compared against the original because
 * the timeline / shot map / xG of the new run never reached Redis.
 *
 * <p>Strategy: full use-case test (no Spring context). Wire a real
 * {@link CareerSave} with 11-man squads for both teams (the detailed match engine
 * requires MIN_AVAILABLE_PLAYERS=7 in the starting list), call
 * {@code useCase.replayMatch(...)} which runs the real
 * replayed matchId.
 *
 * <p>The assertion is RED before the fix (no {@code save(...)} call
 * site exists in {@code executeReplayMatch}) and GREEN after.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestHarnessUseCaseImpl â€” replayMatch persists detailed match detail")
class TestHarnessReplayPersistsDetailE2ETest {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MATCH_ID = "match-001";

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;
    @Mock private DetailedMatchStoragePort detailedMatchStoragePort;
    // resetRound() use case. Default `@Mock` is fine.
    @Mock private com.footballmanager.application.engine.match.MatchEngineRegistry matchEngineRegistry;

    // Real factory so MatchContext has valid teams (a mock would return
    // null teams and the engine's TeamMatchState.create would NPE).
    // See TestHarnessUseCaseImplTest.setUp() comment for the same rationale.
    private MatchContextFactory matchContextFactory;
    private TestHarnessUseCaseImpl useCase;

    private CareerSave career;

    @BeforeEach
    void setUp() {
        matchContextFactory = new MatchContextFactory();
    useCase = new TestHarnessUseCaseImpl(
        careerRepository, careerSessionService,
        matchContextFactory, detailedMatchStoragePort, null, matchEngineRegistry);
    lenient().when(careerSessionService.saveCareer(any(CareerSave.class))).thenReturn(Mono.empty());

    career = new CareerSave();
    career.setLifecycleGeneration("test-generation");
    career.setUserId(USER_ID);
        career.setUserSessionTeamId("user-team-id");

        // 11 healthy players per team so the detailed match engine's
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

    @Test
    @DisplayName("replayMatch: persists new DetailedMatchData to storage port")
    void replayMatch_persistsDetailedSprintetailToStoragePort() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        lenient().when(careerSessionService.saveCareer(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        useCase.replayMatch(USER_ID, MATCH_ID, 42L)
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        ArgumentCaptor<com.footballmanager.domain.model.valueobject.CareerWriteContext> contextCaptor =
            ArgumentCaptor.forClass(com.footballmanager.domain.model.valueobject.CareerWriteContext.class);
        ArgumentCaptor<DetailedMatchData> detailCaptor =
            ArgumentCaptor.forClass(DetailedMatchData.class);

        verify(detailedMatchStoragePort, times(1)).saveWithContext(contextCaptor.capture(), detailCaptor.capture());

        String savedCareerId = contextCaptor.getValue().careerId();
        DetailedMatchData savedDetail = detailCaptor.getValue();

        // The saved detail must be for the same careerId that the
        // controller passes to GET /detail â€” if these diverge the API
        // returns 404 even with the fix in place.
        assertThat(savedCareerId)
            .as("save() careerId must equal career.getData().getCareerId()")
            .isEqualTo(career.getData().getCareerId())
            .isNotBlank();

        // The detail must reference the replayed matchId, not a stale one.
        assertThat(savedDetail)
            .as("DetailedMatchData must not be null â€” BUG_REPLAY_NO_PERSIST means a null save() would silently 404 the detail API")
            .isNotNull();
        assertThat(savedDetail.matchId())
            .as("saved detail.matchId must match the replayed matchId")
            .isEqualTo(MATCH_ID);
        assertThat(savedDetail.careerId())
            .as("saved detail.careerId must match the careerId used in the Redis key")
            .isEqualTo(savedCareerId);
        assertThat(savedDetail.engineType())
            .as("engineType must be Detailed (the engine that produced this data)")
            .isEqualTo("DETAILED_MATCH");
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
        // (kept for backward compat â€” the new save() is idempotent, so the
        // delete is now harmless but not harmful either).
        verify(detailedMatchStoragePort, times(1)).deleteByMatchIdWithContext(
            eq(savedCareerId), eq(MATCH_ID),
            any(com.footballmanager.domain.model.valueobject.CareerWriteContext.class));
    }

    @Test
    @DisplayName("replayMatch: even when save() fails, career + fixture are still persisted (replay is best-effort for detailed match detail)")
    void replayMatch_saveFailure_doesNotPropagate() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(Optional.of(career)));
        lenient().when(careerSessionService.saveCareer(any(CareerSave.class)))
            .thenReturn(Mono.empty());
        // Storage port save throws â€” replay must not fail the whole flow.
        org.mockito.Mockito.doThrow(new RuntimeException("Redis down (simulated)"))
            .when(detailedMatchStoragePort).saveWithContext(
                any(com.footballmanager.domain.model.valueobject.CareerWriteContext.class),
                any(DetailedMatchData.class));

        useCase.replayMatch(USER_ID, MATCH_ID, 42L)
            .as(StepVerifier::create)
            // The fixture save must still complete even if detailed match detail save blew up.
            .expectNextCount(1)
            .verifyComplete();

        // Career save and cache invalidation still ran.
        verify(careerSessionService, times(1)).saveCareer(career);
        verify(careerSessionService, times(1)).invalidateCache(USER_ID);
        // And we DID attempt to persist the new detail (the bug was the opposite).
        verify(detailedMatchStoragePort, times(1)).saveWithContext(
            any(com.footballmanager.domain.model.valueobject.CareerWriteContext.class),
            any(DetailedMatchData.class));
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
        verify(detailedMatchStoragePort, never()).saveWithContext(
            any(com.footballmanager.domain.model.valueobject.CareerWriteContext.class),
            any(DetailedMatchData.class));
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
        // initDefaults() is private â€” replicate its effect so Boolean flags
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
     * Mirrors TestHarnessUseCaseImplTest.wireSquad() â€” keeps this test
     * hermetic (no CareerSessionService.startNewCareer round-trip needed).
     */
    private void wireSquad(CareerSave career, String teamId, List<SessionPlayer> players) {
        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(teamId);
        team.setFormation("4-3-3");
        career.addSessionTeam(team);
        for (SessionPlayer player : players) {
            career.addSessionPlayer(player);
            career.assignPlayerToTeam(player.getSessionPlayerId(), teamId);
        }
    }
}

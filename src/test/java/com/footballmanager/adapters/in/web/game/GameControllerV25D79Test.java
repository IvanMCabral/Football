package com.footballmanager.adapters.in.web.game;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import com.footballmanager.domain.port.in.game.TournamentQueryUseCase;
import com.footballmanager.domain.port.in.match.AdvanceMatchUseCase;
import com.footballmanager.domain.port.in.match.FinalizeMatchUseCase;
import com.footballmanager.domain.port.in.match.StartRoundUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C55.14 OBS-1 — unit tests for the {@link GameController#getMatchState}
 * {@link MatchStateSnapshot} contract.
 *
 * <p>Before C55.14, {@code GET /api/v1/games/match/{matchId}} returned the
 * legacy {@code RuntimeMatch} DTO which lacked
 * {@code homePlayerRatings}, {@code awayPlayerRatings}, and
 * {@code substitutionsRemaining}. After C55.14, the same endpoint returns a
 * {@link MatchStateSnapshot} resolved through the live
 * {@link RoundEngineRegistry}.
 *
 * <p>Strategy: pure JUnit + Mockito unit tests. We mock
 * {@link RoundEngineRegistry} to return a stub {@link RoundEngine} whose
 * {@code getMatchEngine(matchId)} returns a stub {@link MatchEngine} whose
 * {@code getCurrentState()} returns a hand-built snapshot — no Spring
 * context, no WebTestClient, no real engine. This is the cheapest way to
 * pin the contract: if anyone removes the
 * {@code getCurrentMatchSnapshot(matchId)} lookup or regresses to
 * {@code RuntimeMatch}, both tests fail.
 *
 * <p>Scope (2 tests):
 * <ul>
 *       happy path with non-empty player rating lists and a non-default
 *       present on the response body and the status is 200.</li>
 *   <li>{@code getGameState_returnsSubstitutionsRemaining_afterSubstitution}
 *       — pin that {@code substitutionsRemaining == 4} on a snapshot whose
 *       survives end-to-end through the new resolution path.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameController.getMatchState — C55.14 OBS-1 MatchStateSnapshot")
class GameControllerV25D79Test {

    @Mock
    private GameService gameService;

    @Mock
    private StartRoundUseCase startRoundUseCase;

    @Mock
    private RoundEngineRegistry roundEngineRegistry;

    @Mock
    private AdvanceMatchUseCase advanceMatchUseCase;

    @Mock
    private FinalizeMatchUseCase finalizeMatchUseCase;

    @Mock
    private TournamentQueryUseCase tournamentQueryUseCase;

    @Mock
    private RoundEngine roundEngine;

    @Mock
    private MatchEngine matchEngine;

    private GameController controller;
    private Authentication auth;
    private UUID userId;
    private UUID matchId;

    @BeforeEach
    void setUp() {
        // Build the controller manually — no Spring context needed for this
        // unit test. All collaborators are Mockito mocks.
        controller = new GameController(
            gameService, startRoundUseCase, roundEngineRegistry,
            advanceMatchUseCase, finalizeMatchUseCase, tournamentQueryUseCase
        );

        userId = UUID.randomUUID();
        matchId = UUID.randomUUID();

        auth = new UsernamePasswordAuthenticationToken(
            userId.toString(), "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("C55.14 OBS-1: happy path returns 200 with MatchStateSnapshot carrying V25D79 fields")
    void getGameState_returnsMatchStateSnapshotWithV25D79Fields() {
        // Hand-built snapshot with non-empty ratings + substitutionsRemaining=3
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        List<V24PlayerMatchRatingDto> homeRatings = List.of(
            new V24PlayerMatchRatingDto(
                "p-home-1", "Home Striker", homeId.toString(), "ATT",
                7.5, 1, 0, 2, 0, 0, 0, 0, 0, false, false)
        );
        List<V24PlayerMatchRatingDto> awayRatings = List.of(
            new V24PlayerMatchRatingDto(
                "p-away-1", "Away GK", awayId.toString(), "GK",
                6.8, 0, 0, 0, 0, 0, 0, 0, 0, false, false)
        );
        MatchStateSnapshot snapshot = new MatchStateSnapshot(
            matchId, homeId, awayId,
            60, MatchStatus.RUNNING, new Score(1, 0),
            List.of(), "career-c55-14", "user-c55-14",
            55, 45, "BALANCED", "BALANCED", "4-4-2", "4-4-2",
            homeRatings, awayRatings,
            3 // 2 subs used -> 3 remaining
        );

        // Stub the resolution path:
        // roundEngineRegistry.getByMatchId(matchId) -> roundEngine
        // roundEngine.getCurrentMatchSnapshot(matchId) -> snapshot
        when(roundEngineRegistry.getByMatchId(eq(matchId))).thenReturn(roundEngine);
        when(roundEngine.getCurrentMatchSnapshot(eq(matchId))).thenReturn(snapshot);

        // Exercise
        StepVerifier.create(controller.getMatchState(matchId.toString(), auth))
            .assertNext(resp -> {
                assertEquals(HttpStatus.OK, resp.getStatusCode(),
                    "V25D79-aligned endpoint must return 200 on a live match");
                assertNotNull(resp.getBody(), "200 response must carry a snapshot body");
                MatchStateSnapshot body = resp.getBody();
                assertEquals(matchId, body.matchId(), "matchId must round-trip verbatim");
                assertEquals(homeRatings, body.homePlayerRatings(),
                    "homePlayerRatings must carry the V25D79 per-player ratings");
                assertEquals(awayRatings, body.awayPlayerRatings(),
                    "awayPlayerRatings must carry the V25D79 per-player ratings");
                assertNotNull(body.homePlayerRatings(), "homePlayerRatings must NOT be null");
                assertNotNull(body.awayPlayerRatings(), "awayPlayerRatings must NOT be null");
                assertEquals(3, body.substitutionsRemaining(),
                    "substitutionsRemaining must carry the V25D79 sub counter");
                assertTrue(body.substitutionsRemaining() >= 0,
                    "substitutionsRemaining must be >= 0 (clamped at 0 by adaptV24Snapshot)");
            })
            .verifyComplete();

        // Verify the new resolution path was actually used end-to-end.
        verify(roundEngineRegistry).getByMatchId(eq(matchId));
        verify(roundEngine).getCurrentMatchSnapshot(eq(matchId));
    }

    @Test
    @DisplayName("C55.14 OBS-1: substitutionsRemaining == 4 after one substitution through new path")
    void getGameState_returnsSubstitutionsRemaining_afterSubstitution() {
        // Hand-built snapshot representing the live state after exactly one
        // SUBSTITUTION event has been processed (5 - 1 = 4 remaining).
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        MatchStateSnapshot snapshot = new MatchStateSnapshot(
            matchId, homeId, awayId,
            45, MatchStatus.RUNNING, new Score(0, 0),
            List.of(), "career-c55-14", "user-c55-14",
            50, 50, "BALANCED", "BALANCED", "4-4-2", "4-4-2",
            List.of(), List.of(),
            4
        );

        when(roundEngineRegistry.getByMatchId(eq(matchId))).thenReturn(roundEngine);
        when(roundEngine.getCurrentMatchSnapshot(eq(matchId))).thenReturn(snapshot);

        StepVerifier.create(controller.getMatchState(matchId.toString(), auth))
            .assertNext(resp -> {
                assertEquals(HttpStatus.OK, resp.getStatusCode());
                MatchStateSnapshot body = resp.getBody();
                assertNotNull(body);
                assertEquals(4, body.substitutionsRemaining(),
                    "substitutionsRemaining must equal 4 after 1 substitution (5 - 1)");
            })
            .verifyComplete();

        verify(roundEngineRegistry).getByMatchId(eq(matchId));
        verify(roundEngine).getCurrentMatchSnapshot(eq(matchId));
    }
}
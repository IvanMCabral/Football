package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.dto.request.CareerStartRequest;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.career.SeasonAdvancementService;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.entity.CareerSave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V25D78-C55.11 (BUG_C55.9_FINDING_01): unit coverage that
 * {@code POST /api/v1/career/start} invalidates the JVM-local
 * {@code careerCache} inside {@link CareerSessionService} right after a
 * successful start, so a second {@code /career/start} for the same user
 * is not masked by a stale cached {@link CareerSave} on subsequent
 * {@code /career/status} reads.
 *
 * <p>The fix lives in {@link CareerCommandController#startCareer}: a new
 * {@code .doOnNext(started -> sessionService.invalidateCache(userId))} on the
 * Mono returned by {@code sessionService.startNewCareer(...)}. This mirrors the
 * pattern already used by {@code TestHarnessUseCaseImpl},
 * {@code ContinueSeasonUseCaseImpl}, {@code StartRoundUseCaseImpl}, and
 * {@code RegenerateFixturesUseCaseImpl}.
 *
 * <p>Strategy: pure Mockito unit test, no Spring context. Mocks the 5
 * constructor collaborators of {@link CareerCommandController}, exercises
 * the public {@code startCareer} method, and asserts on the side-effect
 * (invalidateCache invocation).
 *
 * <p>Coverage:
 * <ul>
 *   <li>Happy path — startNewCareer emits a CareerSave →
 *       invalidateCache(userId) called exactly once.</li>
 *   <li>Failure path — startNewCareer errors before emitting →
 *       invalidateCache(userId) NOT called (no successful start means no
 *       cache invalidation needed).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CareerCommandController.startCareer — cache invalidation (V25D78-C55.11)")
class CareerCommandControllerStartCareerCacheInvalidationTest {

    private static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0000-0000000000c5");
    private static final String LEAGUE_ID = "league-uuid";
    private static final String TEAM_ID = "team-uuid";

    @Mock private ControllerHelper controllerHelper;
    @Mock private CareerSessionService sessionService;
    @Mock private SeasonAdvancementService seasonAdvancementService;
    @Mock private RoundEngineRegistry roundEngineRegistry;
    @Mock private GameService gameService;

    private CareerCommandController controller;

    @BeforeEach
    void setUp() {
        controller = new CareerCommandController(
            controllerHelper,
            sessionService,
            seasonAdvancementService,
            roundEngineRegistry,
            gameService
        );
    }

    private CareerStartRequest sampleRequest() {
        return new CareerStartRequest(LEAGUE_ID, TEAM_ID, "NORMAL", "NORMAL", 5);
    }

    private Authentication authForUser() {
        Authentication auth = mock(Authentication.class);
        // V25D78-C55.11: only stub the controllerHelper collaborator (the
        // SUT calls controllerHelper.getUserId(auth) directly). We do NOT
        // stub auth.getName() because the mocked controllerHelper never
        // consults it — Mockito strict mode would flag that stub as
        // unnecessary.
        when(controllerHelper.getUserId(auth)).thenReturn(USER_ID);
        return auth;
    }

    @Test
    @DisplayName("startCareer on success invalidates careerCache (BUG_C55.9_FINDING_01)")
    void startCareer_invalidatesCacheOnSuccess() {
        CareerSave newCareer = mock(CareerSave.class);
        Game game = mock(Game.class);

        when(sessionService.startNewCareer(
                eq(USER_ID), eq(LEAGUE_ID), eq(TEAM_ID),
                anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(newCareer));
        when(gameService.createGameFromCareer(
                eq(newCareer), eq(LEAGUE_ID), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(game));

        StepVerifier.create(controller.startCareer(sampleRequest(), authForUser()))
            .verifyComplete();

        // V25D78-C55.11: the cache MUST be invalidated so the next
        // /career/status read re-fetches the new CareerSave from Redis
        // instead of returning the previously-cached one.
        verify(sessionService, times(1)).invalidateCache(USER_ID);
    }

    @Test
    @DisplayName("startCareer on failure does NOT invalidate cache")
    void startCareer_doesNotInvalidateCacheOnFailure() {
        when(sessionService.startNewCareer(
                eq(USER_ID), eq(LEAGUE_ID), eq(TEAM_ID),
                anyString(), anyString(), anyInt()))
            .thenReturn(Mono.error(new IllegalStateException("simulated start failure")));

        StepVerifier.create(controller.startCareer(sampleRequest(), authForUser()))
            .expectErrorMatches(t -> t instanceof IllegalStateException
                && "simulated start failure".equals(t.getMessage()))
            .verify();

        // No successful CareerSave persisted → no cache invalidation
        // (cache would be untouched anyway, but the contract is explicit).
        verify(sessionService, never()).invalidateCache(any(UUID.class));
        verify(gameService, never()).createGameFromCareer(
            any(CareerSave.class), anyString(), anyString(), anyString(), anyInt());
    }
}
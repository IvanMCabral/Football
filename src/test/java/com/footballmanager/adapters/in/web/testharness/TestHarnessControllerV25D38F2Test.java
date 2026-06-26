package com.footballmanager.adapters.in.web.testharness;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.testharness.dto.CreateCustomCareerRequest;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V25D38-F2: regression test for BUG_NPE_AUDIT (audit of POST controllers
 * that take a body, follow-up to V25D37-F3).
 *
 * <p>Before the fix, {@code POST /api/v1/test-harness/career/create-custom}
 * with a malformed body (empty {@code {}}, missing {@code leagueId}, missing
 * {@code teamId}, missing {@code difficulty}, missing {@code gameSpeed}, or
 * malformed UUID strings) propagated to
 * {@code StartCareerUseCaseImpl.start()} which calls
 * {@code UUID.fromString(null)} → NPE → 500 Internal Server Error with the
 * leaky JVM message
 * {@code "Cannot invoke \"String.length()\" because \"name\" is null"}.
 *
 * <p>This was the only sibling left with the bug after V25D37-F3 fixed
 * {@code POST /api/v1/matches}. The audit found
 * {@code setFormation / setStyle / injectPlayerStats / resetRound} return
 * 422 (mapped by {@code GlobalExceptionHandler} from IllegalArgumentException)
 * because the underlying UseCases already null-check their fields.
 *
 * <p>After the fix, the controller pre-validates the body and returns
 * <b>400 Bad Request</b> with a structured error Map before touching any
 * repository / use case (same pattern as V25D37-F3).
 *
 * <p>This test is a pure JUnit + Mockito unit test on the controller method
 * itself (no Spring context, no WebTestClient) — mirrors the
 * {@code MatchControllerReactiveV25D37F3Test} style.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestHarnessController.createCustom — V25D38-F2 bad body validation (unit)")
class TestHarnessControllerV25D38F2Test {

    @Mock
    private TestHarnessUseCase testHarnessUseCase;

    private TestHarnessController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        controller = new TestHarnessController(testHarnessUseCase, new ControllerHelper());
        UUID userId = UUID.randomUUID();
        auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static CreateCustomCareerRequest req(
            String leagueId, String teamId, String difficulty, String gameSpeed,
            Integer teamsPerDivision) {
        return new CreateCustomCareerRequest(
                leagueId, teamId, difficulty, gameSpeed, teamsPerDivision);
    }

    private static void assertBadRequestWithErrorContaining(
            ResponseEntity<?> resp, String expectedSubstring) {
        org.junit.jupiter.api.Assertions.assertEquals(
                HttpStatus.BAD_REQUEST, resp.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                resp.getBody() instanceof Map,
                "Body must be a structured error Map, got " + resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        org.junit.jupiter.api.Assertions.assertTrue(
                body.get("error").toString().contains(expectedSubstring),
                "error must contain '" + expectedSubstring + "', got=" + body.get("error"));
    }

    @Test
    @DisplayName("V25D38-F2: empty body (all null) returns 400 (not 500 NPE)")
    void createCustom_emptyBody_returns400() {
        StepVerifier.create(controller.createCustom(req(null, null, null, null, null), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "leagueId"))
                .verifyComplete();

        // UseCase must NOT be touched — validation short-circuits.
        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: null teamId returns 400 (not 500 NPE)")
    void createCustom_nullTeamId_returns400() {
        StepVerifier.create(controller.createCustom(
                req("00000000-0000-0000-0000-000000000001", null, "NORMAL", "NORMAL", 5), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "teamId"))
                .verifyComplete();

        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: blank difficulty returns 400 (not 500 NPE)")
    void createCustom_blankDifficulty_returns400() {
        StepVerifier.create(controller.createCustom(
                req("00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002",
                        "  ", "NORMAL", 5), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "difficulty"))
                .verifyComplete();

        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: blank gameSpeed returns 400 (not 500 NPE)")
    void createCustom_blankGameSpeed_returns400() {
        StepVerifier.create(controller.createCustom(
                req("00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002",
                        "NORMAL", "", 5), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "gameSpeed"))
                .verifyComplete();

        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: malformed UUID leagueId returns 400 (not 422 IAE / not 500 NPE)")
    void createCustom_malformedLeagueId_returns400() {
        StepVerifier.create(controller.createCustom(
                req("not-a-uuid",
                        "00000000-0000-0000-0000-000000000002",
                        "NORMAL", "NORMAL", 5), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "leagueId/teamId"))
                .verifyComplete();

        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: malformed UUID teamId returns 400 (not 422 IAE / not 500 NPE)")
    void createCustom_malformedTeamId_returns400() {
        StepVerifier.create(controller.createCustom(
                req("00000000-0000-0000-0000-000000000001",
                        "garbage",
                        "NORMAL", "NORMAL", 5), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "leagueId/teamId"))
                .verifyComplete();

        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: teamsPerDivision=1 returns 400 (underflow guard)")
    void createCustom_teamsPerDivisionTooSmall_returns400() {
        StepVerifier.create(controller.createCustom(
                req("00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002",
                        "NORMAL", "NORMAL", 1), auth))
                .assertNext(resp -> assertBadRequestWithErrorContaining(resp, "teamsPerDivision"))
                .verifyComplete();

        verify(testHarnessUseCase, never())
                .createCustom(any(UUID.class), anyString(), anyString(),
                        anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("V25D38-F2: happy path with valid UUIDs and difficulty passes validation (UseCase invoked)")
    void createCustom_happyPath_passesValidation() {
        // Sanity: the validation guards don't block the happy path. We verify
        // that the validation chain at the top of the method lets a valid
        // request through by checking that the UseCase IS invoked (validation
        // would have short-circuited with 400 otherwise).
        when(testHarnessUseCase.createCustom(any(UUID.class), anyString(), anyString(),
                anyString(), anyString(), anyInt())).thenReturn(Mono.empty());

        StepVerifier.create(controller.createCustom(
                req("00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002",
                        "NORMAL", "NORMAL", 5), auth))
                .verifyComplete();

        // UseCase WAS invoked → validation passed.
        verify(testHarnessUseCase).createCustom(any(UUID.class), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(5));
    }
}
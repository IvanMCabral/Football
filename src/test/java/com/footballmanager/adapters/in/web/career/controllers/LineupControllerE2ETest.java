package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.mock;

/**
 * V24D6T — E2E HTTP boundary coverage for LineupController.
 *
 * <p>STATUS: {@code @Disabled}. The HTTP boundary is not yet covered
 * end-to-end because:
 * <ol>
 *   <li>{@code @WebFluxTest} picks up the main {@code @SpringBootApplication}
 *       scan, which transitively requires Postgres ({@code LeagueR2dbcRepository}).
 *   <li>The full {@code @SpringBootTest} would require a running Postgres
 *       + Redis, which is too heavy for a unit-test-tier PR.
 *   <li>{@code SecurityConfig} requires {@code JwtTokenProvider}, which is
 *       not available without the full context.
 *   <li>Manual slice setup with {@code WebTestClient.bindToController}
 *       doesn't support per-request {@code Authentication} injection in
 *       the webflux reactive flow.
 * </ol>
 *
 * <p>The use-case-level contract IS covered by
 * {@code LineupCommandUseCaseImplManualSelectBlockingTest} and
 * {@code LineupBlockingTest} — those tests pin that manual select rejects
 * injured/suspended at the use-case boundary. The HTTP wiring
 * (request parsing, status codes, error mapping) is exercised by the
 * smoke checklist in {@code MANAGER_V24D6S_release_playable_mvp_audit_2026-06-12.md}.
 *
 * <p>To re-enable: replace {@code @Disabled} with a {@code @WebFluxTest}
 * setup that provides a stubbed {@code AuthenticationWebFilter} returning
 * a fixed user. Deferred to V24D6T+ui per the audit.
 */
@Disabled("V24D6T gap: HTTP boundary E2E requires running Postgres+Redis or " +
    "a custom AuthenticationWebFilter stub — deferred to V24D6T+ui per audit")
class LineupControllerE2ETest {

    @Test
    void placeholder_keptForFutureE2E() {
        // Intentional no-op. See class-level Javadoc.
        LineupCommandUseCase cmd = mock(LineupCommandUseCase.class);
        LineupQueryUseCase qry = mock(LineupQueryUseCase.class);
        CareerSessionService cache = mock(CareerSessionService.class);
        WebTestClient client = WebTestClient.bindToController(
            new LineupController(cmd, qry, cache)).build();
        // The client can be exercised with manual Authentication injection
        // once Spring Security's reactive mock support is set up.
        // For now, this test exists to pin the E2E gap and to serve as
        // a marker for the next phase.
        client.post().uri("/api/v1/career/lineup/manual-select")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"formation\":\"4-4-2\",\"playerIds\":[]}")
            .exchange();
    }
}

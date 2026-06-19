package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.v24.MatchComparison;
import com.footballmanager.application.service.simulation.v24.MatchComparisonDiff;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService.BaselineNotFoundException;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService.LiveDetailNotFoundException;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Unit tests for the
 * {@code /compare} endpoint of {@link V24DetailedMatchController}.
 *
 * <p>Tests the controller's response shape and error handling without
 * spinning up the full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class MatchCompareControllerTest {

    @Mock
    private V24DetailedMatchQueryService queryService;
    @Mock
    private MatchComparisonService comparisonService;

    private V24DetailedMatchController controller;

    private static final String CAREER_ID = "career-001";
    private static final String MATCH_ID = "match-001";

    @BeforeEach
    void setUp() {
        controller = new V24DetailedMatchController(queryService, comparisonService);
    }

    @Test
    void getCompare_featureEnabled_returns200WithComparison() {
        when(queryService.isApiEnabled()).thenReturn(true);
        // V24D15-CLEANUP (BUG_COMPARE_404): getComparison now returns Mono.
        when(comparisonService.getComparison(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.just(sampleComparison()));

        Mono<ResponseEntity<Object>> mono = controller.getCompare(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(MatchComparison.class, response.getBody());
        MatchComparison cmp = (MatchComparison) response.getBody();
        assertEquals(MATCH_ID, cmp.live().matchId());
        assertEquals(MATCH_ID, cmp.baseline().matchId());
    }

    @Test
    void getCompare_featureDisabled_returns404() {
        when(queryService.isApiEnabled()).thenReturn(false);

        Mono<ResponseEntity<Object>> mono = controller.getCompare(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        // The compare service should NOT be called when the feature is off
        verify(comparisonService, never()).getComparison(anyString(), anyString());
    }

    @Test
    void getCompare_baselineNotFound_returns404() {
        when(queryService.isApiEnabled()).thenReturn(true);
        // V24D15-CLEANUP (BUG_COMPARE_404): getComparison now returns Mono,
        // so the exception propagates via Mono.error (not thenThrow).
        when(comparisonService.getComparison(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.error(new BaselineNotFoundException(CAREER_ID, MATCH_ID)));

        Mono<ResponseEntity<Object>> mono = controller.getCompare(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCompare_liveDetailNotFound_returns404() {
        when(queryService.isApiEnabled()).thenReturn(true);
        when(comparisonService.getComparison(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.error(new LiveDetailNotFoundException(CAREER_ID, MATCH_ID)));

        Mono<ResponseEntity<Object>> mono = controller.getCompare(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCompare_blankCareerId_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getCompare("", MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("careerId"));
    }

    @Test
    void getCompare_nullMatchId_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getCompare(CAREER_ID, null);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private MatchComparison sampleComparison() {
        V24DetailedMatchData live = new V24DetailedMatchData(
                MATCH_ID, CAREER_ID, 1, 5,
                "home-id", "away-id",
                "Home", "Away",
                2, 1, 1.8, 0.9,
                12, 8, 55, 45,
                List.of(), List.of(),
                "Live", "V24", 1, Instant.now());
        V24DetailedMatchData baseline = new V24DetailedMatchData(
                MATCH_ID, CAREER_ID, 1, 5,
                "home-id", "away-id",
                "Home", "Away",
                1, 0, 1.0, 0.5,
                8, 5, 50, 50,
                List.of(), List.of(),
                "Baseline", "V24", 1, Instant.now());
        MatchComparisonDiff diff = MatchComparisonDiff.calculate(baseline, live);
        return new MatchComparison(baseline, live, diff);
    }
}

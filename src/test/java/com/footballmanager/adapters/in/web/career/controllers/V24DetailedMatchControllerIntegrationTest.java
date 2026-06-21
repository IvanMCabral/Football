package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchQueryService;
import com.footballmanager.application.service.simulation.v24.V24MatchEventDto;
import com.footballmanager.application.service.simulation.v24.V24TimelineSnapshot;
import com.footballmanager.application.service.simulation.v24.MatchComparison;
import com.footballmanager.application.service.simulation.v24.MatchComparisonDiff;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V24D24: Integration tests for {@link V24DetailedMatchController} covering
 * the {@code /detail}, {@code /compare} and the new {@code /timeline} endpoints.
 *
 * <p>Tests the controller's response shape and error handling without
 * spinning up the full Spring context (same pattern as
 * {@link MatchCompareControllerTest}).
 */
@ExtendWith(MockitoExtension.class)
class V24DetailedMatchControllerIntegrationTest {

    @Mock
    private V24DetailedMatchQueryService queryService;
    @Mock
    private MatchComparisonService comparisonService;

    private V24DetailedMatchController controller;

    private static final String CAREER_ID = "career-001";
    private static final String MATCH_ID = "match-001";
    private static final String HOME = "home-team";
    private static final String AWAY = "away-team";

    @BeforeEach
    void setUp() {
        controller = new V24DetailedMatchController(queryService, comparisonService);
    }

    private static V24MatchEventDto ev(int minute, String type, String teamId, double xg) {
        return new V24MatchEventDto(minute, type, teamId, "p1", "Player", null, null, xg, "", null);
    }

    private static V24DetailedMatchData sampleDetail() {
        return new V24DetailedMatchData(
                MATCH_ID, CAREER_ID, 1, 5,
                HOME, AWAY,
                "Home FC", "Away FC",
                2, 1, 1.8, 0.9,
                12, 8, 55, 45,
                List.of(
                        ev(10, "GOAL", HOME, 0.30),
                        ev(20, "SHOT", AWAY, 0.10),
                        ev(40, "GOAL", HOME, 0.25),
                        ev(80, "GOAL", AWAY, 0.15)
                ),
                List.of(),
                "Home win 2-1", "V24", 1, Instant.now(), null, null);
    }

    // ============== /detail ==============

    @Test
    void getDetail_blankCareerId_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getDetail("  ", MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("careerId"));
    }

    @Test
    void getDetail_featureDisabled_returns404() {
        when(queryService.isApiEnabled()).thenReturn(false);

        Mono<ResponseEntity<Object>> mono = controller.getDetail(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        // No call to findDetail when feature flag is off
        verify(queryService, never()).findDetail(anyString(), anyString());
    }

    @Test
    void getDetail_found_returns200() {
        V24DetailedMatchData detail = sampleDetail();
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(detail));

        Mono<ResponseEntity<Object>> mono = controller.getDetail(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(V24DetailedMatchData.class, response.getBody());
    }

    @Test
    void getDetail_notFound_returns404() {
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.empty());

        Mono<ResponseEntity<Object>> mono = controller.getDetail(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ============== /compare (smoke check) ==============

    @Test
    void getCompare_featureDisabled_returns404() {
        when(queryService.isApiEnabled()).thenReturn(false);

        Mono<ResponseEntity<Object>> mono = controller.getCompare(CAREER_ID, MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(comparisonService, never()).getComparison(anyString(), anyString());
    }

    @Test
    void getCompare_blankCareerId_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getCompare("  ", MATCH_ID);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ============== /timeline (V24D24) ==============

    @Test
    void getTimeline_blankCareerId_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getTimeline("  ", MATCH_ID, 45);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("careerId"));
    }

    @Test
    void getTimeline_blankMatchId_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, "  ", 45);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("matchId"));
    }

    @Test
    void getTimeline_nullMinute_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, null);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getTimeline_negativeMinute_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, -1);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("minute"));
    }

    @Test
    void getTimeline_minuteOver130_returns400() {
        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 131);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getTimeline_minute130_isAllowed() {
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(sampleDetail()));

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 130);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getTimeline_featureDisabled_returns404() {
        when(queryService.isApiEnabled()).thenReturn(false);

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 45);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(queryService, never()).findDetail(anyString(), anyString());
    }

    @Test
    void getTimeline_detailNotFound_returns404() {
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.empty());

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 45);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getTimeline_minute45_returns200WithSnapshot() {
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(sampleDetail()));

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 45);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(V24TimelineSnapshot.class, response.getBody());

        V24TimelineSnapshot snap = (V24TimelineSnapshot) response.getBody();
        assertEquals(45, snap.minute());
        // sampleDetail timeline: min 10 (home GOAL), 20 (away SHOT), 40 (home GOAL), 80 (away GOAL)
        // minute 45 → includes min 10, 20, 40 → 3 events
        assertEquals(3, snap.events().size());
        // home goals: min 10, 40 → 2
        assertEquals(2, snap.homeGoals());
        // away goals: 0 (only GOAL at min 80 which is excluded)
        assertEquals(0, snap.awayGoals());
        // home shots: 0 (only GOALs)
        assertEquals(0, snap.homeShots());
        // away shots: 1 (min 20 SHOT)
        assertEquals(1, snap.awayShots());
        // home xG: 0.30 (min 10 GOAL) + 0.25 (min 40 GOAL) = 0.55
        assertEquals(0.55, snap.homeXg(), 0.001);
        // away xG: 0.10 (min 20 SHOT)
        assertEquals(0.10, snap.awayXg(), 0.001);
    }

    @Test
    void getTimeline_minute0_returns200WithEmptySnapshot() {
        // Use a detail with all events > 0 to ensure filter actually excludes them
        V24DetailedMatchData d = new V24DetailedMatchData(
                MATCH_ID, CAREER_ID, 1, 5,
                HOME, AWAY,
                "Home FC", "Away FC",
                2, 1, 1.8, 0.9,
                12, 8, 55, 45,
                List.of(
                        ev(10, "GOAL", HOME, 0.30),
                        ev(40, "GOAL", HOME, 0.25)
                ),
                List.of(),
                "match", "V24", 1, Instant.now(), null, null);
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(d));

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 0);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        V24TimelineSnapshot snap = (V24TimelineSnapshot) response.getBody();
        assertEquals(0, snap.minute());
        assertEquals(0, snap.events().size());
        assertEquals(0, snap.homeGoals());
        assertEquals(0, snap.awayGoals());
    }

    @Test
    void getTimeline_minute90_returns200WithAllEvents() {
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(sampleDetail()));

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 90);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        V24TimelineSnapshot snap = (V24TimelineSnapshot) response.getBody();
        // sampleDetail has 4 events (min 10, 20, 40, 80) — all <= 90
        assertEquals(4, snap.events().size());
        assertEquals(2, snap.homeGoals());
        assertEquals(1, snap.awayGoals());
        assertEquals(0, snap.homeShots());
        assertEquals(1, snap.awayShots());
    }

    @Test
    void getTimeline_minute0WithEmptyTimeline_returns200() {
        V24DetailedMatchData d = new V24DetailedMatchData(
                MATCH_ID, CAREER_ID, 1, 5,
                HOME, AWAY,
                "Home FC", "Away FC",
                0, 0, 0.0, 0.0,
                0, 0, 50, 50,
                List.of(), List.of(),
                "no events", "V24", 1, Instant.now(), null, null);
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.findDetail(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(d));

        Mono<ResponseEntity<Object>> mono = controller.getTimeline(CAREER_ID, MATCH_ID, 0);
        ResponseEntity<Object> response = mono.block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        V24TimelineSnapshot snap = (V24TimelineSnapshot) response.getBody();
        assertEquals(0, snap.minute());
        assertEquals(0, snap.events().size());
    }
}

package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchQueryService;
import com.footballmanager.application.service.simulation.v24.V24MatchEventDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * V24D4C: Tests for V24DetailedMatchQueryService.
 */
@ExtendWith(MockitoExtension.class)
class V24DetailedMatchQueryServiceTest {

    @Mock
    private V24DetailedMatchStoragePort storagePort;

    private V24DetailedMatchQueryService queryService;

    private V24DetailedMatchData sampleDetail;

    @BeforeEach
    void setUp() {
        // API disabled by default
        queryService = new V24DetailedMatchQueryService(storagePort, false);

        List<V24MatchEventDto> timeline = List.of(
                new V24MatchEventDto(23, "GOAL", "home-team", "player-1", "Striker One",
                        "player-2", "Assist King", 0.35, "Goal by Striker One", null)
        );
        List<V24PlayerMatchRatingDto> ratings = List.of(
                new V24PlayerMatchRatingDto("player-1", "Striker One", "home-team", "ATT",
                        8.5, 1, 1, 3, 4, 0, 0, 0, 2, false, true)
        );
        sampleDetail = new V24DetailedMatchData(
                "match-123", "career-abc", 1, 5,
                "home-team", "away-team",
                "Home Utd", "Away City",
                2, 1, 1.8, 0.9,
                12, 8, 55, 45,
                timeline, ratings,
                "Home win 2-1", "V24", 1, Instant.now()
        );
    }

    @Test
    void findDetailReturnsEmptyWhenApiDisabled() {
        Optional<V24DetailedMatchData> result = queryService.findDetail("career-abc", "match-123");

        assertTrue(result.isEmpty());
        verifyNoInteractions(storagePort);
    }

    @Test
    void findDetailReturnsDataWhenPresentAndApiEnabled() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);
        when(storagePort.findByMatchId("career-abc", "match-123")).thenReturn(Optional.of(sampleDetail));

        Optional<V24DetailedMatchData> result = queryService.findDetail("career-abc", "match-123");

        assertTrue(result.isPresent());
        assertEquals("match-123", result.get().matchId());
        assertEquals("career-abc", result.get().careerId());
        assertEquals(2, result.get().homeGoals());
        assertEquals(1, result.get().awayGoals());
        assertEquals(1, result.get().timeline().size());
        assertEquals("GOAL", result.get().timeline().get(0).type());
        assertEquals(1, result.get().playerRatings().size());
        assertEquals(8.5, result.get().playerRatings().get(0).rating(), 0.01);
    }

    @Test
    void findDetailReturnsEmptyWhenMissingAndApiEnabled() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);
        when(storagePort.findByMatchId("career-abc", "nonexistent")).thenReturn(Optional.empty());

        Optional<V24DetailedMatchData> result = queryService.findDetail("career-abc", "nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsBlankCareerId() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                queryService.findDetail("  ", "match-123"));
        assertEquals("careerId must not be blank", e.getMessage());
    }

    @Test
    void rejectsBlankMatchId() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                queryService.findDetail("career-abc", "  "));
        assertEquals("matchId must not be blank", e.getMessage());
    }

    @Test
    void rejectsNullCareerId() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                queryService.findDetail(null, "match-123"));
        assertEquals("careerId must not be blank", e.getMessage());
    }

    @Test
    void rejectsNullMatchId() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                queryService.findDetail("career-abc", null));
        assertEquals("matchId must not be blank", e.getMessage());
    }

    @Test
    void doesNotCallStorageWhenApiDisabled() {
        queryService.findDetail("career-abc", "match-123");

        verifyNoInteractions(storagePort);
    }

    @Test
    void isApiEnabledReturnsFalseWhenDisabled() {
        assertFalse(queryService.isApiEnabled());
    }

    @Test
    void isApiEnabledReturnsTrueWhenEnabled() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);
        assertTrue(queryService.isApiEnabled());
    }

    @Test
    void careerIsolationSameMatchIdDifferentCareer() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);

        V24DetailedMatchData careerXDetail = new V24DetailedMatchData(
                "match-123", "career-xyz", 2, 7,
                "home-team", "away-team",
                "Team X", "Team Y",
                1, 2, 0.5, 1.4,
                6, 14, 35, 65,
                List.of(), List.of(),
                "Away win 2-1", "V24", 1, Instant.now()
        );

        when(storagePort.findByMatchId("career-abc", "match-123")).thenReturn(Optional.of(sampleDetail));
        when(storagePort.findByMatchId("career-xyz", "match-123")).thenReturn(Optional.of(careerXDetail));

        Optional<V24DetailedMatchData> foundAbc = queryService.findDetail("career-abc", "match-123");
        Optional<V24DetailedMatchData> foundXyz = queryService.findDetail("career-xyz", "match-123");

        assertTrue(foundAbc.isPresent());
        assertTrue(foundXyz.isPresent());
        assertEquals("career-abc", foundAbc.get().careerId());
        assertEquals("career-xyz", foundXyz.get().careerId());
        assertEquals(2, foundAbc.get().homeGoals());
        assertEquals(1, foundXyz.get().homeGoals());
    }

    @Test
    void doesNotSimulateOrWriteStorage() {
        queryService = new V24DetailedMatchQueryService(storagePort, true);
        queryService.findDetail("career-abc", "match-123");

        // Verify only read operation
        verify(storagePort, never()).save(anyString(), any());
        verify(storagePort, never()).deleteByCareerId(anyString());
    }
}
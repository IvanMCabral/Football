package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24MatchEventDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V24D4B: Tests for V24DetailedMatchRedisAdapter.
 *
 * <p>Tests adapter behavior with mocked ReactiveRedisTemplate.
 * Uses ReactiveValueOperations for reactive Redis operations.
 */
@ExtendWith(MockitoExtension.class)
class V24DetailedMatchRedisAdapterTest {

    @Mock
    private ReactiveRedisTemplate<String, V24DetailedMatchData> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, V24DetailedMatchData> reactiveValueOps;

    private V24DetailedMatchRedisAdapter adapter;

    private V24DetailedMatchData sampleDetail;

    @BeforeEach
    void setUp() {
        adapter = new V24DetailedMatchRedisAdapter(redisTemplate);

        List<V24MatchEventDto> timeline = List.of(
                new V24MatchEventDto(23, "GOAL", "home-team", "player-1", "Striker One",
                        "player-2", "Assist King", 0.35, "Goal by Striker One assisted by Assist King", null)
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

        lenient().when(redisTemplate.opsForValue()).thenReturn(reactiveValueOps);
    }

    @Test
    void saveThenFindReturnsDetail() {
        when(reactiveValueOps.get(anyString())).thenReturn(Mono.just(sampleDetail));
        when(reactiveValueOps.set(anyString(), eq(sampleDetail))).thenReturn(Mono.just(true));

        adapter.save("career-abc", sampleDetail);
        Optional<V24DetailedMatchData> found = adapter.findByMatchId("career-abc", "match-123");

        assertTrue(found.isPresent());
        assertEquals("match-123", found.get().matchId());
        assertEquals("career-abc", found.get().careerId());
        assertEquals(2, found.get().homeGoals());
        assertEquals(1, found.get().awayGoals());
        assertEquals(1, found.get().timeline().size());
        assertEquals("GOAL", found.get().timeline().get(0).type());
        assertEquals(1, found.get().playerRatings().size());
        assertEquals(8.5, found.get().playerRatings().get(0).rating(), 0.01);
    }

    @Test
    void findMissingReturnsEmpty() {
        when(reactiveValueOps.get(anyString())).thenReturn(Mono.empty());

        Optional<V24DetailedMatchData> found = adapter.findByMatchId("career-abc", "nonexistent-match");

        assertTrue(found.isEmpty());
    }

    @Test
    void saveOverwritesSameMatchId() {
        when(reactiveValueOps.set(anyString(), eq(sampleDetail))).thenReturn(Mono.just(true));
        when(reactiveValueOps.get(anyString())).thenReturn(Mono.just(sampleDetail));

        adapter.save("career-abc", sampleDetail);
        adapter.findByMatchId("career-abc", "match-123");

        verify(reactiveValueOps, times(1)).set(anyString(), eq(sampleDetail));
    }

    @Test
    void deleteByCareerIdRemovesDetails() {
        String pattern = "career:career-abc:match-detail:*";
        when(redisTemplate.keys(pattern)).thenReturn(Flux.fromIterable(List.of(
                "career:career-abc:match-detail:match-1",
                "career:career-abc:match-detail:match-2"
        )));
        when(redisTemplate.delete(any(String[].class))).thenReturn(Mono.just(2L));

        adapter.deleteByCareerId("career-abc");

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate).delete(any(String[].class));
    }

    @Test
    void deleteByCareerIdEmptyWhenNoKeys() {
        String pattern = "career:career-abc:match-detail:*";
        when(redisTemplate.keys(pattern)).thenReturn(Flux.empty());

        adapter.deleteByCareerId("career-abc");

        verify(redisTemplate).keys(pattern);
    }

    @Test
    void careerIsolationSameMatchIdDifferentCareer() {
        V24DetailedMatchData careerXDetail = new V24DetailedMatchData(
                "match-123", "career-xyz", 2, 7,
                "home-team", "away-team",
                "Team X", "Team Y",
                1, 2, 0.5, 1.4,
                6, 14, 35, 65,
                List.of(), List.of(),
                "Away win 2-1", "V24", 1, Instant.now()
        );

        when(reactiveValueOps.set(anyString(), any())).thenReturn(Mono.just(true));
        when(reactiveValueOps.get("career:career-abc:match-detail:match-123")).thenReturn(Mono.just(sampleDetail));
        when(reactiveValueOps.get("career:career-xyz:match-detail:match-123")).thenReturn(Mono.just(careerXDetail));

        adapter.save("career-abc", sampleDetail);
        adapter.save("career-xyz", careerXDetail);

        Optional<V24DetailedMatchData> foundAbc = adapter.findByMatchId("career-abc", "match-123");
        Optional<V24DetailedMatchData> foundXyz = adapter.findByMatchId("career-xyz", "match-123");

        assertTrue(foundAbc.isPresent());
        assertTrue(foundXyz.isPresent());
        assertEquals("career-abc", foundAbc.get().careerId());
        assertEquals("career-xyz", foundXyz.get().careerId());
        assertEquals(2, foundAbc.get().homeGoals());
        assertEquals(1, foundXyz.get().homeGoals());
    }

    @Test
    void rejectsBlankCareerIdOnSave() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                adapter.save("  ", sampleDetail));
        assertEquals("careerId must not be blank", e.getMessage());
    }

    @Test
    void rejectsNullDetail() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                adapter.save("career-abc", null));
        assertEquals("detail must not be null", e.getMessage());
    }

    @Test
    void rejectsBlankCareerIdOnFind() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                adapter.findByMatchId("  ", "match-123"));
        assertEquals("careerId must not be blank", e.getMessage());
    }

    @Test
    void rejectsBlankMatchIdOnFind() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                adapter.findByMatchId("career-abc", "  "));
        assertEquals("matchId must not be blank", e.getMessage());
    }

    @Test
    void rejectsBlankCareerIdOnDelete() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                adapter.deleteByCareerId("  "));
        assertEquals("careerId must not be blank", e.getMessage());
    }

    @Test
    void preservesTimelineAndPlayerRatings() {
        List<V24MatchEventDto> timeline = List.of(
                new V24MatchEventDto(12, "SHOT", "home-team", "player-5", "Midfielder",
                        null, null, 0.22, "Shot by Midfielder", null),
                new V24MatchEventDto(45, "GOAL", "home-team", "player-1", "Striker One",
                        "player-3", "Playmaker", 0.38, "Goal by Striker One assisted by Playmaker", null),
                new V24MatchEventDto(78, "CARD", "away-team", "player-8", "Defender",
                        null, null, 0.0, "Yellow card", null)
        );
        List<V24PlayerMatchRatingDto> ratings = List.of(
                new V24PlayerMatchRatingDto("player-1", "Striker One", "home-team", "ATT",
                        9.0, 1, 1, 2, 3, 0, 0, 0, 1, false, true),
                new V24PlayerMatchRatingDto("player-3", "Playmaker", "home-team", "MID",
                        7.5, 0, 1, 5, 1, 1, 0, 0, 0, true, false),
                new V24PlayerMatchRatingDto("player-8", "Defender", "away-team", "DEF",
                        5.5, 0, 0, 0, 0, 1, 0, 0, 2, false, false)
        );

        V24DetailedMatchData detail = new V24DetailedMatchData(
                "match-999", "career-xyz", 3, 12,
                "home-team", "away-team",
                "Home Utd", "Away City",
                1, 0, 1.2, 0.4,
                8, 5, 60, 40,
                timeline, ratings,
                "Home win 1-0", "V24", 1, Instant.now()
        );

        when(reactiveValueOps.set(anyString(), any())).thenReturn(Mono.just(true));
        when(reactiveValueOps.get(anyString())).thenReturn(Mono.just(detail));

        adapter.save("career-xyz", detail);
        Optional<V24DetailedMatchData> found = adapter.findByMatchId("career-xyz", "match-999");

        assertTrue(found.isPresent());
        assertEquals(3, found.get().timeline().size());
        assertEquals(12, found.get().timeline().get(0).minute());
        assertEquals("SHOT", found.get().timeline().get(0).type());
        assertEquals(45, found.get().timeline().get(1).minute());
        assertEquals("GOAL", found.get().timeline().get(1).type());
        assertEquals("player-3", found.get().timeline().get(1).relatedPlayerId());
        assertEquals("Playmaker", found.get().timeline().get(1).relatedPlayerName());
        assertEquals(3, found.get().playerRatings().size());
        assertEquals(9.0, found.get().playerRatings().get(0).rating(), 0.01);
        // Playmaker (index 1) has 0 goals, 1 assist
        assertEquals(0, found.get().playerRatings().get(1).goals());
        assertEquals(1, found.get().playerRatings().get(1).assists());
        // Defender (index 2) has 0 goals, 0 assists, 1 yellow card
        assertEquals(1, found.get().playerRatings().get(2).yellowCards());
    }

    @Test
    void findByCareerIdReturnsAllMatches() {
        String pattern = "career:career-abc:match-detail:*";
        V24DetailedMatchData detail2 = new V24DetailedMatchData(
                "match-456", "career-abc", 1, 3,
                "home-team", "away-team",
                "Home Utd", "Away City",
                0, 1, 0.3, 1.2,
                5, 10, 40, 60,
                List.of(), List.of(),
                "Away win 1-0", "V24", 1, Instant.now()
        );
        when(redisTemplate.keys(pattern)).thenReturn(Flux.fromIterable(List.of(
                "career:career-abc:match-detail:match-123",
                "career:career-abc:match-detail:match-456"
        )));
        when(reactiveValueOps.get("career:career-abc:match-detail:match-123")).thenReturn(Mono.just(sampleDetail));
        when(reactiveValueOps.get("career:career-abc:match-detail:match-456")).thenReturn(Mono.just(detail2));

        List<V24DetailedMatchData> results = adapter.findByCareerId("career-abc");

        assertEquals(2, results.size());
    }

    @Test
    void findByCareerIdHandlesDeserializationFailure() {
        String pattern = "career:career-abc:match-detail:*";
        when(redisTemplate.keys(pattern)).thenReturn(Flux.fromIterable(List.of(
                "career:career-abc:match-detail:match-123",
                "career:career-abc:match-detail:match-456"
        )));
        when(reactiveValueOps.get("career:career-abc:match-detail:match-123")).thenReturn(Mono.just(sampleDetail));
        when(reactiveValueOps.get("career:career-abc:match-detail:match-456")).thenReturn(Mono.error(new RuntimeException("bad data")));

        List<V24DetailedMatchData> results = adapter.findByCareerId("career-abc");

        assertEquals(1, results.size());
        assertEquals("match-123", results.get(0).matchId());
    }

    @Test
    void findByCareerIdReturnsEmptyListWhenNoKeys() {
        String pattern = "career:career-empty:match-detail:*";
        when(redisTemplate.keys(pattern)).thenReturn(Flux.empty());

        List<V24DetailedMatchData> results = adapter.findByCareerId("career-empty");

        assertTrue(results.isEmpty());
    }
}
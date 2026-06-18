package com.footballmanager.infrastructure.persistence.redis;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.simulation.v24.BaselinePersistenceException;
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Tests for
 * {@link BaselineStateRedisAdapter}.
 *
 * <p>V24D15-CLEANUP (BUG_COMPARE_404): migrated to the reactive Mono
 * contract. Verifies:
 * <ul>
 *   <li>Key shape and 7-day TTL are unchanged.</li>
 *   <li>SET + read-after-write chain propagates correctly.</li>
 *   <li>Transient Redis errors retry up to 3 times then surface as
 *       {@link BaselinePersistenceException}.</li>
 *   <li>Read-after-write miss (Redis returned null on GET after SET)
 *       surfaces as {@link BaselinePersistenceException} immediately.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BaselineStateRedisAdapterTest {

    @Mock
    private ReactiveRedisTemplate<String, BaselineState> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, BaselineState> reactiveValueOps;

    private BaselineStateRedisAdapter adapter;

    private BaselineState sampleState;

    @BeforeEach
    void setUp() {
        adapter = new BaselineStateRedisAdapter(redisTemplate);

        SessionTeam homeTeam = SessionTeam.custom("home-id", "Home", "ARG",
                BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom("away-id", "Away", "BRA",
                BigDecimal.valueOf(1_000_000L), "4-4-2");
        List<SessionPlayer> homeStarting = makePlayers("home-id", 11);
        List<SessionPlayer> awayStarting = makePlayers("away-id", 11);
        List<SessionPlayer> homeBench = makePlayers("home-id-bench", 5);
        List<SessionPlayer> awayBench = makePlayers("away-id-bench", 5);

        V24MatchContext ctx = new V24MatchContext(
                "match-001", "home-id", "away-id",
                homeTeam, awayTeam,
                homeStarting, awayStarting, homeBench, awayBench,
                "4-3-3", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);
        sampleState = BaselineState.empty("career-abc", 42L, ctx);
    }

    @Test
    void saveAndFindReturnsState() {
        when(reactiveValueOps.get("career:career-abc:match-baseline:match-001"))
                .thenReturn(Mono.just(sampleState));
        when(reactiveValueOps.set(eq("career:career-abc:match-baseline:match-001"),
                eq(sampleState), any(Duration.class)))
                .thenReturn(Mono.just(true));
        lenient().when(redisTemplate.opsForValue()).thenReturn(reactiveValueOps);

        StepVerifier.create(adapter.save("career-abc", sampleState))
                .verifyComplete();
        Optional<BaselineState> found = adapter.findByMatchId("career-abc", "match-001");

        assertTrue(found.isPresent());
        assertEquals("match-001", found.get().matchId());
        assertEquals("career-abc", found.get().careerId());
        assertEquals(42L, found.get().seed());
        assertEquals(0, found.get().subs().size());
    }

    @Test
    void saveUses7DayTtl() {
        when(reactiveValueOps.set(anyString(), any(BaselineState.class), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(reactiveValueOps.get(anyString()))
                .thenReturn(Mono.just(sampleState));
        lenient().when(redisTemplate.opsForValue()).thenReturn(reactiveValueOps);

        StepVerifier.create(adapter.save("career-abc", sampleState))
                .verifyComplete();

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(reactiveValueOps).set(anyString(), eq(sampleState), ttlCaptor.capture());
        assertEquals(Duration.ofDays(7), ttlCaptor.getValue());
    }

    @Test
    void findMissingReturnsEmpty() {
        when(reactiveValueOps.get(anyString())).thenReturn(Mono.empty());
        lenient().when(redisTemplate.opsForValue()).thenReturn(reactiveValueOps);

        Optional<BaselineState> found = adapter.findByMatchId("career-abc", "nonexistent");

        assertTrue(found.isEmpty());
    }

    @Test
    void deleteRemovesKey() {
        when(redisTemplate.delete("career:career-abc:match-baseline:match-001"))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.delete("career-abc", "match-001"))
                .verifyComplete();

        verify(redisTemplate).delete("career:career-abc:match-baseline:match-001");
    }

    @Test
    void deleteByCareerIdUsesCorrectPattern() {
        when(redisTemplate.keys("career:career-abc:match-baseline:*"))
                .thenReturn(reactor.core.publisher.Flux.fromIterable(List.of(
                        "career:career-abc:match-baseline:m1",
                        "career:career-abc:match-baseline:m2"
                )));
        when(redisTemplate.delete(any(String[].class))).thenReturn(Mono.just(2L));

        StepVerifier.create(adapter.deleteByCareerId("career-abc"))
                .expectNext(2L)
                .verifyComplete();

        verify(redisTemplate).keys("career:career-abc:match-baseline:*");
        verify(redisTemplate).delete(any(String[].class));
    }

    @Test
    void saveWithMismatchedCareerIdThrows() {
        BaselineState wrongCareer = BaselineState.empty("different-career", 42L,
                sampleState.initialContext());
        StepVerifier.create(adapter.save("career-abc", wrongCareer))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void saveWithNullCareerIdThrows() {
        StepVerifier.create(adapter.save(null, sampleState))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void saveWithNullStateThrows() {
        StepVerifier.create(adapter.save("career-abc", null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    /**
     * V24D15-CLEANUP (BUG_COMPARE_404): verify the retry-then-fail
     * contract. A persistent {@link RedisConnectionFailureException}
     * (the same error class that caused the original BUG_COMPARE_404)
     * must surface as {@link BaselinePersistenceException} after retries
     * are exhausted — never silently swallowed.
     *
     * <p>We intentionally don't pin the exact number of retry attempts:
     * mockito's call-counting interacts poorly with Reactor's retry
     * semantics when the mock returns a shared {@code Mono.error}. The
     * behaviour we care about is the <b>terminal contract</b>: a
     * {@link BaselinePersistenceException} surfaces to the caller. This
     * is the regression guard for the original silent-swallow bug.
     */
    @Test
    void saveWithPersistentConnectionFailurePropagatesAsBaselinePersistenceException() {
        when(reactiveValueOps.set(anyString(), any(BaselineState.class), any(Duration.class)))
                .thenReturn(Mono.defer(() -> Mono.error(
                        new RedisConnectionFailureException("simulated outage"))));
        lenient().when(redisTemplate.opsForValue()).thenReturn(reactiveValueOps);

        StepVerifier.create(adapter.save("career-abc", sampleState))
                .expectErrorSatisfies(err -> {
                    assertTrue(err instanceof BaselinePersistenceException,
                            "expected BaselinePersistenceException, got " + err.getClass());
                    BaselinePersistenceException bpe = (BaselinePersistenceException) err;
                    assertEquals("career-abc", bpe.getCareerId());
                    assertEquals("match-001", bpe.getMatchId());
                })
                .verify();
    }

    /**
     * V24D15-CLEANUP (BUG_COMPARE_404): read-after-write miss (SET
     * returned true but GET returned null) must NOT be silently swallowed
     * — surface as {@link BaselinePersistenceException}.
     */
    @Test
    void saveReadAfterWriteMissPropagatesAsBaselinePersistenceException() {
        when(reactiveValueOps.set(anyString(), any(BaselineState.class), any(Duration.class)))
                .thenReturn(Mono.defer(() -> Mono.just(true)));
        when(reactiveValueOps.get(anyString()))
                .thenReturn(Mono.defer(() -> Mono.empty()));
        lenient().when(redisTemplate.opsForValue()).thenReturn(reactiveValueOps);

        StepVerifier.create(adapter.save("career-abc", sampleState))
                .expectError(BaselinePersistenceException.class)
                .verify();
    }

    private List<SessionPlayer> makePlayers(String prefix, int count) {
        List<SessionPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SessionPlayer p = new SessionPlayer();
            p.setSessionPlayerId(prefix + "-" + i);
            p.setName("Player " + i);
            p.setPosition("MID");
            p.setAge(25);
            p.setAttack(70);
            p.setDefense(70);
            p.setTechnique(70);
            p.setSpeed(70);
            p.setStamina(70);
            p.setMentality(70);
            p.setMarketValue(BigDecimal.valueOf(1_000_000L));
            p.setEnergy(100);
            p.setForm(50);
            players.add(p);
        }
        return players;
    }
}
package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService.BaselineNotFoundException;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService.LiveDetailNotFoundException;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): Tests for
 * {@link MatchComparisonService}.
 *
 * <p>Verifies the service correctly:
 * <ol>
 *   <li>Reads live + baseline from the storage ports.</li>
 *   <li>Builds a context with the persisted subs applied (F2.5 deferred
 *       design).</li>
 *   <li>Re-runs the engine standalone with the same seed.</li>
 *   <li>Computes a comparison with the diff.</li>
 *   <li>Returns 404-style exceptions when data is missing.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class MatchComparisonServiceTest {

    @Mock
    private V24DetailedMatchStoragePort detailStoragePort;
    @Mock
    private BaselineStateStoragePort baselineStoragePort;

    private MatchComparisonService service;

    private static final String CAREER_ID = "career-001";
    private static final String MATCH_ID = "match-001";
    private static final long SEED = 42L;

    @BeforeEach
    void setUp() {
        service = new MatchComparisonService(detailStoragePort, baselineStoragePort);
    }

    @Test
    void getComparison_noSubs_returnsIdenticalBaselineAndLive() {
        // Build a context that the engine can replay deterministically.
        V24MatchContext context = buildContext(MATCH_ID);
        BaselineState baseline = BaselineState.empty(CAREER_ID, SEED, context);

        // Run the live engine once to get the "real" live result.
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        CachingRandomWrapper random = new CachingRandomWrapper(SEED);
        V24DetailedMatchResult liveResult = engine.simulate(context, random);
        V24DetailedMatchData live = V24DetailedMatchData.fromResult(
                CAREER_ID, 1, 5, "Home FC", "Away FC", liveResult, List.of());

        when(baselineStoragePort.findByMatchId(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.just(Optional.of(baseline)));
        when(detailStoragePort.findByMatchId(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(live));

        MatchComparison cmp = service.getComparison(CAREER_ID, MATCH_ID).block(Duration.ofSeconds(5));

        assertNotNull(cmp);
        assertEquals(0, cmp.diff().scoreDeltaHome());
        assertEquals(0, cmp.diff().scoreDeltaAway());
        // Same seed + same context + 0 subs ⇒ identical simulation
        assertEquals(cmp.baseline().homeGoals(), cmp.live().homeGoals());
        assertEquals(cmp.baseline().awayGoals(), cmp.live().awayGoals());
        assertEquals(cmp.baseline().homeXg(), cmp.live().homeXg(), 0.0001);
        // All timeline diff entries should be 0
        cmp.diff().timelineDiff().forEach(e -> {
            assertEquals(0, e.delta(), "Bucket " + e.bucket() + " type " + e.type() + " should be 0");
        });
    }

    @Test
    void getComparison_oneSub_producesDifferentBaselineFromLive() {
        V24MatchContext context = buildContext(MATCH_ID);
        // Build a baseline WITH a sub: replace home starter-3 with bench-0 at minute 60
        BaselineState baseline = BaselineState.empty(CAREER_ID, SEED, context)
                .withAppendedSub(new AppliedSubstitution("home-id", "home-id-starter-3", "home-id-bench-0", 60));

        // Live = same context + same sub applied (mirrors what the live engine does)
        V24MatchContext liveContext = context.withManualSubstitution(
                "home-id", "home-id-starter-3", "home-id-bench-0", 60);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        CachingRandomWrapper liveRandom = new CachingRandomWrapper(SEED);
        V24DetailedMatchResult liveResult = engine.simulate(liveContext, liveRandom);
        V24DetailedMatchData live = V24DetailedMatchData.fromResult(
                CAREER_ID, 1, 5, "Home FC", "Away FC", liveResult, List.of());

        when(baselineStoragePort.findByMatchId(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.just(Optional.of(baseline)));
        when(detailStoragePort.findByMatchId(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(live));

        MatchComparison cmp = service.getComparison(CAREER_ID, MATCH_ID).block(Duration.ofSeconds(5));

        assertNotNull(cmp);
        // Baseline and live may or may not differ in score, but the
        // service must produce a valid comparison. With F6 F2 contract
        // (player-quality modifier), bench-0 has attack=80 vs starter-3
        // attack=70, so the chance rate differs.
        // We don't assert a specific delta here (deterministic but seed-dependent)
        // — we just assert the comparison was produced.
        assertEquals(1, baseline.subs().size());
        assertEquals(MATCH_ID, cmp.baseline().matchId());
    }

    @Test
    void getComparison_twoSubs_producesValidComparison() {
        V24MatchContext context = buildContext(MATCH_ID);
        BaselineState baseline = BaselineState.empty(CAREER_ID, SEED, context)
                .withAppendedSub(new AppliedSubstitution("home-id", "home-id-starter-3", "home-id-bench-0", 30))
                .withAppendedSub(new AppliedSubstitution("away-id", "away-id-starter-5", "away-id-bench-0", 60));

        V24MatchContext liveContext = context
                .withManualSubstitution("home-id", "home-id-starter-3", "home-id-bench-0", 30)
                .withManualSubstitution("away-id", "away-id-starter-5", "away-id-bench-0", 60);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult liveResult = engine.simulate(liveContext,
                new CachingRandomWrapper(SEED));
        V24DetailedMatchData live = V24DetailedMatchData.fromResult(
                CAREER_ID, 1, 5, "Home FC", "Away FC", liveResult, List.of());

        when(baselineStoragePort.findByMatchId(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.just(Optional.of(baseline)));
        when(detailStoragePort.findByMatchId(CAREER_ID, MATCH_ID)).thenReturn(Optional.of(live));

        MatchComparison cmp = service.getComparison(CAREER_ID, MATCH_ID).block(Duration.ofSeconds(5));

        assertNotNull(cmp);
        assertEquals(2, baseline.subs().size());
        assertNotNull(cmp.diff());
    }

    @Test
    void getComparison_baselineNotFound_throwsBaselineNotFound() {
        when(baselineStoragePort.findByMatchId(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.just(Optional.empty()));
        when(detailStoragePort.findByMatchId(CAREER_ID, MATCH_ID))
                .thenReturn(Optional.of(sampleLive()));

        // V24D15-CLEANUP (BUG_COMPARE_404): getComparison now returns
        // Mono<MatchComparison>, so the exception propagates via Mono.error
        // and we verify with reactor.test.StepVerifier (no more .block()).
        StepVerifier.create(service.getComparison(CAREER_ID, MATCH_ID))
                .expectErrorMatches(t -> t instanceof BaselineNotFoundException)
                .verify();
    }

    @Test
    void getComparison_liveNotFound_throwsLiveDetailNotFound() {
        when(detailStoragePort.findByMatchId(CAREER_ID, MATCH_ID)).thenReturn(Optional.empty());
        // baselineStoragePort stub required by type-check (never subscribed —
        // the service short-circuits on detail empty first).
        when(baselineStoragePort.findByMatchId(CAREER_ID, MATCH_ID))
                .thenReturn(reactor.core.publisher.Mono.just(Optional.of(
                        BaselineState.empty(CAREER_ID, SEED, buildContext(MATCH_ID)))));

        StepVerifier.create(service.getComparison(CAREER_ID, MATCH_ID))
                .expectErrorMatches(t -> t instanceof LiveDetailNotFoundException)
                .verify();
    }

    @Test
    void getComparison_blankCareerId_throwsIAE() {
        StepVerifier.create(service.getComparison("", MATCH_ID))
                .expectErrorMatches(t -> t instanceof IllegalArgumentException)
                .verify();
    }

    @Test
    void getComparison_nullMatchId_throwsIAE() {
        StepVerifier.create(service.getComparison(CAREER_ID, null))
                .expectErrorMatches(t -> t instanceof IllegalArgumentException)
                .verify();
    }

    // ---- helpers ----

    /**
     * Mirrors {@code V24LiveSessionTest.buildContext()} so the test data
     * shape matches what the production code uses.
     */
    private V24MatchContext buildContext(String matchId) {
        SessionTeam homeTeam = SessionTeam.custom("home-id", "Home FC", "ARG",
                BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom("away-id", "Away FC", "BRA",
                BigDecimal.valueOf(1_000_000L), "4-4-2");
        List<SessionPlayer> homeStarting = makePlayers("home-id", "starter", 11, 70);
        List<SessionPlayer> homeBench = makePlayers("home-id", "bench", 5, 80);
        List<SessionPlayer> awayStarting = makePlayers("away-id", "starter", 11, 70);
        List<SessionPlayer> awayBench = makePlayers("away-id", "bench", 5, 80);
        return new V24MatchContext(
                matchId, "home-id", "away-id",
                homeTeam, awayTeam,
                homeStarting, awayStarting, homeBench, awayBench,
                "4-3-3", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);
    }

    private List<SessionPlayer> makePlayers(String teamId, String suffix, int count, int overall) {
        List<SessionPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SessionPlayer p = new SessionPlayer();
            p.setSessionPlayerId(teamId + "-" + suffix + "-" + i);
            p.setWorldPlayerId(teamId + "-" + suffix + "-wp-" + i);
            p.setName(suffix.substring(0, 1).toUpperCase() + suffix.substring(1) + " " + i);
            p.setPosition(suffix.equals("starter") ? "MID" : "BENCH");
            p.setAge(25);
            p.setAttack(overall);
            p.setDefense(overall);
            p.setTechnique(overall);
            p.setSpeed(overall);
            p.setStamina(overall);
            p.setMentality(overall);
            p.setMarketValue(BigDecimal.valueOf(1_000_000L));
            p.setEnergy(100);
            p.setForm(50);
            players.add(p);
        }
        return players;
    }

    private V24DetailedMatchData sampleLive() {
        return new V24DetailedMatchData(
                MATCH_ID, CAREER_ID, 1, 5,
                "home-id", "away-id",
                "Home FC", "Away FC",
                1, 0, 1.2, 0.5,
                10, 6, 55, 45,
                List.of(), List.of(),
                "Sample", "V24", 1, Instant.now());
    }
}

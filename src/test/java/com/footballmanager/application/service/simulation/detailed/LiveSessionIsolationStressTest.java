package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.FormationSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSessionIsolationStressTest {

    @Test
    @Timeout(20)
    @DisplayName("interleaved LiveSessions keep deferred substitutions isolated")
    void interleavedLiveSessionsKeepDeferredSubstitutionsIsolated() {
        Scenario alpha = scenario("alpha", 41L, 12, 10, 4, TeamStyle.BALANCED);
        Scenario beta = scenario("beta", 42L, 27, 8, 5, TeamStyle.ATTACKING);

        LiveSession sessionA = new LiveSession(alpha.context(), alpha.seed());
        LiveSession sessionB = new LiveSession(beta.context(), beta.seed());

        tickUntil(sessionA, alpha.subMinute() - 2);
        tickUntil(sessionB, 2);
        sessionA.recordManualSubstitution(alpha.event());
        tickUntil(sessionB, beta.subMinute() - 2);
        sessionB.recordManualSubstitution(beta.event());
        tickUntil(sessionA, 40);
        tickUntil(sessionB, 40);

        SessionSignature actualA = signature(alpha, sessionA);
        SessionSignature actualB = signature(beta, sessionB);

        assertThat(actualA).isEqualTo(runSequential(alpha, 40));
        assertThat(actualB).isEqualTo(runSequential(beta, 40));
        assertIsolated(alpha, sessionA, beta);
        assertIsolated(beta, sessionB, alpha);
    }

    @Test
    @Timeout(30)
    @DisplayName("different seeds and substitutions match sequential baselines under parallel execution")
    void differentSeedsAndSubstitutionsMatchSequentialBaselinesUnderParallelExecution() throws Exception {
        List<Scenario> scenarios = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            scenarios.add(scenario("parallel-" + i, 100L + i, 8 + i, 8 + (i % 3), 4 + (i % 3),
                    i % 2 == 0 ? TeamStyle.BALANCED : TeamStyle.ATTACKING));
        }

        List<SessionSignature> expected = scenarios.stream()
                .map(scenario -> runSequential(scenario, 90))
                .toList();
        List<SessionSignature> actual = runParallel(scenarios, 90);

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @RepeatedTest(value = 6, name = "same seed isolation stress {currentRepetition}/{totalRepetitions}")
    @Timeout(30)
    @DisplayName("same seed concurrent LiveSessions remain deterministic per mutation")
    void sameSeedConcurrentLiveSessionsRemainDeterministicPerMutation() throws Exception {
        List<Scenario> scenarios = List.of(
                scenario("same-seed-a", 777L, 10, 8, 4, TeamStyle.BALANCED),
                scenario("same-seed-b", 777L, 17, 9, 5, TeamStyle.ATTACKING),
                scenario("same-seed-c", 777L, 24, 10, 6, TeamStyle.COUNTER),
                scenario("same-seed-d", 777L, 31, 8, 6, TeamStyle.POSSESSION),
                scenario("same-seed-e", 777L, 38, 9, 4, TeamStyle.DEFENSIVE),
                scenario("same-seed-f", 777L, 45, 10, 5, TeamStyle.BALANCED),
                scenario("same-seed-g", 777L, 52, 8, 4, TeamStyle.ATTACKING),
                scenario("same-seed-h", 777L, 59, 9, 6, TeamStyle.COUNTER));

        List<SessionSignature> expected = scenarios.stream()
                .map(scenario -> runSequential(scenario, 90))
                .toList();
        List<SessionSignature> actual = runParallel(scenarios, 90);

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual.stream().map(SessionSignature::substitutionKey).distinct().count())
                .isEqualTo(scenarios.size());
    }

    @Test
    @Timeout(30)
    @DisplayName("concurrent replay after mutations preserves prefixes and session ownership")
    void concurrentReplayAfterMutationsPreservesPrefixesAndSessionOwnership() throws Exception {
        List<Scenario> scenarios = List.of(
                scenario("replay-a", 901L, 18, 8, 4, TeamStyle.BALANCED),
                scenario("replay-b", 902L, 22, 9, 5, TeamStyle.ATTACKING),
                scenario("replay-c", 901L, 26, 10, 6, TeamStyle.POSSESSION),
                scenario("replay-d", 902L, 30, 8, 6, TeamStyle.COUNTER));

        List<ReplaySignature> expected = scenarios.stream()
                .map(this::runSequentialReplay)
                .toList();
        List<ReplaySignature> actual = runParallelReplay(scenarios);

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual.stream().map(ReplaySignature::postReplayHash).distinct().count())
                .isGreaterThan(1);
    }

    @Test
    @Timeout(20)
    @DisplayName("deferred substitutions survive replay without duplicated accumulated events")
    void deferredSubstitutionsSurviveReplayWithoutDuplicatedAccumulatedEvents() {
        Scenario scenario = scenario("accumulated-replay", 505L, 32, 9, 5, TeamStyle.COUNTER);
        LiveSession session = new LiveSession(scenario.context(), scenario.seed());

        tickUntil(session, scenario.subMinute() - 1);
        session.recordManualSubstitution(scenario.event());
        session.mutateContext(ctx -> ctx.withNewStyle(ctx.homeTeamId(), scenario.style()));
        tickUntil(session, 55);

        assertIsolated(scenario, session, null);
        assertThat(session.accumulatedEvents().stream()
                .filter(event -> event.type() == DetailedMatchEventType.SUBSTITUTION)
                .filter(event -> scenario.onPlayerId().equals(event.relatedPlayerId()))
                .toList())
                .as("accumulated events must expose one logical substitution after replay")
                .hasSize(1);
    }

    @Test
    @Timeout(20)
    @DisplayName("observable composition lifetime is isolated per LiveSession replay")
    void observableCompositionLifetimeIsolatedPerLiveSessionReplay() throws Exception {
        Scenario first = scenario("lifetime-a", 606L, 14, 8, 4, TeamStyle.BALANCED);
        Scenario second = scenario("lifetime-b", 606L, 14, 10, 6, TeamStyle.BALANCED);

        List<SessionSignature> sequential = List.of(runSequential(first, 90), runSequential(second, 90));
        List<SessionSignature> parallel = runParallel(List.of(first, second), 90);

        assertThat(parallel).containsExactlyElementsOf(sequential);
        assertThat(parallel.get(0).substitutionKey()).isNotEqualTo(parallel.get(1).substitutionKey());
        assertThat(parallel.get(0).timelineHash()).isNotEqualTo(parallel.get(1).timelineHash());
    }

    private List<SessionSignature> runParallel(List<Scenario> scenarios, int untilMinute) throws Exception {
        return withPool(scenarios.size(), pool -> {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<SessionSignature>> futures = new ArrayList<>();
            for (Scenario scenario : scenarios) {
                futures.add(pool.submit(() -> {
                    await(start);
                    return runSequential(scenario, untilMinute);
                }));
            }
            start.countDown();
            List<SessionSignature> signatures = new ArrayList<>();
            for (Future<SessionSignature> future : futures) {
                signatures.add(future.get(20, TimeUnit.SECONDS));
            }
            return signatures;
        });
    }

    private List<ReplaySignature> runParallelReplay(List<Scenario> scenarios) throws Exception {
        return withPool(scenarios.size(), pool -> {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ReplaySignature>> futures = new ArrayList<>();
            for (Scenario scenario : scenarios) {
                futures.add(pool.submit(() -> {
                    await(start);
                    return runSequentialReplay(scenario);
                }));
            }
            start.countDown();
            List<ReplaySignature> signatures = new ArrayList<>();
            for (Future<ReplaySignature> future : futures) {
                signatures.add(future.get(20, TimeUnit.SECONDS));
            }
            return signatures;
        });
    }

    private <T> T withPool(int size, PoolWork<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(size);
        try {
            return work.run(pool);
        } finally {
            List<Runnable> cancelled = pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS))
                    .as("executor must terminate; cancelled tasks=" + cancelled.size())
                    .isTrue();
        }
    }

    private SessionSignature runSequential(Scenario scenario, int untilMinute) {
        LiveSession session = new LiveSession(scenario.context(), scenario.seed());
        tickUntil(session, Math.max(1, scenario.subMinute() - 2));
        session.recordManualSubstitution(scenario.event());
        tickUntil(session, untilMinute);
        assertIsolated(scenario, session, null);
        return signature(scenario, session);
    }

    private ReplaySignature runSequentialReplay(Scenario scenario) {
        LiveSession session = new LiveSession(scenario.context(), scenario.seed());
        tickUntil(session, Math.max(2, scenario.subMinute() - 1));
        String prefixBeforeReplay = eventHash(session.snapshot().allEvents());

        session.recordManualSubstitution(scenario.event());
        String immediateHash = eventHash(session.snapshot().allEvents());
        session.mutateContext(ctx -> ctx.withNewStyle(ctx.homeTeamId(), scenario.style()));
        tickUntil(session, Math.min(90, scenario.subMinute() + 10));

        LiveSnapshot snapshot = session.snapshot();
        assertThat(eventHash(snapshot.allEvents())).isNotEqualTo(prefixBeforeReplay);
        assertIsolated(scenario, session, null);
        return new ReplaySignature(
                scenario.id(),
                scenario.seed(),
                prefixBeforeReplay,
                immediateHash,
                eventHash(snapshot.allEvents()),
                signature(scenario, session).substitutionKey());
    }

    private void assertIsolated(Scenario owner, LiveSession ownerSession, Scenario other) {
        DetailedMatchResult result = ownerSession.finalResult();
        List<DetailedMatchEvent> subEvents = result.timeline().events().stream()
                .filter(event -> event.type() == DetailedMatchEventType.SUBSTITUTION)
                .filter(event -> owner.onPlayerId().equals(event.relatedPlayerId()))
                .toList();

        assertThat(subEvents)
                .as(owner.id() + " must apply its manual sub exactly once in engine timeline")
                .hasSize(1);
        DetailedMatchEvent applied = subEvents.get(0);
        assertThat(applied.minute()).isEqualTo(owner.subMinute());
        assertThat(applied.playerId()).isEqualTo(owner.offPlayerId());
        assertThat(applied.relatedPlayerId()).isEqualTo(owner.onPlayerId());

        LiveSnapshot snapshot = ownerSession.snapshot();
        List<DetailedMatchEvent> visibleSubEvents = snapshot.allEvents().stream()
                .filter(event -> event.type() == DetailedMatchEventType.SUBSTITUTION)
                .filter(event -> owner.onPlayerId().equals(event.relatedPlayerId()))
                .toList();
        assertThat(visibleSubEvents)
                .as(owner.id() + " must expose its manual sub exactly once in the live snapshot")
                .hasSize(1);
        assertThat(snapshot.matchId()).isEqualTo(owner.context().matchId());
        assertThat(snapshot.homeSlots().stream().map(FormationSlot::playerId))
                .contains(owner.onPlayerId())
                .doesNotContain(owner.offPlayerId());
        assertThat(ownerSession.context().manualSubstitutions())
                .containsExactly(new MatchContext.ScheduledSub(
                        owner.context().homeTeamId(),
                        owner.offPlayerId(),
                        owner.onPlayerId(),
                        owner.subMinute()));

        if (other != null) {
            assertThat(result.timeline().events().stream()
                    .noneMatch(event -> other.onPlayerId().equals(event.relatedPlayerId())
                            || other.offPlayerId().equals(event.playerId())))
                    .as(owner.id() + " must not contain " + other.id() + " substitution players")
                    .isTrue();
        }
    }

    private SessionSignature signature(Scenario scenario, LiveSession session) {
        DetailedMatchResult result = session.finalResult();
        LiveSnapshot snapshot = session.snapshot();
        List<DetailedMatchEvent> timeline = result.timeline().events();
        long cards = timeline.stream()
                .filter(event -> event.type() == DetailedMatchEventType.YELLOW_CARD
                        || event.type() == DetailedMatchEventType.RED_CARD)
                .count();
        long injuries = timeline.stream()
                .filter(event -> event.type() == DetailedMatchEventType.INJURY)
                .count();
        double ratingProxy = timeline.stream()
                .filter(event -> scenario.onPlayerId().equals(event.playerId())
                        || scenario.onPlayerId().equals(event.relatedPlayerId()))
                .mapToDouble(this::ratingImpact)
                .sum();
        String visibleLineup = snapshot.homeSlots().stream()
                .map(FormationSlot::playerId)
                .toList()
                .toString();
        return new SessionSignature(
                scenario.id(),
                scenario.seed(),
                snapshot.matchId(),
                snapshot.minute(),
                result.homeGoals(),
                result.awayGoals(),
                result.homeShots(),
                result.awayShots(),
                rounded(result.homeXg()),
                rounded(result.awayXg()),
                snapshot.homePossession(),
                snapshot.awayPossession(),
                cards,
                injuries,
                rounded(ratingProxy),
                scenario.offPlayerId() + "->" + scenario.onPlayerId() + "@" + scenario.subMinute(),
                visibleLineup,
                eventHash(timeline));
    }

    private double ratingImpact(DetailedMatchEvent event) {
        return switch (event.type()) {
            case GOAL -> 1.0;
            case SHOT_ON_TARGET -> 0.15;
            case CHANCE_CREATED -> 0.12;
            case SUBSTITUTION -> 0.05;
            case YELLOW_CARD -> -0.25;
            case RED_CARD -> -1.0;
            case INJURY -> -0.4;
            default -> 0.0;
        };
    }

    private void tickUntil(LiveSession session, int minute) {
        while (session.currentMinute() < minute && !session.isFinished()) {
            session.tick();
        }
    }

    private Scenario scenario(String id, long seed, int subMinute, int offIndex, int onIndex, TeamStyle style) {
        String homeTeamId = id + "-home";
        String awayTeamId = id + "-away";
        SessionTeam homeTeam = team(homeTeamId, id + " Home");
        SessionTeam awayTeam = team(awayTeamId, id + " Away");
        MatchContext context = new MatchContext(
                "match-" + id,
                homeTeamId,
                awayTeamId,
                homeTeam,
                awayTeam,
                players(homeTeamId, "starter", false),
                players(awayTeamId, "starter", false),
                players(homeTeamId, "bench", true),
                players(awayTeamId, "bench", true),
                "4-3-3",
                "4-4-2",
                style,
                TeamStyle.BALANCED);
        String offPlayerId = homeTeamId + "-starter-" + offIndex;
        String onPlayerId = homeTeamId + "-bench-" + onIndex;
        DetailedMatchEvent event = new DetailedMatchEvent(
                subMinute,
                DetailedMatchEventType.SUBSTITUTION,
                homeTeamId,
                offPlayerId,
                "Off " + offIndex,
                onPlayerId,
                "On " + onIndex,
                0.0,
                "Substitution: On " + onIndex + " on for Off " + offIndex);
        return new Scenario(id, seed, subMinute, offPlayerId, onPlayerId, style, context, event);
    }

    private SessionTeam team(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)),
                "world-" + id,
                name,
                "Testland",
                BigDecimal.ZERO,
                "4-3-3",
                null);
    }

    private List<SessionPlayer> players(String teamId, String group, boolean bench) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String position = position(i, bench);
            int base = bench ? 82 : 72;
            SessionPlayer player = SessionPlayer.custom(
                    teamId + "-" + group + "-" + i,
                    25,
                    position,
                    base + (i % 5),
                    base - 2 + (i % 4),
                    base + (i % 3),
                    base + (i % 6),
                    90 - (i % 7),
                    base - 1 + (i % 5),
                    BigDecimal.valueOf(70_000L + i));
            player.setSessionPlayerId(teamId + "-" + group + "-" + i);
            player.setEnergy(100);
            players.add(player);
        }
        return players;
    }

    private String position(int index, boolean bench) {
        if (index == 0) {
            return "GK";
        }
        if (bench) {
            return index <= 3 ? "DEF" : "WINGER";
        }
        if (index <= 4) {
            return "DEF";
        }
        if (index <= 7) {
            return "MID";
        }
        if (index <= 9) {
            return "WINGER";
        }
        return "ATT";
    }

    private static void await(CountDownLatch latch) throws InterruptedException, TimeoutException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException("parallel start latch timed out");
        }
    }

    private static String rounded(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String eventHash(List<DetailedMatchEvent> events) {
        String normalized = events.stream()
                .map(event -> event.minute()
                        + ":" + event.type()
                        + ":" + event.teamId()
                        + ":" + event.playerId()
                        + ":" + event.relatedPlayerId()
                        + ":" + rounded(event.xg()))
                .toList()
                .toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Scenario(
            String id,
            long seed,
            int subMinute,
            String offPlayerId,
            String onPlayerId,
            TeamStyle style,
            MatchContext context,
            DetailedMatchEvent event) {
    }

    private record SessionSignature(
            String scenarioId,
            long seed,
            String matchId,
            int minute,
            int homeGoals,
            int awayGoals,
            int homeShots,
            int awayShots,
            String homeXg,
            String awayXg,
            int homePossession,
            int awayPossession,
            long cards,
            long injuries,
            String ratingProxy,
            String substitutionKey,
            String visibleLineup,
            String timelineHash) {
    }

    private record ReplaySignature(
            String scenarioId,
            long seed,
            String prefixHash,
            String immediateReplayHash,
            String postReplayHash,
            String substitutionKey) {
    }

    @FunctionalInterface
    private interface PoolWork<T> {
        T run(ExecutorService pool) throws Exception;
    }
}

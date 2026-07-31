package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveSessionSameInstanceConcurrencyTest {

    @Test
    @Timeout(20)
    @DisplayName("same LiveSession supports concurrent tick and snapshot readers")
    void sameInstanceTickAndSnapshotReadersAreCoherent() throws Exception {
        LiveSession session = new LiveSession(context("tick-snapshot"), 930L);

        SameInstanceObservation observation = runSameInstanceStress(session, List.of(
                tickingWriter(session, 90),
                snapshotReader(session, 160),
                snapshotReader(session, 160),
                scalarReader(session, 160)));

        assertSameInstanceInvariants(session, observation);
    }

    @Test
    @Timeout(20)
    @DisplayName("same LiveSession supports concurrent tick and accumulatedEvents readers")
    void sameInstanceTickAndAccumulatedEventsReadersAreSafe() throws Exception {
        LiveSession session = new LiveSession(context("tick-events"), 931L);

        SameInstanceObservation observation = runSameInstanceStress(session, List.of(
                tickingWriter(session, 90),
                accumulatedEventsReader(session, 160),
                accumulatedEventsReader(session, 160),
                snapshotReader(session, 160)));

        assertSameInstanceInvariants(session, observation);
        assertThatThrownBy(() -> session.accumulatedEvents().add(manualSub(context("unused"), 1, 8, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @RepeatedTest(value = 4, name = "same-instance replay stress {currentRepetition}/{totalRepetitions}")
    @Timeout(25)
    @DisplayName("same LiveSession supports replay and snapshot/event readers without partial state")
    void sameInstanceReplayAndReadersAreCoherent() throws Exception {
        MatchContext context = context("replay-readers");
        LiveSession session = new LiveSession(context, 932L);
        tickUntil(session, 12);
        session.recordManualSubstitution(manualSub(context, 12, 8, 0));

        SameInstanceObservation observation = runSameInstanceStress(session, List.of(
                replayWriter(session, context, 12),
                snapshotReader(session, 120),
                accumulatedEventsReader(session, 120),
                scalarReader(session, 120)));

        assertSameInstanceInvariants(session, observation);
        assertManualSubstitutionVisibleOnce(session, "replay-readers-home-bench-0");
    }

    @Test
    @Timeout(20)
    @DisplayName("same LiveSession finalResult is idempotent under concurrent callers")
    void sameInstanceFinalResultIsIdempotentUnderConcurrentCallers() throws Exception {
        LiveSession session = new LiveSession(context("final-result"), 933L);
        tickUntil(session, 30);

        List<String> hashes = runConcurrent(10, start -> () -> {
            await(start);
            DetailedMatchResult result = session.finalResult();
            return resultHash(result);
        });

        assertThat(hashes).hasSize(10).containsOnly(hashes.getFirst());
        assertThat(session.isFinished()).isTrue();
        assertThat(session.currentMinute()).isEqualTo(90);
        assertThat(session.finalResult()).isSameAs(session.finalResult());
        assertSameInstanceInvariants(session, SameInstanceObservation.empty());
    }

    @Test
    @Timeout(20)
    @DisplayName("same LiveSession tick and finalResult callers converge to one final state")
    void sameInstanceTickAndFinalResultConvergeToOneFinalState() throws Exception {
        LiveSession session = new LiveSession(context("tick-final"), 934L);

        List<String> hashes = runConcurrent(6, start -> () -> {
            await(start);
            if (Thread.currentThread().getName().endsWith("1")) {
                for (int i = 0; i < 90; i++) {
                    session.tick();
                }
            }
            return resultHash(session.finalResult());
        });

        assertThat(hashes).containsOnly(hashes.getFirst());
        assertThat(session.isFinished()).isTrue();
        assertThat(session.currentMinute()).isEqualTo(90);
        assertSameInstanceInvariants(session, SameInstanceObservation.empty());
    }

    @Test
    @Timeout(25)
    @DisplayName("same seed and same mutation across concurrent LiveSessions match sequential baseline")
    void sameSeedSameMutationConcurrentSessionsMatchSequentialBaseline() throws Exception {
        MatchContext baselineContext = context("same-seed-same-mutation");
        SessionSignature expected = runSameMutationBaseline(baselineContext);

        List<SessionSignature> signatures = runConcurrent(8, start -> () -> {
            await(start);
            return runSameMutationBaseline(context("same-seed-same-mutation"));
        });

        assertThat(signatures).hasSize(8).containsOnly(expected);
    }

    private static SameInstanceObservation runSameInstanceStress(
            LiveSession session,
            List<Callable<SameInstanceObservation>> workers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SameInstanceObservation>> futures = new ArrayList<>();
            for (Callable<SameInstanceObservation> worker : workers) {
                futures.add(pool.submit(() -> {
                    await(start);
                    return worker.call();
                }));
            }
            start.countDown();

            SameInstanceObservation merged = SameInstanceObservation.empty();
            for (Future<SameInstanceObservation> future : futures) {
                merged = merged.merge(future.get(15, TimeUnit.SECONDS));
            }
            return merged;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static <T> List<T> runConcurrent(int workers, WorkerFactory<T> factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(factory.create(start)));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Callable<SameInstanceObservation> tickingWriter(LiveSession session, int ticks) {
        return () -> {
            SameInstanceObservation observation = SameInstanceObservation.empty();
            for (int i = 0; i < ticks; i++) {
                LiveSnapshot snapshot = session.tick();
                observation = observation.withSnapshot(snapshot);
            }
            return observation;
        };
    }

    private static Callable<SameInstanceObservation> replayWriter(
            LiveSession session,
            MatchContext context,
            int replayMinute) {
        return () -> {
            SameInstanceObservation observation = SameInstanceObservation.empty();
            TeamStyle[] styles = {
                    TeamStyle.ATTACKING,
                    TeamStyle.COUNTER,
                    TeamStyle.POSSESSION,
                    TeamStyle.BALANCED
            };
            for (TeamStyle style : styles) {
                session.mutateContext(ctx -> ctx.withNewStyle(context.homeTeamId(), style));
                observation = observation.withSnapshot(session.snapshot());
                if (session.currentMinute() >= replayMinute) {
                    session.replayFromMinute(replayMinute);
                }
            }
            return observation;
        };
    }

    private static Callable<SameInstanceObservation> snapshotReader(LiveSession session, int reads) {
        return () -> {
            SameInstanceObservation observation = SameInstanceObservation.empty();
            for (int i = 0; i < reads; i++) {
                observation = observation.withSnapshot(session.snapshot());
            }
            return observation;
        };
    }

    private static Callable<SameInstanceObservation> accumulatedEventsReader(LiveSession session, int reads) {
        return () -> {
            SameInstanceObservation observation = SameInstanceObservation.empty();
            for (int i = 0; i < reads; i++) {
                List<DetailedMatchEvent> events = session.accumulatedEvents();
                observation = observation.withEventCount(events.size());
                assertThatThrownBy(() -> events.add(manualSub(session.context(), 1, 8, 0)))
                        .isInstanceOf(UnsupportedOperationException.class);
            }
            return observation;
        };
    }

    private static Callable<SameInstanceObservation> scalarReader(LiveSession session, int reads) {
        return () -> {
            SameInstanceObservation observation = SameInstanceObservation.empty();
            int previousMinute = -1;
            for (int i = 0; i < reads; i++) {
                int minute = session.currentMinute();
                assertThat(minute).isBetween(previousMinute, 90);
                previousMinute = minute;
                assertThat(session.context().matchId()).isNotBlank();
                observation = observation.withMinute(minute);
                session.isFinished();
            }
            return observation;
        };
    }

    private static SessionSignature runSameMutationBaseline(MatchContext context) {
        LiveSession session = new LiveSession(context, 935L);
        tickUntil(session, 15);
        session.recordManualSubstitution(manualSub(context, 15, 8, 0));
        session.mutateContext(ctx -> ctx.withNewStyle(ctx.homeTeamId(), TeamStyle.ATTACKING));
        DetailedMatchResult result = session.finalResult();
        LiveSnapshot snapshot = session.snapshot();
        return new SessionSignature(
                result.homeGoals(),
                result.awayGoals(),
                eventHash(result.timeline().events()),
                eventHash(snapshot.allEvents()),
                activeLineupKey(snapshot.homeSlots()));
    }

    private static void assertSameInstanceInvariants(
            LiveSession session,
            SameInstanceObservation observation) {
        assertThat(session.currentMinute()).isBetween(0, 90);
        if (session.isFinished()) {
            assertThat(session.currentMinute()).isEqualTo(90);
        }

        List<DetailedMatchEvent> accumulated = session.accumulatedEvents();
        assertThat(accumulated.stream().map(LiveSessionSameInstanceConcurrencyTest::logicalEventKey))
                .doesNotHaveDuplicates();

        LiveSnapshot snapshot = session.snapshot();
        assertThat(snapshot.minute()).isBetween(0, 90);
        assertThat(snapshot.homeGoals()).isBetween(0, 20);
        assertThat(snapshot.awayGoals()).isBetween(0, 20);
        assertThat(snapshot.allEvents().stream().map(LiveSessionSameInstanceConcurrencyTest::logicalEventKey))
                .doesNotHaveDuplicates();

        assertThat(observation.snapshotMinutes()).allSatisfy(minute -> assertThat(minute).isBetween(0, 90));
        assertThat(observation.eventCounts()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(0));
    }

    private static void assertManualSubstitutionVisibleOnce(LiveSession session, String playerOnId) {
        assertThat(session.snapshot().allEvents().stream()
                .filter(event -> event.type() == DetailedMatchEventType.SUBSTITUTION)
                .filter(event -> playerOnId.equals(event.relatedPlayerId()))
                .toList())
                .hasSize(1);
        assertThat(session.accumulatedEvents().stream()
                .filter(event -> event.type() == DetailedMatchEventType.SUBSTITUTION)
                .filter(event -> playerOnId.equals(event.relatedPlayerId()))
                .toList())
                .hasSize(1);
    }

    private static void tickUntil(LiveSession session, int minute) {
        while (session.currentMinute() < minute && !session.isFinished()) {
            session.tick();
        }
    }

    private static String activeLineupKey(List<com.footballmanager.domain.model.valueobject.FormationSlot> slots) {
        return slots.stream()
                .map(slot -> slot.playerId() + "@" + slot.slotIndex())
                .sorted()
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private static String resultHash(DetailedMatchResult result) {
        return result.homeGoals()
                + "-" + result.awayGoals()
                + "|" + eventHash(result.timeline().events());
    }

    private static String eventHash(List<DetailedMatchEvent> events) {
        String payload = events.stream()
                .map(LiveSessionSameInstanceConcurrencyTest::logicalEventKey)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String logicalEventKey(DetailedMatchEvent event) {
        return event.minute()
                + "|" + event.type()
                + "|" + event.teamId()
                + "|" + event.playerId()
                + "|" + event.relatedPlayerId()
                + "|" + event.xg();
    }

    private static DetailedMatchEvent manualSub(MatchContext context, int minute, int offIndex, int benchIndex) {
        SessionPlayer off = context.homeStartingPlayers().get(offIndex);
        SessionPlayer on = context.homeBenchPlayers().get(benchIndex);
        return new DetailedMatchEvent(
                minute,
                DetailedMatchEventType.SUBSTITUTION,
                context.homeTeamId(),
                off.getSessionPlayerId(),
                off.getName(),
                on.getSessionPlayerId(),
                on.getName(),
                0.0,
                "Substitution: " + on.getName() + " on for " + off.getName());
    }

    private static MatchContext context(String prefix) {
        SessionTeam homeTeam = team(prefix + "-home", prefix + " Home");
        SessionTeam awayTeam = team(prefix + "-away", prefix + " Away");
        return new MatchContext(
                "match-" + prefix,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam,
                awayTeam,
                players(prefix + "-home", "starter", 11),
                players(prefix + "-away", "starter", 11),
                players(prefix + "-home", "bench", 7),
                players(prefix + "-away", "bench", 7),
                "4-4-2",
                "4-4-2",
                TeamStyle.BALANCED,
                TeamStyle.BALANCED);
    }

    private static SessionTeam team(String id, String name) {
        SessionTeam team = SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)),
                "world-" + id,
                name,
                "Country",
                BigDecimal.ZERO,
                "4-4-2",
                null);
        team.setSessionTeamId("session-" + id);
        return team;
    }

    private static List<SessionPlayer> players(String prefix, String group, int count) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "-" + group + "-" + i;
            String position = i == 0 ? "GK" : i < 5 ? "DEF" : i < 9 ? "MID" : "ATT";
            players.add(SessionPlayer.fromWorldPlayer(id, id, position, 25, 75));
        }
        return players;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private record SameInstanceObservation(
            List<Integer> snapshotMinutes,
            List<Integer> eventCounts) {
        static SameInstanceObservation empty() {
            return new SameInstanceObservation(new ArrayList<>(), new ArrayList<>());
        }

        SameInstanceObservation withSnapshot(LiveSnapshot snapshot) {
            return withMinute(snapshot.minute()).withEventCount(snapshot.allEvents().size());
        }

        SameInstanceObservation withMinute(int minute) {
            List<Integer> nextMinutes = new ArrayList<>(snapshotMinutes);
            nextMinutes.add(minute);
            return new SameInstanceObservation(nextMinutes, eventCounts);
        }

        SameInstanceObservation withEventCount(int count) {
            List<Integer> nextCounts = new ArrayList<>(eventCounts);
            nextCounts.add(count);
            return new SameInstanceObservation(snapshotMinutes, nextCounts);
        }

        SameInstanceObservation merge(SameInstanceObservation other) {
            List<Integer> nextMinutes = new ArrayList<>(snapshotMinutes);
            nextMinutes.addAll(other.snapshotMinutes);
            List<Integer> nextCounts = new ArrayList<>(eventCounts);
            nextCounts.addAll(other.eventCounts);
            return new SameInstanceObservation(nextMinutes, nextCounts);
        }
    }

    private record SessionSignature(
            int homeGoals,
            int awayGoals,
            String finalTimelineHash,
            String snapshotHash,
            String activeLineupKey) {
    }

    @FunctionalInterface
    private interface WorkerFactory<T> {
        Callable<T> create(CountDownLatch start);
    }
}

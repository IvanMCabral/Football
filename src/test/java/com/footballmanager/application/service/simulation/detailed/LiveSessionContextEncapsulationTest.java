package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveSessionContextEncapsulationTest {

    @Test
    @DisplayName("contextView exposes immutable value collections only")
    void contextViewExposesImmutableValueCollectionsOnly() {
        LiveSession session = new LiveSession(context("immutable-view"), 4101L);

        LiveSessionContextView view = session.contextView();

        assertThatThrownBy(() -> view.homeStartingPlayers().add(view.homeBenchPlayers().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.homeBenchPlayers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.homeTeam().slotsByPlayerId().put(
                "x", new LineupSlot("x", "LIVE-1", 50.0, 50.0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.manualSubstitutions().add(
                new LiveSessionContextView.ScheduledSubstitutionView("team", "off", "on", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("legacy package context returns a defensive copy that cannot mutate the session")
    void contextReturnsDefensiveCopy() {
        LiveSession session = new LiveSession(context("defensive-copy"), 4102L);
        String originalName = session.contextView().homeStartingPlayers().getFirst().name();
        String originalFormation = session.contextView().homeFormation();

        MatchContext copy = session.context();
        copy.homeStartingPlayers().getFirst().setName("External Mutation");
        copy.homeStartingPlayers().getFirst().setStamina(1);
        copy.homeTeam().setFormation("5-4-1");

        LiveSessionContextView after = session.contextView();
        assertThat(after.homeStartingPlayers().getFirst().name()).isEqualTo(originalName);
        assertThat(after.homeStartingPlayers().getFirst().stamina()).isEqualTo(75);
        assertThat(after.homeFormation()).isEqualTo(originalFormation);
        assertThat(session.finalResult()).isSameAs(session.finalResult());
    }

    @Test
    @DisplayName("typed formation mutation replaces internal context under the session monitor")
    void typedFormationMutationOwnsInternalGraph() {
        LiveSession session = new LiveSession(context("typed-formation"), 4103L);
        String teamId = session.contextView().homeTeamId();
        String playerId = session.contextView().homeStartingPlayers().get(5).sessionPlayerId();

        session.changeFormation(
                teamId,
                "4-4-2",
                Map.of(playerId, "ATT"),
                Map.of(playerId, new LineupSlot(playerId, "LIVE-5", 51.5, 30.0)));

        LiveSessionContextView view = session.contextView();
        assertThat(view.homeFormation()).isEqualTo("4-4-2");
        assertThat(view.findPlayer(teamId, playerId).position()).isEqualTo("ATT");
        assertThat(view.homeTeam().slotsByPlayerId().get(playerId).customXPercent()).isEqualTo(51.5);
    }

    @Test
    @Timeout(20)
    @DisplayName("contextView readers remain safe while same instance ticks, replays and finalizes")
    void contextViewConcurrentReadersAreSafe() throws Exception {
        LiveSession session = new LiveSession(context("concurrent-view"), 4104L);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> {
                        await(start);
                        for (int i = 0; i < 30; i++) {
                            session.tick();
                        }
                        return session.contextView().homeFormation();
                    },
                    () -> {
                        await(start);
                        for (int i = 0; i < 80; i++) {
                            assertThat(session.contextView().homeStartingPlayers()).hasSize(11);
                        }
                        return session.contextView().homeTeamId();
                    },
                    () -> {
                        await(start);
                        String teamId = session.contextView().homeTeamId();
                        try {
                            session.changeTeamStyle(teamId, TeamStyle.ATTACKING);
                            session.replayCurrentMinute();
                        } catch (IllegalStateException finishedDuringMutation) {
                            assertThat(finishedDuringMutation.getMessage()).contains("finished");
                        }
                        return session.contextView().homeStyle().name();
                    },
                    () -> {
                        await(start);
                        session.finalResult();
                        return String.valueOf(session.currentMinute());
                    },
                    () -> {
                        await(start);
                        for (int i = 0; i < 80; i++) {
                            LiveSessionContextView view = session.contextView();
                            assertThatThrownBy(() -> view.homeStartingPlayers().clear())
                                    .isInstanceOf(UnsupportedOperationException.class);
                        }
                        return "immutable";
                    });
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isNotBlank();
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
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
                "4-3-3",
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
                "4-3-3",
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
}

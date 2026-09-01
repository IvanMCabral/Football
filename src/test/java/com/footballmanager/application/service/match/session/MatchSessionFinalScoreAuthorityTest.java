package com.footballmanager.application.service.match.session;

import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.LiveSnapshot;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchTimeline;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSessionFinalScoreAuthorityTest {

    @Test
    void h8287Shape_staleEventDerivedSnapshotCannotOverrideFinalDetailedScore() {
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000008701");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-000000008702");
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-000000008703");
        MatchSession session = session(matchId, homeId, awayId);

        // Reproduction shape: a final SSE projection reconstructed only two
        // home goals, although the finalized detailed engine result is 3-1.
        List<DetailedMatchEvent> staleEvents = List.of(goal(17, homeId), goal(44, homeId), goal(66, awayId));
        MatchStateSnapshot staleSnapshot = session.adaptDetailedSnapshot(new LiveSnapshot(
                matchId.toString(), 90, 2, 1, homeId.toString(), awayId.toString(), true,
                staleEvents, 52, 48, "BALANCED", "BALANCED", "4-4-2", "4-4-2"));
        assertEquals(2, staleSnapshot.score().home(), "candidate snapshot reproduces the old 2-1 projection");
        assertEquals(1, staleSnapshot.score().away());

        MatchTimeline finalTimeline = new MatchTimeline();
        finalTimeline.addEvent(goal(17, homeId));
        finalTimeline.addEvent(goal(44, homeId));
        finalTimeline.addEvent(goal(73, homeId));
        finalTimeline.addEvent(goal(66, awayId));
        DetailedMatchResult finalResult = DetailedMatchResult.builder()
                .matchId(matchId.toString())
                .homeTeamId(homeId.toString())
                .awayTeamId(awayId.toString())
                .homeGoals(3).awayGoals(1)
                .homePossession(52).awayPossession(48)
                .timeline(finalTimeline)
                .build();

        forceFinishedLiveSession(session.getLiveSession(), finalResult, staleEvents);
        AtomicReference<com.footballmanager.domain.model.entity.MatchFinishedResult> completed = new AtomicReference<>();
        session.setOnFinishCallback(completed::set);
        MatchStateSnapshot authoritative = session.advanceTick();

        assertEquals(3, authoritative.score().home());
        assertEquals(1, authoritative.score().away());
        assertEquals(3, completed.get().snapshot().score().home(),
                "fixture/summary/standings callback must receive the same final score as detail");
        assertEquals(3, ((DetailedMatchResult) completed.get().detailedResult()).homeGoals());
        assertEquals(3, authoritative.events().stream()
                .filter(event -> event.getEventType().name().equals("GOAL"))
                .filter(event -> homeId.toString().equals(event.getTeamId()))
                .count());
        assertEquals(1, authoritative.events().stream()
                .filter(event -> event.getEventType().name().equals("GOAL"))
                .filter(event -> awayId.toString().equals(event.getTeamId()))
                .count());
    }

    @Test
    void connectedSubscriber_observesOneCanonicalFinishedSnapshotAfterStaleLiveScore() {
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000008711");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-000000008712");
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-000000008713");
        MatchSession session = session(matchId, homeId, awayId);
        List<MatchStateSnapshot> observed = new ArrayList<>();
        session.getStateStream().subscribe(observed::add);

        List<DetailedMatchEvent> staleEvents = List.of(goal(17, homeId), goal(44, homeId), goal(66, awayId));
        MatchStateSnapshot staleLive = session.adaptDetailedSnapshot(new LiveSnapshot(
                matchId.toString(), 90, 2, 1, homeId.toString(), awayId.toString(), false,
                staleEvents, 52, 48, "BALANCED", "BALANCED", "4-4-2", "4-4-2"));
        setFieldUnchecked(session, "currentState", staleLive);
        session.start();

        DetailedMatchResult finalResult = result(matchId, homeId, awayId, 3, 1);
        forceFinishedLiveSession(session.getLiveSession(), finalResult, staleEvents);
        session.advanceTick();

        List<MatchStateSnapshot> terminals = observed.stream()
                .filter(snapshot -> snapshot.status() == MatchStatus.FINISHED)
                .toList();
        assertEquals(List.of(MatchStatus.RUNNING, MatchStatus.FINISHED),
                observed.stream().map(MatchStateSnapshot::status).toList());
        assertEquals(1, terminals.size(), "a connected client receives exactly one terminal SSE snapshot");
        assertEquals(3, terminals.getFirst().score().home());
        assertEquals(1, terminals.getFirst().score().away());
        assertTrue(observed.stream().noneMatch(snapshot -> snapshot.status() == MatchStatus.FINISHED
                        && snapshot.score().home() == 2 && snapshot.score().away() == 1),
                "a stale 2-1 may be live, never terminal");
    }

    @Test
    void lateAndMultipleSubscribers_receiveOnlyTheCanonicalRetainedTerminalSnapshot() {
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000008721");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-000000008722");
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-000000008723");
        MatchSession session = session(matchId, homeId, awayId);
        List<MatchStateSnapshot> firstConnected = new ArrayList<>();
        List<MatchStateSnapshot> secondConnected = new ArrayList<>();
        session.getStateStream().subscribe(firstConnected::add);
        session.getStateStream().subscribe(secondConnected::add);

        DetailedMatchResult finalResult = result(matchId, homeId, awayId, 3, 1);
        forceFinishedLiveSession(session.getLiveSession(), finalResult,
                List.of(goal(17, homeId), goal(44, homeId), goal(66, awayId)));
        session.advanceTick();

        List<MatchStateSnapshot> lateSubscriber = new ArrayList<>();
        session.getStateStream().subscribe(lateSubscriber::add);
        assertCanonicalTerminal(firstConnected);
        assertCanonicalTerminal(secondConnected);
        assertEquals(1, lateSubscriber.size(), "replay().latest() retains only the canonical terminal snapshot");
        assertCanonicalTerminal(lateSubscriber);
    }

    @Test
    void doubleFinalize_emitsAndCallsBackExactlyOnce() {
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000008731");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-000000008732");
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-000000008733");
        MatchSession session = session(matchId, homeId, awayId);
        List<MatchStateSnapshot> observed = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        session.getStateStream().subscribe(observed::add);
        session.setOnFinishCallback(ignored -> callbacks.incrementAndGet());

        DetailedMatchResult finalResult = result(matchId, homeId, awayId, 1, 1);
        forceFinishedLiveSession(session.getLiveSession(), finalResult,
                List.of(goal(17, homeId), goal(66, awayId)));
        session.advanceTick();
        session.advanceTick();
        session.refreshDetailedSnapshot();

        assertEquals(1, observed.stream().filter(snapshot -> snapshot.status() == MatchStatus.FINISHED).count());
        assertEquals(1, callbacks.get());
        assertEquals(1, session.getCurrentState().score().home());
        assertEquals(1, session.getCurrentState().score().away());
    }

    @Test
    void terminalScoreVariants_keepTheCanonicalScoreForScorelessDrawHomeAndAwayResults() {
        assertTerminalScoreVariant("8741", 0, 0);
        assertTerminalScoreVariant("8751", 1, 1);
        assertTerminalScoreVariant("8761", 2, 0);
        assertTerminalScoreVariant("8771", 0, 2);
    }

    @Test
    void backgroundFinalization_completesCallbackOnceWithoutAnSseSubscriber() {
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000008781");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-000000008782");
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-000000008783");
        MatchSession session = session(matchId, homeId, awayId);
        AtomicInteger callbacks = new AtomicInteger();
        session.setOnFinishCallback(ignored -> callbacks.incrementAndGet());
        DetailedMatchResult finalResult = result(matchId, homeId, awayId, 2, 0);
        forceFinishedLiveSession(session.getLiveSession(), finalResult, finalResult.timeline().events());

        session.advanceTick();
        session.advanceTick();

        assertEquals(MatchStatus.FINISHED, session.getCurrentState().status());
        assertEquals(2, session.getCurrentState().score().home());
        assertEquals(0, session.getCurrentState().score().away());
        assertEquals(1, callbacks.get());
    }

    private static void assertCanonicalTerminal(List<MatchStateSnapshot> observed) {
        List<MatchStateSnapshot> terminals = observed.stream()
                .filter(snapshot -> snapshot.status() == MatchStatus.FINISHED)
                .toList();
        assertEquals(1, terminals.size());
        assertEquals(3, terminals.getFirst().score().home());
        assertEquals(1, terminals.getFirst().score().away());
    }

    private static DetailedMatchResult result(UUID matchId, UUID homeId, UUID awayId, int homeGoals, int awayGoals) {
        MatchTimeline timeline = new MatchTimeline();
        for (int goal = 0; goal < homeGoals; goal++) {
            timeline.addEvent(goal(10 + goal, homeId));
        }
        for (int goal = 0; goal < awayGoals; goal++) {
            timeline.addEvent(goal(60 + goal, awayId));
        }
        return DetailedMatchResult.builder()
                .matchId(matchId.toString())
                .homeTeamId(homeId.toString())
                .awayTeamId(awayId.toString())
                .homeGoals(homeGoals).awayGoals(awayGoals)
                .homePossession(52).awayPossession(48)
                .timeline(timeline)
                .build();
    }

    private void assertTerminalScoreVariant(String suffix, int homeGoals, int awayGoals) {
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-00000000" + suffix);
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-00000000" + (Integer.parseInt(suffix) + 1));
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-00000000" + (Integer.parseInt(suffix) + 2));
        MatchSession session = session(matchId, homeId, awayId);
        List<MatchStateSnapshot> observed = new ArrayList<>();
        session.getStateStream().subscribe(observed::add);
        DetailedMatchResult finalResult = result(matchId, homeId, awayId, homeGoals, awayGoals);
        forceFinishedLiveSession(session.getLiveSession(), finalResult, finalResult.timeline().events());

        session.advanceTick();

        assertEquals(1, observed.size());
        assertEquals(MatchStatus.FINISHED, observed.getFirst().status());
        assertEquals(homeGoals, observed.getFirst().score().home());
        assertEquals(awayGoals, observed.getFirst().score().away());
    }

    private static void forceFinishedLiveSession(
            LiveSession liveSession,
            DetailedMatchResult finalResult,
            List<DetailedMatchEvent> staleEvents) {
        try {
            setField(liveSession, "cachedResult", finalResult);
            setField(liveSession, "cachedResultFinal", true);
            setField(liveSession, "finished", true);
            setField(liveSession, "currentMinute", 90);
            @SuppressWarnings("unchecked")
            List<DetailedMatchEvent> engineTimeline =
                    (List<DetailedMatchEvent>) field(liveSession, "engineTimeline").get(liveSession);
            engineTimeline.clear();
            engineTimeline.addAll(staleEvents);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to construct isolated H8.2.87 reproduction", exception);
        }
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = field(target, name);
        field.set(target, value);
    }

    private static void setFieldUnchecked(Object target, String name, Object value) {
        try {
            setField(target, name, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to prepare isolated H8.2.87F2 state", exception);
        }
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private MatchSession session(UUID matchId, UUID homeId, UUID awayId) {
        SessionTeam home = SessionTeam.custom(homeId.toString(), "Synthetic Sevilla", "ES",
                BigDecimal.ONE, "4-4-2");
        SessionTeam away = SessionTeam.custom(awayId.toString(), "Synthetic Oviedo", "ES",
                BigDecimal.ONE, "4-4-2");
        MatchContext context = new MatchContext(matchId.toString(), homeId.toString(), awayId.toString(),
                home, away, roster("home"), roster("away"), List.of(), List.of(),
                "4-4-2", "4-4-2", TeamStyle.BALANCED, TeamStyle.BALANCED);
        MatchState state = new MatchState(matchId);
        state.setHomeTeamId(homeId);
        state.setAwayTeamId(awayId);
        state.setCareerId("career-h8287");
        state.setUserId("user-h8287");
        return new MatchSession(UUID.fromString("00000000-0000-0000-0000-000000008704"),
                matchId, state, new MatchTickHandler(), new LiveSession(context, 87L));
    }

    private List<SessionPlayer> roster(String prefix) {
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> SessionPlayer.fromWorldPlayer(prefix + "-" + index,
                        prefix + " player " + index, "MID", 25, 75))
                .toList();
    }

    private static DetailedMatchEvent goal(int minute, UUID teamId) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.GOAL, teamId.toString(),
                "player-" + minute, "Synthetic scorer", null, null, 0.25, "Synthetic goal");
    }
}

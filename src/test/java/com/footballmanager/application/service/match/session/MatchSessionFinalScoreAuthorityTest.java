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
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

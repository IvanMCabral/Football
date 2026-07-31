package com.footballmanager.application.service.match;

import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.LiveSessionContextView;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TacticalChangeServiceTest {

    private MatchSessionRegistry registry;
    private LiveSession liveSession;
    private MatchSession session;
    private TacticalChangeService service;
    private UUID userId;
    private UUID matchId;
    private MatchContext context;

    @BeforeEach
    void setUp() {
        registry = mock(MatchSessionRegistry.class);
        liveSession = mock(LiveSession.class);
        session = mock(MatchSession.class);
        service = new TacticalChangeService(registry);

        userId = UUID.randomUUID();
        matchId = UUID.randomUUID();
        context = buildContext("home", "away");

        when(session.getLiveSession()).thenReturn(liveSession);
        when(liveSession.contextView()).thenReturn(new LiveSession(context, 1L).contextView());
        when(liveSession.isFinished()).thenReturn(false);
        when(liveSession.currentMinute()).thenReturn(30);
        when(registry.getSession(userId, matchId)).thenReturn(Optional.of(session));
    }

    @Test
    @DisplayName("changeStyle invokes typed LiveSession style mutation")
    void changeStyle_invokesTypedMutation() {
        StepVerifier.create(service.changeStyle(userId, matchId, TeamStyle.ATTACKING))
            .assertNext(result -> {
                assertNotNull(result);
                assertTrue(result.success());
                assertEquals(TeamStyle.ATTACKING, result.currentStyle());
                assertEquals(30, result.minuteApplied());
            })
            .verifyComplete();

        verify(liveSession, atLeastOnce()).changeTeamStyle(context.homeTeamId(), TeamStyle.ATTACKING);
        verify(liveSession, atLeastOnce()).recordTacticalChange(any());
    }

    @Test
    @DisplayName("changeStyle fails with 409-mappable error when no session is registered")
    void changeStyle_noSession_returnsError() {
        when(registry.getSession(userId, matchId)).thenReturn(Optional.empty());

        StepVerifier.create(service.changeStyle(userId, matchId, TeamStyle.DEFENSIVE))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalStateException);
                assertTrue(e.getMessage().toLowerCase().contains("no active match session"));
            })
            .verify();
    }

    @Test
    @DisplayName("changeStyle fails when match is already finished")
    void changeStyle_finishedMatch_returnsError() {
        when(liveSession.isFinished()).thenReturn(true);

        StepVerifier.create(service.changeStyle(userId, matchId, TeamStyle.DEFENSIVE))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalStateException);
                assertTrue(e.getMessage().toLowerCase().contains("already finished"));
            })
            .verify();
    }

    @Test
    @DisplayName("changeStyle fails when newStyle is null")
    void changeStyle_nullStyle_returnsError() {
        StepVerifier.create(service.changeStyle(userId, matchId, null))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalArgumentException);
                assertTrue(e.getMessage().toLowerCase().contains("must not be null"));
            })
            .verify();
    }

    @Test
    @DisplayName("changeFormation validates 10-11 slots, 1 GK, unique playerIds")
    void changeFormation_invalidSlots_returnsError() {
        List<TacticalFormationSlot> tooMany = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tooMany.add(new TacticalFormationSlot("home-starter-" + i, "MID"));
        }
        StepVerifier.create(service.changeFormation(userId, matchId, tooMany))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalArgumentException);
                assertTrue(e.getMessage().toLowerCase().contains("10 and 11"));
            })
            .verify();
    }

    @Test
    @DisplayName("changeFormation fails when no GK slot is present")
    void changeFormation_noGoalkeeper_returnsError() {
        List<TacticalFormationSlot> noGk = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            noGk.add(new TacticalFormationSlot("home-starter-" + (i + 1), "DEF"));
        }
        StepVerifier.create(service.changeFormation(userId, matchId, noGk))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalArgumentException);
                assertTrue(e.getMessage().toLowerCase().contains("exactly 1 gk"));
            })
            .verify();
    }

    @Test
    @DisplayName("changeFormation invokes typed formation mutation on happy path")
    void changeFormation_happyPath_invokesTypedMutation() {
        List<TacticalFormationSlot> formation = home442();

        StepVerifier.create(service.changeFormation(userId, matchId, formation))
            .assertNext(result -> {
                assertNotNull(result);
                assertTrue(result.success());
                assertEquals(30, result.minuteApplied());
                assertEquals(11, result.currentFormation().size());
            })
            .verifyComplete();

        verify(liveSession, atLeastOnce()).changeFormation(eq(context.homeTeamId()), eq("4-4-2"), any(), any());
        verify(liveSession, atLeastOnce()).recordTacticalChange(any());
    }

    @Test
    @DisplayName("changeFormation uses requested formationCode instead of deriving from role counts")
    void changeFormation_requestedFormationCodeWinsOverDerivedCode() {
        List<TacticalFormationSlot> formation = home433Roles();

        StepVerifier.create(service.changeFormation(userId, matchId, formation, "4-3-3"))
            .assertNext(result -> assertTrue(result.success()))
            .verifyComplete();

        verify(liveSession, atLeastOnce()).changeFormation(eq(context.homeTeamId()), eq("4-3-3"), any(), any());
    }

    @Test
    @DisplayName("changeFormation carries live custom pixel coordinates into detailed match context slots")
    void changeFormation_customCoordinatesUpdateContextSlots() {
        List<TacticalFormationSlot> formation = home442WithPixel("home-starter-6", 47.25, 58.75);

        StepVerifier.create(service.changeFormation(userId, matchId, formation, "4-4-2"))
            .assertNext(result -> assertTrue(result.success()))
            .verifyComplete();

        ArgumentCaptor<Map<String, LineupSlot>> slotsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(liveSession, atLeastOnce()).changeFormation(eq(context.homeTeamId()), eq("4-4-2"), any(), slotsCaptor.capture());
        assertTrue(slotsCaptor.getValue().containsKey("home-starter-6"));
        assertEquals(47.25, slotsCaptor.getValue().get("home-starter-6").customXPercent());
        assertEquals(58.75, slotsCaptor.getValue().get("home-starter-6").customYPercent());
    }

    @Test
    @DisplayName("changeFormation records custom pixels in the tactical-change event")
    void changeFormation_customCoordinatesAreVisibleInTimelineEvent() {
        List<TacticalFormationSlot> formation = home442WithPixel("home-starter-6", 47.25, 58.75);

        StepVerifier.create(service.changeFormation(userId, matchId, formation, "4-4-2"))
            .assertNext(result -> assertTrue(result.success()))
            .verifyComplete();

        ArgumentCaptor<DetailedMatchEvent> eventCaptor = ArgumentCaptor.forClass(DetailedMatchEvent.class);
        verify(liveSession, atLeastOnce()).recordTacticalChange(eventCaptor.capture());
        String description = eventCaptor.getValue().description();
        assertTrue(description.contains("Formation changed from 4-3-3 to 4-4-2"));
        assertTrue(description.contains("pixels:"));
        assertTrue(description.contains("47.3/58.8"));
    }

    @Test
    @DisplayName("changeFormation supports away manager roster and writes custom pixels into away slots")
    void changeFormation_awayManagerRosterAndPixels() {
        List<TacticalFormationSlot> formation = away442WithPixel("away-starter-6", 52.5, 41.25);

        StepVerifier.create(service.changeFormation(userId, matchId, formation, "4-4-2"))
            .assertNext(result -> {
                assertTrue(result.success());
                assertEquals(30, result.minuteApplied());
            })
            .verifyComplete();

        ArgumentCaptor<Map<String, LineupSlot>> slotsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(liveSession, atLeastOnce()).changeFormation(eq(context.awayTeamId()), eq("4-4-2"), any(), slotsCaptor.capture());
        assertTrue(slotsCaptor.getValue().containsKey("away-starter-6"));
        assertEquals(52.5, slotsCaptor.getValue().get("away-starter-6").customXPercent());
        assertEquals(41.25, slotsCaptor.getValue().get("away-starter-6").customYPercent());
    }

    private List<TacticalFormationSlot> home442() {
        return List.of(
            new TacticalFormationSlot("home-starter-0", "GK"),
            new TacticalFormationSlot("home-starter-1", "DEF"),
            new TacticalFormationSlot("home-starter-2", "DEF"),
            new TacticalFormationSlot("home-starter-3", "DEF"),
            new TacticalFormationSlot("home-starter-4", "DEF"),
            new TacticalFormationSlot("home-starter-5", "MID"),
            new TacticalFormationSlot("home-starter-6", "MID"),
            new TacticalFormationSlot("home-starter-7", "MID"),
            new TacticalFormationSlot("home-starter-8", "MID"),
            new TacticalFormationSlot("home-starter-9", "ATT"),
            new TacticalFormationSlot("home-starter-10", "ATT"));
    }

    private List<TacticalFormationSlot> home433Roles() {
        return List.of(
            new TacticalFormationSlot("home-starter-0", "GK"),
            new TacticalFormationSlot("home-starter-1", "DEF"),
            new TacticalFormationSlot("home-starter-2", "DEF"),
            new TacticalFormationSlot("home-starter-3", "DEF"),
            new TacticalFormationSlot("home-starter-4", "DEF"),
            new TacticalFormationSlot("home-starter-5", "MID"),
            new TacticalFormationSlot("home-starter-6", "MID"),
            new TacticalFormationSlot("home-starter-7", "MID"),
            new TacticalFormationSlot("home-starter-8", "WINGER"),
            new TacticalFormationSlot("home-starter-9", "ATT"),
            new TacticalFormationSlot("home-starter-10", "WINGER"));
    }

    private List<TacticalFormationSlot> home442WithPixel(String playerId, double x, double y) {
        List<TacticalFormationSlot> formation = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String id = "home-starter-" + i;
            String pos = i == 0 ? "GK" : i < 5 ? "DEF" : i < 9 ? "MID" : "ATT";
            formation.add(new TacticalFormationSlot(id, pos, i, id.equals(playerId) ? x : null, id.equals(playerId) ? y : null));
        }
        return formation;
    }

    private List<TacticalFormationSlot> away442WithPixel(String playerId, double x, double y) {
        List<TacticalFormationSlot> formation = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String id = "away-starter-" + i;
            String pos = i == 0 ? "GK" : i < 5 ? "DEF" : i < 9 ? "MID" : "ATT";
            formation.add(new TacticalFormationSlot(id, pos, i, id.equals(playerId) ? x : null, id.equals(playerId) ? y : null));
        }
        return formation;
    }

    private MatchContext buildContext(String homeTeamId, String awayTeamId) {
        SessionTeam homeTeam = makeTeam(homeTeamId, "Home FC");
        SessionTeam awayTeam = makeTeam(awayTeamId, "Away FC");
        return new MatchContext(
            "match-test",
            homeTeam.getSessionTeamId(),
            awayTeam.getSessionTeamId(),
            homeTeam, awayTeam,
            makePlayers("home", 11, 70),
            makePlayers("away", 11, 70),
            List.of(), List.of(),
            "4-3-3", "4-3-3",
            TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private SessionTeam makeTeam(String id, String name) {
        SessionTeam team = SessionTeam.fromRealTeam(
            UUID.nameUUIDFromBytes(id.getBytes()),
            "world_" + id, name, "Country",
            BigDecimal.ZERO, "4-3-3", null);
        team.setSessionTeamId(id);
        return team;
    }

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "-starter-" + i;
            SessionPlayer p = SessionPlayer.custom(
                id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000L));
            p.setSessionPlayerId(id);
            list.add(p);
        }
        return list;
    }
}

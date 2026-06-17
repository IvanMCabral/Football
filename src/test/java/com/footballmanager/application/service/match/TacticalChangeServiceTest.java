package com.footballmanager.application.service.match;

import com.footballmanager.adapters.in.web.career.simulation.dto.FormationChangeResultDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.FormationSlotDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.StyleChangeResultDTO;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LIVE-MATCH-F2-LIVE F5 (B7): unit tests for {@link TacticalChangeService}.
 *
 * <p>Per the F5 spec section 5: {@code changeStyle_invokesMutateContext}
 * (B3) — verifies the service calls {@code V24LiveSession.mutateContext}
 * with a {@link UnaryOperator} that swaps the home team's style.
 *
 * <p>Mockito is used to mock {@link MatchSessionRegistry} and
 * {@link V24LiveSession} so the test focuses on the service's contract,
 * not the live-session internals.
 */
class TacticalChangeServiceTest {

    private MatchSessionRegistry registry;
    private V24LiveSession liveSession;
    private MatchSession session;
    private TacticalChangeService service;
    private UUID userId;
    private UUID matchId;
    private V24MatchContext context;

    @BeforeEach
    void setUp() {
        registry = mock(MatchSessionRegistry.class);
        liveSession = mock(V24LiveSession.class);
        session = mock(MatchSession.class);
        service = new TacticalChangeService(registry);

        userId = UUID.randomUUID();
        matchId = UUID.randomUUID();
        context = buildContext("home", "away");

        when(session.getV24LiveSession()).thenReturn(liveSession);
        when(liveSession.context()).thenReturn(context);
        when(liveSession.isFinished()).thenReturn(false);
        when(liveSession.currentMinute()).thenReturn(30);
        when(registry.getSession(userId, matchId)).thenReturn(Optional.of(session));
    }

    @Test
    @DisplayName("changeStyle invokes mutateContext with withNewStyle unary operator (B3 GREEN)")
    void changeStyle_invokesMutateContext() {
        // Arrange: a no-op mutateContext so the test doesn't blow up — we only verify
        // that the service CALLS mutateContext with a UnaryOperator that returns a
        // context whose homeStyle equals the new style.
        // mutateContext is void; capture the UnaryOperator arg and execute it.
        org.mockito.Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            UnaryOperator<V24MatchContext> op = (UnaryOperator<V24MatchContext>) inv.getArgument(0);
            V24MatchContext result = op.apply(context);
            assertEquals(TeamStyle.ATTACKING, result.homeStyle(),
                "withNewStyle must swap homeStyle to the new value");
            return null;
        }).when(liveSession).mutateContext(any());

        // Act + Assert
        StepVerifier.create(service.changeStyle(userId, matchId, TeamStyle.ATTACKING))
            .assertNext(result -> {
                assertNotNull(result);
                assertTrue(result.success());
                assertEquals(TeamStyle.ATTACKING, result.currentStyle());
                assertEquals(30, result.minuteApplied());
            })
            .verifyComplete();

        // Verify the service called mutateContext
        verify(liveSession, atLeastOnce()).mutateContext(any());

        // Verify the service recorded a TACTICAL_CHANGE event
        verify(liveSession, atLeastOnce()).recordTacticalChange(any());
    }

    @Test
    @DisplayName("changeStyle fails with 409-mappable error when no session is registered")
    void changeStyle_noSession_returnsError() {
        when(registry.getSession(userId, matchId)).thenReturn(Optional.empty());

        StepVerifier.create(service.changeStyle(userId, matchId, TeamStyle.DEFENSIVE))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalStateException,
                    "Expected IllegalStateException for missing session, got " + e.getClass());
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
    @DisplayName("changeStyle fails when newStyle is null (defense in depth)")
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
        // 12 players — too many
        List<FormationSlotDTO> tooMany = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tooMany.add(new FormationSlotDTO("home-starter-" + i, "MID"));
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
        // 10 DEF slots, 0 GK
        List<FormationSlotDTO> noGk = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            noGk.add(new FormationSlotDTO("home-starter-" + (i + 1), "DEF"));
        }
        StepVerifier.create(service.changeFormation(userId, matchId, noGk))
            .expectErrorSatisfies(e -> {
                assertTrue(e instanceof IllegalArgumentException);
                assertTrue(e.getMessage().toLowerCase().contains("exactly 1 gk"));
            })
            .verify();
    }

    @Test
    @DisplayName("changeFormation invokes mutateContext + recordTacticalChange on happy path")
    void changeFormation_happyPath_invokesMutateContext() {
        // Build a valid 4-4-2 formation using the home starting players.
        List<FormationSlotDTO> formation = new ArrayList<>();
        formation.add(new FormationSlotDTO("home-starter-0", "GK"));
        formation.add(new FormationSlotDTO("home-starter-1", "DEF"));
        formation.add(new FormationSlotDTO("home-starter-2", "DEF"));
        formation.add(new FormationSlotDTO("home-starter-3", "DEF"));
        formation.add(new FormationSlotDTO("home-starter-4", "DEF"));
        formation.add(new FormationSlotDTO("home-starter-5", "MID"));
        formation.add(new FormationSlotDTO("home-starter-6", "MID"));
        formation.add(new FormationSlotDTO("home-starter-7", "MID"));
        formation.add(new FormationSlotDTO("home-starter-8", "MID"));
        formation.add(new FormationSlotDTO("home-starter-9", "ATT"));
        formation.add(new FormationSlotDTO("home-starter-10", "ATT"));

        // No-op mutateContext for the test (mutateContext is void)
        org.mockito.Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            UnaryOperator<V24MatchContext> op = (UnaryOperator<V24MatchContext>) inv.getArgument(0);
            V24MatchContext result = op.apply(context);
            assertEquals("4-4-2", result.homeFormation(),
                "withNewFormation must swap homeFormation to the derived code");
            return null;
        }).when(liveSession).mutateContext(any());

        StepVerifier.create(service.changeFormation(userId, matchId, formation))
            .assertNext(result -> {
                assertNotNull(result);
                assertTrue(result.success());
                assertEquals(30, result.minuteApplied());
                assertEquals(11, result.currentFormation().size());
            })
            .verifyComplete();

        verify(liveSession, atLeastOnce()).mutateContext(any());
        verify(liveSession, atLeastOnce()).recordTacticalChange(any());
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildContext(String homeTeamId, String awayTeamId) {
        SessionTeam homeTeam = makeTeam(homeTeamId, "Home FC");
        SessionTeam awayTeam = makeTeam(awayTeamId, "Away FC");
        return new V24MatchContext(
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
        return SessionTeam.fromRealTeam(
            UUID.nameUUIDFromBytes(id.getBytes()),
            "world_" + id, name, "Country",
            BigDecimal.ZERO, "4-3-3", null);
    }

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "-starter-" + i;
            SessionPlayer p = SessionPlayer.custom(
                id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
            // SessionPlayer.custom generates a random sessionPlayerId; align it
            // with our test id so the formation change's roster lookup works.
            p.setSessionPlayerId(id);
            list.add(p);
        }
        return list;
    }
}

package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24A3: Timeline ordering test for V24DetailedMatchEngine.
 * All events must have minute between 1 and 90, sorted ascending.
 */
class V24TimelineOrderingTest {

    @Test
    void eventsAreOrderedAndWithinMatchMinutes() {
        V24MatchContext ctx = buildContext("match-timeline-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(ctx, 99L);

        List<V24MatchEvent> events = result.timeline().events();
        assertFalse(events.isEmpty(), "Timeline should not be empty");

        int prevMinute = 0;
        for (V24MatchEvent e : events) {
            assertTrue(e.minute() >= 1 && e.minute() <= 90,
                    "Event minute must be 1-90, got " + e.minute());
            assertTrue(e.minute() >= prevMinute,
                    "Events must be sorted ascending by minute");
            prevMinute = e.minute();
        }
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildContext(String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayers("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayers("away", 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            SessionPlayer p = SessionPlayer.custom(
                    id, 25, "MID",
                    ovr, ovr, ovr, ovr, ovr, ovr,
                    BigDecimal.valueOf(ovr * 1000));
            list.add(p);
        }
        return list;
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}
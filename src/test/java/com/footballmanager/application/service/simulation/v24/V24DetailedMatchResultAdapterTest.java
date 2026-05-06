package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24A3: Adapter test — maps only 6 aggregate fields, discards timeline/xG/summary.
 */
class V24DetailedMatchResultAdapterTest {

    @Test
    void mapsAggregateFieldsOnly() {
        // Build a real V24DetailedMatchResult via engine
        V24MatchContext ctx = buildContext("match-adapter-1", 75, 75);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult detailed = engine.simulate(ctx, 123L);

        // Adapt to MatchResultData
        MatchFixture.MatchResultData data =
                V24DetailedMatchResultAdapter.toMatchResultData(detailed);

        assertEquals(detailed.homeGoals(), data.homeGoals, "homeGoals");
        assertEquals(detailed.awayGoals(), data.awayGoals, "awayGoals");
        assertEquals(detailed.homePossession(), data.homePossession, "homePossession");
        assertEquals(detailed.awayPossession(), data.awayPossession, "awayPossession");
        assertEquals(detailed.homeShots(), data.homeShots, "homeShots");
        assertEquals(detailed.awayShots(), data.awayShots, "awayShots");

        // MatchResultData has exactly 6 fields — no xG, no timeline, no summary
        assertEquals(6, MatchFixture.MatchResultData.class.getDeclaredFields().length,
                "MatchResultData must have exactly 6 fields");
    }

    @Test
    void adapterRejectsNullResult() {
        assertThrows(IllegalArgumentException.class, () ->
                V24DetailedMatchResultAdapter.toMatchResultData(null));
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
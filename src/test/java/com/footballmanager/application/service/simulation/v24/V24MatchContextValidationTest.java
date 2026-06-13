package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24A3: Validation tests for V24MatchContext.
 */
class V24MatchContextValidationTest {

    @Test
    void rejectsInvalidStartingEleven() {
        // V24D6U2: short-handed lineups are accepted in [MIN=7, 11]. Test the
        // boundary below MIN (6 players) which should still be rejected.
        List<SessionPlayer> homeStart = makePlayers("home", 6, 75); // below MIN
        List<SessionPlayer> awayStart = makePlayers("away", 11, 75);
        V24MatchContext ctx = buildContext("match-valid", 75, 75);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new V24MatchContext(
                        ctx.matchId(), ctx.homeTeamId(), ctx.awayTeamId(),
                        ctx.homeTeam(), ctx.awayTeam(),
                        homeStart, awayStart,
                        List.of(), List.of(),
                        "4-3-3", "4-3-3",
                        TeamStyle.BALANCED, TeamStyle.BALANCED
                ));
        assertTrue(ex.getMessage().contains("7") || ex.getMessage().contains("home"),
                "Exception should mention 7 or home: " + ex.getMessage());
    }

    @Test
    void acceptsShortHandedStartingEleven() {
        // V24D6U2: 10-player home is now accepted
        List<SessionPlayer> homeStart = makePlayers("home", 10, 75);
        List<SessionPlayer> awayStart = makePlayers("away", 11, 75);
        V24MatchContext ctx = buildContext("match-valid", 75, 75);

        V24MatchContext shortCtx = new V24MatchContext(
                ctx.matchId(), ctx.homeTeamId(), ctx.awayTeamId(),
                ctx.homeTeam(), ctx.awayTeam(),
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
        assertEquals(10, shortCtx.homeStartingPlayers().size());
        assertEquals(11, shortCtx.awayStartingPlayers().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "   "})
    void rejectsBlankMatchId(String blankId) {
        V24MatchContext ctx = buildContext("match-valid", 75, 75);

        assertThrows(IllegalArgumentException.class, () ->
                new V24MatchContext(
                        blankId,
                        ctx.homeTeamId(), ctx.awayTeamId(),
                        ctx.homeTeam(), ctx.awayTeam(),
                        ctx.homeStartingPlayers(), ctx.awayStartingPlayers(),
                        List.of(), List.of(),
                        "4-3-3", "4-3-3",
                        TeamStyle.BALANCED, TeamStyle.BALANCED
                ));
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
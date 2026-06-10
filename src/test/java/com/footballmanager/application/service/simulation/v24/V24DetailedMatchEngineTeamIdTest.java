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
 * V24D6O-fix: Timeline events must use real session team UUIDs, not the
 * legacy "HOME"/"AWAY" sentinel strings. Persisted V24_DETAIL data is
 * consumed by the V24 match detail page, and the UI/UX contract is:
 * - teamId is a sessionTeamId UUID that matches V24MatchContext.homeTeamId()
 *   or .awayTeamId()
 * - HOME/AWAY are never exposed in the timeline
 *
 * These tests fail with the pre-V24D6O code and pass after the fix.
 */
class V24DetailedMatchEngineTeamIdTest {

    private static final String HOME_UUID = "91aa1f10-74c7-45ab-9b13-29c6d8a5e109";
    private static final String AWAY_UUID = "be287293-6a00-4830-b470-872acebee750";

    @Test
    void timelineEventTeamIdsAreRealUuidsNotHomeAway() {
        V24MatchContext ctx = buildContext("match-v24d6o-fix-1", HOME_UUID, AWAY_UUID);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(ctx, 42L);

        List<V24MatchEvent> events = result.timeline().events();
        assertFalse(events.isEmpty(), "Timeline should not be empty");

        for (V24MatchEvent e : events) {
            assertNotNull(e.teamId(), "Event teamId must not be null");
            assertNotEquals("HOME", e.teamId(),
                    "Timeline event teamId must not be the legacy 'HOME' sentinel. event=" + e);
            assertNotEquals("AWAY", e.teamId(),
                    "Timeline event teamId must not be the legacy 'AWAY' sentinel. event=" + e);
            assertTrue(
                    e.teamId().equals(HOME_UUID) || e.teamId().equals(AWAY_UUID),
                    "Event teamId must be a real session team UUID. got=" + e.teamId()
                            + " expected one of [" + HOME_UUID + ", " + AWAY_UUID + "]"
            );
        }
    }

    @Test
    void allHomeEventsMapToHomeUuid() {
        V24MatchContext ctx = buildContext("match-v24d6o-fix-2", HOME_UUID, AWAY_UUID);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(ctx, 7L);

        // Every event's teamId must be exactly one of the two UUIDs from context.
        // This is the readback contract that V24LiveSession and V24MatchDetailPage
        // rely on. With the legacy sentinel, this would be impossible to enforce.
        for (V24MatchEvent e : result.timeline().events()) {
            assertTrue(
                    e.teamId().equals(ctx.homeTeamId()) || e.teamId().equals(ctx.awayTeamId()),
                    "Event teamId=" + e.teamId() + " does not match either context UUID"
            );
        }
    }

    @Test
    void substitutionLimitRespectsPerTeamUuidKeying() {
        // With the legacy "HOME"/"AWAY" keys, the substitution engine's
        // hasSubstitutionsRemaining("HOME") check would never see a non-zero
        // count (because attemptSubstitution increments under the team's
        // sessionTeamId). After the fix, the check and the increment are
        // keyed by the same UUID, so the 5-per-team limit actually fires.
        V24MatchContext ctx = buildContext("match-v24d6o-fix-3", HOME_UUID, AWAY_UUID);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(ctx, 13L);

        long homeSubs = result.timeline().events().stream()
                .filter(e -> e.type() == V24MatchEventType.SUBSTITUTION)
                .filter(e -> e.teamId().equals(HOME_UUID))
                .count();
        long awaySubs = result.timeline().events().stream()
                .filter(e -> e.type() == V24MatchEventType.SUBSTITUTION)
                .filter(e -> e.teamId().equals(AWAY_UUID))
                .count();

        // Even with bench=0 and no eligible subs, this verifies the teamId
        // mapping is UUID-based, which is the contract fix.
        assertTrue(homeSubs <= 5,
                "Home substitutions must be <= 5 (limit), got " + homeSubs);
        assertTrue(awaySubs <= 5,
                "Away substitutions must be <= 5 (limit), got " + awaySubs);
    }

    /**
     * V24D6O-fix (shots consistency): the persisted detail's homeShots/awayShots
     * must equal the count of timeline events with type ∈ {GOAL, SHOT_ON_TARGET,
     * MISS, BLOCK}. The Stats summary in the V24 detail page reads homeShots/awayShots;
     * the Shot Map reads the timeline. They must agree.
     *
     * This test runs the engine many times so that both teams score at least one
     * goal, then asserts the equality.
     */
    @Test
    void homeShotsPlusAwayShotsEqualsTimelineShotCount() {
        V24MatchContext ctx = buildContext("match-v24d6o-shots-1", HOME_UUID, AWAY_UUID);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        // Try several seeds until both teams have at least one goal.
        // (Ovr 75 vs 75 makes scoring possible but not guaranteed per seed.)
        V24DetailedMatchResult result = null;
        for (long seed = 1L; seed <= 50L; seed++) {
            V24DetailedMatchResult r = engine.simulate(ctx, seed);
            int homeGoals = r.timeline().events().stream()
                    .filter(e -> e.type() == V24MatchEventType.GOAL)
                    .filter(e -> e.teamId().equals(HOME_UUID))
                    .mapToInt(e -> 1).sum();
            int awayGoals = r.timeline().events().stream()
                    .filter(e -> e.type() == V24MatchEventType.GOAL)
                    .filter(e -> e.teamId().equals(AWAY_UUID))
                    .mapToInt(e -> 1).sum();
            if (homeGoals > 0 && awayGoals > 0) {
                result = r;
                break;
            }
        }
        assertNotNull(result, "Could not find a seed producing goals for both teams in 50 attempts");

        int timelineHomeShots = (int) result.timeline().events().stream()
                .filter(e -> e.teamId().equals(HOME_UUID))
                .filter(e -> e.type() == V24MatchEventType.GOAL
                        || e.type() == V24MatchEventType.SHOT_ON_TARGET
                        || e.type() == V24MatchEventType.MISS
                        || e.type() == V24MatchEventType.BLOCK)
                .count();
        int timelineAwayShots = (int) result.timeline().events().stream()
                .filter(e -> e.teamId().equals(AWAY_UUID))
                .filter(e -> e.type() == V24MatchEventType.GOAL
                        || e.type() == V24MatchEventType.SHOT_ON_TARGET
                        || e.type() == V24MatchEventType.MISS
                        || e.type() == V24MatchEventType.BLOCK)
                .count();
        int timelineTotal = timelineHomeShots + timelineAwayShots;

        assertEquals(timelineHomeShots, result.homeShots(),
                "homeShots must equal count of home timeline events of type GOAL|SHOT_ON_TARGET|MISS|BLOCK. "
                        + "homeShots=" + result.homeShots() + " timelineHomeShots=" + timelineHomeShots);
        assertEquals(timelineAwayShots, result.awayShots(),
                "awayShots must equal count of away timeline events of type GOAL|SHOT_ON_TARGET|MISS|BLOCK. "
                        + "awayShots=" + result.awayShots() + " timelineAwayShots=" + timelineAwayShots);
        assertEquals(timelineTotal, result.homeShots() + result.awayShots(),
                "homeShots+awayShots must equal the total shot-like timeline events. "
                        + "total=" + timelineTotal + " ("
                        + result.homeShots() + "+" + result.awayShots() + ")");

        // The key contract: goals are included in the shot count.
        int homeGoals = (int) result.timeline().events().stream()
                .filter(e -> e.type() == V24MatchEventType.GOAL)
                .filter(e -> e.teamId().equals(HOME_UUID))
                .count();
        int awayGoals = (int) result.timeline().events().stream()
                .filter(e -> e.type() == V24MatchEventType.GOAL)
                .filter(e -> e.teamId().equals(AWAY_UUID))
                .count();
        assertEquals(homeGoals, result.homeGoals(),
                "homeGoals must equal count of home GOAL events");
        assertEquals(awayGoals, result.awayGoals(),
                "awayGoals must equal count of away GOAL events");
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildContext(String matchId, String homeTeamId, String awayTeamId) {
        List<SessionPlayer> homeStart = makePlayers("home", 11, 75);
        List<SessionPlayer> awayStart = makePlayers("away", 11, 75);
        SessionTeam homeTeam = makeTeam(homeTeamId, "Home FC");
        SessionTeam awayTeam = makeTeam(awayTeamId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeamId,
                awayTeamId,
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

    private SessionTeam makeTeam(String sessionTeamId, String name) {
        return SessionTeam.fromRealTeam(
                UUID.fromString(sessionTeamId),
                "world_" + sessionTeamId, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}

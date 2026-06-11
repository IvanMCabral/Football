package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6Q: Regression test for second-yellow → red card in same match.
 *
 * <p>Bug reproduced by smoke: a player with 2 yellows in the same match did NOT
 * have a RED_CARD event in the timeline, and playerRatings.redCards was 0.
 *
 * <p>Root cause: addYellowCard() flips redCard=true when yellowCards reaches 2.
 * The original engine guard "if (yellowCards() >= 2 && !redCard())" was evaluated
 * AFTER addYellowCard() — so redCard() was always true and the guard never fired.
 *
 * <p>Fix: the engine now calls the package-private helper
 * {@link V24DetailedMatchEngine#applyYellowCardAndMaybeSecondYellowRed} which
 * captures the pre-yellow red state BEFORE addYellowCard() and uses it in the
 * guard.
 *
 * <p>These tests exercise that helper directly — no Mockito, no random seed,
 * fully deterministic. To set up "a player who already has 1 yellow" we call
 * addYellowCard() once before invoking the helper; this is the same code path
 * the engine itself uses.
 */
class V24DetailedMatchEngineSecondYellowTest {

    private final V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

    @Test
    void firstYellowDoesNotEmitRedCard() {
        V24PlayerMatchState player = freshPlayer("p1");
        V24MatchTimeline timeline = new V24MatchTimeline();

        engine.applyYellowCardAndMaybeSecondYellowRed(player, timeline, 30, "home");

        assertEquals(1, player.yellowCards(), "first yellow must increment to 1");
        assertFalse(player.redCard(), "first yellow must not flip redCard");

        long yellows = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.YELLOW_CARD)
                .count();
        long reds = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.RED_CARD)
                .count();
        assertEquals(1, yellows, "exactly 1 YELLOW_CARD event expected");
        assertEquals(0, reds, "no RED_CARD event expected on first yellow");
    }

    @Test
    void secondYellowEmitsRedCard() {
        V24PlayerMatchState player = freshPlayer("p2");
        // Pre-load the first yellow the same way the engine would have, in a
        // previous tick. After this the player has 1 yellow and is not red.
        player.addYellowCard();
        assertEquals(1, player.yellowCards());
        assertFalse(player.redCard());

        V24MatchTimeline timeline = new V24MatchTimeline();

        engine.applyYellowCardAndMaybeSecondYellowRed(player, timeline, 83, "home");

        assertEquals(2, player.yellowCards(), "second yellow must bring total to 2");
        assertTrue(player.redCard(), "second yellow must flip redCard to true");

        long yellows = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.YELLOW_CARD)
                .count();
        long reds = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.RED_CARD)
                .count();
        assertEquals(1, yellows, "exactly 1 YELLOW_CARD event expected (the second one)");
        assertEquals(1, reds, "exactly 1 RED_CARD event expected (second yellow → red)");

        // The RED_CARD event must reference the same playerId/teamId/minute as the YELLOW_CARD.
        V24MatchEvent yellowEv = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.YELLOW_CARD)
                .findFirst().orElseThrow();
        V24MatchEvent redEv = timeline.events().stream()
                .filter(e -> e.type() == V24MatchEventType.RED_CARD)
                .findFirst().orElseThrow();
        assertEquals(yellowEv.playerId(), redEv.playerId(), "RED_CARD must reference same playerId");
        assertEquals(yellowEv.teamId(), redEv.teamId(), "RED_CARD must reference same teamId");
        assertEquals(yellowEv.minute(), redEv.minute(), "RED_CARD must have same minute as second yellow");
        assertTrue(redEv.description().contains("second yellow"),
                "RED_CARD description should mention 'second yellow', got: " + redEv.description());
    }

    @Test
    void playerRatingsCountSecondYellowAsYellowPlusRed() {
        // Verify the stats model counts the engine-produced events correctly:
        // 2 YELLOW_CARD + 1 RED_CARD for the same player → 2 yellows, 1 red.
        V24PlayerMatchStatsModel statsModel = new V24PlayerMatchStatsModel();

        V24PlayerMatchState player = V24PlayerMatchState.fromSessionPlayer(
                makePlayerWithId("p-yy"), "home-team");

        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(30, V24MatchEventType.YELLOW_CARD,
                "home", "p-yy", "Player YY", null, null, 0.0, "first yellow"));
        timeline.addEvent(new V24MatchEvent(83, V24MatchEventType.YELLOW_CARD,
                "home", "p-yy", "Player YY", null, null, 0.0, "second yellow"));
        timeline.addEvent(new V24MatchEvent(83, V24MatchEventType.RED_CARD,
                "home", "p-yy", "Player YY", null, null, 0.0, "second yellow → red"));

        var ratings = statsModel.computeRatings(java.util.List.of(player), timeline);
        V24PlayerMatchRatingDto dto = ratings.get(0);
        assertEquals(2, dto.yellowCards(),
                "Player with 2 yellows in same match must have yellowCards=2");
        assertEquals(1, dto.redCards(),
                "Player with 2nd-yellow red must have redCards=1");
    }

    // ========== Fixtures ==========

    /**
     * Build a fresh V24PlayerMatchState with 0 yellows, not red, on pitch.
     */
    private V24PlayerMatchState freshPlayer(String id) {
        return V24PlayerMatchState.fromSessionPlayer(makePlayerWithId(id), "home-team");
    }

    private SessionPlayer makePlayerWithId(String id) {
        SessionPlayer p = SessionPlayer.custom(
                "Player " + id, 25, "MID",
                70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(70000));
        p.setSessionPlayerId(id);
        return p;
    }
}

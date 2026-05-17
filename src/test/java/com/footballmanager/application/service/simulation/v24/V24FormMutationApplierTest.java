package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6E2: Unit tests for V24FormMutationApplier.
 * Tests form mutation behavior in isolation — no Redis, no Spring, no IO.
 *
 * The applier delegates to V24PlayerRatingsAssembler which computes ratings
 * from CareerSave starting XI + V24MatchTimeline. Tests must set up
 * teamStarting11 to control which players get ratings.
 */
class V24FormMutationApplierTest {

    private final V24FormMutationApplier applier = new V24FormMutationApplier();

    // ========== Helper builders ==========

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private CareerSave careerWithPlayer(String playerId, int initialForm, String teamId) {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Player " + playerId, "MID", 25, 70);
        p.setForm(initialForm);
        career.addSessionPlayer(p);
        // Empty starting XI — player gets no ratings
        career.setTeamStarting11(java.util.Map.of(
                teamId, List.of(),
                otherTeam(teamId), List.of()
        ));
        return career;
    }

    private CareerSave careerWithPlayerInStartingXI(String playerId, int initialForm) {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Player " + playerId, "MID", 25, 70);
        p.setForm(initialForm);
        career.addSessionPlayer(p);
        career.setTeamStarting11(java.util.Map.of(
                "team-A", List.of(playerId),
                "team-B", List.of()
        ));
        return career;
    }

    private CareerSave careerWithTwoPlayers(String p1Id, int form1, String p2Id, int form2) {
        CareerSave career = new CareerSave();
        SessionPlayer p1 = SessionPlayer.fromWorldPlayer(p1Id, "Player " + p1Id, "MID", 25, 70);
        p1.setForm(form1);
        SessionPlayer p2 = SessionPlayer.fromWorldPlayer(p2Id, "Player " + p2Id, "DEF", 25, 70);
        p2.setForm(form2);
        career.addSessionPlayer(p1);
        career.addSessionPlayer(p2);
        career.setTeamStarting11(java.util.Map.of(
                "team-A", List.of(p1Id),
                "team-B", List.of(p2Id)
        ));
        return career;
    }

    private String otherTeam(String teamId) {
        return "team-A".equals(teamId) ? "team-B" : "team-A";
    }

    private V24DetailedMatchResult resultWithTimeline(V24MatchEvent... events) {
        V24MatchTimeline timeline = new V24MatchTimeline();
        for (V24MatchEvent e : events) timeline.addEvent(e);
        return V24DetailedMatchResult.builder()
                .matchId("match-1")
                .homeTeamId("team-A").awayTeamId("team-B")
                .homeGoals(1).awayGoals(0)
                .homeXg(1.2).awayXg(0.8)
                .homeShots(8).awayShots(5)
                .homePossession(55).awayPossession(45)
                .timeline(timeline)
                .summary("Test match")
                .build();
    }

    private V24MatchEvent goalEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.GOAL,
                "team-A", playerId, "Scorer", null, null, 0.35, "Goal");
    }

    // ========== Null-guard tests ==========

    @Test
    void nullCareer_returnsZero() {
        V24DetailedMatchResult res = resultWithTimeline();
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(0, applier.applyForm(null, res, p));
    }

    @Test
    void nullResult_returnsZero() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(0, applier.applyForm(career, null, p));
    }

    @Test
    void nullPolicy_returnsZero() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        V24DetailedMatchResult res = resultWithTimeline();
        assertEquals(0, applier.applyForm(career, res, null));
    }

    @Test
    void disabledPolicy_noMutation() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("p1", 30));
        V24CareerMutationPolicy p = policy(true, false, false, false, false);
        assertEquals(0, applier.applyForm(career, res, p));
        assertEquals(50, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void noRatings_noMutation() {
        // p1 NOT in starting XI → no ratings → no mutation
        CareerSave career = careerWithPlayer("p1", 50, "team-A");
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("p1", 30));
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(0, applier.applyForm(career, res, p));
        assertEquals(50, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void unknownPlayer_skipped() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // Timeline has goal by "unknown" — but p1 is in starting XI, so p1 gets base rating 6.0 → delta 0 → form stays 50
        // The unknown player (not in starting XI) is skipped — no separate rating entry for them
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("unknown", 30));
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(50, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void blankPlayerId_skipped() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // Timeline has goal by blank playerId — p1 in starting XI gets rating
        V24DetailedMatchResult res = resultWithTimeline(
                new V24MatchEvent(30, V24MatchEventType.GOAL, "team-A", "  ", "Scorer", null, null, 0.35, "Goal")
        );
        V24CareerMutationPolicy pol = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, pol));
        // blank playerId is skipped, but p1 is in starting XI and gets base rating 6.0 → form 50
        assertEquals(50, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    // ========== Delta computation tests ==========

    @Test
    void ratingExcellent_increasesForm() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // p1 scores a goal → rating 6.8 → >=6.5 <7.0 → delta +1 → form 51
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("p1", 30));
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(51, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void ratingGood_increasesForm() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // p1 scores twice → rating 7.6 → >=7.0 <8.0 → delta +2 → form 52
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("p1", 30), goalEvent("p1", 60));
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(52, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void ratingAcceptable_increasesForm() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // p1 scores a goal (no assist tracked in relatedPlayerId for scorer)
        // rating 6.8 → >=6.5 <7.0 → delta +1 → form 51
        V24DetailedMatchResult res = resultWithTimeline(
                new V24MatchEvent(30, V24MatchEventType.GOAL, "team-A", "p1", "Scorer", null, null, 0.35, "Goal")
        );
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(51, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void ratingNeutral_noChange() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // No events → base rating 6.0 → delta 0 → form unchanged
        V24DetailedMatchResult res = resultWithTimeline();
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(50, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void ratingPoor_decreasesForm() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // p1 gets yellow + foul → rating 6.0-0.3-0.05=5.65 → >=5.5 → delta 0
        // rating 5.65 is above 5.5 threshold → delta 0
        V24DetailedMatchResult res = resultWithTimeline(
                new V24MatchEvent(30, V24MatchEventType.YELLOW_CARD, "team-A", "p1", "Player", null, null, 0.0, "Card"),
                new V24MatchEvent(45, V24MatchEventType.FOUL, "team-A", "p1", "Player", null, null, 0.0, "Foul")
        );
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(50, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void ratingVeryPoor_decreasesForm() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        // p1 gets red card → rating 6.0-1.5=4.5 → <5.0 → delta -2 → form 48
        V24DetailedMatchResult res = resultWithTimeline(
                new V24MatchEvent(30, V24MatchEventType.RED_CARD, "team-A", "p1", "Player", null, null, 0.0, "Red")
        );
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(48, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void formClampedAtMax() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 98);
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("p1", 30), goalEvent("p1", 60), goalEvent("p1", 75));
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(99, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void formClampedAtMin() {
        CareerSave career = careerWithPlayerInStartingXI("p1", 2);
        // p1 gets red card → rating 4.5 → delta -2 → form max(1, 2-2) = 1
        V24DetailedMatchResult res = resultWithTimeline(
                new V24MatchEvent(30, V24MatchEventType.RED_CARD, "team-A", "p1", "Player", null, null, 0.0, "Red")
        );
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(1, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void nullExistingForm_defaultsNeutral() {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer("p1", "Player p1", "MID", 25, 70);
        // form is null by default
        career.addSessionPlayer(p);
        career.setTeamStarting11(java.util.Map.of("team-A", List.of("p1"), "team-B", List.of()));
        V24DetailedMatchResult res = resultWithTimeline(goalEvent("p1", 30));
        V24CareerMutationPolicy policy = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, policy));
        // base null → 50, rating 6.8 → delta +1 → form 51
        assertEquals(51, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void redCard_noExtraPenaltyBeyondRating() {
        // p1 gets red card → rating 4.5 → delta -2 → form 48
        // redCards field does NOT add extra penalty
        CareerSave career = careerWithPlayerInStartingXI("p1", 50);
        V24DetailedMatchResult res = resultWithTimeline(
                new V24MatchEvent(30, V24MatchEventType.RED_CARD, "team-A", "p1", "Player", null, null, 0.0, "Red")
        );
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(1, applier.applyForm(career, res, p));
        assertEquals(48, career.getPlayerManager().getSessionPlayer("p1").getForm());
    }

    @Test
    void multiplePlayers_independentChanges() {
        CareerSave career = careerWithTwoPlayers("p1", 50, "p2", 50);
        // p1 scores (rating 6.8), p2 gets red card (rating 4.5)
        V24DetailedMatchResult res = resultWithTimeline(
                goalEvent("p1", 30),
                new V24MatchEvent(45, V24MatchEventType.RED_CARD, "team-B", "p2", "Player", null, null, 0.0, "Red")
        );
        V24CareerMutationPolicy p = policy(true, false, false, false, true);
        assertEquals(2, applier.applyForm(career, res, p));
        assertEquals(51, career.getPlayerManager().getSessionPlayer("p1").getForm());
        assertEquals(48, career.getPlayerManager().getSessionPlayer("p2").getForm());
    }
}
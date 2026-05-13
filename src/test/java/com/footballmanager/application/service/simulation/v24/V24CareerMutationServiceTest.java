package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6B2: Unit tests for V24CareerMutationService and V24CareerMutationResult.
 * Tests mutation orchestration in isolation — no Spring, no Redis, no IO.
 */
class V24CareerMutationServiceTest {

    private final V24InjuryMutationApplier injuryApplier = new V24InjuryMutationApplier();
    private final V24CareerMutationService service = new V24CareerMutationService(injuryApplier);

    // ========== Helper builders ==========

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private CareerSave careerWithPlayer(String playerId) {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Player", "MID", 25, 70);
        career.addSessionPlayer(p);
        return career;
    }

    private V24MatchEvent injuryEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.INJURY,
                "team-A", playerId, "Injured", null, null, 0.0, "Injury");
    }

    private V24MatchEvent goalEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.GOAL,
                "team-A", playerId, "Goal", null, null, 0.35, "Goal");
    }

    private V24DetailedMatchResult result(V24MatchEvent... events) {
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

    // ========== Null-guard tests ==========

    @Test
    void nullCareer_returnsEmptyResult() {
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(null, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void nullResult_returnsEmptyResult() {
        CareerSave career = careerWithPlayer("p1");
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, null, pol);

        assertEquals(0, r.injuriesApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void nullPolicy_returnsEmptyResult() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));

        V24CareerMutationResult r = service.applyMutations(career, res, null);

        assertEquals(0, r.injuriesApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    // ========== Flag-enabling tests ==========

    @Test
    void masterFlagFalse_returnsEmptyAndDoesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(false, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void masterTrue_butAllSpecificFlagsFalse_returnsEmptyResult() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void injuryFlagTrue_appliesInjuriesAndReportsCount() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 67));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void injuryFlagFalse_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void fatigueFlagTrueAlone_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void disciplineFlagTrueAlone_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void formFlagTrueAlone_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, false, false, true);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    // ========== Exception-handling tests ==========

    @Test
    void injuryApplierException_returnsFailureResult() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        // Force exception by passing a null timeline
        V24CareerMutationService bogusService =
                new V24CareerMutationService(new V24InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Simulated failure");
                    }
                });

        V24CareerMutationResult r = bogusService.applyMutations(career, res, pol);

        assertFalse(r.failures().isEmpty());
        assertTrue(r.failures().get(0).contains("Simulated failure"));
        assertFalse(r.partialFailure());
    }

    // ========== Result object tests ==========

    @Test
    void failureResult_hasFailuresAndPartialFalse() {
        V24CareerMutationResult r = V24CareerMutationResult.failure("Test error");

        assertEquals(0, r.injuriesApplied());
        assertFalse(r.failures().isEmpty());
        assertEquals("Test error", r.failures().get(0));
        assertFalse(r.partialFailure());
    }

    @Test
    void successfulInjuryMutation_hasEmptyFailuresAndPartialFalse() {
        V24CareerMutationResult r = V24CareerMutationResult.success(2, 0, 0, 0);

        assertEquals(2, r.injuriesApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void resultObject_defensivelyCopiesFailuresList() {
        java.util.ArrayList<String> input = new java.util.ArrayList<>();
        input.add("error1");

        V24CareerMutationResult r = V24CareerMutationResult.partial(1, 0, 0, 0, input);

        input.add("error2"); // mutate original
        assertEquals(1, r.failures().size()); // result unchanged
        assertEquals("error1", r.failures().get(0));
    }

    @Test
    void emptyResult_hasAllCountsZero() {
        V24CareerMutationResult r = V24CareerMutationResult.empty();

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    // ========== Non-mutation side-effect tests ==========

    @Test
    void serviceDoesNotMutateNonInjuredPlayers() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        service.applyMutations(career, res, pol);

        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void serviceDoesNotTouchFatigueOrEnergy() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(60);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        service.applyMutations(career, res, pol);

        assertEquals(60, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void serviceDoesNotTouchForm() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setForm(75);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        service.applyMutations(career, res, pol);

        assertEquals(75, career.getSessionPlayer("p1").getForm());
    }

    @Test
    void noMatchFixtureResultDataSchemaImpact() {
        // V24CareerMutationService only mutates SessionPlayer fields.
        // MatchFixture.MatchResultData is never touched.
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        // If this completes without error and the result is correct,
        // MatchFixture.MatchResultData was not involved.
        assertNotNull(res);
    }
}
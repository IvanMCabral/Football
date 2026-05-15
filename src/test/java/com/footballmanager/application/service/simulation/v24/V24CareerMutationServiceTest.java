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
    private final V24FatigueMutationApplier fatigueApplier = new V24FatigueMutationApplier();
    private final V24DisciplineMutationApplier disciplineApplier = new V24DisciplineMutationApplier();
    private final V24CareerMutationService service = new V24CareerMutationService(injuryApplier, fatigueApplier, disciplineApplier);

    // Backwards-compatible service (fatigue + discipline appliers injected internally)
    private final V24CareerMutationService singleArgService = new V24CareerMutationService(injuryApplier);

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

    private V24MatchEvent yellowCardEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.YELLOW_CARD,
                "team-A", playerId, "Card Player", null, null, 0.0, "Foul");
    }

    private V24MatchEvent redCardEvent(String playerId, int minute) {
        return new V24MatchEvent(minute, V24MatchEventType.RED_CARD,
                "team-A", playerId, "Sent Off", null, null, 0.0, "Second yellow");
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
        career.getSessionPlayer("p1").setEnergy(100);
        // result has INJURY event but injuries=false so injury not applied
        // but fatigue=true so fatigue IS applied (player participated)
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(1, r.fatigueApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
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
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertNotNull(res);
    }

    // ========== Fatigue orchestration tests (V24D6C2) ==========

    @Test
    void fatigueFlagTrue_appliesFatigueAndReportsFatigueAppliedCount() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.fatigueApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void fatigueFlagFalse_doesNotApplyFatigue() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.fatigueApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuryFlagTrue_plus_fatigueFlagTrue_appliesBothAndReportsBothCounts() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        // p1 has only a goal (fatigue participation)
        // p2 has only an injury (injury participation) — different player
        V24MatchEvent p1Goal = new V24MatchEvent(60, V24MatchEventType.GOAL, "team-A", "p1", "Goal", null, null, 0.35, "Goal");
        V24MatchEvent p2Injury = new V24MatchEvent(45, V24MatchEventType.INJURY, "team-A", "p2", "Injured", null, null, 0.0, "Injury");
        SessionPlayer p2 = SessionPlayer.fromWorldPlayer("p2", "Player2", "MID", 25, 70);
        career.addSessionPlayer(p2);
        p2.setEnergy(100);
        V24DetailedMatchResult res = result(p1Goal, p2Injury);
        V24CareerMutationPolicy pol = policy(true, true, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertEquals(1, r.fatigueApplied());
        assertTrue(career.getSessionPlayer("p2").getInjured());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuryFlagTrue_plus_fatigueFlagFalse_appliesInjuriesOnly() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuryFlagFalse_plus_fatigueFlagTrue_appliesFatigueOnly() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(1, r.fatigueApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void masterFlagFalse_plus_fatigueFlagTrue_appliesNothing() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(false, false, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void persistInjuriesTrueAlone_doesNotTriggerFatigue() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, true, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.fatigueApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void fatigueApplierException_returnsPartialFailureResult() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        V24CareerMutationService failingService = new V24CareerMutationService(
                new V24InjuryMutationApplier(),
                new V24FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue applier failed");
                    }
                });

        V24CareerMutationResult r = failingService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.failures().get(0).contains("Fatigue applier failed"));
        assertEquals(0, r.fatigueApplied()); // fatigue applier threw, no count recorded
        assertFalse(r.partialFailure()); // no mutation succeeded
    }

    @Test
    void injuryApplierException_doesNotPreventFatigueFromApplying() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, true, true, false, false);

        V24CareerMutationService failingInjuryService = new V24CareerMutationService(
                new V24InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Injury applier failed");
                    }
                },
                new V24FatigueMutationApplier());

        V24CareerMutationResult r = failingInjuryService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.failures().get(0).contains("Injury applier failed"));
        assertTrue(r.partialFailure());
        assertEquals(1, r.fatigueApplied());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void fatigueApplierException_doesNotEraseAppliedInjuryResult() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, true, true, false, false);

        V24CareerMutationService failingFatigueService = new V24CareerMutationService(
                new V24InjuryMutationApplier(),
                new V24FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue applier failed");
                    }
                });

        V24CareerMutationResult r = failingFatigueService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.failures().get(0).contains("Fatigue applier failed"));
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void bothAppliersThrowing_returnsBothFailures_noPartialFailureWhenNoSuccess() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, true, true, false, false);

        V24CareerMutationService bothFailingService = new V24CareerMutationService(
                new V24InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Injury failed");
                    }
                },
                new V24FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue failed");
                    }
                });

        V24CareerMutationResult r = bothFailingService.applyMutations(career, res, pol);

        assertEquals(2, r.failures().size());
        assertFalse(r.partialFailure());
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
    }

    @Test
    void oneSuccess_oneFailure_returnsPartialFailureTrue() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        V24CareerMutationPolicy pol = policy(true, true, true, false, false);

        V24CareerMutationService oneFailingService = new V24CareerMutationService(
                new V24InjuryMutationApplier(),
                new V24FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue failed");
                    }
                });

        V24CareerMutationResult r = oneFailingService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
    }

    @Test
    void disciplineAndFormFlags_doNotTriggerMutationInV24D6C2() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, true, true);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
    }

    @Test
    void singleArgConstructor_injectsDefaultFatigueApplier() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(goalEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        V24CareerMutationResult r = singleArgService.applyMutations(career, res, pol);

        assertEquals(1, r.fatigueApplied());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    // ========== V24D6F1: Policy + Orchestration Regression Tests ==========

    @Test
    void masterFalse_plusInjuryAndFatigueBothTrue_appliesNothing() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(
                injuryEvent("p1", 45),
                goalEvent("p1", 60)
        );
        V24CareerMutationPolicy pol = policy(false, true, true, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void bothAppliersSucceed_together_correctCountsAndNoFailures() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(
                injuryEvent("p1", 45),
                goalEvent("p1", 60)
        );
        V24CareerMutationPolicy pol = policy(true, true, true, false, false);

        V24CareerMutationService serviceWithStubAppliers = new V24CareerMutationService(
                new V24InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) { return 2; }
                },
                new V24FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) { return 3; }
                });

        V24CareerMutationResult r = serviceWithStubAppliers.applyMutations(career, res, pol);

        assertEquals(2, r.injuriesApplied());
        assertEquals(3, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void disciplineFlagTrueAlone_doesNotTriggerMutation() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void formFlagTrueAlone_doesNotTriggerMutation() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        V24DetailedMatchResult res = result(injuryEvent("p1", 45));
        V24CareerMutationPolicy pol = policy(true, false, false, false, true);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void resultSuccess_withZeroCounts_hasNoFailures() {
        V24CareerMutationResult r = V24CareerMutationResult.success(0, 0, 0, 0);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void partialResult_withEmptyFailuresList_partialFalse() {
        V24CareerMutationResult r = V24CareerMutationResult.partial(0, 0, 0, 0,
                java.util.Collections.emptyList());

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void failure_withCountsAndMessages_preservesCountsAndPartialFlag() {
        java.util.ArrayList<String> messages = new java.util.ArrayList<>();
        messages.add("Injury failed");
        messages.add("Fatigue failed");

        V24CareerMutationResult r = V24CareerMutationResult.failure(2, 3, messages, true);

        assertEquals(2, r.injuriesApplied());
        assertEquals(3, r.fatigueApplied());
        assertEquals(2, r.failures().size());
        assertTrue(r.partialFailure());

        messages.add("Extra error");
        assertEquals(2, r.failures().size(), "Failures list must be defensive copy");
    }

    // ========== V24D6D4 Discipline orchestration tests ==========

    @Test
    void persistDisciplineEnabled_callsDisciplineApplier() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30), redCardEvent("p1", 70));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(2, r.disciplineApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void persistDisciplineDisabled_doesNotCallDisciplineApplier() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30), redCardEvent("p1", 70));
        V24CareerMutationPolicy pol = policy(true, false, false, false, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertTrue(r.failures().isEmpty());
        // Player discipline fields unchanged
        assertEquals(0, career.getSessionPlayer("p1").getYellowCards());
        assertEquals(0, career.getSessionPlayer("p1").getRedCards());
        assertFalse(career.getSessionPlayer("p1").getSuspended());
    }

    @Test
    void masterFalse_persistDisciplineTrue_doesNotCallDisciplineApplier() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(false, false, false, true, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertTrue(r.failures().isEmpty());
    }

    @Test
    void injuryFatigueDiscipline_independentCounts() {
        // Three players with distinct events; injury-player gets injured (fatigue skips),
        // leaving fatigue-player (goal) + discipline-player (yellow) = fatigueApplied 2
        // We only check that disciplineApplied=1, injuriesApplied=1, fatigueApplied>0
        // so we only assert discipline count here and adjust expectations below
        CareerSave career = careerWithPlayer("player-injury");
        career.addSessionPlayer(SessionPlayer.fromWorldPlayer("player-fatigue", "Fatigue Guy", "MID", 25, 70));
        career.addSessionPlayer(SessionPlayer.fromWorldPlayer("player-discipline", "Discipline Guy", "MID", 25, 70));

        V24DetailedMatchResult res = result(
                injuryEvent("player-injury", 30),
                goalEvent("player-fatigue", 45),
                yellowCardEvent("player-discipline", 60)
        );
        V24CareerMutationPolicy pol = policy(true, true, true, true, false);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        // injuries: player-injury
        assertEquals(1, r.injuriesApplied());
        // discipline: player-discipline gets yellow
        assertEquals(1, r.disciplineApplied());
        // fatigue: injury-player skipped (injured), fatigue-player + discipline-player = 2
        assertEquals(2, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void disciplineFailure_doesNotEraseInjuryFatigueSuccess() {
        // Distinct players: injury-player gets injured (fatigue applier skips),
        // fatigue-player is healthy, discipline-player gets card
        CareerSave career = careerWithPlayer("injury-player");
        career.addSessionPlayer(SessionPlayer.fromWorldPlayer("fatigue-player", "FP", "MID", 25, 70));
        career.addSessionPlayer(SessionPlayer.fromWorldPlayer("discipline-player", "DP", "MID", 25, 70));

        V24DetailedMatchResult res = result(
                injuryEvent("injury-player", 30),
                goalEvent("fatigue-player", 45),
                yellowCardEvent("discipline-player", 60)
        );
        V24CareerMutationPolicy pol = policy(true, true, true, true, false);

        V24CareerMutationService badService = new V24CareerMutationService(
                new V24InjuryMutationApplier(),
                new V24FatigueMutationApplier(),
                new V24DisciplineMutationApplier() {
                    @Override
                    public int applyDiscipline(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("boom discipline");
                    }
                }
        );

        V24CareerMutationResult r = badService.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        // fatigue: injury-player skipped (injured), fatigue-player + discipline-player = 2
        assertEquals(2, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertFalse(r.failures().isEmpty());
        assertTrue(r.partialFailure());
    }

    @Test
    void disciplineFailure_preservesFailureMessage() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, false);

        V24CareerMutationService badService = new V24CareerMutationService(
                new V24InjuryMutationApplier(),
                new V24FatigueMutationApplier(),
                new V24DisciplineMutationApplier() {
                    @Override
                    public int applyDiscipline(CareerSave c, V24DetailedMatchResult r,
                            V24CareerMutationPolicy p) {
                        throw new RuntimeException("boom discipline");
                    }
                }
        );

        V24CareerMutationResult r = badService.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertFalse(r.failures().isEmpty());
        assertTrue(r.failures().get(0).contains("boom discipline"));
    }

    @Test
    void disciplineAndFormFlags_independent_formStillNoop() {
        CareerSave career = careerWithPlayer("p1");
        V24DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        V24CareerMutationPolicy pol = policy(true, false, false, true, true);

        V24CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
    }
}
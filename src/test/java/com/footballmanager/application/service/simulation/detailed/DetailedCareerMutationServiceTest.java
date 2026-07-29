package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests mutation orchestration in isolation — no Spring, no Redis, no IO.
 */
class CareerMutationServiceTest {

    private final InjuryMutationApplier injuryApplier = new InjuryMutationApplier();
    private final FatigueMutationApplier fatigueApplier = new FatigueMutationApplier();
    private final DisciplineMutationApplier disciplineApplier = new DisciplineMutationApplier();
    private final CareerMutationService service = new CareerMutationService(injuryApplier, fatigueApplier, disciplineApplier);

    // Backwards-compatible service (fatigue + discipline appliers injected internally)
    private final CareerMutationService singleArgService = new CareerMutationService(injuryApplier);

    // ========== Helper builders ==========

    private CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private CareerSave careerWithPlayer(String playerId) {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Player", "MID", 25, 70);
        career.addSessionPlayer(p);
        return career;
    }

    private DetailedMatchEvent injuryEvent(String playerId, int minute) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.INJURY,
                "team-A", playerId, "Injured", null, null, 0.0, "Injury");
    }

    private DetailedMatchEvent goalEvent(String playerId, int minute) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.GOAL,
                "team-A", playerId, "Goal", null, null, 0.35, "Goal");
    }

    private DetailedMatchEvent yellowCardEvent(String playerId, int minute) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.YELLOW_CARD,
                "team-A", playerId, "Card Player", null, null, 0.0, "Foul");
    }

    private DetailedMatchEvent redCardEvent(String playerId, int minute) {
        return new DetailedMatchEvent(minute, DetailedMatchEventType.RED_CARD,
                "team-A", playerId, "Sent Off", null, null, 0.0, "Second yellow");
    }

    private DetailedMatchResult result(DetailedMatchEvent... events) {
        MatchTimeline timeline = new MatchTimeline();
        for (DetailedMatchEvent e : events) timeline.addEvent(e);
        return DetailedMatchResult.builder()
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
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        CareerMutationResult r = service.applyMutations(null, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void nullResult_returnsEmptyResult() {
        CareerSave career = careerWithPlayer("p1");
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        CareerMutationResult r = service.applyMutations(career, null, pol);

        assertEquals(0, r.injuriesApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void nullPolicy_returnsEmptyResult() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));

        CareerMutationResult r = service.applyMutations(career, res, null);

        assertEquals(0, r.injuriesApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    // ========== Flag-enabling tests ==========

    @Test
    void masterFlagFalse_returnsEmptyAndDoesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(false, true, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void masterTrue_butAllSpecificFlagsFalse_returnsEmptyResult() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void injuryFlagTrue_appliesInjuriesAndReportsCount() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 67));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void injuryFlagFalse_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void fatigueFlagTrueAlone_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        // result has INJURY event but injuries=false so injury not applied
        // but fatigue=true so fatigue IS applied (player participated)
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, true, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(1, r.fatigueApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void disciplineFlagTrueAlone_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void formFlagTrueAlone_doesNotApplyInjuries() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, false, false, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    // ========== Exception-handling tests ==========

    @Test
    void injuryApplierException_returnsFailureResult() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        // Force exception by passing a null timeline
        CareerMutationService bogusService =
                new CareerMutationService(new InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Simulated failure");
                    }
                });

        CareerMutationResult r = bogusService.applyMutations(career, res, pol);

        assertFalse(r.failures().isEmpty());
        assertTrue(r.failures().get(0).contains("Simulated failure"));
        assertFalse(r.partialFailure());
    }

    // ========== Result object tests ==========

    @Test
    void failureResult_hasFailuresAndPartialFalse() {
        CareerMutationResult r = CareerMutationResult.failure("Test error");

        assertEquals(0, r.injuriesApplied());
        assertFalse(r.failures().isEmpty());
        assertEquals("Test error", r.failures().get(0));
        assertFalse(r.partialFailure());
    }

    @Test
    void successfulInjuryMutation_hasEmptyFailuresAndPartialFalse() {
        CareerMutationResult r = CareerMutationResult.success(2, 0, 0, 0);

        assertEquals(2, r.injuriesApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void resultObject_defensivelyCopiesFailuresList() {
        java.util.ArrayList<String> input = new java.util.ArrayList<>();
        input.add("error1");

        CareerMutationResult r = CareerMutationResult.partial(1, 0, 0, 0, input);

        input.add("error2"); // mutate original
        assertEquals(1, r.failures().size()); // result unchanged
        assertEquals("error1", r.failures().get(0));
    }

    @Test
    void emptyResult_hasAllCountsZero() {
        CareerMutationResult r = CareerMutationResult.empty();

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
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        service.applyMutations(career, res, pol);

        assertFalse(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void serviceDoesNotTouchFatigueOrEnergy() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(60);
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        service.applyMutations(career, res, pol);

        assertEquals(60, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void serviceDoesNotTouchForm() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setForm(75);
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        service.applyMutations(career, res, pol);

        assertEquals(75, career.getSessionPlayer("p1").getForm());
    }

    @Test
    void noMatchFixtureResultDataSchemaImpact() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertNotNull(res);
    }

    @Test
    void fatigueFlagTrue_appliesFatigueAndReportsFatigueAppliedCount() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, true, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.fatigueApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void fatigueFlagFalse_doesNotApplyFatigue() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.fatigueApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuryFlagTrue_plus_fatigueFlagTrue_appliesBothAndReportsBothCounts() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        // p1 has only a goal (fatigue participation)
        // p2 has only an injury (injury participation) — different player
        DetailedMatchEvent p1Goal = new DetailedMatchEvent(60, DetailedMatchEventType.GOAL, "team-A", "p1", "Goal", null, null, 0.35, "Goal");
        DetailedMatchEvent p2Injury = new DetailedMatchEvent(45, DetailedMatchEventType.INJURY, "team-A", "p2", "Injured", null, null, 0.0, "Injury");
        SessionPlayer p2 = SessionPlayer.fromWorldPlayer("p2", "Player2", "MID", 25, 70);
        career.addSessionPlayer(p2);
        p2.setEnergy(100);
        DetailedMatchResult res = result(p1Goal, p2Injury);
        CareerMutationPolicy pol = policy(true, true, true, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertEquals(1, r.fatigueApplied());
        assertTrue(career.getSessionPlayer("p2").getInjured());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuryFlagTrue_plus_fatigueFlagFalse_appliesInjuriesOnly() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuryFlagFalse_plus_fatigueFlagTrue_appliesFatigueOnly() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, false, true, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.injuriesApplied());
        assertEquals(1, r.fatigueApplied());
        assertFalse(career.getSessionPlayer("p1").getInjured());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void masterFlagFalse_plus_fatigueFlagTrue_appliesNothing() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(false, false, true, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void persistInjuriesTrueAlone_doesNotTriggerFatigue() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, true, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.fatigueApplied());
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void fatigueApplierException_returnsPartialFailureResult() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, true, false, false);

        CareerMutationService failingService = new CareerMutationService(
                new InjuryMutationApplier(),
                new FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue applier failed");
                    }
                });

        CareerMutationResult r = failingService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.failures().get(0).contains("Fatigue applier failed"));
        assertEquals(0, r.fatigueApplied()); // fatigue applier threw, no count recorded
        assertFalse(r.partialFailure()); // no mutation succeeded
    }

    @Test
    void injuryApplierException_doesNotPreventFatigueFromApplying() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, true, true, false, false);

        CareerMutationService failingInjuryService = new CareerMutationService(
                new InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Injury applier failed");
                    }
                },
                new FatigueMutationApplier());

        CareerMutationResult r = failingInjuryService.applyMutations(career, res, pol);

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
        DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, true, true, false, false);

        CareerMutationService failingFatigueService = new CareerMutationService(
                new InjuryMutationApplier(),
                new FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue applier failed");
                    }
                });

        CareerMutationResult r = failingFatigueService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.failures().get(0).contains("Fatigue applier failed"));
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
        assertTrue(career.getSessionPlayer("p1").getInjured());
    }

    @Test
    void bothAppliersThrowing_returnsBothFailures_noPartialFailureWhenNoSuccess() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, true, true, false, false);

        CareerMutationService bothFailingService = new CareerMutationService(
                new InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Injury failed");
                    }
                },
                new FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue failed");
                    }
                });

        CareerMutationResult r = bothFailingService.applyMutations(career, res, pol);

        assertEquals(2, r.failures().size());
        assertFalse(r.partialFailure());
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
    }

    @Test
    void oneSuccess_oneFailure_returnsPartialFailureTrue() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(injuryEvent("p1", 45), goalEvent("p1", 60));
        CareerMutationPolicy pol = policy(true, true, true, false, false);

        CareerMutationService oneFailingService = new CareerMutationService(
                new InjuryMutationApplier(),
                new FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("Fatigue failed");
                    }
                });

        CareerMutationResult r = oneFailingService.applyMutations(career, res, pol);

        assertEquals(1, r.failures().size());
        assertTrue(r.partialFailure());
        assertEquals(1, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
    }

    @Test
    void disciplineAndFormFlags_doNotTriggerMutationInDetailedSprint6C2() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, true, true, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
    }

    @Test
    void singleArgConstructor_injectsDefaultFatigueApplier() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, true, false, false);

        CareerMutationResult r = singleArgService.applyMutations(career, res, pol);

        assertEquals(1, r.fatigueApplied());
        assertEquals(88, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void masterFalse_plusInjuryAndFatigueBothTrue_appliesNothing() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(
                injuryEvent("p1", 45),
                goalEvent("p1", 60)
        );
        CareerMutationPolicy pol = policy(false, true, true, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

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
        DetailedMatchResult res = result(
                injuryEvent("p1", 45),
                goalEvent("p1", 60)
        );
        CareerMutationPolicy pol = policy(true, true, true, false, false);

        CareerMutationService serviceWithStubAppliers = new CareerMutationService(
                new InjuryMutationApplier() {
                    @Override
                    public int applyInjuries(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) { return 2; }
                },
                new FatigueMutationApplier() {
                    @Override
                    public int applyFatigue(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) { return 3; }
                });

        CareerMutationResult r = serviceWithStubAppliers.applyMutations(career, res, pol);

        assertEquals(2, r.injuriesApplied());
        assertEquals(3, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void disciplineFlagTrueAlone_doesNotTriggerMutation() {
        CareerSave career = careerWithPlayer("p1");
        career.getSessionPlayer("p1").setEnergy(100);
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

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
        DetailedMatchResult res = result(injuryEvent("p1", 45));
        CareerMutationPolicy pol = policy(true, false, false, false, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

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
        CareerMutationResult r = CareerMutationResult.success(0, 0, 0, 0);

        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void partialResult_withEmptyFailuresList_partialFalse() {
        CareerMutationResult r = CareerMutationResult.partial(0, 0, 0, 0,
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

        CareerMutationResult r = CareerMutationResult.failure(2, 3, messages, true);

        assertEquals(2, r.injuriesApplied());
        assertEquals(3, r.fatigueApplied());
        assertEquals(2, r.failures().size());
        assertTrue(r.partialFailure());

        messages.add("Extra error");
        assertEquals(2, r.failures().size(), "Failures list must be defensive copy");
    }

    @Test
    void persistDisciplineEnabled_callsDisciplineApplier() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(yellowCardEvent("p1", 30), redCardEvent("p1", 70));
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(2, r.disciplineApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    @Test
    void persistDisciplineDisabled_doesNotCallDisciplineApplier() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(yellowCardEvent("p1", 30), redCardEvent("p1", 70));
        CareerMutationPolicy pol = policy(true, false, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

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
        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(false, false, false, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

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

        DetailedMatchResult res = result(
                injuryEvent("player-injury", 30),
                goalEvent("player-fatigue", 45),
                yellowCardEvent("player-discipline", 60)
        );
        CareerMutationPolicy pol = policy(true, true, true, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

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

        DetailedMatchResult res = result(
                injuryEvent("injury-player", 30),
                goalEvent("fatigue-player", 45),
                yellowCardEvent("discipline-player", 60)
        );
        CareerMutationPolicy pol = policy(true, true, true, true, false);

        CareerMutationService badService = new CareerMutationService(
                new InjuryMutationApplier(),
                new FatigueMutationApplier(),
                new DisciplineMutationApplier() {
                    @Override
                    public int applyDiscipline(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("boom discipline");
                    }
                }
        );

        CareerMutationResult r = badService.applyMutations(career, res, pol);

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
        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationService badService = new CareerMutationService(
                new InjuryMutationApplier(),
                new FatigueMutationApplier(),
                new DisciplineMutationApplier() {
                    @Override
                    public int applyDiscipline(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("boom discipline");
                    }
                }
        );

        CareerMutationResult r = badService.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertFalse(r.failures().isEmpty());
        assertTrue(r.failures().get(0).contains("boom discipline"));
    }

    @Test
    void disciplineAndFormFlags_independent_formStillNoop() {
        CareerSave career = careerWithPlayer("p1");
        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, true, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.disciplineApplied());
        assertEquals(0, r.formApplied());
        assertTrue(r.failures().isEmpty());
    }

    /**
     * 1. thresholdContributesToDisciplineAppliedCount_orDocumentedCountSemantics
     *
     * Verifies that a yellow-card threshold suspension contributes +1 to disciplineApplied
     * through the existing int return value of applyDiscipline(). No new result field needed.
     * disciplineApplied = 2 (1 yellow event + 1 threshold suspension).
     */
    @Test
    void thresholdContributesToDisciplineAppliedCount_orDocumentedCountSemantics() {
        CareerSave career = careerWithPlayer("p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4); // one YELLOW_CARD will bring to 5

        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        // applied count = 1 yellow event + 1 threshold suspension = 2
        assertEquals(2, r.disciplineApplied());
        assertEquals(0, p.getYellowCards()); // 5 - 5 = 0
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    /**
     * 2a. disabledPolicy_noDisciplineMutationAndNoThresholdEffect — master false
     */
    @Test
    void disabledPolicy_noDisciplineMutationAndNoThresholdEffect_masterFalse() {
        CareerSave career = careerWithPlayer("p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(false, false, false, true, false); // master=false

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertEquals(4, p.getYellowCards()); // unchanged
        assertFalse(p.getSuspended());
        assertEquals(0, p.getSuspensionRemainingMatches());
        assertTrue(r.failures().isEmpty());
    }

    /**
     * 2b. disabledPolicy_noDisciplineMutationAndNoThresholdEffect — discipline flag false
     */
    @Test
    void disabledPolicy_noDisciplineMutationAndNoThresholdEffect_disciplineFlagFalse() {
        CareerSave career = careerWithPlayer("p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, false, false); // discipline=false

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertEquals(4, p.getYellowCards()); // unchanged
        assertFalse(p.getSuspended());
        assertEquals(0, p.getSuspensionRemainingMatches());
        assertTrue(r.failures().isEmpty());
    }

    /**
     * 3. thresholdAndRedCard_bothTrackedCorrectly
     *
     * Player A: yellowCards=4 + YELLOW_CARD → threshold suspension
     * Player B: RED_CARD → red suspension
     * Both contribute to disciplineApplied: 1 yellow + 1 threshold + 1 red = 3
     */
    @Test
    void thresholdAndRedCard_bothTrackedCorrectly() {
        CareerSave career = careerWithPlayer("player-a");
        SessionPlayer playerA = career.getSessionPlayer("player-a");
        playerA.setYellowCards(4);

        SessionPlayer playerB = SessionPlayer.fromWorldPlayer("player-b", "Player B", "DEF", 25, 70);
        career.addSessionPlayer(playerB);

        DetailedMatchResult res = result(
                yellowCardEvent("player-a", 25), // triggers threshold: 5 → suspended, yellowCards=0
                redCardEvent("player-b", 70)     // red suspension: suspended, redCards=1
        );
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        // disciplineApplied = yellow(1) + threshold(1) + red(1) = 3
        assertEquals(3, r.disciplineApplied());
        assertEquals(0, playerA.getYellowCards()); // 5 - 5 = 0
        assertTrue(playerA.getSuspended());
        assertEquals(1, playerA.getSuspensionRemainingMatches());
        assertEquals(0, playerA.getRedCards());
        assertTrue(playerB.getSuspended());
        assertEquals(1, playerB.getSuspensionRemainingMatches());
        assertEquals(1, playerB.getRedCards());
        assertEquals(0, playerB.getYellowCards());
        assertTrue(r.failures().isEmpty());
    }

    /**
     * 4. thresholdDoesNotBreakInjuryFatigueCounts
     *
     * Injury + fatigue + discipline (with threshold) all tracked independently.
     * Confirms threshold logic does not interfere with injury/fatigue applier counts.
     */
    @Test
    void thresholdDoesNotBreakInjuryFatigueCounts() {
        CareerSave career = careerWithPlayer("player-injury");
        SessionPlayer injuryPlayer = career.getSessionPlayer("player-injury");
        injuryPlayer.setEnergy(100);

        SessionPlayer fatiguePlayer = SessionPlayer.fromWorldPlayer("player-fatigue", "FP", "MID", 25, 70);
        fatiguePlayer.setEnergy(100);
        career.addSessionPlayer(fatiguePlayer);

        SessionPlayer disciplinePlayer = SessionPlayer.fromWorldPlayer("player-discipline", "DP", "MID", 25, 70);
        disciplinePlayer.setYellowCards(4); // threshold on first yellow card
        career.addSessionPlayer(disciplinePlayer);

        DetailedMatchResult res = result(
                injuryEvent("player-injury", 30),
                goalEvent("player-fatigue", 45), // fatigue participation
                yellowCardEvent("player-discipline", 60) // triggers threshold
        );
        CareerMutationPolicy pol = policy(true, true, true, true, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        // injuriesApplied: player-injury
        assertEquals(1, r.injuriesApplied());
        assertTrue(injuryPlayer.getInjured());

        // fatigueApplied: injury-player skipped (injured), fatigue-player + discipline-player = 2
        // (disciplinePlayer appears in yellow card event, counts as participating)
        assertEquals(2, r.fatigueApplied());
        assertEquals(88, fatiguePlayer.getEnergy());

        // disciplineApplied: yellow(1) + threshold(1) = 2
        assertEquals(2, r.disciplineApplied());
        assertEquals(0, disciplinePlayer.getYellowCards()); // 5 - 5 = 0
        assertTrue(disciplinePlayer.getSuspended());
        assertEquals(1, disciplinePlayer.getSuspensionRemainingMatches());

        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }

    /**
     * 5. disciplineFailure_semanticsUnchangedForThreshold
     *
     * Uses existing mock pattern from existing disciplineFailure tests.
     * When discipline applier throws, failure semantics are identical to pre-H2.
     * Verifies threshold failure handling is no different than red/yellow failure.
     */
    @Test
    void disciplineFailure_semanticsUnchangedForThreshold() {
        CareerSave career = careerWithPlayer("p1");
        SessionPlayer p = career.getSessionPlayer("p1");
        p.setYellowCards(4);

        DetailedMatchResult res = result(yellowCardEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, true, false);

        CareerMutationService badService = new CareerMutationService(
                new InjuryMutationApplier(),
                new FatigueMutationApplier(),
                new DisciplineMutationApplier() {
                    @Override
                    public int applyDiscipline(CareerSave c, DetailedMatchResult r,
                            CareerMutationPolicy p) {
                        throw new RuntimeException("boom discipline");
                    }
                }
        );

        CareerMutationResult r = badService.applyMutations(career, res, pol);

        assertEquals(0, r.disciplineApplied());
        assertFalse(r.failures().isEmpty());
        assertTrue(r.failures().get(0).contains("boom discipline"));
        assertFalse(r.partialFailure()); // no mutation succeeded
    }

    @Test
    void persistFormEnabled_callsFormApplier() {
        // p1 in starting XI with a goal -> rating 6.8 -> delta +1 -> form 51
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer("p1", "Player p1", "MID", 25, 70);
        p.setForm(50);
        career.addSessionPlayer(p);
        career.setTeamStarting11(java.util.Map.of("team-A", List.of("p1"), "team-B", List.of()));
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, false, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.formApplied());
        assertEquals(0, r.injuriesApplied());
        assertEquals(0, r.fatigueApplied());
        assertEquals(0, r.disciplineApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
        assertEquals(51, career.getSessionPlayer("p1").getForm());
    }

    @Test
    void persistFormDisabled_doesNotCallFormApplier() {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer("p1", "Player p1", "MID", 25, 70);
        p.setForm(50);
        career.addSessionPlayer(p);
        career.setTeamStarting11(java.util.Map.of("team-A", List.of("p1"), "team-B", List.of()));
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(true, false, false, false, false);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.formApplied());
        assertEquals(50, career.getSessionPlayer("p1").getForm());
    }

    @Test
    void masterFalse_persistFormTrue_doesNotCallFormApplier() {
        CareerSave career = new CareerSave();
        SessionPlayer p = SessionPlayer.fromWorldPlayer("p1", "Player p1", "MID", 25, 70);
        p.setForm(50);
        career.addSessionPlayer(p);
        career.setTeamStarting11(java.util.Map.of("team-A", List.of("p1"), "team-B", List.of()));
        DetailedMatchResult res = result(goalEvent("p1", 30));
        CareerMutationPolicy pol = policy(false, false, false, false, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(0, r.formApplied());
        assertEquals(50, career.getSessionPlayer("p1").getForm());
    }

    /**
     *
     * FormMutationApplier is final — cannot be stubbed via anonymous subclass.
     * Real applier always succeeds with valid inputs (no failure path to trigger).
     * This test verifies form success alongside other mutations using real applier.
     */
    @Test
    void formMutationSucceedsAlongsideOtherMutations() {
        // 2 players: p1 gets goal (form +1), p2 gets yellow (discipline +1)
        // Form applier sees both in starting XI — p1 gets rating 6.8 (+1), p2 gets 5.7 (0)
        CareerSave career = new CareerSave();
        SessionPlayer p1 = SessionPlayer.fromWorldPlayer("p1-goal", "Goal Guy", "MID", 25, 70);
        SessionPlayer p2 = SessionPlayer.fromWorldPlayer("p2-yellow", "Card Guy", "MID", 25, 70);
        p1.setForm(50);
        p2.setYellowCards(0);
        career.addSessionPlayer(p1);
        career.addSessionPlayer(p2);
        career.setTeamStarting11(java.util.Map.of(
                "team-A", java.util.List.of("p1-goal", "p2-yellow"),
                "team-B", java.util.List.of()
        ));
        DetailedMatchResult res = result(
                goalEvent("p1-goal", 30),
                yellowCardEvent("p2-yellow", 50)
        );
        CareerMutationPolicy pol = policy(true, false, false, true, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.disciplineApplied());
        // formApplied: ALL starting XI players get a rating entry (applier counts every player, delta may be 0)
        assertEquals(2, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
        // p1-goal: 6.8 → +1 = form 51; p2-yellow: 5.7 → 0 = form unchanged at null→50
        assertEquals(51, career.getSessionPlayer("p1-goal").getForm());
        assertEquals(50, career.getSessionPlayer("p2-yellow").getForm());
        assertEquals(1, career.getSessionPlayer("p2-yellow").getYellowCards());
    }

    @Test
    void allMutationFlagsEnabled_includesForm() {
        // 4 players in starting XI, all get form ratings via real applier
        // p1-injury: injured (no form change since no event for them in form terms)
        // p2-fatigue: goal (fatigue + form)
        // p3-discipline: yellow (discipline + form)
        // p4-form: goal (form only)
        CareerSave career = new CareerSave();
        SessionPlayer p1 = SessionPlayer.fromWorldPlayer("p1-injury", "Injury Guy", "MID", 25, 70);
        SessionPlayer p2 = SessionPlayer.fromWorldPlayer("p2-fatigue", "Fatigue Guy", "MID", 25, 70);
        SessionPlayer p3 = SessionPlayer.fromWorldPlayer("p3-discipline", "Card Guy", "MID", 25, 70);
        SessionPlayer p4 = SessionPlayer.fromWorldPlayer("p4-form", "Form Guy", "MID", 25, 70);
        p1.setInjured(false);
        p2.setEnergy(100);
        p3.setYellowCards(0);
        p4.setForm(50);
        career.addSessionPlayer(p1);
        career.addSessionPlayer(p2);
        career.addSessionPlayer(p3);
        career.addSessionPlayer(p4);
        career.setTeamStarting11(java.util.Map.of(
                "team-A", java.util.List.of("p1-injury", "p2-fatigue", "p3-discipline", "p4-form"),
                "team-B", java.util.List.of()
        ));
        DetailedMatchResult res = result(
                injuryEvent("p1-injury", 30),
                goalEvent("p2-fatigue", 45),
                yellowCardEvent("p3-discipline", 60),
                goalEvent("p4-form", 75)
        );
        CareerMutationPolicy pol = policy(true, true, true, true, true);

        CareerMutationResult r = service.applyMutations(career, res, pol);

        assertEquals(1, r.injuriesApplied());
        // fatigue: p2 + p3 + p4 participated (p1-injury skipped due to injury)
        assertEquals(3, r.fatigueApplied());
        assertEquals(1, r.disciplineApplied());
        // form: all 4 in starting XI get ratings (p1:6.0, p2:6.8, p3:5.7, p4:6.8)
        assertEquals(4, r.formApplied());
        assertTrue(r.failures().isEmpty());
        assertFalse(r.partialFailure());
    }
}
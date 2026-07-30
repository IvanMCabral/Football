package com.footballmanager.application.service.simulation.detailed;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

record DetailedMatchMinuteComposition(
        MinuteTacticalPolicies tacticalPolicies,
        MinuteEventPolicies eventPolicies,
        MinutePlayerStatePolicies playerStatePolicies,
        MinuteSubstitutionPolicies substitutionPolicies) {

    static DetailedMatchMinuteComposition create(
            DisciplineModel disciplineModel,
            AtomicInteger goalAdditions,
            Logger log) {
        FatigueModel fatigueModel = new FatigueModel();
        InjuryModel injuryModel = new InjuryModel();
        ShotXgCalculator xgCalculator = new ShotXgCalculator();
        AssistModel assistModel = new AssistModel();
        ShotLocationService shotLocationService = new ShotLocationService();
        TacticalPositionService tacticalPositionService = new TacticalPositionService();
        TacticalEffectivenessService tacticalEffectivenessService = new TacticalEffectivenessService(tacticalPositionService);
        DefenseChannelService defenseChannelService = new DefenseChannelService(
                tacticalPositionService, tacticalEffectivenessService);
        AttackContributionService attackContributionService = new AttackContributionService(tacticalEffectivenessService);
        TacticalShapeService tacticalShapeService = new TacticalShapeService(
                tacticalPositionService, tacticalEffectivenessService);
        MatchProbabilityService matchProbabilityService = new MatchProbabilityService();
        ShotAttemptService shotAttemptService = new ShotAttemptService(
                xgCalculator,
                fatigueModel,
                assistModel,
                shotLocationService,
                defenseChannelService,
                attackContributionService,
                matchProbabilityService,
                goalAdditions,
                log);
        return new DetailedMatchMinuteComposition(
                new MinuteTacticalPolicies(
                        defenseChannelService,
                        attackContributionService,
                        tacticalShapeService,
                        matchProbabilityService,
                        new EffectiveSlotService(),
                        new PlayerSkillService()),
                new MinuteEventPolicies(shotAttemptService, new CardEventService()),
                new MinutePlayerStatePolicies(fatigueModel, disciplineModel, injuryModel),
                new MinuteSubstitutionPolicies(new SubstitutionEngine()));
    }
}

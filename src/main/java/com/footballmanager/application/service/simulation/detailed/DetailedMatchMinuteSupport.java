package com.footballmanager.application.service.simulation.detailed;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

final class DetailedMatchMinuteSupport {
    final FatigueModel fatigueModel;
    final DisciplineModel disciplineModel;
    final InjuryModel injuryModel;
    final SubstitutionEngine substitutionEngine;
    final DefenseChannelService defenseChannelService;
    final AttackContributionService attackContributionService;
    final TacticalShapeService tacticalShapeService;
    final MatchProbabilityService matchProbabilityService;
    final ShotAttemptService shotAttemptService;
    final EffectiveSlotService effectiveSlotService;
    final CardEventService cardEventService;
    final PlayerSkillService playerSkillService;

    DetailedMatchMinuteSupport(DisciplineModel disciplineModel, AtomicInteger goalAdditions, Logger log) {
        this.fatigueModel = new FatigueModel();
        this.disciplineModel = disciplineModel;
        this.injuryModel = new InjuryModel();
        this.substitutionEngine = new SubstitutionEngine();
        ShotXgCalculator xgCalculator = new ShotXgCalculator();
        AssistModel assistModel = new AssistModel();
        ShotLocationService shotLocationService = new ShotLocationService();
        TacticalPositionService tacticalPositionService = new TacticalPositionService();
        TacticalEffectivenessService tacticalEffectivenessService = new TacticalEffectivenessService(tacticalPositionService);
        this.defenseChannelService = new DefenseChannelService(tacticalPositionService, tacticalEffectivenessService);
        this.attackContributionService = new AttackContributionService(tacticalEffectivenessService);
        this.tacticalShapeService = new TacticalShapeService(tacticalPositionService, tacticalEffectivenessService);
        this.matchProbabilityService = new MatchProbabilityService();
        this.shotAttemptService = new ShotAttemptService(xgCalculator, fatigueModel, assistModel, shotLocationService,
                defenseChannelService, attackContributionService, matchProbabilityService, goalAdditions, log);
        this.effectiveSlotService = new EffectiveSlotService();
        this.cardEventService = new CardEventService();
        this.playerSkillService = new PlayerSkillService();
    }
}

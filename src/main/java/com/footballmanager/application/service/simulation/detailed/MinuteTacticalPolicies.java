package com.footballmanager.application.service.simulation.detailed;

record MinuteTacticalPolicies(
        DefenseChannelService defenseChannelService,
        AttackContributionService attackContributionService,
        TacticalShapeService tacticalShapeService,
        MatchProbabilityService matchProbabilityService,
        EffectiveSlotService effectiveSlotService,
        PlayerSkillService playerSkillService) {
}

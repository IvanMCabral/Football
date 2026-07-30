package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;

final class MinuteTacticalStatePhase {
    private static final double HOME_POSSESSION_ADVANTAGE = 1.035;
    private static final double AWAY_POSSESSION_FRICTION = 0.985;

    private final MinuteTacticalPolicies tacticalPolicies;

    MinuteTacticalStatePhase(MinuteTacticalPolicies tacticalPolicies) {
        this.tacticalPolicies = tacticalPolicies;
    }

    MinuteTacticalState resolve(MinuteSimulationInput minuteContext) {
        int minute = minuteContext.minute();
        int homeMaxPasser = tacticalPolicies.playerSkillService().maxSkill(
                minuteContext.homeState().startingPlayers(), PlayerSkill.PASSER);
        int awayMaxPasser = tacticalPolicies.playerSkillService().maxSkill(
                minuteContext.awayState().startingPlayers(), PlayerSkill.PASSER);
        Map<String, LineupSlot> homeEffectiveSlots = tacticalPolicies.effectiveSlotService().effectiveSlotsForMinute(
                minuteContext.matchContext().homeSlotsByPlayerId(),
                minuteContext.matchContext().manualSubstitutions(),
                minuteContext.matchContext().homeTeamId(),
                minute);
        Map<String, LineupSlot> awayEffectiveSlots = tacticalPolicies.effectiveSlotService().effectiveSlotsForMinute(
                minuteContext.matchContext().awaySlotsByPlayerId(),
                minuteContext.matchContext().manualSubstitutions(),
                minuteContext.matchContext().awayTeamId(),
                minute);
        TacticalShapeProfile homeShape = tacticalPolicies.tacticalShapeService().tacticalShapeProfile(
                minuteContext.homeState(), minuteContext.matchContext().homeFormation(), homeEffectiveSlots);
        TacticalShapeProfile awayShape = tacticalPolicies.tacticalShapeService().tacticalShapeProfile(
                minuteContext.awayState(), minuteContext.matchContext().awayFormation(), awayEffectiveSlots);
        double homePossAdj = minuteContext.homePossBase() * (1.0 + homeMaxPasser / 300.0)
                * homeShape.possessionMultiplier()
                * HOME_POSSESSION_ADVANTAGE;
        double awayPossAdj = minuteContext.awayPossBase() * (1.0 + awayMaxPasser / 300.0)
                * awayShape.possessionMultiplier()
                * AWAY_POSSESSION_FRICTION;
        double homeShare = homePossAdj / (homePossAdj + awayPossAdj);
        return new MinuteTacticalState(
                homeMaxPasser, awayMaxPasser, homeEffectiveSlots, awayEffectiveSlots, homeShape, awayShape, homeShare);
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        return tacticalPolicies.tacticalShapeService().tacticalShapeProfile(team, formation, slotsByPlayerId);
    }
}

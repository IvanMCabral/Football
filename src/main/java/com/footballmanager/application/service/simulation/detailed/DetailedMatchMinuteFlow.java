package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

final class DetailedMatchMinuteFlow {
    private final DetailedMatchMinutePipeline pipeline;

    DetailedMatchMinuteFlow(DisciplineModel disciplineModel, Logger log) {
        DetailedMatchMinuteComposition composition = DetailedMatchMinuteComposition.create(disciplineModel, log);
        this.pipeline = new DetailedMatchMinutePipeline(
                new MinuteScheduledSubstitutionPhase(log, composition.substitutionPolicies()),
                new MinuteTacticalStatePhase(composition.tacticalPolicies()),
                new MinutePossessionPhase(composition.playerStatePolicies()),
                new MinuteAttackPhase(
                        composition.tacticalPolicies(), composition.eventPolicies(), composition.playerStatePolicies()),
                new MinuteDisciplinePhase(composition.playerStatePolicies(), composition.eventPolicies()),
                new MinuteInjuryPhase(composition.playerStatePolicies().injuryModel()),
                new MinuteRestartEventPhase(),
                new MinuteAutomaticSubstitutionPhase(composition.substitutionPolicies()));
    }

    MinuteSimulationResult processMinute(MinuteSimulationInput minuteContext) {
        return pipeline.processMinute(minuteContext);
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        return pipeline.tacticalShapeProfile(team, formation, slotsByPlayerId);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return pipeline.aggregateOpponentDefenderSkills(opponents);
    }

    void applyYellowCardAndMaybeSecondYellowRed(PlayerMatchState player, MatchTimeline timeline, int minute, String teamRole) {
        pipeline.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
    }
}

package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class DetailedMatchMinuteFlow {
    private final DetailedMatchMinuteSupport support;
    private final MinuteScheduledSubstitutionPhase scheduledSubstitutionPhase;
    private final MinuteTacticalStatePhase tacticalStatePhase;
    private final MinutePossessionPhase possessionPhase;
    private final MinuteAttackPhase attackPhase;
    private final MinuteDisciplinePhase disciplinePhase;
    private final MinutePhysicalStatePhase physicalStatePhase;
    private final MinuteAutomaticSubstitutionPhase automaticSubstitutionPhase;

    DetailedMatchMinuteFlow(DisciplineModel disciplineModel, AtomicInteger goalAdditions, Logger log) {
        this.support = new DetailedMatchMinuteSupport(disciplineModel, goalAdditions, log);
        this.scheduledSubstitutionPhase = new MinuteScheduledSubstitutionPhase(log);
        this.tacticalStatePhase = new MinuteTacticalStatePhase(support);
        this.possessionPhase = new MinutePossessionPhase(support);
        this.attackPhase = new MinuteAttackPhase(support);
        this.disciplinePhase = new MinuteDisciplinePhase(support);
        this.physicalStatePhase = new MinutePhysicalStatePhase(support);
        this.automaticSubstitutionPhase = new MinuteAutomaticSubstitutionPhase(support);
    }

    void processMinute(MinuteSimulationContext minuteContext) {
        scheduledSubstitutionPhase.apply(minuteContext);
        MinuteTacticalState tacticalState = tacticalStatePhase.resolve(minuteContext);
        MinutePossessionState possessionState = possessionPhase.apply(minuteContext, tacticalState);
        MinuteAttackState attackState = attackPhase.apply(minuteContext, possessionState);
        disciplinePhase.apply(minuteContext, attackState.possession());
        physicalStatePhase.apply(minuteContext, attackState.possession());
        automaticSubstitutionPhase.apply(minuteContext, attackState.possession());
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        return tacticalStatePhase.tacticalShapeProfile(team, formation, slotsByPlayerId);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return support.shotAttemptService.aggregateOpponentDefenderSkills(opponents);
    }

    void applyYellowCardAndMaybeSecondYellowRed(PlayerMatchState player, MatchTimeline timeline, int minute, String teamRole) {
        disciplinePhase.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
    }
}

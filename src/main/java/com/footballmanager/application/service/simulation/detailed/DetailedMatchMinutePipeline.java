package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.List;
import java.util.Map;

final class DetailedMatchMinutePipeline {
    private final MinuteScheduledSubstitutionPhase scheduledSubstitutionPhase;
    private final MinuteTacticalStatePhase tacticalStatePhase;
    private final MinutePossessionPhase possessionPhase;
    private final MinuteAttackPhase attackPhase;
    private final MinuteDisciplinePhase disciplinePhase;
    private final MinuteInjuryPhase injuryPhase;
    private final MinuteRestartEventPhase restartEventPhase;
    private final MinuteAutomaticSubstitutionPhase automaticSubstitutionPhase;

    DetailedMatchMinutePipeline(
            MinuteScheduledSubstitutionPhase scheduledSubstitutionPhase,
            MinuteTacticalStatePhase tacticalStatePhase,
            MinutePossessionPhase possessionPhase,
            MinuteAttackPhase attackPhase,
            MinuteDisciplinePhase disciplinePhase,
            MinuteInjuryPhase injuryPhase,
            MinuteRestartEventPhase restartEventPhase,
            MinuteAutomaticSubstitutionPhase automaticSubstitutionPhase) {
        this.scheduledSubstitutionPhase = java.util.Objects.requireNonNull(scheduledSubstitutionPhase);
        this.tacticalStatePhase = java.util.Objects.requireNonNull(tacticalStatePhase);
        this.possessionPhase = java.util.Objects.requireNonNull(possessionPhase);
        this.attackPhase = java.util.Objects.requireNonNull(attackPhase);
        this.disciplinePhase = java.util.Objects.requireNonNull(disciplinePhase);
        this.injuryPhase = java.util.Objects.requireNonNull(injuryPhase);
        this.restartEventPhase = java.util.Objects.requireNonNull(restartEventPhase);
        this.automaticSubstitutionPhase = java.util.Objects.requireNonNull(automaticSubstitutionPhase);
    }

    MinuteSimulationResult processMinute(MinuteSimulationInput minuteContext) {
        int eventsBefore = minuteContext.timeline().size();
        scheduledSubstitutionPhase.apply(minuteContext);
        MinuteTacticalState tacticalState = tacticalStatePhase.resolve(minuteContext);
        MinutePossessionState possessionState = possessionPhase.apply(minuteContext, tacticalState);
        MinuteAttackState attackState = attackPhase.apply(minuteContext, possessionState);
        disciplinePhase.apply(minuteContext, attackState.possession());
        injuryPhase.apply(minuteContext, attackState.possession());
        restartEventPhase.apply(minuteContext, attackState.possession());
        automaticSubstitutionPhase.apply(minuteContext, attackState.possession());
        return new MinuteSimulationResult(minuteContext.minute(), eventsBefore, minuteContext.timeline().size());
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        return tacticalStatePhase.tacticalShapeProfile(team, formation, slotsByPlayerId);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return attackPhase.aggregateOpponentDefenderSkills(opponents);
    }

    void applyYellowCardAndMaybeSecondYellowRed(
            PlayerMatchState player,
            MatchTimeline timeline,
            int minute,
            String teamRole) {
        disciplinePhase.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
    }
}

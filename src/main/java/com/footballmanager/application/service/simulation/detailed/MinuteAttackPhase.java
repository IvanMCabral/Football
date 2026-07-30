package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

final class MinuteAttackPhase {
    private final MinuteTacticalPolicies tacticalPolicies;
    private final MinuteEventPolicies eventPolicies;
    private final MinutePlayerStatePolicies playerStatePolicies;

    MinuteAttackPhase(
            MinuteTacticalPolicies tacticalPolicies,
            MinuteEventPolicies eventPolicies,
            MinutePlayerStatePolicies playerStatePolicies) {
        this.tacticalPolicies = tacticalPolicies;
        this.eventPolicies = eventPolicies;
        this.playerStatePolicies = playerStatePolicies;
    }

    MinuteAttackState apply(MinuteSimulationInput minuteContext, MinutePossessionState possession) {
        AttackingThreat threat = strongestAttackingThreat(possession);
        double chanceProbability = chanceProbability(minuteContext, possession, threat);
        if (minuteContext.random().nextDouble() < chanceProbability) {
            eventPolicies.shotAttemptService().attemptShot(
                    possession.possessor(),
                    possession.opponent(),
                    possession.selector(),
                    possession.formation(),
                    possession.opponentFormation(),
                    possession.possessorShape(),
                    possession.opponentShape(),
                    possession.possessorSlots(),
                    possession.opponentSlots(),
                    possession.teamRole(),
                    minuteContext.minute(),
                    minuteContext.random(),
                    minuteContext.timeline(),
                    minuteContext.matchIntensity());
        }
        maybeCreateChance(minuteContext, possession, chanceProbability);
        return new MinuteAttackState(possession, chanceProbability);
    }

    private AttackingThreat strongestAttackingThreat(MinutePossessionState possession) {
        int keyAttack = 70;
        int keySpeed = 70;
        int keyDribbler = 0;
        int keySpeedster = 0;
        int bestAttack = Integer.MIN_VALUE;
        for (PlayerMatchState p : possession.possessor().startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard() && p.attack() > bestAttack) {
                bestAttack = p.attack();
                keyAttack = p.attack();
                keySpeed = p.speed();
                keyDribbler = p.getSkillLevel(PlayerSkill.DRIBBLER);
                keySpeedster = p.getSkillLevel(PlayerSkill.SPEEDSTER);
            }
        }
        return new AttackingThreat(keyAttack, keySpeed, keyDribbler, keySpeedster);
    }

    private double chanceProbability(
            MinuteSimulationInput minuteContext,
            MinutePossessionState possession,
            AttackingThreat threat) {
        double aggregateAttack = tacticalPolicies.attackContributionService().aggregateAttackerStat(
                possession.possessor().startingPlayers(), possession.possessorSlots());
        int teamAttackInfluence = (int) Math.round((threat.attack() * 0.40) + (aggregateAttack * 0.60));
        double opponentDefenderStat = tacticalPolicies.defenseChannelService().aggregateDefenderStat(
                possession.opponent().startingPlayers(), possession.opponentSlots());
        double possessorCollectiveStat = tacticalPolicies.attackContributionService().aggregateCollectiveStat(
                possession.possessor().startingPlayers(), possession.possessorSlots());
        double opponentCollectiveStat = tacticalPolicies.attackContributionService().aggregateCollectiveStat(
                possession.opponent().startingPlayers(), possession.opponentSlots());
        return tacticalPolicies.matchProbabilityService().chanceProbability(
                possession.possessor().style(),
                minuteContext.minute(),
                teamAttackInfluence,
                threat.speed(),
                threat.dribblerSkill(),
                threat.speedsterSkill())
                * tacticalPolicies.matchProbabilityService().professionalShotTempoMultiplier()
                * Math.sqrt((1.0 + minuteContext.matchIntensity()) / 2.0)
                * possession.possessorShape().attackVolumeMultiplier()
                * possession.opponentShape().defensiveResistanceMultiplier()
                * tacticalPolicies.defenseChannelService().defenderRosterChanceVolumeMultiplier(opponentDefenderStat)
                * tacticalPolicies.attackContributionService().scheduledSubAttackVolumeMultiplier(
                        possession.possessor(),
                        minuteContext.matchContext().manualSubstitutions(),
                        possession.possessor().teamId(),
                        minuteContext.minute())
                * tacticalPolicies.matchProbabilityService().collectiveQualityChanceVolumeMultiplier(
                        possessorCollectiveStat, opponentCollectiveStat)
                * tacticalPolicies.matchProbabilityService().defensiveStyleChanceVolumeMultiplier(possession.opponent().style())
                * tacticalPolicies.matchProbabilityService().homeFieldChanceVolumeMultiplier(possession.homeHasPossession())
                * channelMismatchMultiplier(possession.possessorShape(), possession.opponentShape());
    }

    private void maybeCreateChance(
            MinuteSimulationInput minuteContext,
            MinutePossessionState possession,
            double chanceProbability) {
        if (minuteContext.random().nextDouble() < chanceProbability * 0.6) {
            var creator = possession.selector().selectShooter(
                    possession.possessor().startingPlayers(), possession.formation());
            if (creator.isPresent()) {
                PlayerMatchState c = creator.get();
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minuteContext.minute(),
                        DetailedMatchEventType.CHANCE_CREATED,
                        possession.teamRole(),
                        c.sessionPlayerId(),
                        c.name(),
                        null, null,
                        0.0,
                        "Chance created for " + possession.possessor().name()
                ));
                playerStatePolicies.fatigueModel().applyDrain(c, 3);
            }
        }
    }

    private record AttackingThreat(
            int attack,
            int speed,
            int dribblerSkill,
            int speedsterSkill) {
    }

    private double channelMismatchMultiplier(TacticalShapeProfile attack, TacticalShapeProfile defense) {
        if (attack == null || defense == null) {
            return 1.0;
        }
        double leftEdge = attack.attackLeft() - defense.defenseLeft();
        double centerEdge = attack.attackCenter() - defense.defenseCenter();
        double rightEdge = attack.attackRight() - defense.defenseRight();
        double bestEdge = Math.max(leftEdge, Math.max(centerEdge, rightEdge));
        double worstEdge = Math.min(leftEdge, Math.min(centerEdge, rightEdge));
        double advantage = Math.max(0.0, bestEdge) * 0.125;
        double deadEnd = Math.max(0.0, -worstEdge) * 0.100;
        double laneClosure = (Math.max(0.0, -leftEdge)
                + Math.max(0.0, -centerEdge)
                + Math.max(0.0, -rightEdge)) / 3.0 * 0.060;
        return clamp(1.0 + advantage - deadEnd - laneClosure, 0.78, 1.18);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    java.util.Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(java.util.List<PlayerMatchState> opponents) {
        return eventPolicies.shotAttemptService().aggregateOpponentDefenderSkills(opponents);
    }
}

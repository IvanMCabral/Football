package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

final class MinuteAttackPhase {
    private final DetailedMatchMinuteSupport support;

    MinuteAttackPhase(DetailedMatchMinuteSupport support) {
        this.support = support;
    }

    MinuteAttackState apply(MinuteSimulationContext minuteContext, MinutePossessionState possession) {
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
        double aggregateAttack = support.attackContributionService.aggregateAttackerStat(
                possession.possessor().startingPlayers(), possession.possessorSlots());
        int teamAttackInfluence = (int) Math.round((keyAttack * 0.40) + (aggregateAttack * 0.60));
        double opponentDefenderStat = support.defenseChannelService.aggregateDefenderStat(
                possession.opponent().startingPlayers(), possession.opponentSlots());
        double possessorCollectiveStat = support.attackContributionService.aggregateCollectiveStat(
                possession.possessor().startingPlayers(), possession.possessorSlots());
        double opponentCollectiveStat = support.attackContributionService.aggregateCollectiveStat(
                possession.opponent().startingPlayers(), possession.opponentSlots());
        double chanceProbability = support.matchProbabilityService.chanceProbability(
                possession.possessor().style(),
                minuteContext.minute(),
                teamAttackInfluence,
                keySpeed,
                keyDribbler,
                keySpeedster)
                * support.matchProbabilityService.professionalShotTempoMultiplier()
                * Math.sqrt((1.0 + minuteContext.matchIntensity()) / 2.0)
                * possession.possessorShape().attackVolumeMultiplier()
                * possession.opponentShape().defensiveResistanceMultiplier()
                * support.defenseChannelService.defenderRosterChanceVolumeMultiplier(opponentDefenderStat)
                * support.attackContributionService.scheduledSubAttackVolumeMultiplier(
                        possession.possessor(),
                        minuteContext.matchContext().manualSubstitutions(),
                        possession.possessor().teamId(),
                        minuteContext.minute())
                * support.matchProbabilityService.collectiveQualityChanceVolumeMultiplier(
                        possessorCollectiveStat, opponentCollectiveStat)
                * support.matchProbabilityService.defensiveStyleChanceVolumeMultiplier(possession.opponent().style())
                * support.matchProbabilityService.homeFieldChanceVolumeMultiplier(possession.homeHasPossession())
                * channelMismatchMultiplier(possession.possessorShape(), possession.opponentShape());
        if (minuteContext.random().nextDouble() < chanceProbability) {
            support.shotAttemptService.attemptShot(
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
                support.fatigueModel.applyDrain(c, 3);
            }
        }
        return new MinuteAttackState(possession, chanceProbability);
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
}

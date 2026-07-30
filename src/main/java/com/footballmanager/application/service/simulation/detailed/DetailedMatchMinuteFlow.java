package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class DetailedMatchMinuteFlow {
    private static final double HOME_POSSESSION_ADVANTAGE = 1.035;
    private static final double AWAY_POSSESSION_FRICTION = 0.985;

    private final DetailedMatchMinuteSupport support;
    private final Logger log;

    DetailedMatchMinuteFlow(DisciplineModel disciplineModel, AtomicInteger goalAdditions, Logger log) {
        this.support = new DetailedMatchMinuteSupport(disciplineModel, goalAdditions, log);
        this.log = log;
    }

    void processMinute(MinuteSimulationContext minuteContext) {
        int minute = minuteContext.minute();
        for (MatchContext.ScheduledSub sub : minuteContext.matchContext().manualSubstitutions()) {
            if (sub.effectiveMinute() != minute) {
                continue;
            }
            String subKey = sub.effectiveMinute() + ":" + sub.teamId() + ":" + sub.playerOffId();
            TeamMatchState target = sub.teamId().equals(minuteContext.matchContext().homeTeamId())
                    ? minuteContext.homeState() : minuteContext.awayState();
            if (minuteContext.appliedScheduledSubs().contains(subKey)) {
                continue;
            }
            try {
                DetailedMatchEvent subEvent = minuteContext.scheduledSubEngine().manualSubstitute(
                        target, sub.playerOffId(), sub.playerOnId(), sub.effectiveMinute());
                minuteContext.timeline().addEvent(subEvent);
                minuteContext.appliedScheduledSubs().add(subKey);
                log.trace("Applied scheduled sub at minute {}: teamId={} off={} on={}",
                        minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
            } catch (IllegalStateException e) {
                log.warn("Could not apply scheduled sub at minute {} "
                        + "teamId={} off={} on={}: {}",
                        minute, sub.teamId(), sub.playerOffId(), sub.playerOnId(), e.getMessage());
            }
        }
        int homeMaxPasser = support.playerSkillService.maxSkill(minuteContext.homeState().startingPlayers(), PlayerSkill.PASSER);
        int awayMaxPasser = support.playerSkillService.maxSkill(minuteContext.awayState().startingPlayers(), PlayerSkill.PASSER);
        Map<String, LineupSlot> homeEffectiveSlots = support.effectiveSlotService.effectiveSlotsForMinute(
                minuteContext.matchContext().homeSlotsByPlayerId(),
                minuteContext.matchContext().manualSubstitutions(),
                minuteContext.matchContext().homeTeamId(),
                minute);
        Map<String, LineupSlot> awayEffectiveSlots = support.effectiveSlotService.effectiveSlotsForMinute(
                minuteContext.matchContext().awaySlotsByPlayerId(),
                minuteContext.matchContext().manualSubstitutions(),
                minuteContext.matchContext().awayTeamId(),
                minute);
        TacticalShapeProfile homeShape = tacticalShapeProfile(minuteContext.homeState(), minuteContext.matchContext().homeFormation(), homeEffectiveSlots);
        TacticalShapeProfile awayShape = tacticalShapeProfile(minuteContext.awayState(), minuteContext.matchContext().awayFormation(), awayEffectiveSlots);
        double homePossAdj = minuteContext.homePossBase() * (1.0 + homeMaxPasser / 300.0)
                * homeShape.possessionMultiplier()
                * HOME_POSSESSION_ADVANTAGE;
        double awayPossAdj = minuteContext.awayPossBase() * (1.0 + awayMaxPasser / 300.0)
                * awayShape.possessionMultiplier()
                * AWAY_POSSESSION_FRICTION;
        double homeShare = homePossAdj / (homePossAdj + awayPossAdj);
        double roll = minuteContext.random().nextDouble();
        boolean homeHasPossession = roll < homeShare;
        TeamMatchState possessor = homeHasPossession ? minuteContext.homeState() : minuteContext.awayState();
        TeamMatchState opponent = homeHasPossession ? minuteContext.awayState() : minuteContext.homeState();
        PlayerSelector selector = homeHasPossession ? minuteContext.homeSelector() : minuteContext.awaySelector();
        String teamRole = homeHasPossession ? minuteContext.matchContext().homeTeamId() : minuteContext.matchContext().awayTeamId();
        String formation = homeHasPossession ? minuteContext.matchContext().homeFormation() : minuteContext.matchContext().awayFormation();
        String opponentFormation = homeHasPossession ? minuteContext.matchContext().awayFormation() : minuteContext.matchContext().homeFormation();
        possessor.addPossessionTick();
        applyMinuteDrain(minuteContext.homeState(), minuteContext.matchContext().homeStyle());
        applyMinuteDrain(minuteContext.awayState(), minuteContext.matchContext().awayStyle());
        int keyAttack = 70;
        int keySpeed = 70;
        int keyDribbler = 0;
        int keySpeedster = 0;
        int bestAttack = Integer.MIN_VALUE;
        for (PlayerMatchState p : possessor.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard() && p.attack() > bestAttack) {
                bestAttack = p.attack();
                keyAttack = p.attack();
                keySpeed = p.speed();
                keyDribbler = p.getSkillLevel(PlayerSkill.DRIBBLER);
                keySpeedster = p.getSkillLevel(PlayerSkill.SPEEDSTER);
            }
        }
        TacticalShapeProfile possessorShape = homeHasPossession ? homeShape : awayShape;
        TacticalShapeProfile opponentShape = homeHasPossession ? awayShape : homeShape;
        Map<String, LineupSlot> possessorSlots = homeHasPossession
                ? homeEffectiveSlots
                : awayEffectiveSlots;
        Map<String, LineupSlot> opponentSlots = homeHasPossession
                ? awayEffectiveSlots
                : homeEffectiveSlots;
        double aggregateAttack = support.attackContributionService.aggregateAttackerStat(
                possessor.startingPlayers(),
                possessorSlots);
        int teamAttackInfluence = (int) Math.round((keyAttack * 0.40) + (aggregateAttack * 0.60));
        double opponentDefenderStat = support.defenseChannelService.aggregateDefenderStat(opponent.startingPlayers(), opponentSlots);
        double possessorCollectiveStat = support.attackContributionService.aggregateCollectiveStat(possessor.startingPlayers(), possessorSlots);
        double opponentCollectiveStat = support.attackContributionService.aggregateCollectiveStat(opponent.startingPlayers(), opponentSlots);
        double chanceProbability = support.matchProbabilityService.chanceProbability(possessor.style(), minute, teamAttackInfluence, keySpeed, keyDribbler, keySpeedster)
                * support.matchProbabilityService.professionalShotTempoMultiplier()
                * Math.sqrt((1.0 + minuteContext.matchIntensity()) / 2.0)
                * possessorShape.attackVolumeMultiplier()
                * opponentShape.defensiveResistanceMultiplier()
                * support.defenseChannelService.defenderRosterChanceVolumeMultiplier(opponentDefenderStat)
                * support.attackContributionService.scheduledSubAttackVolumeMultiplier(
                        possessor,
                        minuteContext.matchContext().manualSubstitutions(),
                        possessor.teamId(),
                        minute)
                * support.matchProbabilityService.collectiveQualityChanceVolumeMultiplier(possessorCollectiveStat, opponentCollectiveStat)
                * support.matchProbabilityService.defensiveStyleChanceVolumeMultiplier(opponent.style())
                * support.matchProbabilityService.homeFieldChanceVolumeMultiplier(homeHasPossession)
                * channelMismatchMultiplier(possessorShape, opponentShape);
        if (minuteContext.random().nextDouble() < chanceProbability) {
            support.shotAttemptService.attemptShot(
                    possessor, opponent, selector, formation, opponentFormation,
                    possessorShape, opponentShape, possessorSlots, opponentSlots,
                    teamRole, minute, minuteContext.random(), minuteContext.timeline(), minuteContext.matchIntensity());
        }
        if (minuteContext.random().nextDouble() < chanceProbability * 0.6) {
            var creator = selector.selectShooter(possessor.startingPlayers(), formation);
            if (creator.isPresent()) {
                PlayerMatchState c = creator.get();
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minute,
                        DetailedMatchEventType.CHANCE_CREATED,
                        teamRole,
                        c.sessionPlayerId(),
                        c.name(),
                        null, null,
                        0.0,
                        "Chance created for " + possessor.name()
                ));
                support.fatigueModel.applyDrain(c, 3);
            }
        }
        var potentialFouler = selector.selectShooter(possessor.startingPlayers(), formation);
        if (potentialFouler.isPresent()) {
            PlayerMatchState f = potentialFouler.get();
            boolean defending = !homeHasPossession; // fouler is on defending side when opponent has possession
            if (support.disciplineModel.shouldCommitFoul(f, possessor.style(), defending, minuteContext.random())) {
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minute,
                        DetailedMatchEventType.FOUL,
                        teamRole,
                        f.sessionPlayerId(),
                        f.name(),
                        null, null,
                        0.0,
                        f.name() + " committed a foul"
                ));
                support.fatigueModel.applyDrain(f, 5);
                if (support.disciplineModel.shouldReceiveYellow(f, possessor.style(), minuteContext.random()) && !f.redCard()) {
                    support.cardEventService.applyYellowCardAndMaybeSecondYellowRed(f, minuteContext.timeline(), minute, teamRole);
                }
            }
        }
        var potentialInjured = selector.selectShooter(possessor.startingPlayers(), formation);
        if (potentialInjured.isPresent()) {
            PlayerMatchState p = potentialInjured.get();
            if (support.injuryModel.shouldInjure(p, possessor.style(), false, minuteContext.random())) {
                p.injure();
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minute,
                        DetailedMatchEventType.INJURY,
                        teamRole,
                        p.sessionPlayerId(),
                        p.name(),
                        null, null,
                        0.0,
                        p.name() + " was injured"
                ));
            }
        }
        if (minuteContext.random().nextDouble() < 0.035) {
            var player = selector.selectShooter(possessor.startingPlayers(), formation);
            if (player.isPresent()) {
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minute,
                        DetailedMatchEventType.CORNER,
                        teamRole,
                        player.get().sessionPlayerId(),
                        player.get().name(),
                        null, null,
                        0.0,
                        "Corner for " + possessor.name()
                ));
            }
        }
        if (minuteContext.random().nextDouble() < 0.04 && possessor.style() != TeamStyle.DEFENSIVE) {
            var player = selector.selectShooter(possessor.startingPlayers(), formation);
            if (player.isPresent()) {
                minuteContext.timeline().addEvent(new DetailedMatchEvent(
                        minute,
                        DetailedMatchEventType.OFFSIDE,
                        teamRole,
                        player.get().sessionPlayerId(),
                        player.get().name(),
                        null, null,
                        0.0,
                        "Offside"
                ));
            }
        }
        if (minute >= 60 && !minuteContext.homeState().startingPlayers().isEmpty() && support.substitutionEngine.hasSubstitutionsRemaining(minuteContext.matchContext().homeTeamId()) && !homeHasPossession) {
            support.substitutionEngine.attemptSubstitution(minuteContext.homeState(), minute)
                    .ifPresent(e -> minuteContext.timeline().addEvent(e));
        }
        if (minute >= 60 && !minuteContext.awayState().startingPlayers().isEmpty() && support.substitutionEngine.hasSubstitutionsRemaining(minuteContext.matchContext().awayTeamId()) && homeHasPossession) {
            support.substitutionEngine.attemptSubstitution(minuteContext.awayState(), minute)
                    .ifPresent(e -> minuteContext.timeline().addEvent(e));
        }
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        return support.tacticalShapeService.tacticalShapeProfile(team, formation, slotsByPlayerId);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return support.shotAttemptService.aggregateOpponentDefenderSkills(opponents);
    }

    void applyYellowCardAndMaybeSecondYellowRed(PlayerMatchState player, MatchTimeline timeline, int minute, String teamRole) {
        support.cardEventService.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
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

    private void applyMinuteDrain(TeamMatchState team, TeamStyle style) {
        int baseDrain = support.fatigueModel.baseDrainPerMinute(style);
        for (PlayerMatchState p : team.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard()) {
                support.fatigueModel.applyDrain(p, baseDrain);
            }
        }
    }
}

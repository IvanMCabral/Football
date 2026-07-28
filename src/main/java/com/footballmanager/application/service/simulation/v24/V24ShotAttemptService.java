package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

final class V24ShotAttemptService {

    private final V24ShotXgCalculator xgCalculator;
    private final V24FatigueModel fatigueModel;
    private final V24AssistModel assistModel;
    private final V24ShotLocationService shotLocationService;
    private final V24DefenseChannelService defenseChannelService;
    private final V24AttackContributionService attackContributionService;
    private final V24MatchProbabilityService matchProbabilityService;
    private final AtomicInteger goalCounter;
    private final Logger log;

    V24ShotAttemptService(
            V24ShotXgCalculator xgCalculator,
            V24FatigueModel fatigueModel,
            V24AssistModel assistModel,
            V24ShotLocationService shotLocationService,
            V24DefenseChannelService defenseChannelService,
            V24AttackContributionService attackContributionService,
            V24MatchProbabilityService matchProbabilityService,
            AtomicInteger goalCounter,
            Logger log) {
        this.xgCalculator = xgCalculator;
        this.fatigueModel = fatigueModel;
        this.assistModel = assistModel;
        this.shotLocationService = shotLocationService;
        this.defenseChannelService = defenseChannelService;
        this.attackContributionService = attackContributionService;
        this.matchProbabilityService = matchProbabilityService;
        this.goalCounter = goalCounter;
        this.log = log;
    }

    void attemptShot(
            V24TeamMatchState possessor,
            V24TeamMatchState opponent,
            V24PlayerSelector selector,
            String formation,
            String opponentFormation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Map<String, LineupSlot> possessorSlotsByPlayerId,
            Map<String, LineupSlot> opponentSlotsByPlayerId,
            String teamRole,
            int minute,
            Random random,
            V24MatchTimeline timeline,
            double matchIntensity) {

        var shooterOpt = selector.selectShooter(possessor.startingPlayers(), formation);
        if (shooterOpt.isEmpty()) return;

        V24PlayerMatchState shooter = shooterOpt.get();
        double possessorAttack = attackContributionService.aggregateAttackerStat(
                possessor.startingPlayers(),
                possessorSlotsByPlayerId);
        double opponentDefense = defenseChannelService.aggregateDefenderStat(
                opponent.startingPlayers(),
                opponentSlotsByPlayerId);
        V24PlayerMatchState opponentGk = findGkOnPitch(opponent.startingPlayers());
        double rawShooterQuality = selector.shooterQuality(shooter);
        double shooterQuality = fatigueModel.applyFatigueToQuality(rawShooterQuality, shooter);
        V24ShotLocation location = shotLocationService.selectShotLocation(
                possessor.style(), formation, possessorShape, opponentShape, random);
        V24ShotCoordinate shotCoord = shotLocationService.generateShotCoordinate(
                location, possessor.style(), possessorShape, opponentShape, random);
        opponentDefense = defenseChannelService.aggregateDefenderStatForLocation(
                opponent.startingPlayers(),
                opponentSlotsByPlayerId,
                location,
                shotCoord,
                opponentDefense);
        var assistOpt = assistModel.selectAssistProvider(
                possessor.startingPlayers(), shooter, formation, possessor.style(), random);
        String assistPlayerId = assistOpt.map(V24PlayerMatchState::sessionPlayerId).orElse(null);
        String assistPlayerName = assistOpt.map(V24PlayerMatchState::name).orElse(null);
        double assistQuality = assistOpt.map(selector::assistQuality).orElse(0.3);
        if (assistOpt.isPresent()) {
            int playmakerSkill = assistOpt.get().getSkillLevel(PlayerSkill.PLAYMAKER);
            assistQuality = playmakerAdjustedAssistQuality(assistQuality, playmakerSkill);
        }
        double defPressure = defensivePressure(opponent, random);
        double gkQuality = gkQuality(opponent.startingPlayers(), random);

        V24ShotQuality quality = new V24ShotQuality(
                location,
                shooterQuality,
                assistQuality,
                defPressure,
                gkQuality,
                matchProbabilityService.styleToModifier(possessor.style())
        );
        Map<PlayerSkill, Integer> opponentDefenderSkills =
                aggregateOpponentDefenderSkills(opponent.startingPlayers());
        double xg = xgCalculator.calculateXg(
                quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooter.skillLevels(), shooter.heightCm(),
                opponentGk != null ? opponentGk.skillLevels() : null,
                opponentGk != null ? opponentGk.heightCm() : null,
                V24ShotEventType.OPEN_PLAY,
                opponentDefenderSkills, null);
        xg *= matchProbabilityService.defensiveShapeShotQualityMultiplier(opponentShape, location);
        xg *= matchProbabilityService.defensiveStyleShotQualityMultiplier(opponent.style(), location);
        possessor.addXg(xg);
        fatigueModel.applyDrain(shooter, 8);
        boolean onTarget = random.nextDouble() < onTargetProbability(xg);
        boolean isGoal = false;

        if (onTarget) {
            isGoal = random.nextDouble() < (xg * matchIntensity / 0.60);
            if (isGoal) {
                possessor.addGoal();
                int n = goalCounter.incrementAndGet();
                log.trace("[V24-XG-COUNTER] addGoal called; counter={}, minute={}, xg={}",
                    n, minute, xg);
                possessor.addShot(true);
                String goalDesc = assistPlayerId != null
                        ? "Goal by " + shooter.name() + " assisted by " + assistPlayerName + " " + minute + "'"
                        : "Goal! " + shooter.name() + " " + minute + "'";
                timeline.addEvent(new V24MatchEvent(
                        minute,
                        V24MatchEventType.GOAL,
                        teamRole,
                        shooter.sessionPlayerId(),
                        shooter.name(),
                        assistPlayerId,
                        assistPlayerName,
                        Math.round(xg * 1000.0) / 1000.0,
                        goalDesc
                ).withShotCoordinate(shotCoord));
            }
        }

        if (onTarget && !isGoal) {
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.SHOT_ON_TARGET,
                    teamRole,
                    shooter.sessionPlayerId(),
                    shooter.name(),
                    assistPlayerId,
                    assistPlayerName,
                    Math.round(xg * 1000.0) / 1000.0,
                    "Shot saved"
            ).withShotCoordinate(shotCoord));
            possessor.addShot(true);
        } else if (!onTarget) {
            V24MatchEventType missType = random.nextDouble() < 0.3
                    ? V24MatchEventType.BLOCK
                    : V24MatchEventType.MISS;
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    missType,
                    teamRole,
                    shooter.sessionPlayerId(),
                    shooter.name(),
                    assistPlayerId,
                    assistPlayerName,
                    Math.round(xg * 1000.0) / 1000.0,
                    "Shot missed"
            ).withShotCoordinate(shotCoord));
            possessor.addShot(false);
        }
    }
    private double defensivePressure(V24TeamMatchState opponent, Random random) {
        double basePressure = 0.5;
        long defendersOnPitch = opponent.startingPlayers().stream()
                .filter(p -> p.onPitch() && (p.position().equals("DEF") || p.position().equals("MID")))
                .count();
        double defMod = Math.min(0.9, defendersOnPitch / 11.0 * 1.2);
        double randomFactor = 0.7 + random.nextDouble() * 0.6;
        return Math.min(1.0, basePressure * defMod * randomFactor);
    }

    static double playmakerAdjustedAssistQuality(double baseAssistQuality, int playmakerSkill) {
        if (playmakerSkill <= 0) return baseAssistQuality;
        return baseAssistQuality * (1.0 + playmakerSkill / 200.0);
    }

    private double gkQuality(List<V24PlayerMatchState> players, Random random) {
        var gk = players.stream()
                .filter(p -> p.position().equals("GK") && p.onPitch())
                .findFirst();
        if (gk.isEmpty()) return 0.5;
        return Math.round((gk.get().stamina() / 100.0 * 0.5 + gk.get().mentality() / 100.0 * 0.5) * 1000.0) / 1000.0;
    }

    static double onTargetProbability(double xg) {
        double normalizedXg = clamp(xg, 0.0, 0.60) / 0.60;
        return clamp(0.38 + normalizedXg * 0.16, 0.38, 0.54);
    }

    private V24PlayerMatchState findGkOnPitch(List<V24PlayerMatchState> players) {
        return players.stream()
                .filter(p -> p.position().equals("GK") && p.onPitch())
                .findFirst()
                .orElse(null);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<V24PlayerMatchState> opponents) {
        List<V24PlayerMatchState> defsOnPitch = opponents.stream()
                .filter(V24PlayerMatchState::onPitch)
                .filter(p -> p.position().equals("DEF"))
                .toList();
        if (defsOnPitch.isEmpty()) return Map.of();

        Map<PlayerSkill, Integer> result = new HashMap<>();
        double markerAvg = defsOnPitch.stream()
                .mapToInt(p -> p.getSkillLevel(PlayerSkill.MARKER))
                .average()
                .orElse(0.0);
        if (markerAvg > 0) {
            result.put(PlayerSkill.MARKER, (int) Math.round(markerAvg));
        }
        double tacklerAvg = defsOnPitch.stream()
                .mapToInt(p -> p.getSkillLevel(PlayerSkill.TACKLER))
                .average()
                .orElse(0.0);
        if (tacklerAvg > 0) {
            result.put(PlayerSkill.TACKLER, (int) Math.round(tacklerAvg));
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}

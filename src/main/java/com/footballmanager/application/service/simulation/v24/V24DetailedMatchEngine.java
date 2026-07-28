package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;
import com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class V24DetailedMatchEngine implements V24DetailedMatchEngineProvider {
    private static final Logger log = LoggerFactory.getLogger(V24DetailedMatchEngine.class);
    private static final AtomicInteger goalAdditions = new AtomicInteger(0);

    private final V24ShotXgCalculator xgCalculator = new V24ShotXgCalculator();
    private final V24FatigueModel fatigueModel = new V24FatigueModel();
    private final V24DisciplineModel disciplineModel;
    private final V24InjuryModel injuryModel = new V24InjuryModel();
    private final V24SubstitutionEngine substitutionEngine = new V24SubstitutionEngine();
    private final V24SubstitutionEngine scheduledSubEngine = new V24SubstitutionEngine();
    private final Set<String> appliedScheduledSubs = new HashSet<>();
    private final V24AssistModel assistModel = new V24AssistModel();
    private final V24ShotCoordinateGenerator coordGenerator = new V24ShotCoordinateGenerator();
    private double matchIntensity = 1.0;
    private static final double HOME_POSSESSION_ADVANTAGE = 1.035;
    private static final double AWAY_POSSESSION_FRICTION = 0.985;
    private static final double HOME_CHANCE_VOLUME_ADVANTAGE = 1.040;
    private static final double AWAY_CHANCE_VOLUME_FRICTION = 0.985;

    public V24DetailedMatchEngine() {
        this(new V24DisciplineModel());
    }

    V24DetailedMatchEngine(V24DisciplineModel disciplineModel) {
        this.disciplineModel = disciplineModel != null ? disciplineModel : new V24DisciplineModel();
    }

    public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
        return simulateWithRandom(context,
            new Random(seed),
            new Random(seed),
            new Random(seed + 1));
    }

    public V24DetailedMatchResult simulate(V24MatchContext context, Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        return simulateWithRandom(context, random, random, random);
    }

    public V24DetailedMatchResult simulate(V24MatchContext context, Random random, int maxMinute) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            throw new IllegalArgumentException(
                "maxMinute must be in [1, 90], got " + maxMinute);
        }
        return simulateWithRandomBounded(context, random, random, random, maxMinute);
    }

    private V24DetailedMatchResult simulateWithRandom(
            V24MatchContext context, Random random, Random homeSelectorRandom, Random awaySelectorRandom) {
        return simulateWithRandomBounded(context, random, homeSelectorRandom, awaySelectorRandom, 90);
    }

    private V24DetailedMatchResult simulateWithRandomBounded(
            V24MatchContext context, Random random, Random homeSelectorRandom, Random awaySelectorRandom, int maxMinute) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            throw new IllegalArgumentException(
                "maxMinute must be in [1, 90], got " + maxMinute);
        }

        V24TeamMatchState homeState = V24TeamMatchState.create(
                context.homeTeam(), context.homeStartingPlayers(),
                context.homeBenchPlayers(), context.homeStyle(),
                context.homeSlotsByPlayerId());

        V24TeamMatchState awayState = V24TeamMatchState.create(
                context.awayTeam(), context.awayStartingPlayers(),
                context.awayBenchPlayers(), context.awayStyle(),
                context.awaySlotsByPlayerId());

        V24MatchClock clock = new V24MatchClock(90);
        V24MatchTimeline timeline = new V24MatchTimeline();
        double homePossBase = possessionBase(context.homeStyle());
        double awayPossBase = possessionBase(context.awayStyle());
        int homeMaxPasser = maxPasserSkill(homeState.startingPlayers());
        int awayMaxPasser = maxPasserSkill(awayState.startingPlayers());
        V24TacticalShapeProfile homeShape = tacticalShapeProfile(
                homeState, context.homeFormation(), context.homeSlotsByPlayerId());
        V24TacticalShapeProfile awayShape = tacticalShapeProfile(
                awayState, context.awayFormation(), context.awaySlotsByPlayerId());
        double homePossAdj = homePossBase * (1.0 + homeMaxPasser / 300.0)
                * homeShape.possessionMultiplier()
                * HOME_POSSESSION_ADVANTAGE;
        double awayPossAdj = awayPossBase * (1.0 + awayMaxPasser / 300.0)
                * awayShape.possessionMultiplier()
                * AWAY_POSSESSION_FRICTION;
        double homeShare = homePossAdj / (homePossAdj + awayPossAdj);
        double homeAvgOverall = computeTeamAvgOverall(context.homeStartingPlayers());
        double awayAvgOverall = computeTeamAvgOverall(context.awayStartingPlayers());
        double overallDiffRatio = computeOverallDiffRatio(homeAvgOverall, awayAvgOverall);
        this.matchIntensity = computeMatchIntensity(overallDiffRatio);
        log.trace("matchIntensity={} (homeOvr={}, awayOvr={}, diffRatio={})",
            matchIntensity, homeAvgOverall, awayAvgOverall, overallDiffRatio);
        V24PlayerSelector homeSelector = new V24PlayerSelector(homeSelectorRandom);
        V24PlayerSelector awaySelector = new V24PlayerSelector(awaySelectorRandom);

        while (clock.isRunning()) {
            int minute = clock.currentMinute();
            if (minute > maxMinute) {
                break;
            }
            for (V24MatchContext.ScheduledSub sub : context.manualSubstitutions()) {
                if (sub.effectiveMinute() != minute) {
                    continue;
                }
                String subKey = sub.effectiveMinute() + ":" + sub.teamId() + ":" + sub.playerOffId();
                V24TeamMatchState target = sub.teamId().equals(context.homeTeamId())
                        ? homeState : awayState;
                if (appliedScheduledSubs.contains(subKey)) {
                    applyScheduledSubManually(target, sub);
                    timeline.addEvent(new V24MatchEvent(
                            sub.effectiveMinute(),
                            V24MatchEventType.SUBSTITUTION,
                            sub.teamId(),
                            sub.playerOffId(),
                            null,
                            sub.playerOnId(),
                            null,
                            0.0,
                            "Substitution: " + sub.playerOnId() + " on for " + sub.playerOffId()));
                    log.trace("Re-applied scheduled sub at minute {} (subsequent tick): teamId={} off={} on={}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
                    continue;
                }
                try {
                    V24MatchEvent subEvent = scheduledSubEngine.manualSubstitute(
                            target, sub.playerOffId(), sub.playerOnId(), sub.effectiveMinute());
                    timeline.addEvent(subEvent);
                    applyScheduledSubManually(target, sub);
                    appliedScheduledSubs.add(subKey);
                    log.trace("Applied scheduled sub at minute {}: teamId={} off={} on={}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
                } catch (IllegalStateException e) {
                    log.warn("Could not apply scheduled sub at minute {} "
                            + "teamId={} off={} on={}: {}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId(), e.getMessage());
                }
            }
            homeMaxPasser = maxPasserSkill(homeState.startingPlayers());
            awayMaxPasser = maxPasserSkill(awayState.startingPlayers());
            Map<String, LineupSlotDTO> homeEffectiveSlots = effectiveSlotsForMinute(
                    context.homeSlotsByPlayerId(),
                    context.manualSubstitutions(),
                    context.homeTeamId(),
                    minute);
            Map<String, LineupSlotDTO> awayEffectiveSlots = effectiveSlotsForMinute(
                    context.awaySlotsByPlayerId(),
                    context.manualSubstitutions(),
                    context.awayTeamId(),
                    minute);
            homeShape = tacticalShapeProfile(homeState, context.homeFormation(), homeEffectiveSlots);
            awayShape = tacticalShapeProfile(awayState, context.awayFormation(), awayEffectiveSlots);
            homePossAdj = homePossBase * (1.0 + homeMaxPasser / 300.0)
                    * homeShape.possessionMultiplier()
                    * HOME_POSSESSION_ADVANTAGE;
            awayPossAdj = awayPossBase * (1.0 + awayMaxPasser / 300.0)
                    * awayShape.possessionMultiplier()
                    * AWAY_POSSESSION_FRICTION;
            homeShare = homePossAdj / (homePossAdj + awayPossAdj);
            double roll = random.nextDouble();
            boolean homeHasPossession = roll < homeShare;

            V24TeamMatchState possessor = homeHasPossession ? homeState : awayState;
            V24TeamMatchState opponent = homeHasPossession ? awayState : homeState;
            V24PlayerSelector selector = homeHasPossession ? homeSelector : awaySelector;
            String teamRole = homeHasPossession ? context.homeTeamId() : context.awayTeamId();
            String formation = homeHasPossession ? context.homeFormation() : context.awayFormation();
            String opponentFormation = homeHasPossession ? context.awayFormation() : context.homeFormation();
            possessor.addPossessionTick();
            applyMinuteDrain(homeState, context.homeStyle());
            applyMinuteDrain(awayState, context.awayStyle());
            int keyAttack = 70;
            int keySpeed = 70;
            int keyDribbler = 0;
            int keySpeedster = 0;
            int bestAttack = Integer.MIN_VALUE;
            for (V24PlayerMatchState p : possessor.startingPlayers()) {
                if (p.onPitch() && !p.injured() && !p.redCard() && p.attack() > bestAttack) {
                    bestAttack = p.attack();
                    keyAttack = p.attack();
                    keySpeed = p.speed();
                    keyDribbler = p.getSkillLevel(PlayerSkill.DRIBBLER);
                    keySpeedster = p.getSkillLevel(PlayerSkill.SPEEDSTER);
                }
            }
            V24TacticalShapeProfile possessorShape = homeHasPossession ? homeShape : awayShape;
            V24TacticalShapeProfile opponentShape = homeHasPossession ? awayShape : homeShape;
            Map<String, LineupSlotDTO> possessorSlots = homeHasPossession
                    ? homeEffectiveSlots
                    : awayEffectiveSlots;
            Map<String, LineupSlotDTO> opponentSlots = homeHasPossession
                    ? awayEffectiveSlots
                    : homeEffectiveSlots;
            double aggregateAttack = aggregateAttackerStat(
                    possessor.startingPlayers(),
                    formation,
                    possessorSlots);
            int teamAttackInfluence = (int) Math.round((keyAttack * 0.40) + (aggregateAttack * 0.60));
            double opponentDefenderStat = aggregateDefenderStat(opponent.startingPlayers(), opponentSlots);
            double possessorCollectiveStat = aggregateCollectiveStat(possessor.startingPlayers(), possessorSlots);
            double opponentCollectiveStat = aggregateCollectiveStat(opponent.startingPlayers(), opponentSlots);
            double chanceProbability = chanceProbability(possessor.style(), minute, teamAttackInfluence, keySpeed, keyDribbler, keySpeedster)
                    * professionalShotTempoMultiplier()
                    * Math.sqrt((1.0 + matchIntensity) / 2.0)
                    * possessorShape.attackVolumeMultiplier()
                    * opponentShape.defensiveResistanceMultiplier()
                    * defenderRosterChanceVolumeMultiplier(opponentDefenderStat)
                    * scheduledSubAttackVolumeMultiplier(
                            possessor,
                            context.manualSubstitutions(),
                            possessor.teamId(),
                            minute)
                    * collectiveQualityChanceVolumeMultiplier(possessorCollectiveStat, opponentCollectiveStat)
                    * defensiveStyleChanceVolumeMultiplier(opponent.style())
                    * homeFieldChanceVolumeMultiplier(homeHasPossession)
                    * channelMismatchMultiplier(possessorShape, opponentShape);
            if (random.nextDouble() < chanceProbability) {
                attemptShot(possessor, opponent, selector, formation, opponentFormation,
                        possessorShape, opponentShape, possessorSlots, opponentSlots,
                        teamRole, minute, random, timeline);
            }
            if (random.nextDouble() < chanceProbability * 0.6) {
                var creator = selector.selectShooter(possessor.startingPlayers(), formation);
                if (creator.isPresent()) {
                    V24PlayerMatchState c = creator.get();
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.CHANCE_CREATED,
                            teamRole,
                            c.sessionPlayerId(),
                            c.name(),
                            null, null,
                            0.0,
                            "Chance created for " + possessor.name()
                    ));
                    fatigueModel.applyDrain(c, 3);
                }
            }
            var potentialFouler = selector.selectShooter(possessor.startingPlayers(), formation);
            if (potentialFouler.isPresent()) {
                V24PlayerMatchState f = potentialFouler.get();
                boolean defending = !homeHasPossession; // fouler is on defending side when opponent has possession
                if (disciplineModel.shouldCommitFoul(f, possessor.style(), defending, random)) {
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.FOUL,
                            teamRole,
                            f.sessionPlayerId(),
                            f.name(),
                            null, null,
                            0.0,
                            f.name() + " committed a foul"
                    ));
                    fatigueModel.applyDrain(f, 5);
                    if (disciplineModel.shouldReceiveYellow(f, possessor.style(), random) && !f.redCard()) {
                        applyYellowCardAndMaybeSecondYellowRed(f, timeline, minute, teamRole);
                    }
                }
            }
            var potentialInjured = selector.selectShooter(possessor.startingPlayers(), formation);
            if (potentialInjured.isPresent()) {
                V24PlayerMatchState p = potentialInjured.get();
                if (injuryModel.shouldInjure(p, possessor.style(), false, random)) {
                    p.injure();
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.INJURY,
                            teamRole,
                            p.sessionPlayerId(),
                            p.name(),
                            null, null,
                            0.0,
                            p.name() + " was injured"
                    ));
                }
            }
            if (random.nextDouble() < 0.035) {
                var player = selector.selectShooter(possessor.startingPlayers(), formation);
                if (player.isPresent()) {
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.CORNER,
                            teamRole,
                            player.get().sessionPlayerId(),
                            player.get().name(),
                            null, null,
                            0.0,
                            "Corner for " + possessor.name()
                    ));
                }
            }
            if (random.nextDouble() < 0.04 && possessor.style() != TeamStyle.DEFENSIVE) {
                var player = selector.selectShooter(possessor.startingPlayers(), formation);
                if (player.isPresent()) {
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.OFFSIDE,
                            teamRole,
                            player.get().sessionPlayerId(),
                            player.get().name(),
                            null, null,
                            0.0,
                            "Offside"
                    ));
                }
            }
            if (minute >= 60 && !homeState.startingPlayers().isEmpty() && substitutionEngine.hasSubstitutionsRemaining(context.homeTeamId()) && !homeHasPossession) {
                substitutionEngine.attemptSubstitution(homeState, minute)
                        .ifPresent(e -> timeline.addEvent(e));
            }
            if (minute >= 60 && !awayState.startingPlayers().isEmpty() && substitutionEngine.hasSubstitutionsRemaining(context.awayTeamId()) && homeHasPossession) {
                substitutionEngine.attemptSubstitution(awayState, minute)
                        .ifPresent(e -> timeline.addEvent(e));
            }

            clock.advance();
        }

        return finalizeResult(context, homeState, awayState, timeline);
    }

    private void attemptShot(
            V24TeamMatchState possessor,
            V24TeamMatchState opponent,
            V24PlayerSelector selector,
            String formation,
            String opponentFormation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Map<String, LineupSlotDTO> possessorSlotsByPlayerId,
            Map<String, LineupSlotDTO> opponentSlotsByPlayerId,
            String teamRole,
            int minute,
            Random random,
            V24MatchTimeline timeline) {

        var shooterOpt = selector.selectShooter(possessor.startingPlayers(), formation);
        if (shooterOpt.isEmpty()) return;

        V24PlayerMatchState shooter = shooterOpt.get();
        double possessorAttack = aggregateAttackerStat(
                possessor.startingPlayers(),
                formation,
                possessorSlotsByPlayerId);
        double opponentDefense = aggregateDefenderStat(
                opponent.startingPlayers(),
                opponentSlotsByPlayerId);
        V24PlayerMatchState opponentGk = findGkOnPitch(opponent.startingPlayers());
        double rawShooterQuality = selector.shooterQuality(shooter);
        double shooterQuality = fatigueModel.applyFatigueToQuality(rawShooterQuality, shooter);
        V24ShotLocation location = selectShotLocation(
                possessor.style(), formation, possessorShape, opponentShape, random);
        V24ShotCoordinate shotCoord = generateShotCoordinate(
                location, possessor.style(), possessorShape, opponentShape, random);
        opponentDefense = aggregateDefenderStatForLocation(
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
                styleToModifier(possessor.style())
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
        xg *= defensiveShapeShotQualityMultiplier(opponentShape, location);
        xg *= defensiveStyleShotQualityMultiplier(opponent.style(), location);
        possessor.addXg(xg);
        fatigueModel.applyDrain(shooter, 8);
        boolean onTarget = random.nextDouble() < onTargetProbability(xg);
        boolean isGoal = false;

        if (onTarget) {
            isGoal = random.nextDouble() < (xg * matchIntensity / 0.60);
            if (isGoal) {
                possessor.addGoal();
                int n = goalAdditions.incrementAndGet();
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
    private static final V24ShotLocation[] LOCATIONS = V24ShotLocation.values();
    private static final V24FormationParser FORMATION_PARSER = new V24FormationParser();

    private V24ShotLocation selectShotLocation(
            TeamStyle style,
            String formation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Random random) {
        double[] weights = computeLocationWeights(style, formation, possessorShape, opponentShape);
        double total = weights[0] + weights[1] + weights[2] + weights[3] + weights[4];
        if (total <= 0.0) return V24ShotLocation.PENALTY_AREA_CENTER;
        double roll = random.nextDouble() * total;
        double cum = 0.0;
        for (int i = 0; i < LOCATIONS.length; i++) {
            cum += weights[i];
            if (roll < cum) return LOCATIONS[i];
        }
        return LOCATIONS[LOCATIONS.length - 1];
    }

    private V24ShotCoordinate generateShotCoordinate(V24ShotLocation location, TeamStyle style, Random random) {
        if (location == V24ShotLocation.PENALTY_AREA_WIDE && style == TeamStyle.LEFT_FLANK) {
            return coordGenerator.generateWideFlank(true, random);
        }
        if (location == V24ShotLocation.PENALTY_AREA_WIDE && style == TeamStyle.RIGHT_FLANK) {
            return coordGenerator.generateWideFlank(false, random);
        }
        return coordGenerator.generate(location, random);
    }

    private V24ShotCoordinate generateShotCoordinate(
            V24ShotLocation location,
            TeamStyle style,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Random random) {
        if (location != V24ShotLocation.PENALTY_AREA_WIDE
                || style == TeamStyle.LEFT_FLANK
                || style == TeamStyle.RIGHT_FLANK
                || possessorShape == null
                || opponentShape == null) {
            return generateShotCoordinate(location, style, random);
        }
        double leftOpportunity = flankExploitOpportunity(possessorShape.attackLeft(), opponentShape.defenseRight());
        double rightOpportunity = flankExploitOpportunity(possessorShape.attackRight(), opponentShape.defenseLeft());
        double opportunityGap = Math.abs(leftOpportunity - rightOpportunity);
        if (opportunityGap < 0.04) {
            return generateShotCoordinate(location, style, random);
        }

        boolean attackLeft = leftOpportunity > rightOpportunity;
        double bias = clamp(0.50 + opportunityGap * 1.00, 0.50, 0.88);
        if (random.nextDouble() < bias) {
            return coordGenerator.generateWideFlank(attackLeft, random);
        }
        return coordGenerator.generateWideFlank(!attackLeft, random);
    }

    private V24ShotLocation selectShotLocation(TeamStyle style, String formation, Random random) {
        return selectShotLocation(style, formation, neutralShapeProfile(), neutralShapeProfile(), random);
    }

    private double[] computeLocationWeights(
            TeamStyle style,
            String formation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape) {
        double[] w = { 0.25, 0.27, 0.20, 0.18, 0.10 };
        double[] shift = styleLocationShift(style);
        for (int i = 0; i < w.length; i++) {
            w[i] *= shift[i];
        }
        V24FormationParser.V24Formation f = FORMATION_PARSER.parse(formation);
        if (f.hasWingers()) {
            w[2] *= 1.25;  // PENALTY_AREA_WIDE +25%
            w[0] *= 0.90;  // SIX_YARD_BOX -10%
        }
        if (f.defenders() == 3) {
            if (f.hasWingers()) {
                w[2] *= 0.88;  // PENALTY_AREA_WIDE -12% after winger boost
                w[1] *= 1.05;  // PENALTY_AREA_CENTER +5%
            } else {
                w[2] *= 0.62;  // PENALTY_AREA_WIDE -38%
                w[1] *= 1.12;  // PENALTY_AREA_CENTER +12%
            }
        }
        if (f.forwards() == 1) {
            w[0] *= 1.30;  // SIX_YARD_BOX +30%
            w[4] *= 0.70;  // LONG_RANGE -30%
        }
        if (f.forwards() == 2) {
            w[1] *= 1.20;  // PENALTY_AREA_CENTER +20%
        }
        applyNamedFormationIdentityLocationShift(w, formation);
        if (possessorShape != null && opponentShape != null) {
            double centralAttack = possessorShape.attackCenter();
            double wideAttack = (possessorShape.attackLeft() + possessorShape.attackRight()) / 2.0;
            double centralDefense = opponentShape.defenseCenter();
            double wideDefense = (opponentShape.defenseLeft() + opponentShape.defenseRight()) / 2.0;

            double centralEdge = centralAttack - centralDefense;
            double wideEdge = wideAttack - wideDefense;
            double flankImbalance = Math.abs(possessorShape.attackLeft() - possessorShape.attackRight());
            double leftFlankEdge = flankExploitOpportunity(possessorShape.attackLeft(), opponentShape.defenseRight());
            double rightFlankEdge = flankExploitOpportunity(possessorShape.attackRight(), opponentShape.defenseLeft());
            double bestFlankEdge = Math.max(leftFlankEdge, rightFlankEdge);
            double flankExploitGap = Math.abs(leftFlankEdge - rightFlankEdge);

            w[0] *= clamp(1.0 + centralEdge * 0.18, 0.86, 1.18);
            w[1] *= clamp(1.0 + centralEdge * 0.22, 0.84, 1.22);
            w[2] *= clamp(1.0 + wideEdge * 0.22, 0.80, 1.22);
            w[2] *= clamp(1.0 + Math.max(0.0, bestFlankEdge) * 0.16 + flankExploitGap * 0.18, 0.92, 1.18);
            w[3] *= clamp(1.0 + Math.max(0.0, wideDefense - wideAttack) * 0.14, 0.92, 1.16);
            w[4] *= clamp(1.0 + Math.max(0.0, centralDefense - centralAttack) * 0.12, 0.94, 1.14);
            w[1] *= clamp(1.0 - flankImbalance * 0.10, 0.88, 1.0);
        }
        return w;
    }

    private void applyNamedFormationIdentityLocationShift(double[] w, String formation) {
        if (w == null || w.length < 5 || formation == null) return;
        switch (formation) {
            case "4-3-3" -> {
                w[0] *= 0.93; // SIX_YARD_BOX
                w[2] *= 1.32; // PENALTY_AREA_WIDE
                w[4] *= 0.90; // LONG_RANGE
            }
            case "4-2-2-2" -> {
                w[1] *= 1.10; // PENALTY_AREA_CENTER
                w[2] *= 0.88; // PENALTY_AREA_WIDE
                w[3] *= 1.06; // OUTSIDE_BOX
            }
            case "4-1-2-3" -> {
                w[0] *= 0.94; // SIX_YARD_BOX
                w[1] *= 1.08; // PENALTY_AREA_CENTER
                w[4] *= 0.92; // LONG_RANGE
            }
            case "3-5-2-CDM" -> {
                w[1] *= 1.07; // PENALTY_AREA_CENTER
                w[2] *= 0.92; // PENALTY_AREA_WIDE
                w[4] *= 0.95; // LONG_RANGE
            }
            default -> {
            }
        }
    }

    private double[] computeLocationWeights(TeamStyle style, String formation) {
        return computeLocationWeights(style, formation, neutralShapeProfile(), neutralShapeProfile());
    }

    private double flankExploitOpportunity(double attackLane, double mirroredOpponentDefenseLane) {
        double vulnerability = Math.max(0.0, 1.0 - mirroredOpponentDefenseLane);
        return (attackLane * 0.90) - (mirroredOpponentDefenseLane * 0.70) + (vulnerability * 0.35);
    }

    private double[] styleLocationShift(TeamStyle style) {
        return switch (style) {
            case ATTACKING -> new double[] { 1.60, 1.11, 0.85, 0.50, 0.30 };
            case POSSESSION -> new double[] { 1.20, 1.11, 0.90, 0.56, 0.30 };
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> new double[] { 0.98, 0.94, 1.42, 0.98, 0.86 };
            case CENTRAL_PLAY -> new double[] { 1.12, 1.20, 0.72, 0.94, 0.86 };
            case COUNTER -> new double[] { 0.72, 1.11, 0.90, 0.94, 0.60 };
            case DEFENSIVE -> new double[] { 0.40, 0.93, 0.90, 1.22, 0.90 };
            default -> new double[] { 1.00, 1.00, 1.00, 1.00, 1.00 };
        };
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

    private double playmakerAdjustedAssistQuality(double baseAssistQuality, int playmakerSkill) {
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

    private double aggregateAttackerStat(
            List<V24PlayerMatchState> players,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (players.isEmpty()) return 70.0;
        List<V24PlayerMatchState> sorted = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .sorted((a, b) -> Integer.compare(b.attack(), a.attack()))
                .limit(7)
                .toList();
        double avg = sorted.stream()
                .mapToDouble(p -> p.attack()
                        * tacticalEffectiveness(p, slotsByPlayerId)
                        * forwardIntentMultiplier(p, slotsByPlayerId))
                .average()
                .orElse(70.0);
        return avg;
    }

    private double scheduledSubAttackVolumeMultiplier(
            V24TeamMatchState team,
            List<V24MatchContext.ScheduledSub> substitutions,
            String teamId,
            int minute) {
        if (team == null || substitutions == null || substitutions.isEmpty() || teamId == null) {
            return 1.0;
        }
        double delta = 0.0;
        for (V24MatchContext.ScheduledSub sub : substitutions) {
            if (sub == null
                    || !teamId.equals(sub.teamId())
                    || sub.effectiveMinute() > minute) {
                continue;
            }
            V24PlayerMatchState off = findPlayerForSubImpact(team, sub.playerOffId());
            V24PlayerMatchState on = findPlayerForSubImpact(team, sub.playerOnId());
            if (off == null || on == null) {
                continue;
            }
            delta += substitutionAttackFootprint(on) - substitutionAttackFootprint(off);
        }
        if (Math.abs(delta) < 0.001) {
            return 1.0;
        }
        return clamp(1.0 + (delta / 700.0), 0.86, 1.14);
    }

    private V24PlayerMatchState findPlayerForSubImpact(V24TeamMatchState team, String playerId) {
        if (team == null || playerId == null || playerId.isBlank()) return null;
        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p != null && playerId.equals(p.sessionPlayerId())) return p;
        }
        for (V24PlayerMatchState p : team.benchPlayers()) {
            if (p != null && playerId.equals(p.sessionPlayerId())) return p;
        }
        return null;
    }

    private double substitutionAttackFootprint(V24PlayerMatchState player) {
        if (player == null) return 0.0;
        String pos = player.naturalPosition() != null ? player.naturalPosition().toUpperCase(Locale.ROOT) : "";
        double roleWeight = switch (pos) {
            case "ATT", "ST", "CF" -> 1.18;
            case "WINGER", "LW", "RW" -> 1.12;
            case "MID", "CM", "CAM", "AM", "LM", "RM" -> 0.96;
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> 0.70;
            default -> 0.88;
        };
        return roleWeight * (
                player.attack() * 2.6
                    + player.technique() * 1.5
                    + player.speed() * 1.1
                    + player.mentality() * 0.8
                    + player.stamina() * 0.4);
    }

    private double aggregateCollectiveStat(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (players == null || players.isEmpty()) return 70.0;
        double avg = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .mapToDouble(p -> {
                    double outfieldBase = "GK".equals(p.position())
                            ? ((p.defense() + p.mentality()) / 2.0)
                            : ((p.attack() + p.defense() + p.mentality()) / 3.0);
                    return outfieldBase * tacticalEffectiveness(p, slotsByPlayerId);
                })
                .average()
                .orElse(70.0);
        return avg;
    }

    private double aggregateDefenderStat(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (players.isEmpty()) return 70.0;
        List<V24PlayerMatchState> defenders = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .filter(p -> p.position().equals("DEF") || p.position().equals("GK"))
                .toList();
        if (defenders.isEmpty()) {
            return players.stream()
                    .filter(V24PlayerMatchState::onPitch)
                    .mapToInt(V24PlayerMatchState::defense)
                    .average()
                    .orElse(70.0);
        }
        double avg = defenders.stream()
                .mapToDouble(p -> {
                    double eff = tacticalEffectiveness(p, slotsByPlayerId);
                    return ((p.defense() + p.mentality()) / 2.0) * eff;
                })
                .average()
                .orElse(70.0);
        if (slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return avg;
        }
        double weakestLink = defenders.stream()
                .mapToDouble(p -> {
                    double eff = tacticalEffectiveness(p, slotsByPlayerId);
                    return ((p.defense() + p.mentality()) / 2.0) * eff;
                })
                .min()
                .orElse(avg);
        return (avg * 0.45) + (weakestLink * 0.55);
    }

    private double aggregateDefenderStatForLocation(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId,
            V24ShotLocation location,
            double fallbackGlobalDefense) {
        return aggregateDefenderStatForLocation(
                players, slotsByPlayerId, location, null, fallbackGlobalDefense);
    }

    private double aggregateDefenderStatForLocation(
            List<V24PlayerMatchState> players,
            Map<String, LineupSlotDTO> slotsByPlayerId,
            V24ShotLocation location,
            V24ShotCoordinate shotCoordinate,
            double fallbackGlobalDefense) {
        if (players == null || players.isEmpty() || slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return fallbackGlobalDefense;
        }
        List<V24PlayerMatchState> defenders = players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .filter(p -> p.position().equals("DEF") || p.position().equals("GK"))
                .toList();
        if (defenders.isEmpty()) {
            return fallbackGlobalDefense;
        }

        double weighted = 0.0;
        double totalWeight = 0.0;
        for (V24PlayerMatchState p : defenders) {
            double x = tacticalXPercent(p, slotsByPlayerId);
            double y = tacticalYPercent(p, slotsByPlayerId);
            double channelWeight = defenderChannelWeight(location, x, shotCoordinate);
            double depthWeight = "GK".equals(p.position()) ? 1.05 : clamp(y / 82.0, 0.45, 1.18);
            double weight = channelWeight * depthWeight;
            if (weight <= 0.0) continue;
            double eff = tacticalEffectiveness(p, slotsByPlayerId);
            double stat = ((p.defense() + p.mentality()) / 2.0) * eff;
            weighted += stat * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            return fallbackGlobalDefense;
        }
        double channelDefense = weighted / totalWeight;
        return (channelDefense * 0.80) + (fallbackGlobalDefense * 0.20);
    }

    private double defenderChannelWeight(V24ShotLocation location, double xPercent, V24ShotCoordinate shotCoordinate) {
        double distanceFromCenter = Math.abs(xPercent - 50.0) / 50.0;
        double leftAffinity = laneLeftWeight(xPercent);
        double centerAffinity = laneCenterWeight(xPercent);
        double rightAffinity = laneRightWeight(xPercent);
        double wideAffinity = Math.max(leftAffinity, rightAffinity);
        return switch (location) {
            case PENALTY_AREA_WIDE -> {
                if (shotCoordinate == null) {
                    yield clamp(0.30 + wideAffinity * 1.15 + distanceFromCenter * 0.18, 0.30, 1.58);
                }
                boolean shotLeft = shotCoordinate.y() < 50.0;
                double sameSideAffinity = shotLeft ? leftAffinity : rightAffinity;
                double oppositeSideAffinity = shotLeft ? rightAffinity : leftAffinity;
                yield clamp(0.30
                        + sameSideAffinity * 1.35
                        + oppositeSideAffinity * 0.05
                        + distanceFromCenter * 0.08,
                        0.30, 1.68);
            }
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> clamp(0.45 + centerAffinity * 0.75, 0.45, 1.20);
            case OUTSIDE_BOX -> clamp(0.85 + centerAffinity * 0.10, 0.85, 0.95);
            case LONG_RANGE -> 0.70;
        };
    }

    private double defenderRosterChanceVolumeMultiplier(double defenderStat) {
        double delta = (70.0 - defenderStat) / 55.0;
        return clamp(1.0 + delta, 0.78, 1.35);
    }

    private double tacticalEffectiveness(
            V24PlayerMatchState player,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (player == null) {
            return 1.0;
        }
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot == null) {
            return PositionEffectivenessCalculator.effectiveness(
                    player.naturalPosition(), player.position());
        }
        double x = tacticalXPercent(player, slotsByPlayerId);
        double y = tacticalYPercent(player, slotsByPlayerId);
        return SubdivisionEffectivenessCalculator.effectiveness(
                player.naturalPosition(),
                x,
                y,
                player.position());
    }

    private double forwardIntentMultiplier(
            V24PlayerMatchState player,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (player == null) {
            return 1.0;
        }
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot == null || slot.customYPercent() == null || Double.isNaN(slot.customYPercent())) {
            return 1.0;
        }
        double y = slot.customYPercent();
        if ("ATT".equals(player.position())) {
            double forward = clamp((22.0 - y) / 18.0, 0.0, 1.0);
            double width = clamp(Math.abs(tacticalXPercent(player, slotsByPlayerId) - 50.0) / 50.0, 0.0, 1.0);
            return 1.0 + (0.12 * forward) + (0.05 * width);
        }
        double forward = clamp((55.0 - y) / 40.0, 0.0, 1.0);
        return 1.0 + (0.25 * forward);
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

    private int maxPasserSkill(List<V24PlayerMatchState> players) {
        return players.stream()
                .filter(V24PlayerMatchState::onPitch)
                .mapToInt(p -> p.getSkillLevel(PlayerSkill.PASSER))
                .max()
                .orElse(0);
    }

    private record V24TacticalShapeProfile(
            double possessionMultiplier,
            double attackVolumeMultiplier,
            double defensiveResistanceMultiplier,
            double attackLeft,
            double attackCenter,
            double attackRight,
            double defenseLeft,
            double defenseCenter,
            double defenseRight
    ) {}

    public record TacticalShapeDebug(
            double possessionMultiplier,
            double attackVolumeMultiplier,
            double defensiveResistanceMultiplier,
            double attackLeft,
            double attackCenter,
            double attackRight,
            double defenseLeft,
            double defenseCenter,
            double defenseRight
    ) {}

    public TacticalShapeDebug debugTacticalShape(
            SessionTeam team,
            List<SessionPlayer> starting,
            List<SessionPlayer> bench,
            TeamStyle style,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        V24TeamMatchState teamState = V24TeamMatchState.create(
                team,
                starting != null ? starting : List.of(),
                bench != null ? bench : List.of(),
                style,
                slotsByPlayerId != null ? slotsByPlayerId : Map.of());
        if (formation != null && !formation.isBlank()) {
            teamState.setFormation(formation);
        }
        V24TacticalShapeProfile profile = tacticalShapeProfile(teamState, formation, slotsByPlayerId);
        return new TacticalShapeDebug(
                profile.possessionMultiplier(),
                profile.attackVolumeMultiplier(),
                profile.defensiveResistanceMultiplier(),
                profile.attackLeft(),
                profile.attackCenter(),
                profile.attackRight(),
                profile.defenseLeft(),
                profile.defenseCenter(),
                profile.defenseRight());
    }

    private V24TacticalShapeProfile tacticalShapeProfile(
            V24TeamMatchState team,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        return tacticalShapeProfile(team, null, slotsByPlayerId);
    }

    private V24TacticalShapeProfile tacticalShapeProfile(
            V24TeamMatchState team,
            String formation,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (team == null || team.startingPlayers().isEmpty()) {
            return neutralShapeProfile();
        }

        int gk = 0;
        double def = 0.0;
        double mid = 0.0;
        double att = 0.0;
        double widthSum = 0.0;
        int widthCount = 0;
        double defWidthSum = 0.0;
        double midWidthSum = 0.0;
        double attWidthSum = 0.0;
        double defYSum = 0.0;
        double midYSum = 0.0;
        double attYSum = 0.0;
        double leftLane = 0.0;
        double centerLane = 0.0;
        double rightLane = 0.0;
        double attackLeft = 0.0;
        double attackCenter = 0.0;
        double attackRight = 0.0;
        double defenseLeft = 0.0;
        double defenseCenter = 0.0;
        double defenseRight = 0.0;
        double wingbackProjectionIntent = 0.0;
        double wingbackCoverIntent = 0.0;

        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p == null || !p.onPitch() || p.injured() || p.redCard()) continue;
            if ("GK".equals(p.position())) {
                gk++;
                continue;
            }

            double y = tacticalYPercent(p, slotsByPlayerId);
            double x = tacticalXPercent(p, slotsByPlayerId);
            double eff = tacticalEffectiveness(p, slotsByPlayerId);
            double structureEff = clamp(eff, 0.25, 1.0);
            double midfieldEff = midfieldStructureEffectiveness(p, eff);
            double widthFromCenter = Math.min(1.0, Math.abs(x - 50.0) / 50.0);
            double attW = clamp((55.0 - y) / 38.0, 0.0, 1.0);
            double defW = clamp((y - 65.0) / 18.0, 0.0, 1.0);
            double shapeSum = attW + defW;
            if (shapeSum > 1.0) {
                attW /= shapeSum;
                defW /= shapeSum;
                shapeSum = 1.0;
            }
            double midW = 1.0 - shapeSum;

            double weightedAttW = attW * structureEff;
            double weightedMidW = midW * midfieldEff * midfieldProfileMultiplier(p);
            double weightedDefW = defW * structureEff;

            att += weightedAttW;
            mid += weightedMidW;
            def += weightedDefW;
            attWidthSum += widthFromCenter * weightedAttW;
            midWidthSum += widthFromCenter * weightedMidW;
            defWidthSum += widthFromCenter * weightedDefW;
            attYSum += y * weightedAttW;
            midYSum += y * weightedMidW;
            defYSum += y * weightedDefW;

            double leftWeight = laneLeftWeight(x);
            double centerWeight = laneCenterWeight(x);
            double rightWeight = laneRightWeight(x);
            leftLane += leftWeight;
            centerLane += centerWeight;
            rightLane += rightWeight;

            double wingbackVerticalIntent = wingbackVerticalIntent(x, y);
            wingbackProjectionIntent += Math.max(0.0, wingbackVerticalIntent);
            wingbackCoverIntent += Math.max(0.0, -wingbackVerticalIntent);
            double verticalAttackIntent = clamp((100.0 - y) / 100.0, 0.0, 1.0);
            double verticalDefenseIntent = clamp(y / 100.0, 0.0, 1.0);
            double manualLaneIntent = 1.0 + (widthFromCenter - 0.40) * 0.16;
            double attackWeight = verticalAttackIntent
                    * structureEff
                    * clamp(manualLaneIntent, 0.92, 1.10)
                    * clamp(1.0 + Math.max(0.0, wingbackVerticalIntent) * 0.34
                            + Math.min(0.0, wingbackVerticalIntent) * 0.24,
                        0.82, 1.24);
            double defenseQuality = defensiveChannelQuality(p);
            double defenseWeight = verticalDefenseIntent
                    * structureEff
                    * defenseQuality
                    * clamp(1.0 + (widthFromCenter - 0.36) * 0.12, 0.94, 1.10)
                    * clamp(1.0 - Math.max(0.0, wingbackVerticalIntent) * 0.28
                            - Math.min(0.0, wingbackVerticalIntent) * 0.30,
                        0.82, 1.24);
            attackLeft += attackWeight * leftWeight;
            attackCenter += attackWeight * centerWeight;
            attackRight += attackWeight * rightWeight;
            defenseLeft += defenseWeight * leftWeight;
            defenseCenter += defenseWeight * centerWeight;
            defenseRight += defenseWeight * rightWeight;

            widthSum += widthFromCenter;
            widthCount++;
        }

        double width = widthCount > 0 ? widthSum / widthCount : 0.45;
        double defWidth = def > 0 ? defWidthSum / def : width;
        double midWidth = mid > 0 ? midWidthSum / mid : width;
        double attWidth = att > 0 ? attWidthSum / att : width;
        double defAvgY = def > 0 ? defYSum / def : 78.0;
        double midAvgY = mid > 0 ? midYSum / mid : 50.0;
        double attAvgY = att > 0 ? attYSum / att : 15.0;
        double centerShare = widthCount > 0 ? (double) centerLane / widthCount : 0.45;
        double sideBalance = widthCount > 0
                ? 1.0 - (Math.abs(leftLane - rightLane) / (double) widthCount)
                : 1.0;

        double midDelta = (mid - 4.0) * 0.065;
        double midfieldWidthBonus = (midWidth - 0.34) * 0.20;
        double centralOverloadBonus = Math.min(0.065, Math.max(0.0, centerShare - 0.45) * 0.13);
        double noOutletPenalty = Math.max(0.0, 0.22 - attWidth) * 0.22;
        double excessiveWidthPenalty = Math.max(0.0, width - 0.68) * 0.12;
        double midfieldShortagePenalty = Math.max(0.0, 4.0 - mid) * 0.045;
        double possession = 1.0 + midDelta + midfieldWidthBonus + centralOverloadBonus
                - noOutletPenalty - excessiveWidthPenalty - midfieldShortagePenalty;
        double attackDelta = (att - 2.0) * 0.145;
        double usefulAttackWidth = (attWidth - 0.30) * 0.36;
        double supportFromMidfield = (66.6667 - midAvgY) / 66.6667 * 0.10;
        double advancedLineBonus = (22.2222 - attAvgY) / 22.2222 * 0.075;
        double sideImbalancePenalty = Math.max(0.0, 0.72 - sideBalance) * 0.10;
        double noGkPenalty = gk == 1 ? 0.0 : 0.08;
        double attackVolume = 1.0 + attackDelta + usefulAttackWidth + supportFromMidfield
                + advancedLineBonus - sideImbalancePenalty - noGkPenalty;
        attackVolume += wingbackProjectionIntent * 0.035;
        attackVolume -= wingbackCoverIntent * 0.025;
        double defDelta = (def - 4.0) * 0.125;
        double defensiveWidthBonus = Math.min(0.095, Math.max(0.0, defWidth - 0.34) * 0.24);
        double lowBlockBonus = Math.max(0.0, defAvgY - 74.0) * 0.0048;
        double midfieldScreenBonus = Math.max(0.0, mid - 3.0) * 0.030;
        double midfieldScreenPenalty = Math.max(0.0, 4.0 - mid) * 0.070;
        double flankGapPenalty = Math.max(0.0, 0.30 - defWidth) * 0.34;
        double centralGapPenalty = Math.max(0.0, defWidth - 0.72) * 0.19;
        double defensiveStrength = defDelta + defensiveWidthBonus + lowBlockBonus + midfieldScreenBonus
                - midfieldScreenPenalty - flankGapPenalty - centralGapPenalty;
        double resistance = 1.0 - defensiveStrength;
        resistance += wingbackProjectionIntent * 0.026;
        resistance -= wingbackCoverIntent * 0.034;
        double lowBlockBackFiveShell = clamp(
                Math.max(0.0, defAvgY - 78.0) * 0.035
                        + Math.max(0.0, def - 4.0) * 0.35,
                0.0, 0.55);
        double lowBlockSecondLineDepth = clamp((midAvgY - 56.0) / 20.0, 0.0, 1.0);
        double lowBlockShapeIntent = clamp(
                lowBlockBackFiveShell + (lowBlockSecondLineDepth * 0.45),
                0.0, 1.0);

        if ("4-1-2-3".equals(formation)) {
            possession += 0.070;   // pivot improves circulation/control
            attackVolume -= 0.040; // one safer midfielder, but still a real front three
            resistance -= 0.140;   // lower opponent chance quality via central screen
        } else if ("4-3-3".equals(formation)) {
            possession -= 0.010;
            attackVolume += 0.075;
            resistance += 0.065;
        } else if ("4-2-2-2".equals(formation)) {
            possession -= 0.015;   // narrow box can be pressed toward touchlines
            attackVolume += 0.040; // two ST + two inside AMs create vertical punches
            resistance -= 0.024;   // double pivot keeps the narrow box from collapsing centrally
        } else if ("3-5-2-CDM".equals(formation)) {
            possession += 0.025;   // holder gives cleaner reset option
            attackVolume -= 0.020; // one CM sits instead of joining attacks
            resistance -= 0.060;   // real central shield
        } else if ("3-5-2".equals(formation)) {
            possession += 0.010 + (wingbackProjectionIntent * 0.010);
            attackVolume += 0.020 + (wingbackProjectionIntent * 0.030);
            resistance -= 0.055;
            resistance += wingbackProjectionIntent * 0.035; // high carrileros create transition space behind them
        } else if ("5-3-2".equals(formation)) {
            possession += 0.015;   // extra security helps recycle possession
            attackVolume -= 0.040; // fewer natural high/wide outlets
            resistance -= 0.180;   // five defenders should reduce opponent quality/volume
        } else if ("5-4-1".equals(formation)) {
            double secondLineOutlet = Math.max(0.0, 68.0 - midAvgY) * 0.018;
            possession -= 0.012 + (lowBlockShapeIntent * 0.018);
            attackVolume -= 0.085 + (lowBlockShapeIntent * 0.055);
            attackVolume += secondLineOutlet;
            resistance -= 0.080 + (lowBlockShapeIntent * 0.190);
        }

        possession = clamp(possession, 0.84, 1.18);
        attackVolume = clamp(attackVolume, 0.70, 1.30);
        resistance = clamp(resistance, 0.68, 1.24);

        double attackLeftChannel = normalizeChannel(attackLeft);
        double attackCenterChannel = normalizeChannel(attackCenter);
        double attackRightChannel = normalizeChannel(attackRight);
        double defenseLeftChannel = normalizeChannel(defenseLeft);
        double defenseCenterChannel = normalizeChannel(defenseCenter);
        double defenseRightChannel = normalizeChannel(defenseRight);

        if ("5-4-1".equals(formation)) {
            double lowBlockChannelIntent = 0.35 + (lowBlockShapeIntent * 0.65);
            defenseCenterChannel = clamp(defenseCenterChannel + (0.26 * lowBlockChannelIntent), 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + (0.16 * lowBlockChannelIntent), 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + (0.16 * lowBlockChannelIntent), 0.35, 1.65);
            attackCenterChannel = clamp(attackCenterChannel - (0.08 * lowBlockChannelIntent), 0.35, 1.65);
        } else if ("5-3-2".equals(formation)) {
            defenseCenterChannel = clamp(defenseCenterChannel + 0.14, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.24, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.24, 0.35, 1.65);
        } else if ("3-5-2".equals(formation)) {
            double projectedWingbackAttack = 0.06 + Math.min(0.12, wingbackProjectionIntent * 0.045);
            attackLeftChannel = clamp(attackLeftChannel + projectedWingbackAttack, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + projectedWingbackAttack, 0.35, 1.65);
            defenseCenterChannel = clamp(defenseCenterChannel + 0.06, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.26, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.26, 0.35, 1.65);
        } else if ("3-5-2-CDM".equals(formation)) {
            attackLeftChannel = clamp(attackLeftChannel + 0.06, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + 0.06, 0.35, 1.65);
            defenseCenterChannel = clamp(defenseCenterChannel + 0.10, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.36, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.36, 0.35, 1.65);
        } else if ("4-3-3".equals(formation)) {
            attackLeftChannel = clamp(attackLeftChannel + 0.24, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + 0.24, 0.35, 1.65);
            attackCenterChannel = clamp(attackCenterChannel - 0.06, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel - 0.08, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel - 0.08, 0.35, 1.65);
        } else if ("4-2-2-2".equals(formation)) {
            defenseCenterChannel = clamp(defenseCenterChannel + 0.10, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel - 0.04, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel - 0.04, 0.35, 1.65);
        }

        return new V24TacticalShapeProfile(
                possession,
                attackVolume,
                resistance,
                attackLeftChannel,
                attackCenterChannel,
                attackRightChannel,
                defenseLeftChannel,
                defenseCenterChannel,
                defenseRightChannel);
    }

    private V24TacticalShapeProfile neutralShapeProfile() {
        return new V24TacticalShapeProfile(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    private double wingbackVerticalIntent(double xPercent, double yPercent) {
        double widthFromCenter = Math.abs(xPercent - 50.0) / 50.0;
        if (widthFromCenter < 0.68 || yPercent < 38.0 || yPercent > 82.0) {
            return 0.0;
        }
        return clamp((55.0 - yPercent) / 17.0, -1.0, 1.0);
    }

    private double defensiveChannelQuality(V24PlayerMatchState player) {
        if (player == null) return 1.0;
        double defensiveBase = ((player.defense() + player.mentality()) / 2.0) / 70.0;
        double positionalMultiplier = switch (player.position()) {
            case "GK" -> 1.05;
            case "DEF" -> 1.00;
            case "MID" -> 0.88;
            default -> 0.72;
        };
        return clamp(defensiveBase * positionalMultiplier, 0.55, 1.22);
    }

    private double midfieldStructureEffectiveness(V24PlayerMatchState player, double tacticalEffectiveness) {
        if (player == null) return 1.0;
        double eff = clamp(tacticalEffectiveness, 0.0, 1.0);
        return clamp(eff * eff, 0.20, 1.0);
    }

    private double midfieldProfileMultiplier(V24PlayerMatchState player) {
        if (player == null) return 1.0;
        double controlProfile =
                player.technique() * 0.38
                        + player.mentality() * 0.24
                        + player.getSkillLevel(PlayerSkill.PASSER) * 0.16
                        + player.getSkillLevel(PlayerSkill.PLAYMAKER) * 0.12
                        + player.stamina() * 0.10;
        double screenProfile =
                player.defense() * 0.44
                        + player.mentality() * 0.20
                        + player.stamina() * 0.14
                        + player.getSkillLevel(PlayerSkill.TACKLER) * 0.14
                        + player.getSkillLevel(PlayerSkill.MARKER) * 0.08;
        double midfieldProfile = (controlProfile * 0.56) + (screenProfile * 0.44);
        double naturalMidfieldFit = midfieldNaturalFit(player);
        return clamp((0.72 + (midfieldProfile / 250.0)) * naturalMidfieldFit, 0.50, 1.12);
    }

    private double midfieldNaturalFit(V24PlayerMatchState player) {
        if (player == null) return 1.0;
        String natural = player.naturalPosition() != null ? player.naturalPosition() : player.position();
        String tactical = player.position();
        return switch (natural) {
            case "MID" -> 1.0;
            case "DEF" -> "MID".equals(tactical) ? 0.84 : 0.78;
            case "WINGER" -> 0.76;
            case "ATT" -> 0.70;
            case "GK" -> 0.20;
            default -> 0.86;
        };
    }

    private double normalizeChannel(double raw) {
        return clamp(raw / 2.0, 0.35, 1.65);
    }

    private double laneLeftWeight(double xPercent) {
        return clamp((50.0 - xPercent) / 30.0, 0.0, 1.0);
    }

    private double laneRightWeight(double xPercent) {
        return clamp((xPercent - 50.0) / 30.0, 0.0, 1.0);
    }

    private double laneCenterWeight(double xPercent) {
        return 1.0 - Math.max(laneLeftWeight(xPercent), laneRightWeight(xPercent));
    }

    private double channelMismatchMultiplier(V24TacticalShapeProfile attack, V24TacticalShapeProfile defense) {
        if (attack == null || defense == null) return 1.0;
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

    private double professionalShotTempoMultiplier() {
        return 1.00;
    }

    private double homeFieldChanceVolumeMultiplier(boolean homeHasPossession) {
        return homeHasPossession ? HOME_CHANCE_VOLUME_ADVANTAGE : AWAY_CHANCE_VOLUME_FRICTION;
    }

    private double collectiveQualityChanceVolumeMultiplier(double possessorCollectiveStat, double opponentCollectiveStat) {
        double edge = possessorCollectiveStat - opponentCollectiveStat;
        return clamp(1.0 + (edge * 0.022), 0.89, 1.11);
    }

    private double defensiveShapeShotQualityMultiplier(V24TacticalShapeProfile defense, V24ShotLocation location) {
        if (defense == null || location == null) return 1.0;

        double centralCover = defense.defenseCenter();
        double wideCover = (defense.defenseLeft() + defense.defenseRight()) / 2.0;
        double laneCover = switch (location) {
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> centralCover;
            case PENALTY_AREA_WIDE -> wideCover;
            case OUTSIDE_BOX -> (centralCover * 0.65) + (wideCover * 0.35);
            case LONG_RANGE -> centralCover;
        };

        double laneEffect = (laneCover - 1.0) * switch (location) {
            case SIX_YARD_BOX -> 0.155;
            case PENALTY_AREA_CENTER -> 0.135;
            case PENALTY_AREA_WIDE -> 0.125;
            case OUTSIDE_BOX -> 0.070;
            case LONG_RANGE -> 0.045;
        };
        double resistanceEffect = (1.0 - defense.defensiveResistanceMultiplier()) * 0.220;
        return clamp(1.0 - laneEffect - resistanceEffect, 0.72, 1.18);
    }

    private double tacticalYPercent(V24PlayerMatchState player, Map<String, LineupSlotDTO> slotsByPlayerId) {
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot != null && slot.customYPercent() != null && Double.isFinite(slot.customYPercent())) {
            return clamp(slot.customYPercent(), 0.0, 100.0);
        }
        Double canonical = canonicalYPercent(slot);
        if (canonical != null) return canonical;
        return switch (player.position()) {
            case "ATT", "WINGER" -> 15.0;
            case "MID" -> 50.0;
            case "GK" -> 92.0;
            default -> 78.0;
        };
    }

    private double tacticalXPercent(V24PlayerMatchState player, Map<String, LineupSlotDTO> slotsByPlayerId) {
        LineupSlotDTO slot = slotFor(player, slotsByPlayerId);
        if (slot != null && slot.customXPercent() != null && Double.isFinite(slot.customXPercent())) {
            return clamp(slot.customXPercent(), 0.0, 100.0);
        }
        Double canonical = canonicalXPercent(slot);
        if (canonical != null) return canonical;
        return switch (player.position()) {
            case "WINGER" -> 18.0;
            default -> 50.0;
        };
    }

    private LineupSlotDTO slotFor(V24PlayerMatchState player, Map<String, LineupSlotDTO> slotsByPlayerId) {
        if (player == null || slotsByPlayerId == null || slotsByPlayerId.isEmpty()) return null;
        return slotsByPlayerId.get(player.sessionPlayerId());
    }

    private Double canonicalXPercent(LineupSlotDTO slot) {
        int[] parsed = parseSubdivision(slot);
        if (parsed == null) return null;
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return clamp(left + 11.11 / 2.0, 0.0, 100.0);
    }

    private Double canonicalYPercent(LineupSlotDTO slot) {
        if (slot != null && "GK-1".equals(slot.subdivisionId())) {
            return 93.0;
        }
        int[] parsed = parseSubdivision(slot);
        if (parsed == null) return null;
        int sector = parsed[0];
        int sectorRow = (sector - 1) / 3;
        double top = sectorRow * 11.11;
        return clamp(top + 11.11 / 2.0, 0.0, 100.0);
    }

    private int[] parseSubdivision(LineupSlotDTO slot) {
        if (slot == null || slot.subdivisionId() == null) return null;
        String id = slot.subdivisionId();
        if ("GK-1".equals(id)) return null;
        if (!id.startsWith("S")) return null;
        int dash = id.indexOf('-');
        if (dash < 0 || dash >= id.length() - 1) return null;
        try {
            int sector = Integer.parseInt(id.substring(1, dash));
            int subIndex = Integer.parseInt(id.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) return null;
            return new int[] { sector, subIndex };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static double computeTeamAvgOverall(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) return 50.0;
        int sum = 0;
        int count = 0;
        for (SessionPlayer p : players) {
            if (p != null) {
                Integer overall = p.calculateOverall();
                if (overall != null) {
                    sum += overall;
                    count++;
                }
            }
        }
        return count > 0 ? (double) sum / count : 50.0;
    }

    private static double computeOverallDiffRatio(double homeOvr, double awayOvr) {
        double max = Math.max(homeOvr, awayOvr);
        if (max <= 0.0) return 0.0;
        return Math.abs(homeOvr - awayOvr) / max;
    }

    private static double computeMatchIntensity(double diffRatio) {
        final double PAREJOS_INTENSITY = 0.60;
        final double DESIGUALES_INTENSITY = 1.00;
        final double DIFF_PAREJOS_THRESHOLD = 0.05;
        final double DIFF_DESIGUALES_THRESHOLD = 0.30;
        if (diffRatio <= DIFF_PAREJOS_THRESHOLD) return PAREJOS_INTENSITY;
        if (diffRatio >= DIFF_DESIGUALES_THRESHOLD) return DESIGUALES_INTENSITY;
        double t = (diffRatio - DIFF_PAREJOS_THRESHOLD)
                / (DIFF_DESIGUALES_THRESHOLD - DIFF_PAREJOS_THRESHOLD);
        return PAREJOS_INTENSITY + (DESIGUALES_INTENSITY - PAREJOS_INTENSITY) * t;
    }

    private double styleToModifier(TeamStyle style) {
        return switch (style) {
            case ATTACKING -> 1.15;
            case POSSESSION -> 1.05;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.04;
            case CENTRAL_PLAY -> 1.02;
            case BALANCED -> 1.00;
            case COUNTER -> 0.95;
            case DEFENSIVE -> 0.85;
        };
    }

    private double defensiveStyleChanceVolumeMultiplier(TeamStyle defendingStyle) {
        if (defendingStyle == null) return 1.0;
        return switch (defendingStyle) {
            case DEFENSIVE -> 0.82;
            case COUNTER -> 0.91;
            case POSSESSION -> 0.96;
            case BALANCED -> 1.00;
            case CENTRAL_PLAY -> 1.01;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.01;
            case ATTACKING -> 1.08;
        };
    }

    private double defensiveStyleShotQualityMultiplier(TeamStyle defendingStyle, V24ShotLocation location) {
        if (defendingStyle == null || location == null) return 1.0;
        double base = switch (defendingStyle) {
            case DEFENSIVE -> 0.91;
            case COUNTER -> 0.96;
            case POSSESSION -> 0.98;
            case BALANCED -> 1.00;
            case CENTRAL_PLAY -> 1.01;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.01;
            case ATTACKING -> 1.06;
        };
        double laneAdjustment = switch (location) {
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> defendingStyle == TeamStyle.DEFENSIVE ? 0.97 : 1.0;
            case PENALTY_AREA_WIDE -> defendingStyle == TeamStyle.WIDE_PLAY
                    || defendingStyle == TeamStyle.LEFT_FLANK
                    || defendingStyle == TeamStyle.RIGHT_FLANK ? 0.98 : 1.0;
            case OUTSIDE_BOX -> 1.0;
            case LONG_RANGE -> defendingStyle == TeamStyle.DEFENSIVE ? 0.95 : 1.0;
        };
        return clamp(base * laneAdjustment, 0.84, 1.10);
    }

    private double chanceProbability(TeamStyle style, int minute) {
        return chanceProbability(style, minute, 70, 70, 0, 0);
    }

    private double chanceProbability(TeamStyle style, int minute, int possessorAttack, int possessorSpeed) {
        return chanceProbability(style, minute, possessorAttack, possessorSpeed, 0, 0);
    }

    private double chanceProbability(TeamStyle style, int minute, int possessorAttack,
                                     int possessorSpeed, int dribblerSkill) {
        return chanceProbability(style, minute, possessorAttack, possessorSpeed,
                dribblerSkill, 0);
    }

    private double chanceProbability(TeamStyle style, int minute, int possessorAttack,
                                     int possessorSpeed, int dribblerSkill, int speedsterSkill) {
        double base = switch (style) {
            case ATTACKING -> 0.42;
            case POSSESSION -> 0.38;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 0.36;
            case CENTRAL_PLAY -> 0.34;
            case COUNTER -> 0.35;
            case DEFENSIVE -> 0.28;
            case BALANCED -> 0.35;
        };
        double secondHalf = (minute > 45) ? 1.15 : 1.0;
        double endGame = (minute > 75) ? 1.2 : 1.0;
        int effectiveSpeed = possessorSpeed;
        if (style == TeamStyle.COUNTER && speedsterSkill > 0) {
            effectiveSpeed += speedsterSkill / 3;
        }
        double qualityMod = 1.0
            + (possessorAttack - 70) * 0.02
            + (effectiveSpeed - 70) * 0.01;
        double dribblerMult = 1.0 + (dribblerSkill / 600.0);

        return base * secondHalf * endGame * qualityMod * dribblerMult;
    }

    private double possessionBase(TeamStyle style) {
        return switch (style) {
            case POSSESSION -> 58.0;
            case ATTACKING -> 52.0;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK, CENTRAL_PLAY -> 50.0;
            case COUNTER -> 48.0;
            case DEFENSIVE -> 45.0;
            case BALANCED -> 50.0;
        };
    }
    private void applyMinuteDrain(V24TeamMatchState team, TeamStyle style) {
        int baseDrain = fatigueModel.baseDrainPerMinute(style);
        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard()) {
                fatigueModel.applyDrain(p, baseDrain);
            }
        }
    }

    private void applyScheduledSubManually(V24TeamMatchState team, V24MatchContext.ScheduledSub sub) {
        if (team == null || sub == null) return;
        team.swapStartingBenchForF25(sub.playerOffId(), sub.playerOnId());
    }

    private Map<String, LineupSlotDTO> effectiveSlotsForMinute(
            Map<String, LineupSlotDTO> baseSlotsByPlayerId,
            List<V24MatchContext.ScheduledSub> substitutions,
            String teamId,
            int minute) {
        if (baseSlotsByPlayerId == null || baseSlotsByPlayerId.isEmpty()
                || substitutions == null || substitutions.isEmpty()
                || teamId == null) {
            return baseSlotsByPlayerId;
        }
        Map<String, LineupSlotDTO> effective = null;
        for (V24MatchContext.ScheduledSub sub : substitutions) {
            if (sub == null
                    || !teamId.equals(sub.teamId())
                    || sub.effectiveMinute() > minute) {
                continue;
            }
            LineupSlotDTO offSlot = baseSlotsByPlayerId.get(sub.playerOffId());
            if (offSlot == null) {
                continue;
            }
            if (effective == null) {
                effective = new HashMap<>(baseSlotsByPlayerId);
            }
            effective.put(sub.playerOnId(), offSlot);
        }
        return effective != null ? effective : baseSlotsByPlayerId;
    }

    private V24DetailedMatchResult finalizeResult(
            V24MatchContext ctx,
            V24TeamMatchState home,
            V24TeamMatchState away,
            V24MatchTimeline timeline) {

        int homePossTicks = home.possessionTicks();
        int awayPossTicks = away.possessionTicks();
        int totalPoss = homePossTicks + awayPossTicks;
        int homePoss = totalPoss > 0 ? (int) Math.round(100.0 * homePossTicks / totalPoss) : 50;
        int awayPoss = 100 - homePoss;
        long goalsInTimeline = timeline.events().stream()
            .filter(e -> e.type() == V24MatchEventType.GOAL)
            .count();
        int totalPossessedGoals = home.goals() + away.goals();
        if (goalsInTimeline != totalPossessedGoals) {
            log.warn("[V24-XG-DIVERGENCE] matchId={}, homeGoals={}, awayGoals={}, "
                    + "goalsInTimeline={}, counter={}, divergence={}",
                ctx.matchId(),
                home.goals(), away.goals(),
                goalsInTimeline, goalAdditions.get(),
                totalPossessedGoals - goalsInTimeline);
        }
        double homeXg = home.xg();
        double awayXg = away.xg();
        int totalGoals = totalPossessedGoals;
        if (totalGoals > 0 && (homeXg + awayXg) > 0
            && totalGoals > 5 * (homeXg + awayXg)) {
            log.warn("[V24-XG-DIVERGENCE-OUTLIER] matchId={}, goals={} ({}x), xG={}, homeXg={}, awayXg={}",
                ctx.matchId(), totalGoals,
                String.format("%.2f", totalGoals / (homeXg + awayXg)),
                homeXg + awayXg, homeXg, awayXg);
        }

        String summary = String.format("%s %d - %d %s",
                ctx.homeTeam().getName(),
                home.goals(),
                away.goals(),
                ctx.awayTeam().getName());

        return V24DetailedMatchResult.builder()
                .matchId(ctx.matchId())
                .homeTeamId(ctx.homeTeamId())
                .awayTeamId(ctx.awayTeamId())
                .homeGoals(home.goals())
                .awayGoals(away.goals())
                .homeXg(Math.round(home.xg() * 1000.0) / 1000.0)
                .awayXg(Math.round(away.xg() * 1000.0) / 1000.0)
                .homeShots(home.shots())
                .awayShots(away.shots())
                .homePossession(homePoss)
                .awayPossession(awayPoss)
                .timeline(timeline)
                .summary(summary)
                .build();
    }

    void applyYellowCardAndMaybeSecondYellowRed(
            V24PlayerMatchState player,
            V24MatchTimeline timeline,
            int minute,
            String teamRole) {
        boolean wasRedBefore = player.redCard();
        player.addYellowCard();
        timeline.addEvent(new V24MatchEvent(
                minute,
                V24MatchEventType.YELLOW_CARD,
                teamRole,
                player.sessionPlayerId(),
                player.name(),
                null, null,
                0.0,
                player.name() + " received a yellow card"
        ));
        if (player.yellowCards() >= 2 && !wasRedBefore) {
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.RED_CARD,
                    teamRole,
                    player.sessionPlayerId(),
                    player.name(),
                    null, null,
                    0.0,
                    player.name() + " received a red card (second yellow)"
            ));
        }
    }
}

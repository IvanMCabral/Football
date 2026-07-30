package com.footballmanager.application.service.simulation.detailed;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
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
final class DetailedMatchEngineFlow implements DetailedMatchEngineProvider {
    private static final Logger log = LoggerFactory.getLogger(DetailedMatchEngineFlow.class);
    private static final AtomicInteger goalAdditions = new AtomicInteger(0);
    private final ShotXgCalculator xgCalculator = new ShotXgCalculator();
    private final FatigueModel fatigueModel = new FatigueModel();
    private final DisciplineModel disciplineModel;
    private final InjuryModel injuryModel = new InjuryModel();
    private final SubstitutionEngine substitutionEngine = new SubstitutionEngine();
    private final AssistModel assistModel = new AssistModel();
    private final ShotLocationService shotLocationService = new ShotLocationService();
    private final TacticalPositionService tacticalPositionService = new TacticalPositionService();
    private final TacticalEffectivenessService tacticalEffectivenessService =
            new TacticalEffectivenessService(tacticalPositionService);
    private final DefenseChannelService defenseChannelService =
            new DefenseChannelService(tacticalPositionService, tacticalEffectivenessService);
    private final AttackContributionService attackContributionService =
            new AttackContributionService(tacticalEffectivenessService);
    private final TacticalShapeService tacticalShapeService =
            new TacticalShapeService(tacticalPositionService, tacticalEffectivenessService);
    private final MatchProbabilityService matchProbabilityService = new MatchProbabilityService();
    private final MatchResultFinalizer matchResultFinalizer = new MatchResultFinalizer();
    private final ShotAttemptService shotAttemptService =
            new ShotAttemptService(xgCalculator, fatigueModel, assistModel, shotLocationService,
                    defenseChannelService, attackContributionService, matchProbabilityService, goalAdditions, log);
    private final MatchIntensityService matchIntensityService = new MatchIntensityService();
    private final EffectiveSlotService effectiveSlotService = new EffectiveSlotService();
    private final CardEventService cardEventService = new CardEventService();
    private final PlayerSkillService playerSkillService = new PlayerSkillService();
    private static final double HOME_POSSESSION_ADVANTAGE = 1.035;
    private static final double AWAY_POSSESSION_FRICTION = 0.985;
    private static final double HOME_CHANCE_VOLUME_ADVANTAGE = 1.040;
    private static final double AWAY_CHANCE_VOLUME_FRICTION = 0.985;
    DetailedMatchEngineFlow() {
        this(new DisciplineModel());
    }
    DetailedMatchEngineFlow(DisciplineModel disciplineModel) {
        this.disciplineModel = disciplineModel != null ? disciplineModel : new DisciplineModel();
    }
    public DetailedMatchResult simulate(MatchContext context, long seed) {
        return simulateWithRandom(context,
            new Random(seed),
            new Random(seed),
            new Random(seed + 1));
    }
    public DetailedMatchResult simulate(MatchContext context, Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        return simulateWithRandom(context, random, random, random);
    }
    public DetailedMatchResult simulate(MatchContext context, Random random, int maxMinute) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            throw new IllegalArgumentException(
                "maxMinute must be in [1, 90], got " + maxMinute);
        }
        return simulateWithRandomBounded(context, random, random, random, maxMinute);
    }
    private DetailedMatchResult simulateWithRandom(
            MatchContext context, Random random, Random homeSelectorRandom, Random awaySelectorRandom) {
        return simulateWithRandomBounded(context, random, homeSelectorRandom, awaySelectorRandom, 90);
    }
    private DetailedMatchResult simulateWithRandomBounded(
            MatchContext context, Random random, Random homeSelectorRandom, Random awaySelectorRandom, int maxMinute) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            throw new IllegalArgumentException(
                "maxMinute must be in [1, 90], got " + maxMinute);
        }
        TeamMatchState homeState = TeamMatchState.create(
                context.homeTeam(), context.homeStartingPlayers(),
                context.homeBenchPlayers(), context.homeStyle(),
                context.homeSlotsByPlayerId());
        TeamMatchState awayState = TeamMatchState.create(
                context.awayTeam(), context.awayStartingPlayers(),
                context.awayBenchPlayers(), context.awayStyle(),
                context.awaySlotsByPlayerId());
        MatchClock clock = new MatchClock(90);
        MatchTimeline timeline = new MatchTimeline();
        double homePossBase = matchProbabilityService.possessionBase(context.homeStyle());
        double awayPossBase = matchProbabilityService.possessionBase(context.awayStyle());
        int homeMaxPasser = playerSkillService.maxSkill(homeState.startingPlayers(), PlayerSkill.PASSER);
        int awayMaxPasser = playerSkillService.maxSkill(awayState.startingPlayers(), PlayerSkill.PASSER);
        com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile homeShape = tacticalShapeProfile(
                homeState, context.homeFormation(), context.homeSlotsByPlayerId());
        com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile awayShape = tacticalShapeProfile(
                awayState, context.awayFormation(), context.awaySlotsByPlayerId());
        double homePossAdj = homePossBase * (1.0 + homeMaxPasser / 300.0)
                * homeShape.possessionMultiplier()
                * HOME_POSSESSION_ADVANTAGE;
        double awayPossAdj = awayPossBase * (1.0 + awayMaxPasser / 300.0)
                * awayShape.possessionMultiplier()
                * AWAY_POSSESSION_FRICTION;
        double homeShare = homePossAdj / (homePossAdj + awayPossAdj);
        double homeAvgOverall = matchIntensityService.computeTeamAvgOverall(context.homeStartingPlayers());
        double awayAvgOverall = matchIntensityService.computeTeamAvgOverall(context.awayStartingPlayers());
        double overallDiffRatio = matchIntensityService.computeOverallDiffRatio(homeAvgOverall, awayAvgOverall);
        double matchIntensity = matchIntensityService.computeMatchIntensity(overallDiffRatio);
        Set<String> appliedScheduledSubs = new HashSet<>();
        SubstitutionEngine scheduledSubEngine = new SubstitutionEngine();
        log.trace("matchIntensity={} (homeOvr={}, awayOvr={}, diffRatio={})",
            matchIntensity, homeAvgOverall, awayAvgOverall, overallDiffRatio);
        PlayerSelector homeSelector = new PlayerSelector(homeSelectorRandom);
        PlayerSelector awaySelector = new PlayerSelector(awaySelectorRandom);
        while (clock.isRunning()) {
            int minute = clock.currentMinute();
            if (minute > maxMinute) {
                break;
            }
            for (MatchContext.ScheduledSub sub : context.manualSubstitutions()) {
                if (sub.effectiveMinute() != minute) {
                    continue;
                }
                String subKey = sub.effectiveMinute() + ":" + sub.teamId() + ":" + sub.playerOffId();
                TeamMatchState target = sub.teamId().equals(context.homeTeamId())
                        ? homeState : awayState;
                if (appliedScheduledSubs.contains(subKey)) {
                    continue;
                }
                try {
                    DetailedMatchEvent subEvent = scheduledSubEngine.manualSubstitute(
                            target, sub.playerOffId(), sub.playerOnId(), sub.effectiveMinute());
                    timeline.addEvent(subEvent);
                    appliedScheduledSubs.add(subKey);
                    log.trace("Applied scheduled sub at minute {}: teamId={} off={} on={}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
                } catch (IllegalStateException e) {
                    log.warn("Could not apply scheduled sub at minute {} "
                            + "teamId={} off={} on={}: {}",
                            minute, sub.teamId(), sub.playerOffId(), sub.playerOnId(), e.getMessage());
                }
            }
            homeMaxPasser = playerSkillService.maxSkill(homeState.startingPlayers(), PlayerSkill.PASSER);
            awayMaxPasser = playerSkillService.maxSkill(awayState.startingPlayers(), PlayerSkill.PASSER);
            Map<String, LineupSlot> homeEffectiveSlots = effectiveSlotService.effectiveSlotsForMinute(
                    context.homeSlotsByPlayerId(),
                    context.manualSubstitutions(),
                    context.homeTeamId(),
                    minute);
            Map<String, LineupSlot> awayEffectiveSlots = effectiveSlotService.effectiveSlotsForMinute(
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
            TeamMatchState possessor = homeHasPossession ? homeState : awayState;
            TeamMatchState opponent = homeHasPossession ? awayState : homeState;
            PlayerSelector selector = homeHasPossession ? homeSelector : awaySelector;
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
            for (PlayerMatchState p : possessor.startingPlayers()) {
                if (p.onPitch() && !p.injured() && !p.redCard() && p.attack() > bestAttack) {
                    bestAttack = p.attack();
                    keyAttack = p.attack();
                    keySpeed = p.speed();
                    keyDribbler = p.getSkillLevel(PlayerSkill.DRIBBLER);
                    keySpeedster = p.getSkillLevel(PlayerSkill.SPEEDSTER);
                }
            }
            com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile possessorShape = homeHasPossession ? homeShape : awayShape;
            com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile opponentShape = homeHasPossession ? awayShape : homeShape;
            Map<String, LineupSlot> possessorSlots = homeHasPossession
                    ? homeEffectiveSlots
                    : awayEffectiveSlots;
            Map<String, LineupSlot> opponentSlots = homeHasPossession
                    ? awayEffectiveSlots
                    : homeEffectiveSlots;
            double aggregateAttack = attackContributionService.aggregateAttackerStat(
                    possessor.startingPlayers(),
                    possessorSlots);
            int teamAttackInfluence = (int) Math.round((keyAttack * 0.40) + (aggregateAttack * 0.60));
            double opponentDefenderStat = defenseChannelService.aggregateDefenderStat(opponent.startingPlayers(), opponentSlots);
            double possessorCollectiveStat = attackContributionService.aggregateCollectiveStat(possessor.startingPlayers(), possessorSlots);
            double opponentCollectiveStat = attackContributionService.aggregateCollectiveStat(opponent.startingPlayers(), opponentSlots);
            double chanceProbability = matchProbabilityService.chanceProbability(possessor.style(), minute, teamAttackInfluence, keySpeed, keyDribbler, keySpeedster)
                    * matchProbabilityService.professionalShotTempoMultiplier()
                    * Math.sqrt((1.0 + matchIntensity) / 2.0)
                    * possessorShape.attackVolumeMultiplier()
                    * opponentShape.defensiveResistanceMultiplier()
                    * defenseChannelService.defenderRosterChanceVolumeMultiplier(opponentDefenderStat)
                    * attackContributionService.scheduledSubAttackVolumeMultiplier(
                            possessor,
                            context.manualSubstitutions(),
                            possessor.teamId(),
                            minute)
                    * matchProbabilityService.collectiveQualityChanceVolumeMultiplier(possessorCollectiveStat, opponentCollectiveStat)
                    * matchProbabilityService.defensiveStyleChanceVolumeMultiplier(opponent.style())
                    * matchProbabilityService.homeFieldChanceVolumeMultiplier(homeHasPossession)
                    * channelMismatchMultiplier(possessorShape, opponentShape);
            if (random.nextDouble() < chanceProbability) {
                shotAttemptService.attemptShot(
                        possessor, opponent, selector, formation, opponentFormation,
                        possessorShape, opponentShape, possessorSlots, opponentSlots,
                        teamRole, minute, random, timeline, matchIntensity);
            }
            if (random.nextDouble() < chanceProbability * 0.6) {
                var creator = selector.selectShooter(possessor.startingPlayers(), formation);
                if (creator.isPresent()) {
                    PlayerMatchState c = creator.get();
                    timeline.addEvent(new DetailedMatchEvent(
                            minute,
                            DetailedMatchEventType.CHANCE_CREATED,
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
                PlayerMatchState f = potentialFouler.get();
                boolean defending = !homeHasPossession; // fouler is on defending side when opponent has possession
                if (disciplineModel.shouldCommitFoul(f, possessor.style(), defending, random)) {
                    timeline.addEvent(new DetailedMatchEvent(
                            minute,
                            DetailedMatchEventType.FOUL,
                            teamRole,
                            f.sessionPlayerId(),
                            f.name(),
                            null, null,
                            0.0,
                            f.name() + " committed a foul"
                    ));
                    fatigueModel.applyDrain(f, 5);
                    if (disciplineModel.shouldReceiveYellow(f, possessor.style(), random) && !f.redCard()) {
                        cardEventService.applyYellowCardAndMaybeSecondYellowRed(f, timeline, minute, teamRole);
                    }
                }
            }
            var potentialInjured = selector.selectShooter(possessor.startingPlayers(), formation);
            if (potentialInjured.isPresent()) {
                PlayerMatchState p = potentialInjured.get();
                if (injuryModel.shouldInjure(p, possessor.style(), false, random)) {
                    p.injure();
                    timeline.addEvent(new DetailedMatchEvent(
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
            if (random.nextDouble() < 0.035) {
                var player = selector.selectShooter(possessor.startingPlayers(), formation);
                if (player.isPresent()) {
                    timeline.addEvent(new DetailedMatchEvent(
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
            if (random.nextDouble() < 0.04 && possessor.style() != TeamStyle.DEFENSIVE) {
                var player = selector.selectShooter(possessor.startingPlayers(), formation);
                if (player.isPresent()) {
                    timeline.addEvent(new DetailedMatchEvent(
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
        return matchResultFinalizer.finalizeResult(
                context, homeState, awayState, timeline, goalAdditions.get(), log);
    }
    static double onTargetProbability(double xg) {
        return ShotAttemptService.onTargetProbability(xg);
    }
    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return shotAttemptService.aggregateOpponentDefenderSkills(opponents);
    }
    void applyYellowCardAndMaybeSecondYellowRed(PlayerMatchState player, MatchTimeline timeline, int minute, String teamRole) {
        cardEventService.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
    }
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
            Map<String, LineupSlot> slotsByPlayerId) {
        TeamMatchState teamState = TeamMatchState.create(
                team,
                starting != null ? starting : List.of(),
                bench != null ? bench : List.of(),
                style,
                slotsByPlayerId != null ? slotsByPlayerId : Map.of());
        if (formation != null && !formation.isBlank()) {
            teamState.setFormation(formation);
        }
        com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile profile = tacticalShapeProfile(teamState, formation, slotsByPlayerId);
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
    private com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            Map<String, LineupSlot> slotsByPlayerId) {
        return tacticalShapeService.tacticalShapeProfile(team, slotsByPlayerId);
    }
    private com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        return tacticalShapeService.tacticalShapeProfile(team, formation, slotsByPlayerId);
    }
    private com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile neutralShapeProfile() {
        return tacticalShapeService.neutralShapeProfile();
    }
    private double channelMismatchMultiplier(com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile attack, com.footballmanager.application.service.simulation.detailed.TacticalShapeProfile defense) {
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
    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
private void applyMinuteDrain(TeamMatchState team, TeamStyle style) {
        int baseDrain = fatigueModel.baseDrainPerMinute(style);
        for (PlayerMatchState p : team.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard()) {
                fatigueModel.applyDrain(p, baseDrain);
            }
        }
    }
}


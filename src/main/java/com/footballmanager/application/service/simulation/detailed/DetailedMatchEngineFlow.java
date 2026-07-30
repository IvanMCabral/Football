package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class DetailedMatchEngineFlow implements DetailedMatchEngineProvider {
    private static final Logger log = LoggerFactory.getLogger(DetailedMatchEngineFlow.class);

    private final DisciplineModel disciplineModel;
    private final DetailedMatchMinuteFlow minuteFlow;
    private final MatchProbabilityService matchProbabilityService = new MatchProbabilityService();
    private final MatchResultFinalizer matchResultFinalizer = new MatchResultFinalizer();
    private final MatchIntensityService matchIntensityService = new MatchIntensityService();

    DetailedMatchEngineFlow() {
        this(new DisciplineModel());
    }

    DetailedMatchEngineFlow(DisciplineModel disciplineModel) {
        this.disciplineModel = disciplineModel != null ? disciplineModel : new DisciplineModel();
        this.minuteFlow = new DetailedMatchMinuteFlow(this.disciplineModel, log);
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
            throw new IllegalArgumentException("maxMinute must be in [1, 90], got " + maxMinute);
        }
        return simulateWithRandomBounded(context, random, random, random, maxMinute);
    }

    private DetailedMatchResult simulateWithRandom(
            MatchContext context,
            Random random,
            Random homeSelectorRandom,
            Random awaySelectorRandom) {
        return simulateWithRandomBounded(context, random, homeSelectorRandom, awaySelectorRandom, 90);
    }

    private DetailedMatchResult simulateWithRandomBounded(
            MatchContext context,
            Random random,
            Random homeSelectorRandom,
            Random awaySelectorRandom,
            int maxMinute) {
        validate(context, maxMinute);

        TeamMatchState homeState = createTeamState(
                context.homeTeam(), context.homeStartingPlayers(), context.homeBenchPlayers(),
                context.homeStyle(), context.homeSlotsByPlayerId());
        TeamMatchState awayState = createTeamState(
                context.awayTeam(), context.awayStartingPlayers(), context.awayBenchPlayers(),
                context.awayStyle(), context.awaySlotsByPlayerId());
        MatchTimeline timeline = new MatchTimeline();
        Set<String> appliedScheduledSubs = new HashSet<>();
        SubstitutionEngine scheduledSubEngine = new SubstitutionEngine();
        PlayerSelector homeSelector = new PlayerSelector(homeSelectorRandom);
        PlayerSelector awaySelector = new PlayerSelector(awaySelectorRandom);

        double homePossBase = matchProbabilityService.possessionBase(context.homeStyle());
        double awayPossBase = matchProbabilityService.possessionBase(context.awayStyle());
        double matchIntensity = matchIntensity(context);

        MatchClock clock = new MatchClock(90);
        while (clock.isRunning()) {
            int minute = clock.currentMinute();
            if (minute > maxMinute) {
                break;
            }
            MinuteSimulationConfig minuteConfig = new MinuteSimulationConfig(
                    context,
                    homePossBase,
                    awayPossBase,
                    matchIntensity);
            MinuteMatchState minuteMatchState = new MinuteMatchState(
                    random,
                    homeState,
                    awayState,
                    timeline,
                    homeSelector,
                    awaySelector,
                    appliedScheduledSubs,
                    scheduledSubEngine);
            minuteFlow.processMinute(new MinuteSimulationInput(minuteConfig, minuteMatchState, minute));
            clock.advance();
        }

        return matchResultFinalizer.finalizeResult(
                context, homeState, awayState, timeline, log);
    }

    static double onTargetProbability(double xg) {
        return ShotAttemptService.onTargetProbability(xg);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return minuteFlow.aggregateOpponentDefenderSkills(opponents);
    }

    void applyYellowCardAndMaybeSecondYellowRed(
            PlayerMatchState player,
            MatchTimeline timeline,
            int minute,
            String teamRole) {
        minuteFlow.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
    }

    public TacticalShapeDebug debugTacticalShape(
            SessionTeam team,
            List<SessionPlayer> starting,
            List<SessionPlayer> bench,
            TeamStyle style,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        TeamMatchState teamState = createTeamState(
                team,
                starting != null ? starting : List.of(),
                bench != null ? bench : List.of(),
                style,
                slotsByPlayerId != null ? slotsByPlayerId : Map.of());
        if (formation != null && !formation.isBlank()) {
            teamState.setFormation(formation);
        }
        TacticalShapeProfile profile = minuteFlow.tacticalShapeProfile(teamState, formation, slotsByPlayerId);
        return TacticalShapeDebug.from(profile);
    }

    private void validate(MatchContext context, int maxMinute) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (maxMinute < 1 || maxMinute > 90) {
            throw new IllegalArgumentException("maxMinute must be in [1, 90], got " + maxMinute);
        }
    }

    private TeamMatchState createTeamState(
            SessionTeam team,
            List<SessionPlayer> starting,
            List<SessionPlayer> bench,
            TeamStyle style,
            Map<String, LineupSlot> slotsByPlayerId) {
        return TeamMatchState.create(team, starting, bench, style, slotsByPlayerId);
    }

    private double matchIntensity(MatchContext context) {
        double homeAvgOverall = matchIntensityService.computeTeamAvgOverall(context.homeStartingPlayers());
        double awayAvgOverall = matchIntensityService.computeTeamAvgOverall(context.awayStartingPlayers());
        double overallDiffRatio = matchIntensityService.computeOverallDiffRatio(homeAvgOverall, awayAvgOverall);
        double matchIntensity = matchIntensityService.computeMatchIntensity(overallDiffRatio);
        log.trace("matchIntensity={} (homeOvr={}, awayOvr={}, diffRatio={})",
                matchIntensity, homeAvgOverall, awayAvgOverall, overallDiffRatio);
        return matchIntensity;
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
            double defenseRight) {
        static TacticalShapeDebug from(TacticalShapeProfile profile) {
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
    }
}

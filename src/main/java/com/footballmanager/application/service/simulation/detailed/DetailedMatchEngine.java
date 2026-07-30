package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Public detailed match engine facade.
 *
 * <p>The facade expresses the simulation entry points and exposes the small
 * debug/test seams that already existed. Minute-by-minute tactical, event and
 * mutation rules are delegated to {@link DetailedMatchEngineFlow}, which keeps
 * the orchestration testable without making this class construct or know every
 * low-level collaborator.
 */
public class DetailedMatchEngine implements DetailedMatchEngineProvider {

    private final DetailedMatchEngineFlow flow;

    public DetailedMatchEngine() {
        this(new DetailedMatchEngineFlow());
    }

    DetailedMatchEngine(DisciplineModel disciplineModel) {
        this(new DetailedMatchEngineFlow(disciplineModel));
    }

    DetailedMatchEngine(DetailedMatchEngineFlow flow) {
        this.flow = flow != null ? flow : new DetailedMatchEngineFlow();
    }

    @Override
    public DetailedMatchResult simulate(MatchContext context, long seed) {
        return flow.simulate(context, seed);
    }

    public DetailedMatchResult simulate(MatchContext context, Random random) {
        return flow.simulate(context, random);
    }

    public DetailedMatchResult simulate(MatchContext context, Random random, int maxMinute) {
        return flow.simulate(context, random, maxMinute);
    }

    static double onTargetProbability(double xg) {
        return DetailedMatchEngineFlow.onTargetProbability(xg);
    }

    Map<PlayerSkill, Integer> aggregateOpponentDefenderSkills(List<PlayerMatchState> opponents) {
        return flow.aggregateOpponentDefenderSkills(opponents);
    }

    void applyYellowCardAndMaybeSecondYellowRed(PlayerMatchState player, MatchTimeline timeline, int minute, String teamRole) {
        flow.applyYellowCardAndMaybeSecondYellowRed(player, timeline, minute, teamRole);
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
        DetailedMatchEngineFlow.TacticalShapeDebug debug = flow.debugTacticalShape(
                team, starting, bench, style, formation, slotsByPlayerId);
        return new TacticalShapeDebug(
                debug.possessionMultiplier(),
                debug.attackVolumeMultiplier(),
                debug.defensiveResistanceMultiplier(),
                debug.attackLeft(),
                debug.attackCenter(),
                debug.attackRight(),
                debug.defenseLeft(),
                debug.defenseCenter(),
                debug.defenseRight());
    }
}

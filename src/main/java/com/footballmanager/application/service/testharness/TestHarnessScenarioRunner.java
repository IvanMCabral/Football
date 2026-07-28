package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.ScenarioMatrixRow;

import java.util.Random;

final class TestHarnessScenarioRunner {

    private TestHarnessScenarioRunner() {
    }

    static ScenarioMatrixRow run(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam home,
            SessionTeam away,
            String userTeamId,
            long seed,
            String scenario,
            String description,
            String formation,
            TeamStyle initialStyle,
            Integer changeMinute,
            ScenarioAction action,
            MatchContextFactory matchContextFactory) {

        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        TeamStyle homeStyle = userIsHome ? initialStyle : home.getStyle();
        TeamStyle awayStyle = userIsHome ? away.getStyle() : initialStyle;
        ScenarioAction safeAction = action != null ? action : ScenarioAction.none();

        MatchContext context = matchContextFactory.buildWithStyles(
            career, fixture, home, away, homeStyle, awayStyle, seed);

        ScenarioRunResult runResult = simulateScenario(
            context, fixture, userTeamId, userIsHome, seed, changeMinute, safeAction);

        return toRow(
            scenario,
            description,
            formation,
            initialStyle,
            changeMinute,
            safeAction,
            runResult);
    }

    private static ScenarioRunResult simulateScenario(
            MatchContext context,
            MatchFixture fixture,
            String userTeamId,
            boolean userIsHome,
            long seed,
            Integer changeMinute,
            ScenarioAction safeAction) {
        if (changeMinute == null || safeAction.type() == ScenarioActionType.NONE) {
            DetailedMatchResult result = new DetailedMatchEngine().simulate(context, new Random(seed));
            return new ScenarioRunResult(result, 0, 0);
        }

        LiveSession session = new LiveSession(context, seed);
        for (int i = 0; i < changeMinute; i++) {
            session.tick();
        }

        ScenarioMutationCounters counters = applyAction(
            session,
            fixture,
            userTeamId,
            userIsHome,
            changeMinute,
            safeAction);

        while (!session.isFinished()) {
            session.tick();
        }
        return new ScenarioRunResult(
            session.finalResult(),
            counters.tacticalChanges(),
            counters.substitutions());
    }

    private static ScenarioMutationCounters applyAction(
            LiveSession session,
            MatchFixture fixture,
            String userTeamId,
            boolean userIsHome,
            int changeMinute,
            ScenarioAction safeAction) {
        if (safeAction.type() == ScenarioActionType.STYLE) {
            session.mutateContext(ctx -> ctx.withNewStyle(userTeamId, safeAction.changedStyle()));
            return new ScenarioMutationCounters(1, 0);
        }
        if (safeAction.type() == ScenarioActionType.OPPONENT_STYLE) {
            String opponentTeamId = userIsHome ? fixture.getAwayTeamId() : fixture.getHomeTeamId();
            session.mutateContext(ctx -> ctx.withNewStyle(opponentTeamId, safeAction.changedStyle()));
            return new ScenarioMutationCounters(1, 0);
        }
        if (safeAction.type() == ScenarioActionType.NOOP_REPLAY) {
            session.mutateContext(ctx -> ctx);
            return new ScenarioMutationCounters(0, 0);
        }
        if (safeAction.type() == ScenarioActionType.FORMATION) {
            session.mutateContext(ctx -> {
                MatchContext changed = ctx.withNewFormation(userTeamId, safeAction.changedFormation());
                return safeAction.formationSlotsByPlayerId() != null
                    && !safeAction.formationSlotsByPlayerId().isEmpty()
                        ? changed.withSlots(userTeamId, safeAction.formationSlotsByPlayerId())
                        : changed;
            });
            return new ScenarioMutationCounters(1, 0);
        }
        if (safeAction.type() == ScenarioActionType.POSITION && safeAction.positionPlan() != null) {
            PositionPlan plan = safeAction.positionPlan();
            session.mutateContext(ctx -> ctx.withSlots(userTeamId, plan.slotsByPlayerId()));
            return new ScenarioMutationCounters(1, 0);
        }
        if (safeAction.type() == ScenarioActionType.SUBSTITUTION && safeAction.subPlan() != null) {
            SubPlan plan = safeAction.subPlan();
            session.mutateContext(ctx -> ctx.withManualSubstitution(
                userTeamId,
                plan.playerOffId(),
                plan.playerOnId(),
                changeMinute));
            return new ScenarioMutationCounters(0, 1);
        }
        if (safeAction.type() == ScenarioActionType.POSITION_AND_SUBSTITUTION
            && safeAction.positionPlan() != null
            && safeAction.subPlan() != null) {
            PositionPlan positionPlan = safeAction.positionPlan();
            SubPlan subPlan = safeAction.subPlan();
            session.mutateContext(ctx -> ctx
                .withSlots(userTeamId, positionPlan.slotsByPlayerId())
                .withManualSubstitution(
                    userTeamId,
                    subPlan.playerOffId(),
                    subPlan.playerOnId(),
                    changeMinute));
            return new ScenarioMutationCounters(1, 1);
        }
        return new ScenarioMutationCounters(0, 0);
    }

    private static ScenarioMatrixRow toRow(
            String scenario,
            String description,
            String formation,
            TeamStyle initialStyle,
            Integer changeMinute,
            ScenarioAction safeAction,
            ScenarioRunResult runResult) {
        DetailedMatchResult result = runResult.result();
        TestHarnessZoneCounts zones = TestHarnessZoneCounter.count(result);
        return new ScenarioMatrixRow(
            scenario,
            description,
            formation,
            initialStyle,
            changeMinute,
            safeAction.changedStyle(),
            safeAction.type().name(),
            safeAction.detail(),
            result.homeGoals(),
            result.awayGoals(),
            result.homeXg(),
            result.awayXg(),
            result.homeShots(),
            result.awayShots(),
            result.homePossession(),
            result.awayPossession(),
            zones.homeCentral(),
            zones.homeWide(),
            zones.homeLong(),
            zones.awayCentral(),
            zones.awayWide(),
            zones.awayLong(),
            zones.homeCentralXg(),
            zones.homeWideXg(),
            zones.homeLongXg(),
            zones.homeLeftWide(),
            zones.homeRightWide(),
            zones.homeLeftWideXg(),
            zones.homeRightWideXg(),
            zones.awayCentralXg(),
            zones.awayWideXg(),
            zones.awayLongXg(),
            zones.awayLeftWide(),
            zones.awayRightWide(),
            zones.awayLeftWideXg(),
            zones.awayRightWideXg(),
            runResult.tacticalChanges(),
            runResult.substitutions()
        );
    }

    private record ScenarioRunResult(
        DetailedMatchResult result,
        long tacticalChanges,
        long substitutions
    ) {
    }

    private record ScenarioMutationCounters(long tacticalChanges, long substitutions) {
    }
}

package com.footballmanager.application.service.simulation.detailed;

import org.slf4j.Logger;

final class MatchResultFinalizer {

    DetailedMatchResult finalizeResult(
            MatchContext ctx,
            TeamMatchState home,
            TeamMatchState away,
            MatchTimeline timeline,
            int goalAdditions,
            Logger log) {

        int homePossTicks = home.possessionTicks();
        int awayPossTicks = away.possessionTicks();
        int totalPoss = homePossTicks + awayPossTicks;
        int homePoss = totalPoss > 0 ? (int) Math.round(100.0 * homePossTicks / totalPoss) : 50;
        int awayPoss = 100 - homePoss;

        warnIfTimelineDiverged(ctx, home, away, timeline, goalAdditions, log);
        warnIfXgOutlier(ctx, home, away, log);

        String summary = String.format("%s %d - %d %s",
            ctx.homeTeam().getName(),
            home.goals(),
            away.goals(),
            ctx.awayTeam().getName());

        return DetailedMatchResult.builder()
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

    private void warnIfTimelineDiverged(
            MatchContext ctx,
            TeamMatchState home,
            TeamMatchState away,
            MatchTimeline timeline,
            int goalAdditions,
            Logger log) {
        long goalsInTimeline = timeline.events().stream()
            .filter(e -> e.type() == DetailedMatchEventType.GOAL)
            .count();
        int totalPossessedGoals = home.goals() + away.goals();
        if (goalsInTimeline != totalPossessedGoals) {
            log.warn("[DETAIL-XG-DIVERGENCE] matchId={}, homeGoals={}, awayGoals={}, "
                    + "goalsInTimeline={}, counter={}, divergence={}",
                ctx.matchId(),
                home.goals(), away.goals(),
                goalsInTimeline, goalAdditions,
                totalPossessedGoals - goalsInTimeline);
        }
    }

    private void warnIfXgOutlier(
            MatchContext ctx,
            TeamMatchState home,
            TeamMatchState away,
            Logger log) {
        double homeXg = home.xg();
        double awayXg = away.xg();
        int totalGoals = home.goals() + away.goals();
        if (totalGoals > 0 && (homeXg + awayXg) > 0
            && totalGoals > 5 * (homeXg + awayXg)) {
            log.warn("[DETAIL-XG-DIVERGENCE-OUTLIER] matchId={}, goals={} ({}x), xG={}, homeXg={}, awayXg={}",
                ctx.matchId(), totalGoals,
                String.format("%.2f", totalGoals / (homeXg + awayXg)),
                homeXg + awayXg, homeXg, awayXg);
        }
    }
}

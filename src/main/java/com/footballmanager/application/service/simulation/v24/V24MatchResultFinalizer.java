package com.footballmanager.application.service.simulation.v24;

import org.slf4j.Logger;

final class V24MatchResultFinalizer {

    V24DetailedMatchResult finalizeResult(
            V24MatchContext ctx,
            V24TeamMatchState home,
            V24TeamMatchState away,
            V24MatchTimeline timeline,
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

    private void warnIfTimelineDiverged(
            V24MatchContext ctx,
            V24TeamMatchState home,
            V24TeamMatchState away,
            V24MatchTimeline timeline,
            int goalAdditions,
            Logger log) {
        long goalsInTimeline = timeline.events().stream()
            .filter(e -> e.type() == V24MatchEventType.GOAL)
            .count();
        int totalPossessedGoals = home.goals() + away.goals();
        if (goalsInTimeline != totalPossessedGoals) {
            log.warn("[V24-XG-DIVERGENCE] matchId={}, homeGoals={}, awayGoals={}, "
                    + "goalsInTimeline={}, counter={}, divergence={}",
                ctx.matchId(),
                home.goals(), away.goals(),
                goalsInTimeline, goalAdditions,
                totalPossessedGoals - goalsInTimeline);
        }
    }

    private void warnIfXgOutlier(
            V24MatchContext ctx,
            V24TeamMatchState home,
            V24TeamMatchState away,
            Logger log) {
        double homeXg = home.xg();
        double awayXg = away.xg();
        int totalGoals = home.goals() + away.goals();
        if (totalGoals > 0 && (homeXg + awayXg) > 0
            && totalGoals > 5 * (homeXg + awayXg)) {
            log.warn("[V24-XG-DIVERGENCE-OUTLIER] matchId={}, goals={} ({}x), xG={}, homeXg={}, awayXg={}",
                ctx.matchId(), totalGoals,
                String.format("%.2f", totalGoals / (homeXg + awayXg)),
                homeXg + awayXg, homeXg, awayXg);
        }
    }
}

package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.Random;

/**
 * V24A2: Deterministic skeleton engine.
 * Produces a plausible but placeholder match result from V24MatchContext.
 *
 * <p>No persistence, no Spring, no production wiring.
 * Deterministic: same context + same seed = identical result.
 *
 * <p>V24A2 behavior: generates placeholder events deterministically.
 * Real simulation logic (xG formula, possession, events) belongs to V24B+.
 */
public class V24DetailedMatchEngine {

    public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        Random random = new Random(seed);

        // Initialize team states from context
        V24TeamMatchState homeState = V24TeamMatchState.create(
                context.homeTeam(), context.homeStartingPlayers(),
                context.homeBenchPlayers(), context.homeStyle());

        V24TeamMatchState awayState = V24TeamMatchState.create(
                context.awayTeam(), context.awayStartingPlayers(),
                context.awayBenchPlayers(), context.awayStyle());

        V24MatchClock clock = new V24MatchClock(90);
        V24MatchTimeline timeline = new V24MatchTimeline();

        // Style influence on possession baseline
        double homePossessionBase = possessionBase(context.homeStyle());
        double awayPossessionBase = possessionBase(context.awayStyle());

        // Simulate 90 minutes
        while (clock.isRunning()) {
            simulateMinute(homeState, awayState, clock, timeline, random, homePossessionBase, awayPossessionBase);
            clock.advance();
        }

        return finalizeResult(context, homeState, awayState, timeline);
    }

    private void simulateMinute(
            V24TeamMatchState home,
            V24TeamMatchState away,
            V24MatchClock clock,
            V24MatchTimeline timeline,
            Random random,
            double homePossBase,
            double awayPossBase) {

        int minute = clock.currentMinute();

        // Determine possession side for this minute (tick-level accumulation)
        double roll = random.nextDouble();
        double homeShare = homePossBase / (homePossBase + awayPossBase);
        boolean homeHasPossession = roll < homeShare;

        V24TeamMatchState possessor = homeHasPossession ? home : away;
        V24TeamMatchState opponent = homeHasPossession ? away : home;
        String teamRole = homeHasPossession ? "HOME" : "AWAY";

        // Accumulate possession ticks
        possessor.addPossessionTick();

        // Placeholder event generation — deterministic with seed
        // Every ~9 minutes: CHANCE_CREATED event
        if (minute % 9 == 0) {
            V24PlayerMatchState actor = randomPlayerFrom(possessor, random);
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.CHANCE_CREATED,
                    teamRole,
                    actor.sessionPlayerId(),
                    actor.name(),
                    null, null,
                    0.0,
                    "Chance created for " + possessor.name()
            ));
        }

        // Every ~7 minutes: SHOT or MISS
        if (minute % 7 == 0) {
            V24PlayerMatchState shooter = randomPlayerFrom(possessor, random);
            boolean onTarget = random.nextDouble() > 0.35; // 65% on target placeholder
            double xg = xgPlaceholder(random, onTarget);

            V24MatchEventType shotType = onTarget ? V24MatchEventType.SHOT_ON_TARGET : V24MatchEventType.SHOT;
            possessor.addShot(onTarget);
            possessor.addXg(xg);

            timeline.addEvent(new V24MatchEvent(
                    minute,
                    shotType,
                    teamRole,
                    shooter.sessionPlayerId(),
                    shooter.name(),
                    null, null,
                    xg,
                    onTarget ? "Shot on target" : "Shot missed"
            ));

            // Rare goal: ~2% per shot
            if (random.nextDouble() < 0.02) {
                possessor.addGoal();
                timeline.addEvent(new V24MatchEvent(
                        minute,
                        V24MatchEventType.GOAL,
                        teamRole,
                        shooter.sessionPlayerId(),
                        shooter.name(),
                        null, null,
                        xg,
                        "Goal! " + shooter.name() + " " + minute + "'"
                ));
            }
        }

        // Rare foul: ~5% per minute
        if (random.nextDouble() < 0.05) {
            V24PlayerMatchState fouler = randomPlayerFrom(possessor, random);
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.FOUL,
                    teamRole,
                    fouler.sessionPlayerId(),
                    fouler.name(),
                    null, null,
                    0.0,
                    "Foul by " + fouler.name()
            ));

            // Small chance of yellow card
            if (random.nextDouble() < 0.4) {
                timeline.addEvent(new V24MatchEvent(
                        minute,
                        V24MatchEventType.YELLOW_CARD,
                        teamRole,
                        fouler.sessionPlayerId(),
                        fouler.name(),
                        null, null,
                        0.0,
                        "Yellow card for " + fouler.name()
                ));
            }
        }

        // Rare corner: ~4% per minute
        if (random.nextDouble() < 0.04) {
            V24PlayerMatchState player = randomPlayerFrom(possessor, random);
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.CORNER,
                    teamRole,
                    player.sessionPlayerId(),
                    player.name(),
                    null, null,
                    0.0,
                    "Corner for " + possessor.name()
            ));
        }

        // Rare offside: ~3% per minute
        if (random.nextDouble() < 0.03) {
            V24PlayerMatchState player = randomPlayerFrom(possessor, random);
            timeline.addEvent(new V24MatchEvent(
                    minute,
                    V24MatchEventType.OFFSIDE,
                    teamRole,
                    player.sessionPlayerId(),
                    player.name(),
                    null, null,
                    0.0,
                    "Offside"
            ));
        }
    }

    private double possessionBase(TeamStyle style) {
        return switch (style) {
            case POSSESSION -> 58.0;
            case ATTACKING -> 52.0;
            case COUNTER -> 48.0;
            case DEFENSIVE -> 45.0;
            case BALANCED -> 50.0;
        };
    }

    private double xgPlaceholder(Random random, boolean onTarget) {
        // Simple placeholder xG: shots miss more often when off-target
        double base = onTarget ? 0.12 + random.nextDouble() * 0.20 : 0.03 + random.nextDouble() * 0.07;
        return Math.round(base * 1000.0) / 1000.0;
    }

    private V24PlayerMatchState randomPlayerFrom(V24TeamMatchState team, Random random) {
        var onPitch = team.startingPlayers().stream()
                .filter(p -> p.onPitch() && !p.redCard() && !p.injured())
                .toList();
        if (onPitch.isEmpty()) {
            return team.startingPlayers().get(0);
        }
        return onPitch.get(random.nextInt(onPitch.size()));
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
}
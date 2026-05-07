package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * V24B: Minute-by-minute match simulation with real xG, possession, and player attribution.
 *
 * <p>Replaces placeholder events from V24A2 with:
 * <ul>
 *   <li>Possession per minute (TeamStyle-influenced baseline)</li>
 *   <li>Chance creation and shot selection (position + attribute weighted)</li>
 *   <li>Real xG per shot (multi-factor model)</li>
 *   <li>Goal resolution (xG threshold)</li>
 *   <li>Player attribution from V24PlayerMatchState</li>
 *   <li>Fatigue, cards, and substitution mechanics</li>
 * </ul>
 *
 * <p>Deterministic: same context + same seed = identical result.
 * No persistence, no Spring, no production wiring.
 */
public class V24DetailedMatchEngine {

    private final V24ShotXgCalculator xgCalculator = new V24ShotXgCalculator();
    private final V24FatigueModel fatigueModel = new V24FatigueModel();
    private final V24DisciplineModel disciplineModel = new V24DisciplineModel();

    public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        Random random = new Random(seed);

        V24TeamMatchState homeState = V24TeamMatchState.create(
                context.homeTeam(), context.homeStartingPlayers(),
                context.homeBenchPlayers(), context.homeStyle());

        V24TeamMatchState awayState = V24TeamMatchState.create(
                context.awayTeam(), context.awayStartingPlayers(),
                context.awayBenchPlayers(), context.awayStyle());

        V24MatchClock clock = new V24MatchClock(90);
        V24MatchTimeline timeline = new V24MatchTimeline();

        // Style-influenced possession baselines
        double homePossBase = possessionBase(context.homeStyle());
        double awayPossBase = possessionBase(context.awayStyle());
        double homeShare = homePossBase / (homePossBase + awayPossBase);

        // Player selector seeded by match
        V24PlayerSelector homeSelector = new V24PlayerSelector(new Random(seed));
        V24PlayerSelector awaySelector = new V24PlayerSelector(new Random(seed + 1));

        // Match-level state
        int subsRemainingHome = 3;
        int subsRemainingAway = 3;

        while (clock.isRunning()) {
            int minute = clock.currentMinute();

            // Determine possession for this minute
            double roll = random.nextDouble();
            boolean homeHasPossession = roll < homeShare;

            V24TeamMatchState possessor = homeHasPossession ? homeState : awayState;
            V24TeamMatchState opponent = homeHasPossession ? awayState : homeState;
            V24PlayerSelector selector = homeHasPossession ? homeSelector : awaySelector;
            String teamRole = homeHasPossession ? "HOME" : "AWAY";

            // Accumulate possession
            possessor.addPossessionTick();

            // V24C1: Per-minute base stamina drain for all on-pitch players
            applyMinuteDrain(homeState, context.homeStyle());
            applyMinuteDrain(awayState, context.awayStyle());

            // Style modifier for chance creation probability
            double chanceProbability = chanceProbability(possessor.style(), minute);
            if (random.nextDouble() < chanceProbability) {
                // Attempt a shot
                attemptShot(possessor, opponent, selector, teamRole, minute, random, timeline);
            }

            // Chance created event (broader than shot)
            if (random.nextDouble() < chanceProbability * 0.6) {
                var creator = selector.selectShooter(possessor.startingPlayers());
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
                    // V24C1: Action drain for chance involvement
                    fatigueModel.applyDrain(c, 3);
                }
            }

            // V24C2: Foul / yellow card / red card using discipline model
            var potentialFouler = selector.selectShooter(possessor.startingPlayers());
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

                    // V24C1: Action drain for foul committed
                    fatigueModel.applyDrain(f, 5);

                    // V24C2: Yellow card check
                    if (disciplineModel.shouldReceiveYellow(f, possessor.style(), random) && !f.redCard()) {
                        f.addYellowCard();
                        timeline.addEvent(new V24MatchEvent(
                                minute,
                                V24MatchEventType.YELLOW_CARD,
                                teamRole,
                                f.sessionPlayerId(),
                                f.name(),
                                null, null,
                                0.0,
                                f.name() + " received a yellow card"
                        ));

                        // V24C2: Second yellow → red card
                        if (f.yellowCards() >= 2 && !f.redCard()) {
                            f.giveRedCard();
                            timeline.addEvent(new V24MatchEvent(
                                    minute,
                                    V24MatchEventType.RED_CARD,
                                    teamRole,
                                    f.sessionPlayerId(),
                                    f.name(),
                                    null, null,
                                    0.0,
                                    f.name() + " received a red card"
                            ));
                            // No substitution for red-carded player — team plays with one fewer
                        }
                    }
                }
            }

            // Injury event (rare: ~0.5% per minute)
            if (random.nextDouble() < 0.005) {
                var injured = selector.selectShooter(possessor.startingPlayers());
                if (injured.isPresent()) {
                    V24PlayerMatchState p = injured.get();
                    p.injure();
                    timeline.addEvent(new V24MatchEvent(
                            minute,
                            V24MatchEventType.INJURY,
                            teamRole,
                            p.sessionPlayerId(),
                            p.name(),
                            null, null,
                            0.0,
                            "Injury: " + p.name() + " is down"
                    ));
                }
            }

            // Corner (when possession is near goal but no shot)
            if (random.nextDouble() < 0.035) {
                var player = selector.selectShooter(possessor.startingPlayers());
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

            // Offside (when team is pushing forward)
            if (random.nextDouble() < 0.04 && possessor.style() != TeamStyle.DEFENSIVE) {
                var player = selector.selectShooter(possessor.startingPlayers());
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

            // Substitutions (once per team, 3 max, after minute 60)
            if (minute >= 60 && !homeState.startingPlayers().isEmpty() && subsRemainingHome > 0 && !homeHasPossession) {
                attemptSubstitution(homeState, timeline, minute, "HOME");
                subsRemainingHome--;
            }
            if (minute >= 60 && !awayState.startingPlayers().isEmpty() && subsRemainingAway > 0 && homeHasPossession) {
                attemptSubstitution(awayState, timeline, minute, "AWAY");
                subsRemainingAway--;
            }

            clock.advance();
        }

        return finalizeResult(context, homeState, awayState, timeline);
    }

    private void attemptShot(
            V24TeamMatchState possessor,
            V24TeamMatchState opponent,
            V24PlayerSelector selector,
            String teamRole,
            int minute,
            Random random,
            V24MatchTimeline timeline) {

        var shooterOpt = selector.selectShooter(possessor.startingPlayers());
        if (shooterOpt.isEmpty()) return;

        V24PlayerMatchState shooter = shooterOpt.get();

        // V24C1: Apply fatigue to shooter quality before xG calculation
        double rawShooterQuality = selector.shooterQuality(shooter);
        double shooterQuality = fatigueModel.applyFatigueToQuality(rawShooterQuality, shooter);

        // Determine shot location
        V24ShotLocation location = selectShotLocation(possessor.style(), random);

        // Get assist provider
        var assistOpt = selector.selectAssistProvider(possessor.startingPlayers(), shooter);
        String assistPlayerId = assistOpt.map(V24PlayerMatchState::sessionPlayerId).orElse(null);
        String assistPlayerName = assistOpt.map(V24PlayerMatchState::name).orElse(null);

        // Build shot quality bundle
        double assistQuality = assistOpt.map(selector::assistQuality).orElse(0.3);
        double defPressure = defensivePressure(opponent, random);
        double gkQuality = gkQuality(possessor.startingPlayers(), random); // simplified

        V24ShotQuality quality = new V24ShotQuality(
                location,
                shooterQuality,
                assistQuality,
                defPressure,
                gkQuality,
                styleToModifier(possessor.style())
        );

        double xg = xgCalculator.calculateXg(quality);
        possessor.addXg(xg);

        // V24C1: Action drain for shot attempt
        fatigueModel.applyDrain(shooter, 8);

        // Resolve shot outcome (xG threshold + randomness)
        boolean onTarget = random.nextDouble() < (0.30 + (1 - xg) * 0.50);
        boolean isGoal = false;

        if (onTarget) {
            // Goal if xG > random threshold (higher xG = more likely to beat keeper)
            isGoal = random.nextDouble() < (xg / 0.45); // scale: 0.45 xG = ~50% goal
            if (isGoal) {
                possessor.addGoal();
                timeline.addEvent(new V24MatchEvent(
                        minute,
                        V24MatchEventType.GOAL,
                        teamRole,
                        shooter.sessionPlayerId(),
                        shooter.name(),
                        assistPlayerId,
                        assistPlayerName,
                        Math.round(xg * 1000.0) / 1000.0,
                        "Goal! " + shooter.name() + " " + minute + "'"
                ));
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
            ));
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
            ));
            possessor.addShot(false);
        }
    }

    private V24ShotLocation selectShotLocation(TeamStyle style, Random random) {
        double roll = random.nextDouble();
        return switch (style) {
            case ATTACKING -> {
                // More inside-box attempts
                if (roll < 0.40) yield V24ShotLocation.SIX_YARD_BOX;
                if (roll < 0.70) yield V24ShotLocation.PENALTY_AREA_CENTER;
                if (roll < 0.85) yield V24ShotLocation.PENALTY_AREA_WIDE;
                if (roll < 0.95) yield V24ShotLocation.OUTSIDE_BOX;
                yield V24ShotLocation.LONG_RANGE;
            }
            case POSSESSION -> {
                if (roll < 0.30) yield V24ShotLocation.SIX_YARD_BOX;
                if (roll < 0.60) yield V24ShotLocation.PENALTY_AREA_CENTER;
                if (roll < 0.78) yield V24ShotLocation.PENALTY_AREA_WIDE;
                if (roll < 0.92) yield V24ShotLocation.OUTSIDE_BOX;
                yield V24ShotLocation.LONG_RANGE;
            }
            case COUNTER -> {
                // More long-range and outside-box (fast breaks)
                if (roll < 0.18) yield V24ShotLocation.SIX_YARD_BOX;
                if (roll < 0.40) yield V24ShotLocation.PENALTY_AREA_CENTER;
                if (roll < 0.60) yield V24ShotLocation.PENALTY_AREA_WIDE;
                if (roll < 0.82) yield V24ShotLocation.OUTSIDE_BOX;
                yield V24ShotLocation.LONG_RANGE;
            }
            case DEFENSIVE -> {
                // Prefer long-range and outside-box
                if (roll < 0.10) yield V24ShotLocation.SIX_YARD_BOX;
                if (roll < 0.25) yield V24ShotLocation.PENALTY_AREA_CENTER;
                if (roll < 0.45) yield V24ShotLocation.PENALTY_AREA_WIDE;
                if (roll < 0.72) yield V24ShotLocation.OUTSIDE_BOX;
                yield V24ShotLocation.LONG_RANGE;
            }
            default -> { // BALANCED
                if (roll < 0.25) yield V24ShotLocation.SIX_YARD_BOX;
                if (roll < 0.52) yield V24ShotLocation.PENALTY_AREA_CENTER;
                if (roll < 0.72) yield V24ShotLocation.PENALTY_AREA_WIDE;
                if (roll < 0.90) yield V24ShotLocation.OUTSIDE_BOX;
                yield V24ShotLocation.LONG_RANGE;
            }
        };
    }

    private double defensivePressure(V24TeamMatchState opponent, Random random) {
        double basePressure = 0.5;
        // Count defenders currently on pitch
        long defendersOnPitch = opponent.startingPlayers().stream()
                .filter(p -> p.onPitch() && (p.position().equals("DEF") || p.position().equals("MID")))
                .count();
        double defMod = Math.min(0.9, defendersOnPitch / 11.0 * 1.2);
        double randomFactor = 0.7 + random.nextDouble() * 0.6;
        return Math.min(1.0, basePressure * defMod * randomFactor);
    }

    private double gkQuality(List<V24PlayerMatchState> players, Random random) {
        // Simplified: average GK save quality from position
        var gk = players.stream()
                .filter(p -> p.position().equals("GK") && p.onPitch())
                .findFirst();
        if (gk.isEmpty()) return 0.5;
        // GK quality from stamina + mentality (normalized)
        return Math.round((gk.get().stamina() / 100.0 * 0.5 + gk.get().mentality() / 100.0 * 0.5) * 1000.0) / 1000.0;
    }

    private void attemptSubstitution(V24TeamMatchState team, V24MatchTimeline timeline, int minute, String teamRole) {
        var onPitch = team.startingPlayers().stream()
                .filter(p -> p.onPitch() && !p.injured())
                .toList();
        var bench = team.benchPlayers().stream()
                .filter(p -> !p.injured())
                .toList();
        if (onPitch.isEmpty() || bench.isEmpty()) return;

        // Substitute off a tired player (low stamina)
        var substitutedOpt = onPitch.stream()
                .filter(p -> p.currentStamina() < 60)
                .findFirst();
        if (substitutedOpt.isEmpty()) return;

        V24PlayerMatchState subOff = substitutedOpt.get();
        subOff.substituteOff();

        V24PlayerMatchState subOn = bench.get(0);
        subOn.setTeamId(team.teamId()); // ensure team context

        timeline.addEvent(new V24MatchEvent(
                minute,
                V24MatchEventType.SUBSTITUTION,
                teamRole,
                subOff.sessionPlayerId(),
                subOff.name(),
                subOn.sessionPlayerId(),
                subOn.name(),
                0.0,
                "Substitution: " + subOn.name() + " on for " + subOff.name()
        ));
    }

    private double styleToModifier(TeamStyle style) {
        return switch (style) {
            case ATTACKING -> 1.15;
            case POSSESSION -> 1.05;
            case BALANCED -> 1.00;
            case COUNTER -> 0.95;
            case DEFENSIVE -> 0.85;
        };
    }

    private double chanceProbability(TeamStyle style, int minute) {
        // Base chance per minute: ~25-35% depending on style
        double base = switch (style) {
            case ATTACKING -> 0.32;
            case POSSESSION -> 0.28;
            case COUNTER -> 0.25;
            case DEFENSIVE -> 0.20;
            case BALANCED -> 0.26;
        };

        // Slight increase in second half (more open)
        double secondHalf = (minute > 45) ? 1.15 : 1.0;
        // Open play tends to increase toward end of match
        double endGame = (minute > 75) ? 1.2 : 1.0;

        return base * secondHalf * endGame;
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

    // V24C1: Apply base stamina drain to all on-pitch players of a team
    private void applyMinuteDrain(V24TeamMatchState team, TeamStyle style) {
        int baseDrain = fatigueModel.baseDrainPerMinute(style);
        for (V24PlayerMatchState p : team.startingPlayers()) {
            if (p.onPitch() && !p.injured() && !p.redCard()) {
                fatigueModel.applyDrain(p, baseDrain);
            }
        }
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
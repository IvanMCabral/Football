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
public class V24DetailedMatchEngine implements V24DetailedMatchEngineProvider {

    private final V24ShotXgCalculator xgCalculator = new V24ShotXgCalculator();
    private final V24FatigueModel fatigueModel = new V24FatigueModel();
    private final V24DisciplineModel disciplineModel;
    private final V24InjuryModel injuryModel = new V24InjuryModel();
    private final V24SubstitutionEngine substitutionEngine = new V24SubstitutionEngine();
    private final V24AssistModel assistModel = new V24AssistModel();
    private final V24ShotCoordinateGenerator coordGenerator = new V24ShotCoordinateGenerator();

    public V24DetailedMatchEngine() {
        this(new V24DisciplineModel());
    }

    /**
     * V24D6Q: Constructor for testing — allows injecting a discipline model
     * whose shouldCommitFoul / shouldReceiveYellow can be stubbed to force
     * second-yellow scenarios deterministically.
     */
    V24DetailedMatchEngine(V24DisciplineModel disciplineModel) {
        this.disciplineModel = disciplineModel != null ? disciplineModel : new V24DisciplineModel();
    }

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

        while (clock.isRunning()) {
            int minute = clock.currentMinute();

            // Determine possession for this minute
            double roll = random.nextDouble();
            boolean homeHasPossession = roll < homeShare;

            V24TeamMatchState possessor = homeHasPossession ? homeState : awayState;
            V24TeamMatchState opponent = homeHasPossession ? awayState : homeState;
            V24PlayerSelector selector = homeHasPossession ? homeSelector : awaySelector;
            // V24D6O-fix: use real team UUIDs (not "HOME"/"AWAY" sentinels) so the
            // persisted detail timeline exposes sessionTeamId UUIDs, matching
            // the V24MatchContext and the V24 frontend model. Substitution-engine
            // counters are also keyed by the same UUID below, so the substitution
            // limit (5/team) is now enforced correctly.
            String teamRole = homeHasPossession ? context.homeTeamId() : context.awayTeamId();

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
                        applyYellowCardAndMaybeSecondYellowRed(f, timeline, minute, teamRole);
                    }
                }
            }

            // V24C3: Injury event using injury model
            var potentialInjured = selector.selectShooter(possessor.startingPlayers());
            if (potentialInjured.isPresent()) {
                V24PlayerMatchState p = potentialInjured.get();
                // highIntensityAction=false here; shots/fouls/chances already covered by their own drains
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

            // V24C4: Substitutions using V24SubstitutionEngine (5 max, priority-based)
            // Home team substitution after minute 60 when not in possession
            if (minute >= 60 && !homeState.startingPlayers().isEmpty() && substitutionEngine.hasSubstitutionsRemaining(context.homeTeamId()) && !homeHasPossession) {
                substitutionEngine.attemptSubstitution(homeState, minute)
                        .ifPresent(e -> timeline.addEvent(e));
            }
            // Away team substitution after minute 60 when in possession
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

        // Get assist provider via V24AssistModel
        var assistOpt = assistModel.selectAssistProvider(
                possessor.startingPlayers(), shooter, null, possessor.style(), random);
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

        // V24D3C: Generate shot coordinate for this attempt
        V24ShotCoordinate shotCoord = coordGenerator.generate(location, random);

        // Resolve shot outcome (xG threshold + randomness)
        // V24D6U4: Tuned both onTarget and goal thresholds to reduce conversion rate.
        // Previous: onTarget base 30%, goal at xg/0.45 → 45% xG = 100% goal (too generous).
        // New: onTarget base 18%, goal at xg/0.40 → 40% xG = 100% goal.
        // Combined effect: fewer shots on target AND harder to score from same xG.
        boolean onTarget = random.nextDouble() < (0.18 + (1 - xg) * 0.42);
        boolean isGoal = false;

        if (onTarget) {
            // Goal if xG > random threshold (higher xG = more likely to beat keeper)
            isGoal = random.nextDouble() < (xg / 0.40); // scale: 0.40 xG = ~50% goal
            if (isGoal) {
                possessor.addGoal();
                // V24D6O-fix: count goal as a shot on target so homeShots/awayShots
                // (used in the Stats summary) is consistent with the Shot Map total.
                // A goal is by definition a shot that hit the target and went in.
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
        // V24D6U4: Tuned from ~0.26 base to ~0.10 to reduce goals from ~5/team to ~1.25/team.
        // Target: λ ≈ 1.25 per team (Poisson), ~2.5 total/match.
        // Previous: λ ≈ 4.5 → 46.7% of matches with 5+ goals.
        // New: λ ≈ 1.25 → 91.4% of matches with ≤3 goals.
        double base = switch (style) {
            case ATTACKING -> 0.13;
            case POSSESSION -> 0.11;
            case COUNTER -> 0.10;
            case DEFENSIVE -> 0.08;
            case BALANCED -> 0.10;
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

    /**
     * V24D6Q: Apply a yellow card to a player and, if this is the player's
     * second yellow of the match, also emit a RED_CARD event.
     *
     * <p>Package-private to allow deterministic unit testing without
     * depending on random or stubbed discipline models.
     *
     * <p>The pre-yellow red state is captured BEFORE addYellowCard() because
     * addYellowCard() itself flips redCard=true when yellowCards reaches 2.
     * Using a guard against the post-add state would never fire.
     */
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
            // player.redCard() is already true (set by addYellowCard) — no need to call giveRedCard().
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
            // No substitution for red-carded player — team plays with one fewer
        }
    }
}
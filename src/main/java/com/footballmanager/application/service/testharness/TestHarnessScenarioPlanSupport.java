package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

final class TestHarnessScenarioPlanSupport {

    private static final String AUTO_POSITION_PIXEL_PREFIX = "__AUTO_";
    private static final FormationService formationService = new FormationService();

    private TestHarnessScenarioPlanSupport() {
    }

static Optional<PositionPlan> chooseMidfielderPositionPlan(
            MatchContext context,
            String userTeamId,
            double xPercent,
            double yPercent) {

        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            return Optional.empty();
        }

        List<SessionPlayer> starters = userIsHome
            ? context.homeStartingPlayers()
            : context.awayStartingPlayers();
        Map<String, LineupSlot> baseSlots = userIsHome
            ? context.homeSlotsByPlayerId()
            : context.awaySlotsByPlayerId();

        return starters.stream()
            .filter(p -> p != null
                && p.getSessionPlayerId() != null
                && "MID".equals(p.getPosition()))
            .findFirst()
            .map(player -> {
                String playerId = player.getSessionPlayerId();
                LineupSlot previous = baseSlots.get(playerId);
                Map<String, LineupSlot> moved = new LinkedHashMap<>(baseSlots);
                moved.put(playerId, new LineupSlot(
                    playerId,
                    previous != null ? previous.subdivisionId() : "S5-2",
                    xPercent,
                    yPercent));
                return new PositionPlan(
                    playerId,
                    TestHarnessCommonSupport.safeName(player),
                    xPercent,
                    yPercent,
                    moved);
            });
    }

    static Optional<PositionPlan> buildShapePlan(
            MatchContext context,
            String userTeamId,
            String formation,
            String shapeName,
            ShapePreset preset) {

        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            return Optional.empty();
        }

        List<SessionPlayer> starters = userIsHome
            ? context.homeStartingPlayers()
            : context.awayStartingPlayers();
        Map<String, LineupSlot> baseSlots = userIsHome
            ? context.homeSlotsByPlayerId()
            : context.awaySlotsByPlayerId();
        if (starters == null || starters.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> positionIndex = new HashMap<>();
        Map<String, LineupSlot> moved = new LinkedHashMap<>(baseSlots);
        Map<String, double[]> formationCoords = formationService.getCoordsByFormation(formation);
        for (SessionPlayer player : starters) {
            if (player == null || player.getSessionPlayerId() == null) continue;
            String position = player.getPosition() != null ? player.getPosition() : "MID";
            int index = positionIndex.merge(position, 1, Integer::sum) - 1;
            LineupSlot previous = baseSlots.get(player.getSessionPlayerId());
            double[] coords = shapeCoords(position, index, preset, previous, formationCoords);
            moved.put(player.getSessionPlayerId(), new LineupSlot(
                player.getSessionPlayerId(),
                previous != null ? previous.subdivisionId() : fallbackSubdivision(position),
                coords[0],
                coords[1]));
        }

        return Optional.of(new PositionPlan(
            shapeName,
            shapeName,
            50.0,
            50.0,
            moved));
    }

    static double[] shapeCoords(
            String position,
            int index,
            ShapePreset preset,
            LineupSlot previous,
            Map<String, double[]> formationCoords) {
        double[] canonical = previous != null && previous.subdivisionId() != null
            ? formationCoords.get(previous.subdivisionId())
            : null;
        if ("GK".equals(position)) {
            return new double[] {
                previous != null && previous.customXPercent() != null ? previous.customXPercent() : canonical != null ? canonical[0] : 50.0,
                previous != null && previous.customYPercent() != null ? previous.customYPercent() : canonical != null ? canonical[1] : 94.0
            };
        }

        double y = previous != null && previous.customYPercent() != null ? previous.customYPercent() : canonical != null ? canonical[1] : switch (position) {
            case "DEF" -> 78.0;
            case "ATT" -> 18.0;
            case "WINGER" -> 30.0;
            default -> 52.0;
        };
        double x = previous != null && previous.customXPercent() != null ? previous.customXPercent() : canonical != null ? canonical[0] : switch (position) {
            case "DEF" -> pick(index, 18.0, 38.0, 62.0, 82.0, 50.0);
            case "ATT" -> pick(index, 42.0, 58.0, 50.0, 35.0, 65.0);
            case "WINGER" -> pick(index, 18.0, 82.0, 30.0, 70.0, 50.0);
            default -> pick(index, 24.0, 42.0, 58.0, 76.0, 50.0);
        };

        switch (preset) {
            case COMPACT_CENTER -> x = 50.0 + ((x - 50.0) * switch (position) {
                case "DEF" -> 0.75;
                case "ATT" -> 0.65;
                case "WINGER" -> 0.55;
                default -> 0.55;
            });
            case WIDE_OVERLOAD -> x = 50.0 + ((x - 50.0) * switch (position) {
                case "DEF" -> 1.00;
                case "ATT" -> 1.03;
                case "WINGER" -> 1.08;
                default -> 1.07;
            });
            case ATTACKING_STEP -> y = Math.max(8.0, y - ("DEF".equals(position) ? 3.0 : 5.0));
            case ATTACKING_HIGH -> y = Math.max(8.0, y - ("DEF".equals(position) ? 3.0 : 6.0));
            case HIGH_PRESS -> y = Math.max(8.0, y - switch (position) {
                case "DEF" -> 7.0;
                case "MID" -> 8.0;
                case "WINGER" -> 7.0;
                case "ATT" -> 4.0;
                default -> 7.0;
            });
            case DOUBLE_STRIKER -> {
                y = switch (position) {
                    case "ATT" -> Math.max(8.0, y - 4.0);
                    case "WINGER" -> Math.max(10.0, y - 9.0);
                    case "MID" -> Math.max(18.0, y - 5.0);
                    case "DEF" -> Math.max(60.0, y - 2.0);
                    default -> Math.max(8.0, y - 4.0);
                };
                if ("WINGER".equals(position) || "ATT".equals(position)) {
                    x = 50.0 + ((x - 50.0) * 0.72);
                }
            }
            case ALL_OUT -> {
                y = Math.max(8.0, y - switch (position) {
                    case "DEF" -> 6.0;
                    case "MID" -> 10.0;
                    case "WINGER" -> 12.0;
                    case "ATT" -> 7.0;
                    default -> 9.0;
                });
                x = 50.0 + ((x - 50.0) * switch (position) {
                    case "DEF" -> 0.88;
                    case "MID" -> 0.82;
                    case "WINGER" -> 0.90;
                    case "ATT" -> 0.75;
                    default -> 0.85;
                });
            }
            case DEFENSIVE_STEP -> y = Math.min(92.0, y + switch (position) {
                case "DEF" -> 10.0;
                case "MID" -> 7.0;
                case "WINGER" -> 5.0;
                case "ATT" -> 4.0;
                default -> 6.0;
            });
            case DEFENSIVE_LOW -> y = Math.min(92.0, y + switch (position) {
                case "DEF" -> 8.0;
                case "MID" -> 8.0;
                case "WINGER" -> 7.0;
                case "ATT" -> 6.0;
                default -> 7.0;
            });
            case LEFT_OVERLOAD -> x = clamp(x - switch (position) {
                case "DEF" -> 5.0;
                case "ATT" -> 8.0;
                case "WINGER" -> 12.0;
                default -> 12.0;
            }, 8.0, 92.0);
            case RIGHT_OVERLOAD -> x = clamp(x + switch (position) {
                case "DEF" -> 5.0;
                case "ATT" -> 8.0;
                case "WINGER" -> 12.0;
                default -> 12.0;
            }, 8.0, 92.0);
        }
        return new double[] {clamp(x, 0.0, 100.0), clamp(y, 0.0, 100.0)};
    }

    static double pick(int index, double... values) {
        if (values.length == 0) return 50.0;
        return values[Math.floorMod(index, values.length)];
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static String fallbackSubdivision(String position) {
        String normalized = position != null ? position.toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "GK" -> "GK-1";
            case "RB", "RWB" -> "S24-3";
            case "LB", "LWB" -> "S22-1";
            case "CB", "DEF" -> "S23-2";
            case "DM", "CDM" -> "S20-2";
            case "LM" -> "S16-1";
            case "RM" -> "S18-3";
            case "CM", "MID", "AM", "CAM" -> "S17-2";
            case "LW" -> "S04-1";
            case "RW" -> "S06-3";
            case "ST", "CF", "ATT", "WINGER" -> "S05-2";
            default -> "S17-2";
        };
    }

    static Optional<SubPlan> chooseImpactSubstitution(
            MatchContextFactory matchContextFactory,
            CareerSave career,
            MatchFixture fixture,
            String userTeamId,
            SessionTeam home,
            SessionTeam away) {

        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        MatchContext context = matchContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            userIsHome ? TeamStyle.BALANCED : home.getStyle(),
            userIsHome ? away.getStyle() : TeamStyle.BALANCED,
            12345L);

        List<SessionPlayer> starters = userIsHome ? context.homeStartingPlayers() : context.awayStartingPlayers();
        List<SessionPlayer> bench = userIsHome ? context.homeBenchPlayers() : context.awayBenchPlayers();

        return starters.stream()
            .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> TestHarnessAutoSwapSupport.impactSubPositionPriority(p.getPosition()))
                .thenComparingInt(TestHarnessAutoSwapSupport::substitutionScore)
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .map(off -> bestBenchReplacement(off, bench)
                .map(on -> new SubPlan(
                    off.getSessionPlayerId(),
                    on.getSessionPlayerId(),
                    TestHarnessCommonSupport.safeName(off),
                    TestHarnessCommonSupport.safeName(on),
                    off.getPosition(),
                    on.getPosition(),
                    TestHarnessAutoSwapSupport.substitutionScore(on) - TestHarnessAutoSwapSupport.substitutionScore(off))))
            .flatMap(Optional::stream)
            .filter(plan -> plan.scoreDelta() >= 25)
            .findFirst();
    }

    static Optional<SessionPlayer> bestBenchReplacement(SessionPlayer off, List<SessionPlayer> bench) {
        Optional<SessionPlayer> exactRole = bench.stream()
            .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .filter(p -> Objects.equals(off.getPosition(), p.getPosition()))
            .max(Comparator
                .comparingInt(TestHarnessAutoSwapSupport::substitutionScore)
                .thenComparingInt(p -> TestHarnessCommonSupport.safeInt(p.getTechnique()))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)));
        if (exactRole.isPresent() && TestHarnessAutoSwapSupport.substitutionScore(exactRole.get()) > TestHarnessAutoSwapSupport.substitutionScore(off)) {
            return exactRole;
        }
        return bench.stream()
            .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .filter(p -> TestHarnessAutoSwapSupport.samePosition(off, p))
            .filter(p -> TestHarnessAutoSwapSupport.substitutionScore(p) > TestHarnessAutoSwapSupport.substitutionScore(off))
            .max(Comparator
                .comparingInt(TestHarnessAutoSwapSupport::substitutionScore)
                .thenComparingInt(p -> TestHarnessCommonSupport.safeInt(p.getTechnique()))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)));
    }

    static Optional<SubPlan> chooseScoredSubstitution(
            MatchContextFactory matchContextFactory,
            CareerSave career,
            MatchFixture fixture,
            String userTeamId,
            SessionTeam home,
            SessionTeam away,
            boolean upgrade) {
        return chooseScoredSubstitution(matchContextFactory, career, fixture, userTeamId, home, away, upgrade, Set.of());
    }

    static Optional<SubPlan> chooseScoredSubstitution(
            MatchContextFactory matchContextFactory,
            CareerSave career,
            MatchFixture fixture,
            String userTeamId,
            SessionTeam home,
            SessionTeam away,
            boolean upgrade,
            Set<String> allowedPositions) {

        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        MatchContext context = matchContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            userIsHome ? TeamStyle.BALANCED : home.getStyle(),
            userIsHome ? away.getStyle() : TeamStyle.BALANCED,
            12345L);

        List<SessionPlayer> starters = userIsHome ? context.homeStartingPlayers() : context.awayStartingPlayers();
        List<SessionPlayer> bench = userIsHome ? context.homeBenchPlayers() : context.awayBenchPlayers();

        return starters.stream()
            .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
            .filter(off -> allowedPositions == null
                || allowedPositions.isEmpty()
                || allowedPositions.contains(off.getPosition()))
            .flatMap(off -> bench.stream()
                .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
                .filter(on -> upgrade
                    ? Objects.equals(off.getPosition(), on.getPosition())
                    : TestHarnessAutoSwapSupport.samePosition(off, on))
                .map(on -> toSubPlan(off, on)))
            .filter(plan -> upgrade ? plan.scoreDelta() > 0 : plan.scoreDelta() < 0)
            .max(upgrade
                ? Comparator.comparingInt(SubPlan::scoreDelta)
                : Comparator.comparingInt((SubPlan plan) -> -plan.scoreDelta()));
    }

static SubPlan toSubPlan(SessionPlayer off, SessionPlayer on) {
        return new SubPlan(
            off.getSessionPlayerId(),
            on.getSessionPlayerId(),
            TestHarnessCommonSupport.safeName(off),
            TestHarnessCommonSupport.safeName(on),
            off.getPosition(),
            on.getPosition(),
            TestHarnessAutoSwapSupport.substitutionScore(on) - TestHarnessAutoSwapSupport.substitutionScore(off));
    }

static Optional<SessionPlayer> resolvePositionPixelPlayer(List<SessionPlayer> players, String playerId) {
        if (players == null || playerId == null || playerId.isBlank()) {
            return Optional.empty();
        }
        if (!playerId.startsWith(AUTO_POSITION_PIXEL_PREFIX)) {
            return TestHarnessCommonSupport.findPlayer(players, playerId);
        }

        String requestedLine = playerId.substring(AUTO_POSITION_PIXEL_PREFIX.length()).toUpperCase(Locale.ROOT);
        Optional<SessionPlayer> exactLine = players.stream()
            .filter(p -> p != null && requestedLine.equals(TestHarnessPixelSupport.autoLine(p)))
            .findFirst();
        if (exactLine.isPresent()) {
            return exactLine;
        }
        return players.stream()
            .filter(p -> p != null && !"GK".equalsIgnoreCase(p.getPosition()))
            .findFirst();
    }

static String resolveSlotId(
            MatchContext context,
            boolean userIsHome,
            String starterPlayerId,
            String requestedSlotId) {
        Map<String, LineupSlot> slots = userIsHome
            ? context.homeSlotsByPlayerId()
            : context.awaySlotsByPlayerId();
        LineupSlot slot = slots.get(starterPlayerId);
        if (slot != null && slot.subdivisionId() != null && !slot.subdivisionId().isBlank()) {
            return slot.subdivisionId();
        }
        return requestedSlotId;
    }
}

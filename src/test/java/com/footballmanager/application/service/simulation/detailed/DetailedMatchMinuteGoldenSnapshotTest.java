package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DetailedMatchMinuteGoldenSnapshotTest {

    @Test
    void goldenSnapshotsFreezeDetailedMinuteBehavior() {
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("balanced-42", snapshot(baseContext("balanced", 76, 76, TeamStyle.BALANCED, TeamStyle.BALANCED), 42L));
        actual.put("favorite-7", snapshot(baseContext("favorite", 88, 68, TeamStyle.ATTACKING, TeamStyle.DEFENSIVE), 7L));
        actual.put("defensive-99", snapshot(baseContext("defensive", 74, 78, TeamStyle.DEFENSIVE, TeamStyle.ATTACKING), 99L));
        MatchContext manualSubContext = baseContext("manual-sub", 77, 77, TeamStyle.BALANCED, TeamStyle.COUNTER);
        actual.put("manual-sub-12345", snapshot(manualSubContext
                .withManualSubstitution(manualSubContext.homeTeamId(),
                        manualSubContext.homeStartingPlayers().get(10).getSessionPlayerId(),
                        manualSubContext.homeBenchPlayers().get(0).getSessionPlayerId(),
                        30), 12345L));

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("balanced-42", "score=0-1;shots=23-32;onTarget=25+0;xg=1.702-1.637;poss=46-54;events=103;goals=1;goalMinutes=[7:A:away-balanced starter 9];chances=31;corners=1;offsides=4;fouls=2;yellows=2;reds=0;injuries=1;subs=7;timelineHash=bf1952ad201ac8a44f874e9df636c8ec359cca3b49b2863267e71cd00d8ca33c;timelineHead=[1:CHANCE_CREATED:A:0.000, 2:BLOCK:H:0.074, 3:CHANCE_CREATED:A:0.000, 4:SHOT_ON_TARGET:A:0.072, 5:MISS:A:0.069, 7:GOAL:A:0.067, 10:CHANCE_CREATED:H:0.000, 10:OFFSIDE:H:0.000];timelineTail=[83:CHANCE_CREATED:H:0.000, 84:MISS:A:0.062, 84:CHANCE_CREATED:A:0.000, 85:SHOT_ON_TARGET:A:0.120, 87:MISS:H:0.124, 88:MISS:H:0.067, 89:SHOT_ON_TARGET:H:0.068, 89:CHANCE_CREATED:H:0.000]");
        expected.put("favorite-7", "score=3-0;shots=40-21;onTarget=25+0;xg=4.945-0.549;poss=46-54;events=124;goals=3;goalMinutes=[15:H:home-favorite starter 2, 76:H:home-favorite starter 6, 82:H:home-favorite starter 8];chances=41;corners=4;offsides=2;fouls=6;yellows=1;reds=0;injuries=1;subs=8;timelineHash=7908e9cc954e7e2bb2c7c930da4c35bb324154e9bca18e6d4e8d983933d9965d;timelineHead=[2:SHOT_ON_TARGET:A:0.011, 3:MISS:H:0.171, 4:CHANCE_CREATED:A:0.000, 5:SHOT_ON_TARGET:H:0.185, 6:MISS:H:0.117, 7:MISS:H:0.155, 7:CHANCE_CREATED:H:0.000, 9:SHOT_ON_TARGET:H:0.112];timelineTail=[86:SUBSTITUTION:H:0.000, 87:MISS:H:0.189, 87:CHANCE_CREATED:H:0.000, 88:SHOT_ON_TARGET:H:0.200, 88:CHANCE_CREATED:H:0.000, 88:SUBSTITUTION:A:0.000, 89:BLOCK:A:0.070, 89:CHANCE_CREATED:A:0.000]");
        expected.put("defensive-99", "score=0-1;shots=18-35;onTarget=12+0;xg=0.812-2.558;poss=47-53;events=105;goals=1;goalMinutes=[78:A:away-defensive starter 6];chances=29;corners=3;offsides=3;fouls=6;yellows=3;reds=0;injuries=0;subs=8;timelineHash=871f2fdbddfa5d24d63918aff3cf228018337045828c8da8c902e81236ab6f8a;timelineHead=[1:SHOT_ON_TARGET:A:0.070, 2:CHANCE_CREATED:A:0.000, 4:MISS:A:0.065, 5:CORNER:A:0.000, 6:SHOT_ON_TARGET:A:0.079, 6:CHANCE_CREATED:A:0.000, 8:MISS:A:0.026, 8:CHANCE_CREATED:A:0.000];timelineTail=[84:SUBSTITUTION:A:0.000, 85:MISS:A:0.127, 88:SHOT_ON_TARGET:H:0.074, 88:CHANCE_CREATED:H:0.000, 88:FOUL:H:0.000, 88:YELLOW_CARD:H:0.000, 89:MISS:A:0.071, 89:SUBSTITUTION:H:0.000]");
        expected.put("manual-sub-12345", "score=1-0;shots=24-31;onTarget=22+0;xg=1.495-1.868;poss=48-52;events=109;goals=1;goalMinutes=[40:H:home-manual-sub starter 8];chances=27;corners=4;offsides=2;fouls=7;yellows=4;reds=1;injuries=1;subs=8;timelineHash=ee47c33d2c9ea4e8ab9b8a5294d4221c51adad92c3fd8eb4a17b1b7d30d42d43;timelineHead=[2:SHOT_ON_TARGET:A:0.025, 3:BLOCK:A:0.064, 3:CHANCE_CREATED:A:0.000, 5:MISS:H:0.023, 5:CHANCE_CREATED:H:0.000, 5:FOUL:H:0.000, 6:OFFSIDE:H:0.000, 8:MISS:A:0.024];timelineTail=[84:CHANCE_CREATED:A:0.000, 85:BLOCK:H:0.063, 85:CHANCE_CREATED:H:0.000, 85:SUBSTITUTION:A:0.000, 87:SHOT_ON_TARGET:H:0.123, 88:FOUL:H:0.000, 88:YELLOW_CARD:H:0.000, 89:SHOT_ON_TARGET:A:0.066]");
        assertThat(actual).containsExactlyEntriesOf(expected);
    }

    @Test
    void concurrentMatchesKeepIndependentDeterministicState() throws Exception {
        MatchContext context = baseContext("parallel", 82, 72, TeamStyle.ATTACKING, TeamStyle.COUNTER);
        List<Long> seeds = List.of(3L, 17L, 42L, 91L);
        Map<Long, String> sequential = new ConcurrentHashMap<>();
        for (long seed : seeds) {
            sequential.put(seed, snapshot(context, seed));
        }
        Map<Long, String> parallel = new ConcurrentHashMap<>();
        try (var executor = Executors.newFixedThreadPool(seeds.size())) {
            List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>();
            for (long seed : seeds) {
                tasks.add(() -> {
                    parallel.put(seed, snapshot(context, seed));
                    return null;
                });
            }
            for (var future : executor.invokeAll(tasks)) {
                future.get();
            }
        }
        assertThat(parallel).containsExactlyInAnyOrderEntriesOf(sequential);
    }

    private static String snapshot(MatchContext context, long seed) {
        DetailedMatchResult result = new DetailedMatchEngine().simulate(context, seed);
        long goals = count(result, DetailedMatchEventType.GOAL);
        long chances = count(result, DetailedMatchEventType.CHANCE_CREATED);
        long corners = count(result, DetailedMatchEventType.CORNER);
        long offsides = count(result, DetailedMatchEventType.OFFSIDE);
        long fouls = count(result, DetailedMatchEventType.FOUL);
        long yellows = count(result, DetailedMatchEventType.YELLOW_CARD);
        long reds = count(result, DetailedMatchEventType.RED_CARD);
        long injuries = count(result, DetailedMatchEventType.INJURY);
        long substitutions = count(result, DetailedMatchEventType.SUBSTITUTION);
        String goalMinutes = result.timeline().goalEvents().stream()
                .map(e -> e.minute() + ":" + side(context, e.teamId()) + ":" + e.playerName())
                .toList()
                .toString();
        List<String> normalizedTimeline = result.timeline().events().stream()
                .map(e -> e.minute() + ":" + e.type() + ":" + side(context, e.teamId())
                        + ":" + String.format(java.util.Locale.ROOT, "%.3f", e.xg()))
                .toList();
        String timelineHead = normalizedTimeline.stream().limit(8).toList().toString();
        String timelineTail = normalizedTimeline.stream()
                .skip(Math.max(0, normalizedTimeline.size() - 8))
                .toList()
                .toString();
        return "score=" + result.homeGoals() + "-" + result.awayGoals()
                + ";shots=" + result.homeShots() + "-" + result.awayShots()
                + ";onTarget=" + count(result, DetailedMatchEventType.SHOT_ON_TARGET) + "+" + count(result, DetailedMatchEventType.SAVE)
                + ";xg=" + format(result.homeXg()) + "-" + format(result.awayXg())
                + ";poss=" + result.homePossession() + "-" + result.awayPossession()
                + ";events=" + result.timeline().size()
                + ";goals=" + goals
                + ";goalMinutes=" + goalMinutes
                + ";chances=" + chances
                + ";corners=" + corners
                + ";offsides=" + offsides
                + ";fouls=" + fouls
                + ";yellows=" + yellows
                + ";reds=" + reds
                + ";injuries=" + injuries
                + ";subs=" + substitutions
                + ";timelineHash=" + sha256(String.join("|", normalizedTimeline))
                + ";timelineHead=" + timelineHead
                + ";timelineTail=" + timelineTail;
    }

    private static long count(DetailedMatchResult result, DetailedMatchEventType type) {
        return result.timeline().events().stream().filter(e -> e.type() == type).count();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String side(MatchContext context, String teamId) {
        if (context.homeTeamId().equals(teamId)) {
            return "H";
        }
        if (context.awayTeamId().equals(teamId)) {
            return "A";
        }
        return "-";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static MatchContext baseContext(String id, int homeOvr, int awayOvr, TeamStyle homeStyle, TeamStyle awayStyle) {
        SessionTeam home = team("home-" + id, "Home " + id);
        SessionTeam away = team("away-" + id, "Away " + id);
        return new MatchContext(
                "golden-" + id,
                home.getSessionTeamId(),
                away.getSessionTeamId(),
                home,
                away,
                players("home-" + id, homeOvr),
                players("away-" + id, awayOvr),
                bench("home-" + id, homeOvr + 5),
                bench("away-" + id, awayOvr + 3),
                "4-3-3",
                "4-4-2",
                homeStyle,
                awayStyle);
    }

    private static List<SessionPlayer> players(String prefix, int ovr) {
        List<String> positions = List.of("GK", "DEF", "DEF", "DEF", "DEF", "MID", "MID", "MID", "WINGER", "WINGER", "ATT");
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int delta = i % 3;
            players.add(SessionPlayer.fromWorldPlayer(
                    prefix + "-starter-" + i,
                    prefix + " starter " + i,
                    positions.get(i),
                    24 + i,
                    ovr + delta));
        }
        return players;
    }

    private static List<SessionPlayer> bench(String prefix, int ovr) {
        List<String> positions = List.of("ATT", "MID", "DEF", "WINGER", "GK");
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            players.add(SessionPlayer.fromWorldPlayer(
                    prefix + "-bench-" + i,
                    prefix + " bench " + i,
                    positions.get(i),
                    20 + i,
                    ovr));
        }
        return players;
    }

    private static SessionTeam team(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "world-" + id,
                name,
                "Country",
                BigDecimal.ZERO,
                "4-3-3",
                null);
    }
}

package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

class MinutePhaseFixture {
    final MatchContext context;
    final TeamMatchState home;
    final TeamMatchState away;
    final MatchTimeline timeline = new MatchTimeline();
    final HashSet<String> appliedScheduledSubs = new HashSet<>();
    final PlayerSelector homeSelector = new PlayerSelector(new Random(11));
    final PlayerSelector awaySelector = new PlayerSelector(new Random(17));

    MinutePhaseFixture(TeamStyle homeStyle, TeamStyle awayStyle) {
        SessionTeam homeTeam = team("home-phase", "Home Phase");
        SessionTeam awayTeam = team("away-phase", "Away Phase");
        this.context = new MatchContext(
                "phase-match",
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam,
                awayTeam,
                players("home", 78),
                players("away", 76),
                bench("home", 80),
                bench("away", 79),
                "4-4-2",
                "4-4-2",
                homeStyle,
                awayStyle);
        this.home = TeamMatchState.create(
                homeTeam, context.homeStartingPlayers(), context.homeBenchPlayers(), homeStyle, Map.of());
        this.away = TeamMatchState.create(
                awayTeam, context.awayStartingPlayers(), context.awayBenchPlayers(), awayStyle, Map.of());
    }

    MinuteSimulationInput input(int minute, Random random) {
        return new MinuteSimulationInput(
                new MinuteSimulationConfig(context, 0.5, 0.5, 1.0),
                new MinuteMatchState(random, home, away, timeline, homeSelector, awaySelector, appliedScheduledSubs),
                minute);
    }

    MinutePossessionState homePossession() {
        TacticalShapeProfile neutral = new TacticalShapeProfile(1, 1, 1, 1, 1, 1, 1, 1, 1);
        return new MinutePossessionState(
                true,
                home,
                away,
                homeSelector,
                context.homeTeamId(),
                context.homeFormation(),
                context.awayFormation(),
                neutral,
                neutral,
                Map.of(),
                Map.of());
    }

    MinuteTacticalState tacticalState(double homeShare) {
        TacticalShapeProfile neutral = new TacticalShapeProfile(1, 1, 1, 1, 1, 1, 1, 1, 1);
        return new MinuteTacticalState(0, 0, Map.of(), Map.of(), neutral, neutral, homeShare);
    }

    static MatchContext contextWithScheduledHomeSub(int minute) {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);
        return fixture.context.withManualSubstitution(
                fixture.context.homeTeamId(),
                fixture.context.homeStartingPlayers().get(10).getSessionPlayerId(),
                fixture.context.homeBenchPlayers().get(0).getSessionPlayerId(),
                minute);
    }

    private static List<SessionPlayer> players(String prefix, int overall) {
        List<String> positions = List.of("GK", "DEF", "DEF", "DEF", "DEF", "MID", "MID", "MID", "WINGER", "ATT", "ATT");
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            players.add(SessionPlayer.fromWorldPlayer(
                    prefix + "-starter-" + i,
                    prefix + " starter " + i,
                    positions.get(i),
                    20 + i,
                    overall + (i % 3)));
        }
        return players;
    }

    private static List<SessionPlayer> bench(String prefix, int overall) {
        List<String> positions = List.of("ATT", "MID", "DEF", "WINGER", "GK");
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            players.add(SessionPlayer.fromWorldPlayer(
                    prefix + "-bench-" + i,
                    prefix + " bench " + i,
                    positions.get(i),
                    24 + i,
                    overall));
        }
        return players;
    }

    private static SessionTeam team(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)),
                "world-" + id,
                name,
                "Country",
                BigDecimal.ZERO,
                "4-4-2",
                null);
    }

    static final class SequenceRandom extends Random {
        private final double[] doubles;
        private int doubleIndex;

        SequenceRandom(double... doubles) {
            this.doubles = doubles;
        }

        @Override
        public double nextDouble() {
            if (doubleIndex >= doubles.length) {
                return 0.99;
            }
            return doubles[doubleIndex++];
        }
    }
}

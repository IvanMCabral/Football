package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.SubstitutionWhatIfSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessSubstitutionWhatIfService {

    private final CareerRepository careerRepository;
    private final V24MatchContextFactory v24ContextFactory;

    Mono<SubstitutionWhatIfSummaryRow> run(
            UUID userId,
            String matchId,
            String playerOffId,
            String playerOnId,
            Integer minute,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        validate(matchId, playerOffId, playerOnId, minute, seedCount);
        int effectiveMinute = minute != null ? minute : 60;
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> optionalCareer
                .map(career -> Mono.fromSupplier(() -> execute(
                    career,
                    matchId,
                    playerOffId,
                    playerOnId,
                    effectiveMinute,
                    seedStart,
                    seedCount,
                    controlledTeamSide)))
                .orElseGet(() -> Mono.error(new IllegalStateException(
                    "Career not found for userId=" + userId))));
    }

    private void validate(String matchId, String playerOffId, String playerOnId, Integer minute, int seedCount) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId is required");
        }
        if (playerOffId == null || playerOffId.isBlank()) {
            throw new IllegalArgumentException("playerOffId is required");
        }
        if (playerOnId == null || playerOnId.isBlank()) {
            throw new IllegalArgumentException("playerOnId is required");
        }
        if (playerOffId.equals(playerOnId)) {
            throw new IllegalArgumentException("playerOffId and playerOnId must differ");
        }
        int effectiveMinute = minute != null ? minute : 60;
        if (effectiveMinute < 0 || effectiveMinute > 90) {
            throw new IllegalArgumentException("minute must be between 0 and 90");
        }
        if (seedCount < 1 || seedCount > 100) {
            throw new IllegalArgumentException("seedCount must be between 1 and 100");
        }
    }

    private SubstitutionWhatIfSummaryRow execute(
            CareerSave career,
            String matchId,
            String playerOffId,
            String playerOnId,
            int minute,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Match not found in current tournament: " + matchId));
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }

        String controlledTeamId = TestHarnessCommonSupport.resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean userIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("Substitution what-if controlled team is not part of match: " + controlledTeamId);
        }

        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;
        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(career, fixture, home, away, homeStyle, awayStyle, seedStart);
        List<SessionPlayer> starters = userIsHome ? baseContext.homeStartingPlayers() : baseContext.awayStartingPlayers();
        List<SessionPlayer> bench = userIsHome ? baseContext.homeBenchPlayers() : baseContext.awayBenchPlayers();
        SessionPlayer off = TestHarnessCommonSupport.findPlayer(starters, playerOffId)
            .orElseThrow(() -> new IllegalArgumentException("playerOffId '" + playerOffId + "' not in controlled starting XI"));
        SessionPlayer on = TestHarnessCommonSupport.findPlayer(bench, playerOnId)
            .orElseThrow(() -> new IllegalArgumentException("playerOnId '" + playerOnId + "' not on controlled bench"));

        TestHarnessSwapAccumulator baseline = new TestHarnessSwapAccumulator();
        TestHarnessSwapAccumulator substituted = new TestHarnessSwapAccumulator();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            V24MatchContext seededBase = v24ContextFactory.buildWithStyles(career, fixture, home, away, homeStyle, awayStyle, seed);
            baseline.add(simulateWithNoopReplay(seededBase, minute, seed), userIsHome);
            substituted.add(simulateWithManualSubstitution(seededBase, controlledTeamId, playerOffId, playerOnId, minute, seed), userIsHome);
        }

        TestHarnessSwapAverages baseAvg = baseline.averages();
        TestHarnessSwapAverages subAvg = substituted.averages();
        String formation = TestHarnessCommonSupport.currentFormation(career, controlledTeamId, userIsHome ? home : away);
        double deltaXgFor = TestHarnessCommonSupport.round3(subAvg.xgFor() - baseAvg.xgFor());
        double deltaXgAgainst = TestHarnessCommonSupport.round3(subAvg.xgAgainst() - baseAvg.xgAgainst());
        double deltaShotsFor = TestHarnessCommonSupport.round2(subAvg.shotsFor() - baseAvg.shotsFor());
        String read = TestHarnessCommonSupport.safeName(off) + " -> " + TestHarnessCommonSupport.safeName(on)
            + " min " + minute + " | - " + deltaXgFor + " | - " + deltaShotsFor + " | - " + deltaXgAgainst;

        return new SubstitutionWhatIfSummaryRow(
            matchId, formation, minute, seedStart, seedStart + seedCount - 1L, seedCount,
            playerOffId, TestHarnessCommonSupport.safeName(off), off.getPosition(), TestHarnessCommonSupport.playerOverall(off),
            playerOnId, TestHarnessCommonSupport.safeName(on), on.getPosition(), TestHarnessCommonSupport.playerOverall(on),
            baseAvg.goalsFor(), baseAvg.goalsAgainst(), baseAvg.goalDiff(), baseAvg.shotsFor(), baseAvg.shotsAgainst(),
            baseAvg.possessionFor(), baseAvg.xgFor(), baseAvg.xgAgainst(), baseAvg.xgDiff(),
            subAvg.goalsFor(), subAvg.goalsAgainst(), subAvg.goalDiff(), subAvg.shotsFor(), subAvg.shotsAgainst(),
            subAvg.possessionFor(), subAvg.xgFor(), subAvg.xgAgainst(), subAvg.xgDiff(),
            TestHarnessCommonSupport.round2(subAvg.goalsFor() - baseAvg.goalsFor()),
            TestHarnessCommonSupport.round2(subAvg.goalsAgainst() - baseAvg.goalsAgainst()),
            TestHarnessCommonSupport.round2(subAvg.goalDiff() - baseAvg.goalDiff()),
            deltaShotsFor,
            TestHarnessCommonSupport.round2(subAvg.shotsAgainst() - baseAvg.shotsAgainst()),
            TestHarnessCommonSupport.round2(subAvg.possessionFor() - baseAvg.possessionFor()),
            deltaXgFor,
            deltaXgAgainst,
            TestHarnessCommonSupport.round3(subAvg.xgDiff() - baseAvg.xgDiff()),
            TestHarnessCommonSupport.round2(subAvg.centralShotsFor() - baseAvg.centralShotsFor()),
            TestHarnessCommonSupport.round2(subAvg.wideShotsFor() - baseAvg.wideShotsFor()),
            TestHarnessCommonSupport.round2(subAvg.longShotsFor() - baseAvg.longShotsFor()),
            TestHarnessCommonSupport.round3(subAvg.centralXgFor() - baseAvg.centralXgFor()),
            TestHarnessCommonSupport.round3(subAvg.wideXgFor() - baseAvg.wideXgFor()),
            TestHarnessCommonSupport.round3(subAvg.longXgFor() - baseAvg.longXgFor()),
            read);
    }

    private V24DetailedMatchResult simulateWithManualSubstitution(
            V24MatchContext context,
            String teamId,
            String playerOffId,
            String playerOnId,
            int minute,
            long seed) {
        V24LiveSession session = new V24LiveSession(context, seed);
        for (int i = 0; i < minute; i++) {
            session.tick();
        }
        session.mutateContext(ctx -> ctx.withManualSubstitution(teamId, playerOffId, playerOnId, minute));
        while (!session.isFinished()) {
            session.tick();
        }
        return session.finalResult();
    }

    private V24DetailedMatchResult simulateWithNoopReplay(V24MatchContext context, int minute, long seed) {
        V24LiveSession session = new V24LiveSession(context, seed);
        for (int i = 0; i < minute; i++) {
            session.tick();
        }
        session.mutateContext(ctx -> ctx);
        while (!session.isFinished()) {
            session.tick();
        }
        return session.finalResult();
    }
}

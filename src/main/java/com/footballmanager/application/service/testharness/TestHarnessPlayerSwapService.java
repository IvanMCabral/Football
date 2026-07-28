package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.PlayerSwapMatrixSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessPlayerSwapService {

    private final CareerRepository careerRepository;
    private final V24MatchContextFactory v24ContextFactory;

    Mono<PlayerSwapMatrixSummaryRow> run(
            UUID userId,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String slotId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (starterPlayerId == null || starterPlayerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("starterPlayerId is required"));
        }
        if (benchPlayerId == null || benchPlayerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("benchPlayerId is required"));
        }
        if (starterPlayerId.equals(benchPlayerId)) {
            return Mono.error(new IllegalArgumentException("starterPlayerId and benchPlayerId must differ"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executePlayerSwapMatrixSummary(
                    optionalCareer.get(),
                    matchId,
                    starterPlayerId,
                    benchPlayerId,
                    slotId,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    Mono<PlayerSwapMatrixSummaryRow> run(
            UUID userId,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String slotId,
            long seedStart,
            int seedCount) {
        return run(
            userId,
            matchId,
            starterPlayerId,
            benchPlayerId,
            slotId,
            seedStart,
            seedCount,
            null);
    }

private PlayerSwapMatrixSummaryRow executePlayerSwapMatrixSummary(
            CareerSave career,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String requestedSlotId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {

        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }

        String controlledTeamId = TestHarnessCommonSupport.resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean userIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Player swap matrix controlled team is not part of match: " + controlledTeamId);
        }
        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;

        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seedStart);
        List<SessionPlayer> userStarters = userIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        List<SessionPlayer> userBench = userIsHome
            ? baseContext.homeBenchPlayers()
            : baseContext.awayBenchPlayers();

        String resolvedStarterPlayerId = starterPlayerId;
        String resolvedBenchPlayerId = benchPlayerId;
        if (TestHarnessAutoSwapSupport.isAutoToken(resolvedStarterPlayerId) || TestHarnessAutoSwapSupport.isAutoToken(resolvedBenchPlayerId)) {
            String autoMode = TestHarnessAutoSwapSupport.mode(resolvedStarterPlayerId, resolvedBenchPlayerId);
            PlayerSwapAutoPair pair = TestHarnessAutoSwapSupport.choosePair(userStarters, userBench, autoMode)
                .orElseThrow(() -> new IllegalArgumentException(
                    "No automatic starter/bench swap candidate found in V24 match context for mode " + autoMode));
            resolvedStarterPlayerId = pair.starter().getSessionPlayerId();
            resolvedBenchPlayerId = pair.bench().getSessionPlayerId();
        }

        final String effectiveStarterPlayerId = resolvedStarterPlayerId;
        final String effectiveBenchPlayerId = resolvedBenchPlayerId;
        SessionPlayer starter = TestHarnessCommonSupport.findPlayer(userStarters, effectiveStarterPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "starterPlayerId '" + effectiveStarterPlayerId + "' not in user starting XI"));
        SessionPlayer bench = TestHarnessCommonSupport.findPlayer(userBench, effectiveBenchPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "benchPlayerId '" + effectiveBenchPlayerId + "' not on user bench"));

        String formation = TestHarnessCommonSupport.currentFormation(career, controlledTeamId, userIsHome ? home : away);
        String slotId = resolveSlotId(baseContext, userIsHome, effectiveStarterPlayerId, requestedSlotId);
        TestHarnessSwapAccumulator baseline = new TestHarnessSwapAccumulator();
        TestHarnessSwapAccumulator swapped = new TestHarnessSwapAccumulator();
        TestHarnessSwapAccumulator baselinePreAutoSub = new TestHarnessSwapAccumulator();
        TestHarnessSwapAccumulator swappedPreAutoSub = new TestHarnessSwapAccumulator();

        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            V24MatchContext seededBase = v24ContextFactory.buildWithStyles(
                career,
                fixture,
                home,
                away,
                homeStyle,
                awayStyle,
                seed);
            V24DetailedMatchResult baselineResult =
                new V24DetailedMatchEngine().simulate(seededBase, new Random(seed));
            V24MatchContext swappedContext = TestHarnessContextMutationSupport.buildInitialSwapContext(
                seededBase, controlledTeamId, effectiveStarterPlayerId, effectiveBenchPlayerId);
            V24DetailedMatchResult swappedResult =
                new V24DetailedMatchEngine().simulate(swappedContext, new Random(seed));
            V24DetailedMatchResult baselinePreAutoSubResult =
                new V24DetailedMatchEngine().simulate(seededBase, new Random(seed), 59);
            V24DetailedMatchResult swappedPreAutoSubResult =
                new V24DetailedMatchEngine().simulate(swappedContext, new Random(seed), 59);
            baseline.add(baselineResult, userIsHome);
            swapped.add(swappedResult, userIsHome);
            baselinePreAutoSub.add(baselinePreAutoSubResult, userIsHome);
            swappedPreAutoSub.add(swappedPreAutoSubResult, userIsHome);
        }

        TestHarnessSwapAverages baseAvg = baseline.averages();
        TestHarnessSwapAverages swapAvg = swapped.averages();
        TestHarnessSwapAverages basePreAutoSubAvg = baselinePreAutoSub.averages();
        TestHarnessSwapAverages swapPreAutoSubAvg = swappedPreAutoSub.averages();
        return new PlayerSwapMatrixSummaryRow(
            matchId,
            formation,
            slotId,
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            effectiveStarterPlayerId,
            TestHarnessCommonSupport.safeName(starter),
            starter.getPosition(),
            TestHarnessCommonSupport.playerOverall(starter),
            effectiveBenchPlayerId,
            TestHarnessCommonSupport.safeName(bench),
            bench.getPosition(),
            TestHarnessCommonSupport.playerOverall(bench),
            baseAvg.goalsFor(),
            baseAvg.goalsAgainst(),
            baseAvg.goalDiff(),
            baseAvg.shotsFor(),
            baseAvg.shotsAgainst(),
            baseAvg.possessionFor(),
            baseAvg.xgFor(),
            baseAvg.xgAgainst(),
            baseAvg.xgDiff(),
            baseAvg.centralShotsFor(),
            baseAvg.wideShotsFor(),
            baseAvg.longShotsFor(),
            baseAvg.centralShotsAgainst(),
            baseAvg.wideShotsAgainst(),
            baseAvg.longShotsAgainst(),
            baseAvg.centralXgFor(),
            baseAvg.wideXgFor(),
            baseAvg.longXgFor(),
            baseAvg.centralXgAgainst(),
            baseAvg.wideXgAgainst(),
            baseAvg.longXgAgainst(),
            swapAvg.goalsFor(),
            swapAvg.goalsAgainst(),
            swapAvg.goalDiff(),
            swapAvg.shotsFor(),
            swapAvg.shotsAgainst(),
            swapAvg.possessionFor(),
            swapAvg.xgFor(),
            swapAvg.xgAgainst(),
            swapAvg.xgDiff(),
            swapAvg.centralShotsFor(),
            swapAvg.wideShotsFor(),
            swapAvg.longShotsFor(),
            swapAvg.centralShotsAgainst(),
            swapAvg.wideShotsAgainst(),
            swapAvg.longShotsAgainst(),
            swapAvg.centralXgFor(),
            swapAvg.wideXgFor(),
            swapAvg.longXgFor(),
            swapAvg.centralXgAgainst(),
            swapAvg.wideXgAgainst(),
            swapAvg.longXgAgainst(),
            TestHarnessCommonSupport.round2(swapAvg.goalsFor() - baseAvg.goalsFor()),
            TestHarnessCommonSupport.round2(swapAvg.goalsAgainst() - baseAvg.goalsAgainst()),
            TestHarnessCommonSupport.round2(swapAvg.goalDiff() - baseAvg.goalDiff()),
            TestHarnessCommonSupport.round2(swapAvg.shotsFor() - baseAvg.shotsFor()),
            TestHarnessCommonSupport.round2(swapAvg.shotsAgainst() - baseAvg.shotsAgainst()),
            TestHarnessCommonSupport.round2(swapAvg.possessionFor() - baseAvg.possessionFor()),
            TestHarnessCommonSupport.round3(swapAvg.xgFor() - baseAvg.xgFor()),
            TestHarnessCommonSupport.round3(swapAvg.xgAgainst() - baseAvg.xgAgainst()),
            TestHarnessCommonSupport.round3(swapAvg.xgDiff() - baseAvg.xgDiff()),
            TestHarnessCommonSupport.round2(swapAvg.centralShotsFor() - baseAvg.centralShotsFor()),
            TestHarnessCommonSupport.round2(swapAvg.wideShotsFor() - baseAvg.wideShotsFor()),
            TestHarnessCommonSupport.round2(swapAvg.longShotsFor() - baseAvg.longShotsFor()),
            TestHarnessCommonSupport.round2(swapAvg.centralShotsAgainst() - baseAvg.centralShotsAgainst()),
            TestHarnessCommonSupport.round2(swapAvg.wideShotsAgainst() - baseAvg.wideShotsAgainst()),
            TestHarnessCommonSupport.round2(swapAvg.longShotsAgainst() - baseAvg.longShotsAgainst()),
            TestHarnessCommonSupport.round3(swapAvg.centralXgFor() - baseAvg.centralXgFor()),
            TestHarnessCommonSupport.round3(swapAvg.wideXgFor() - baseAvg.wideXgFor()),
            TestHarnessCommonSupport.round3(swapAvg.longXgFor() - baseAvg.longXgFor()),
            TestHarnessCommonSupport.round3(swapAvg.centralXgAgainst() - baseAvg.centralXgAgainst()),
            TestHarnessCommonSupport.round3(swapAvg.wideXgAgainst() - baseAvg.wideXgAgainst()),
            TestHarnessCommonSupport.round3(swapAvg.longXgAgainst() - baseAvg.longXgAgainst()),
            TestHarnessCommonSupport.round2(swapPreAutoSubAvg.shotsFor() - basePreAutoSubAvg.shotsFor()),
            TestHarnessCommonSupport.round2(swapPreAutoSubAvg.shotsAgainst() - basePreAutoSubAvg.shotsAgainst()),
            TestHarnessCommonSupport.round3(swapPreAutoSubAvg.xgFor() - basePreAutoSubAvg.xgFor()),
            TestHarnessCommonSupport.round3(swapPreAutoSubAvg.xgAgainst() - basePreAutoSubAvg.xgAgainst()),
            TestHarnessCommonSupport.round3(swapPreAutoSubAvg.xgDiff() - basePreAutoSubAvg.xgDiff()));
    }


    private String resolveSlotId(
            V24MatchContext context,
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

    private String fallbackSubdivision(String position) {
        String normalized = position != null ? position.toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "GK" -> "GK-1";
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> "S22-2";
            case "ATT", "ST", "CF", "LW", "RW", "WINGER" -> "S5-2";
            default -> "S14-2";
        };
    }

}

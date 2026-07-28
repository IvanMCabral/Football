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
import com.footballmanager.domain.port.in.testharness.PositionPixelMatrixSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessPositionPixelService {

    private static final String AUTO_POSITION_PIXEL_PREFIX = "__AUTO_";

    private final CareerRepository careerRepository;
    private final V24MatchContextFactory v24ContextFactory;

    Mono<PositionPixelMatrixSummaryRow> run(
            UUID userId,
            String matchId,
            String playerId,
            Double targetXPercent,
            Double targetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (playerId == null || playerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("playerId is required"));
        }
        boolean hasAbsoluteTarget = targetXPercent != null && targetYPercent != null;
        boolean hasRelativeDelta = deltaXPercent != null && deltaYPercent != null;
        if (!hasAbsoluteTarget && !hasRelativeDelta) {
            return Mono.error(new IllegalArgumentException(
                "targetXPercent/targetYPercent or deltaXPercent/deltaYPercent are required"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException("Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executePositionPixelMatrixSummary(
                    optionalCareer.get(),
                    matchId,
                    playerId,
                    hasAbsoluteTarget ? TestHarnessPixelSupport.clampPercent(targetXPercent) : null,
                    hasAbsoluteTarget ? TestHarnessPixelSupport.clampPercent(targetYPercent) : null,
                    hasRelativeDelta ? deltaXPercent : null,
                    hasRelativeDelta ? deltaYPercent : null,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    Mono<PositionPixelMatrixSummaryRow> run(
            UUID userId,
            String matchId,
            String playerId,
            Double targetXPercent,
            Double targetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount) {
        return run(
            userId,
            matchId,
            playerId,
            targetXPercent,
            targetYPercent,
            deltaXPercent,
            deltaYPercent,
            seedStart,
            seedCount,
            null);
    }

    private PositionPixelMatrixSummaryRow executePositionPixelMatrixSummary(
            CareerSave career,
            String matchId,
            String playerId,
            Double requestedTargetXPercent,
            Double requestedTargetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
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
            throw new IllegalArgumentException("Position pixel matrix controlled team is not part of match: " + controlledTeamId);
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
        List<SessionPlayer> userStarters = userIsHome ? baseContext.homeStartingPlayers() : baseContext.awayStartingPlayers();
        SessionPlayer player = resolvePositionPixelPlayer(userStarters, playerId)
            .orElseThrow(() -> new IllegalArgumentException("playerId '" + playerId + "' not in user starting XI"));
        String resolvedPlayerId = player.getSessionPlayerId();
        LineupSlot baseSlot = (userIsHome ? baseContext.homeSlotsByPlayerId() : baseContext.awaySlotsByPlayerId()).get(resolvedPlayerId);
        String slotId = baseSlot != null ? baseSlot.subdivisionId() : fallbackSubdivision(player.getPosition());
        double fromX = baseSlot != null && baseSlot.customXPercent() != null
            ? baseSlot.customXPercent()
            : TestHarnessPixelSupport.canonicalXPercent(slotId).orElse(50.0);
        double fromY = baseSlot != null && baseSlot.customYPercent() != null
            ? baseSlot.customYPercent()
            : TestHarnessPixelSupport.canonicalYPercent(slotId).orElse(TestHarnessPixelSupport.fallbackYPercent(player.getPosition()));
        double targetXPercent = deltaXPercent != null
            ? TestHarnessPixelSupport.clampPercent(fromX + deltaXPercent)
            : requestedTargetXPercent;
        double targetYPercent = deltaYPercent != null
            ? TestHarnessPixelSupport.clampPercent(fromY + deltaYPercent)
            : requestedTargetYPercent;
        PositionPixelPlayerDiagnostic baselinePlayerDiagnostic =
            TestHarnessPixelSupport.diagnostic(player, slotId, fromX, fromY);
        PositionPixelPlayerDiagnostic movedPlayerDiagnostic =
            TestHarnessPixelSupport.diagnostic(player, slotId, targetXPercent, targetYPercent);

        TestHarnessSwapAccumulator baseline = new TestHarnessSwapAccumulator();
        TestHarnessSwapAccumulator moved = new TestHarnessSwapAccumulator();
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
            V24MatchContext movedContext = TestHarnessContextMutationSupport.buildMovedPositionContext(
                seededBase,
                controlledTeamId,
                resolvedPlayerId,
                slotId,
                targetXPercent,
                targetYPercent);
            V24DetailedMatchResult movedResult =
                new V24DetailedMatchEngine().simulate(movedContext, new Random(seed));
            baseline.add(baselineResult, userIsHome);
            moved.add(movedResult, userIsHome);
        }

        TestHarnessSwapAverages baseAvg = baseline.averages();
        TestHarnessSwapAverages movedAvg = moved.averages();
        return new PositionPixelMatrixSummaryRow(
            matchId,
            TestHarnessCommonSupport.currentFormation(career, controlledTeamId, userIsHome ? home : away),
            resolvedPlayerId,
            TestHarnessCommonSupport.safeName(player),
            player.getPosition(),
            slotId,
            TestHarnessCommonSupport.round2(fromX),
            TestHarnessCommonSupport.round2(fromY),
            TestHarnessCommonSupport.round2(targetXPercent),
            TestHarnessCommonSupport.round2(targetYPercent),
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            baseAvg.goalsFor(), baseAvg.goalsAgainst(), baseAvg.goalDiff(),
            baseAvg.shotsFor(), baseAvg.shotsAgainst(), baseAvg.possessionFor(),
            baseAvg.xgFor(), baseAvg.xgAgainst(), baseAvg.xgDiff(),
            baseAvg.centralShotsFor(), baseAvg.wideShotsFor(), baseAvg.longShotsFor(),
            baseAvg.centralShotsAgainst(), baseAvg.wideShotsAgainst(), baseAvg.longShotsAgainst(),
            baseAvg.centralXgFor(), baseAvg.wideXgFor(), baseAvg.longXgFor(),
            baseAvg.centralXgAgainst(), baseAvg.wideXgAgainst(), baseAvg.longXgAgainst(),
            movedAvg.goalsFor(), movedAvg.goalsAgainst(), movedAvg.goalDiff(),
            movedAvg.shotsFor(), movedAvg.shotsAgainst(), movedAvg.possessionFor(),
            movedAvg.xgFor(), movedAvg.xgAgainst(), movedAvg.xgDiff(),
            movedAvg.centralShotsFor(), movedAvg.wideShotsFor(), movedAvg.longShotsFor(),
            movedAvg.centralShotsAgainst(), movedAvg.wideShotsAgainst(), movedAvg.longShotsAgainst(),
            movedAvg.centralXgFor(), movedAvg.wideXgFor(), movedAvg.longXgFor(),
            movedAvg.centralXgAgainst(), movedAvg.wideXgAgainst(), movedAvg.longXgAgainst(),
            TestHarnessCommonSupport.round2(movedAvg.goalsFor() - baseAvg.goalsFor()),
            TestHarnessCommonSupport.round2(movedAvg.goalsAgainst() - baseAvg.goalsAgainst()),
            TestHarnessCommonSupport.round2(movedAvg.goalDiff() - baseAvg.goalDiff()),
            TestHarnessCommonSupport.round2(movedAvg.shotsFor() - baseAvg.shotsFor()),
            TestHarnessCommonSupport.round2(movedAvg.shotsAgainst() - baseAvg.shotsAgainst()),
            TestHarnessCommonSupport.round2(movedAvg.possessionFor() - baseAvg.possessionFor()),
            TestHarnessCommonSupport.round3(movedAvg.xgFor() - baseAvg.xgFor()),
            TestHarnessCommonSupport.round3(movedAvg.xgAgainst() - baseAvg.xgAgainst()),
            TestHarnessCommonSupport.round3(movedAvg.xgDiff() - baseAvg.xgDiff()),
            TestHarnessCommonSupport.round2(movedAvg.centralShotsFor() - baseAvg.centralShotsFor()),
            TestHarnessCommonSupport.round2(movedAvg.wideShotsFor() - baseAvg.wideShotsFor()),
            TestHarnessCommonSupport.round2(movedAvg.longShotsFor() - baseAvg.longShotsFor()),
            TestHarnessCommonSupport.round2(movedAvg.centralShotsAgainst() - baseAvg.centralShotsAgainst()),
            TestHarnessCommonSupport.round2(movedAvg.wideShotsAgainst() - baseAvg.wideShotsAgainst()),
            TestHarnessCommonSupport.round2(movedAvg.longShotsAgainst() - baseAvg.longShotsAgainst()),
            TestHarnessCommonSupport.round3(movedAvg.centralXgFor() - baseAvg.centralXgFor()),
            TestHarnessCommonSupport.round3(movedAvg.wideXgFor() - baseAvg.wideXgFor()),
            TestHarnessCommonSupport.round3(movedAvg.longXgFor() - baseAvg.longXgFor()),
            TestHarnessCommonSupport.round2(movedAvg.leftWideShotsFor() - baseAvg.leftWideShotsFor()),
            TestHarnessCommonSupport.round2(movedAvg.rightWideShotsFor() - baseAvg.rightWideShotsFor()),
            TestHarnessCommonSupport.round3(movedAvg.leftWideXgFor() - baseAvg.leftWideXgFor()),
            TestHarnessCommonSupport.round3(movedAvg.rightWideXgFor() - baseAvg.rightWideXgFor()),
            TestHarnessCommonSupport.round3(movedAvg.centralXgAgainst() - baseAvg.centralXgAgainst()),
            TestHarnessCommonSupport.round3(movedAvg.wideXgAgainst() - baseAvg.wideXgAgainst()),
            TestHarnessCommonSupport.round3(movedAvg.longXgAgainst() - baseAvg.longXgAgainst()),
            TestHarnessCommonSupport.round2(movedAvg.leftWideShotsAgainst() - baseAvg.leftWideShotsAgainst()),
            TestHarnessCommonSupport.round2(movedAvg.rightWideShotsAgainst() - baseAvg.rightWideShotsAgainst()),
            TestHarnessCommonSupport.round3(movedAvg.leftWideXgAgainst() - baseAvg.leftWideXgAgainst()),
            TestHarnessCommonSupport.round3(movedAvg.rightWideXgAgainst() - baseAvg.rightWideXgAgainst()),
            baselinePlayerDiagnostic.tacticalPosition(),
            movedPlayerDiagnostic.tacticalPosition(),
            baselinePlayerDiagnostic.effectiveness(),
            movedPlayerDiagnostic.effectiveness(),
            TestHarnessCommonSupport.round3(movedPlayerDiagnostic.effectiveness() - baselinePlayerDiagnostic.effectiveness()),
            baselinePlayerDiagnostic.collective(),
            movedPlayerDiagnostic.collective(),
            TestHarnessCommonSupport.round2(movedPlayerDiagnostic.collective() - baselinePlayerDiagnostic.collective()));
    }


    private Optional<SessionPlayer> resolvePositionPixelPlayer(List<SessionPlayer> players, String playerId) {
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

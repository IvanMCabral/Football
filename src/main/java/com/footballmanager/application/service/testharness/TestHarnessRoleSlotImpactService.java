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
import com.footballmanager.domain.port.in.testharness.RoleSlotImpactSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessRoleSlotImpactService {

    private final CareerRepository careerRepository;
    private final V24MatchContextFactory v24ContextFactory;

    Mono<List<RoleSlotImpactSummaryRow>> run(
            UUID userId,
            String matchId,
            String slotId,
            List<String> naturalPositions,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (slotId == null || slotId.isBlank()) {
            return Mono.error(new IllegalArgumentException("slotId is required"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }
        List<String> safeNaturalPositions = naturalPositions == null || naturalPositions.isEmpty()
            ? List.of("WINGER", "MID", "ATT", "DEF")
            : naturalPositions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (safeNaturalPositions.isEmpty()) {
            return Mono.error(new IllegalArgumentException("naturalPositions must contain at least one position"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException("Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executeRoleSlotImpactSummary(
                    optionalCareer.get(),
                    matchId,
                    slotId,
                    safeNaturalPositions,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    private List<RoleSlotImpactSummaryRow> executeRoleSlotImpactSummary(
            CareerSave career,
            String matchId,
            String slotId,
            List<String> naturalPositions,
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
            throw new IllegalArgumentException("Role slot impact controlled team is not part of match: " + controlledTeamId);
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
        Map<String, LineupSlot> userSlots = userIsHome
            ? baseContext.homeSlotsByPlayerId()
            : baseContext.awaySlotsByPlayerId();
        LineupSlot slot = userSlots.values().stream()
            .filter(s -> s != null && slotId.equals(s.subdivisionId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("slotId '" + slotId + "' not found in controlled team lineup"));
        List<SessionPlayer> starters = userIsHome ? baseContext.homeStartingPlayers() : baseContext.awayStartingPlayers();
        SessionPlayer baselinePlayer = TestHarnessCommonSupport.findPlayer(starters, slot.playerId())
            .orElseThrow(() -> new IllegalArgumentException("slot player not found in starting XI: " + slot.playerId()));

        double x = slot.customXPercent() != null ? slot.customXPercent() : TestHarnessPixelSupport.canonicalXPercent(slotId).orElse(50.0);
        double y = slot.customYPercent() != null ? slot.customYPercent() : TestHarnessPixelSupport.canonicalYPercent(slotId).orElse(TestHarnessPixelSupport.fallbackYPercent(baselinePlayer.getPosition()));
        String formation = TestHarnessCommonSupport.currentFormation(career, controlledTeamId, userIsHome ? home : away);

        List<RoleSlotImpactSummaryRow> rows = new ArrayList<>();
        for (String natural : naturalPositions) {
            PositionPixelPlayerDiagnostic diagnostic =
                TestHarnessPixelSupport.diagnostic(TestHarnessContextMutationSupport.roleOverrideClone(baselinePlayer, natural), slotId, x, y);
            TestHarnessSwapAccumulator accumulator = new TestHarnessSwapAccumulator();
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
                V24MatchContext roleContext = TestHarnessContextMutationSupport.buildRoleOverrideContext(
                    seededBase,
                    controlledTeamId,
                    baselinePlayer.getSessionPlayerId(),
                    natural);
                V24DetailedMatchResult result =
                    new V24DetailedMatchEngine().simulate(roleContext, new Random(seed));
                accumulator.add(result, userIsHome);
            }
            TestHarnessSwapAverages avg = accumulator.averages();
            rows.add(new RoleSlotImpactSummaryRow(
                matchId,
                formation,
                slotId,
                TestHarnessCommonSupport.round2(x),
                TestHarnessCommonSupport.round2(y),
                baselinePlayer.getSessionPlayerId(),
                TestHarnessCommonSupport.safeName(baselinePlayer),
                baselinePlayer.getPosition(),
                natural,
                diagnostic.tacticalPosition(),
                seedStart,
                seedStart + seedCount - 1L,
                seedCount,
                diagnostic.effectiveness(),
                diagnostic.collective(),
                avg.goalsFor(),
                avg.goalsAgainst(),
                avg.goalDiff(),
                avg.shotsFor(),
                avg.shotsAgainst(),
                avg.possessionFor(),
                avg.xgFor(),
                avg.xgAgainst(),
                avg.xgDiff(),
                avg.centralShotsFor(),
                avg.wideShotsFor(),
                avg.longShotsFor(),
                avg.centralXgFor(),
                avg.wideXgFor(),
                avg.longXgFor()));
        }
        return rows;
    }


}

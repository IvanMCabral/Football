package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.application.service.simulation.detailed.MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.detailed.ShotLocation;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;
import com.footballmanager.domain.port.in.testharness.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile({"dev", "local", "test"})
@Slf4j
class TestHarnessFormationMatrixService {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final MatchContextFactory matchContextFactory;
    private final BaselineStateStoragePort baselineStoragePort;
    private final DetailedMatchStoragePort v24StoragePort;
    private final FormationService formationService = new FormationService();
    private final TestHarnessSideMirrorSyntheticLabService sideMirrorSyntheticLabService;

    TestHarnessFormationMatrixService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            MatchContextFactory matchContextFactory,
            BaselineStateStoragePort baselineStoragePort,
            DetailedMatchStoragePort v24StoragePort,
            TestHarnessSideMirrorSyntheticLabService sideMirrorSyntheticLabService) {
        this.careerRepository = careerRepository;
        this.careerSessionService = careerSessionService;
        this.matchContextFactory = matchContextFactory;
        this.baselineStoragePort = baselineStoragePort;
        this.v24StoragePort = v24StoragePort;
        this.sideMirrorSyntheticLabService = sideMirrorSyntheticLabService;
    }

    public Mono<List<FormationMatrixRow>> runFormationMatrix(UUID userId, String matchId, Long seedOverride, String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : 12345L;

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executeFormationMatrix(optionalCareer.get(), matchId, seed, controlledTeamSide));
            });
    }

    public Mono<List<FormationMatrixSummaryRow>> runFormationMatrixSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
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
                return Mono.fromSupplier(() ->
                    executeFormationMatrixSummary(optionalCareer.get(), matchId, seedStart, seedCount, controlledTeamSide));
            });
    }

    public Mono<List<SideMirrorSyntheticLabRow>> runSideMirrorSyntheticLab(
            UUID userId,
            long seedStart,
            int seedCount) {
        return sideMirrorSyntheticLabService.runSideMirrorSyntheticLab(userId, seedStart, seedCount);
    }

    private List<FormationMatrixSummaryRow> executeFormationMatrixSummary(
            CareerSave career,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        Map<String, FormationMatrixSummaryAccumulator> summaries = new LinkedHashMap<>();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            for (FormationMatrixRow row : executeFormationMatrix(career, matchId, seed, controlledTeamSide)) {
                summaries.computeIfAbsent(row.formation(), FormationMatrixSummaryAccumulator::new)
                    .add(row, "HOME".equalsIgnoreCase(controlledTeamSide) || !"AWAY".equalsIgnoreCase(controlledTeamSide));
            }
        }
        return summaries.values().stream()
            .map(summary -> summary.toRow(seedStart, seedCount))
            .toList();
    }

private List<FormationMatrixRow> executeFormationMatrix(CareerSave career, String matchId, long seed, String controlledTeamSide) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException(
                "SessionTeam not found for match " + matchId
                    + " (home=" + fixture.getHomeTeamId()
                    + ", away=" + fixture.getAwayTeamId() + ")");
        }

        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean controlledIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean controlledIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!controlledIsHome && !controlledIsAway) {
            throw new IllegalArgumentException(
                "Formation matrix controlled team is not part of match: " + controlledTeamId);
        }

        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;

        MatchContext baseContext = matchContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seed);
        List<SessionPlayer> userStarters = controlledIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        List<SessionPlayer> userBench = controlledIsHome
            ? baseContext.homeBenchPlayers()
            : baseContext.awayBenchPlayers();
        if (userStarters.size() != 11) {
            throw new IllegalStateException(
                "Formation matrix needs exactly 11 user starters, got " + userStarters.size());
        }

        List<FormationMatrixRow> rows = new ArrayList<>();
        DetailedMatchEngine engine = new DetailedMatchEngine();
        for (FormationDefinition formation : formationService.getAllFormations()) {
            Map<String, LineupSlot> slots = FormationMatrixSlotSupport.buildSlots(userStarters, formation);
            MatchContext shapedContext = baseContext
                .withNewFormation(controlledTeamId, formation.name())
                .withSlots(controlledTeamId, slots);
            DetailedMatchEngine.TacticalShapeDebug shapeDebug = engine.debugTacticalShape(
                controlledIsHome ? home : away,
                userStarters,
                userBench,
                controlledIsHome ? homeStyle : awayStyle,
                formation.name(),
                slots);
            DetailedMatchResult result =
                engine.simulate(shapedContext, new Random(seed));
            ZoneCounts zones = countZones(result);
            rows.add(new FormationMatrixRow(
                formation.name(),
                result.homeGoals(),
                result.awayGoals(),
                result.homeXg(),
                result.awayXg(),
                result.homeShots(),
                result.awayShots(),
                result.homePossession(),
                result.awayPossession(),
                zones.homeCentral(),
                zones.homeWide(),
                zones.homeLong(),
                zones.awayCentral(),
                zones.awayWide(),
                zones.awayLong(),
                zones.homeLeftWide(),
                zones.homeRightWide(),
                zones.homeLeftWideXg(),
                zones.homeRightWideXg(),
                zones.awayLeftWide(),
                zones.awayRightWide(),
                zones.awayLeftWideXg(),
                zones.awayRightWideXg(),
                round3(shapeDebug.possessionMultiplier()),
                round3(shapeDebug.attackVolumeMultiplier()),
                round3(shapeDebug.defensiveResistanceMultiplier()),
                round3(shapeDebug.attackLeft()),
                round3(shapeDebug.attackCenter()),
                round3(shapeDebug.attackRight()),
                round3(shapeDebug.defenseLeft()),
                round3(shapeDebug.defenseCenter()),
                round3(shapeDebug.defenseRight())));
        }
        return rows;
    }

    private String resolveControlledTeamId(CareerSave career, MatchFixture fixture, String controlledTeamSide) {
        String normalized = controlledTeamSide == null
            ? "USER"
            : controlledTeamSide.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HOME" -> fixture.getHomeTeamId();
            case "AWAY" -> fixture.getAwayTeamId();
            default -> {
                String selectedTeamId = career.getUserSessionTeamId();
                if (selectedTeamId != null
                    && (selectedTeamId.equals(fixture.getHomeTeamId())
                    || selectedTeamId.equals(fixture.getAwayTeamId()))) {
                    yield selectedTeamId;
                }
                yield fixture.getHomeTeamId();
            }
        };
    }

private ZoneCounts countZones(DetailedMatchResult result) {
        int homeCentral = 0, homeWide = 0, homeLong = 0;
        int awayCentral = 0, awayWide = 0, awayLong = 0;
        int homeLeftWide = 0, homeRightWide = 0;
        int awayLeftWide = 0, awayRightWide = 0;
        double homeCentralXg = 0.0, homeWideXg = 0.0, homeLongXg = 0.0;
        double awayCentralXg = 0.0, awayWideXg = 0.0, awayLongXg = 0.0;
        double homeLeftWideXg = 0.0, homeRightWideXg = 0.0;
        double awayLeftWideXg = 0.0, awayRightWideXg = 0.0;
        for (DetailedMatchEvent event : result.timeline().events()) {
            if (!isShotLike(event) || event.shotCoordinate() == null) {
                continue;
            }
            ShotLocation location = event.shotCoordinate().location();
            boolean home = result.homeTeamId().equals(event.teamId());
            if (location == ShotLocation.SIX_YARD_BOX || location == ShotLocation.PENALTY_AREA_CENTER) {
                if (home) {
                    homeCentral++;
                    homeCentralXg += event.xg();
                } else {
                    awayCentral++;
                    awayCentralXg += event.xg();
                }
            } else if (location == ShotLocation.PENALTY_AREA_WIDE) {
                if (home) {
                    homeWide++;
                    homeWideXg += event.xg();
                    if (isLeftWide(event)) {
                        homeLeftWide++;
                        homeLeftWideXg += event.xg();
                    } else {
                        homeRightWide++;
                        homeRightWideXg += event.xg();
                    }
                } else {
                    awayWide++;
                    awayWideXg += event.xg();
                    if (isLeftWide(event)) {
                        awayLeftWide++;
                        awayLeftWideXg += event.xg();
                    } else {
                        awayRightWide++;
                        awayRightWideXg += event.xg();
                    }
                }
            } else {
                if (home) {
                    homeLong++;
                    homeLongXg += event.xg();
                } else {
                    awayLong++;
                    awayLongXg += event.xg();
                }
            }
        }
        return new ZoneCounts(
            homeCentral,
            homeWide,
            homeLong,
            awayCentral,
            awayWide,
            awayLong,
            round3(homeCentralXg),
            round3(homeWideXg),
            round3(homeLongXg),
            homeLeftWide,
            homeRightWide,
            round3(homeLeftWideXg),
            round3(homeRightWideXg),
            round3(awayCentralXg),
            round3(awayWideXg),
            round3(awayLongXg),
            awayLeftWide,
            awayRightWide,
            round3(awayLeftWideXg),
            round3(awayRightWideXg));
    }

private boolean isLeftWide(DetailedMatchEvent event) {
        return event.shotCoordinate() != null && event.shotCoordinate().y() < 50.0;
    }

private boolean isShotLike(DetailedMatchEvent event) {
        return event.type() == DetailedMatchEventType.SHOT
            || event.type() == DetailedMatchEventType.SHOT_ON_TARGET
            || event.type() == DetailedMatchEventType.MISS
            || event.type() == DetailedMatchEventType.BLOCK
            || event.type() == DetailedMatchEventType.GOAL;
    }


private record ZoneCounts(
        int homeCentral,
        int homeWide,
        int homeLong,
        int awayCentral,
        int awayWide,
        int awayLong,
        double homeCentralXg,
        double homeWideXg,
        double homeLongXg,
        int homeLeftWide,
        int homeRightWide,
        double homeLeftWideXg,
        double homeRightWideXg,
        double awayCentralXg,
        double awayWideXg,
        double awayLongXg,
        int awayLeftWide,
        int awayRightWide,
        double awayLeftWideXg,
        double awayRightWideXg
    ) {}

    private List<MatchLineupPlayerDto> lineupSnapshot(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        return players.stream()
            .filter(java.util.Objects::nonNull)
            .map(MatchLineupPlayerDto::fromSessionPlayer)
            .toList();
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}


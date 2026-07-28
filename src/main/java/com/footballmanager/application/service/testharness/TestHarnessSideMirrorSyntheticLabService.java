package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24ShotLocation;
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
class TestHarnessSideMirrorSyntheticLabService {

    private final FormationService formationService = new FormationService();

public Mono<List<SideMirrorSyntheticLabRow>> runSideMirrorSyntheticLab(
            UUID userId,
            long seedStart,
            int seedCount) {
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }
        return Mono.fromSupplier(() -> executeSideMirrorSyntheticLab(seedStart, seedCount));
    }

private List<SideMirrorSyntheticLabRow> executeSideMirrorSyntheticLab(long seedStart, int seedCount) {
        List<SideMirrorSyntheticLabRow> rows = new ArrayList<>();
        for (FormationDefinition formation : formationService.getAllFormations()) {
            FormationMatrixSummaryAccumulator weakLeft = new FormationMatrixSummaryAccumulator(formation.name());
            FormationMatrixSummaryAccumulator weakRight = new FormationMatrixSummaryAccumulator(formation.name());
            for (int i = 0; i < seedCount; i++) {
                long seed = seedStart + i;
                weakLeft.add(executeSyntheticSideMirrorRow(formation, seed, true), true);
                weakRight.add(executeSyntheticSideMirrorRow(formation, seed, false), true);
            }
            FormationMatrixSummaryRow left = weakLeft.toRow(seedStart, seedCount);
            FormationMatrixSummaryRow right = weakRight.toRow(seedStart, seedCount);
            rows.add(TestHarnessSideMirrorReadSupport.toSyntheticSideMirrorRow(formation.name(), seedStart, seedCount, left, right));
        }
        return rows;
    }

private FormationMatrixRow executeSyntheticSideMirrorRow(
            FormationDefinition formation,
            long seed,
            boolean weakenOpponentLeft) {
        SessionTeam home = syntheticTeam("synthetic-home", "Synthetic Probe", formation.name());
        SessionTeam away = syntheticTeam("synthetic-away", "Synthetic Mirror", formation.name());
        List<SessionPlayer> homeStarters = syntheticPlayers("H", false);
        List<SessionPlayer> awayStarters = syntheticPlayers("A", false);
        Map<String, LineupSlot> homeSlots = FormationMatrixSlotSupport.buildSlots(homeStarters, formation);
        Map<String, LineupSlot> awaySlots = FormationMatrixSlotSupport.buildSlots(awayStarters, formation);
        weakenSyntheticWideDefender(awayStarters, awaySlots, weakenOpponentLeft);

        V24MatchContext context = new V24MatchContext(
            "synthetic-side-mirror-" + formation.name() + "-" + (weakenOpponentLeft ? "WL" : "WR") + "-" + seed,
            home.getSessionTeamId(),
            away.getSessionTeamId(),
            home,
            away,
            homeStarters,
            awayStarters,
            List.of(),
            List.of(),
            formation.name(),
            formation.name(),
            TeamStyle.BALANCED,
            TeamStyle.BALANCED,
            List.of(),
            homeSlots,
            awaySlots);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchEngine.TacticalShapeDebug shapeDebug = engine.debugTacticalShape(
            home,
            homeStarters,
            List.of(),
            TeamStyle.BALANCED,
            formation.name(),
            homeSlots);
        V24DetailedMatchResult result = engine.simulate(context, new Random(seed));
        ZoneCounts zones = countZones(result);
        return new FormationMatrixRow(
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
            round3(shapeDebug.defenseRight()));
    }

private SessionTeam syntheticTeam(String id, String name, String formation) {
        SessionTeam team = SessionTeam.custom(id, name, "LAB", BigDecimal.ZERO, formation);
        team.setSessionTeamId(id);
        team.setStyle(TeamStyle.BALANCED);
        return team;
    }

private List<SessionPlayer> syntheticPlayers(String prefix, boolean weak) {
        List<SessionPlayer> players = new ArrayList<>();
        players.add(syntheticPlayer(prefix + "-GK", "GK", 74, weak));
        players.add(syntheticPlayer(prefix + "-DEF-L", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-DEF-CL", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-DEF-CR", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-DEF-R", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-MID-L", "MID", 76, weak));
        players.add(syntheticPlayer(prefix + "-MID-C", "MID", 76, weak));
        players.add(syntheticPlayer(prefix + "-MID-R", "MID", 76, weak));
        players.add(syntheticPlayer(prefix + "-WING-L", "WINGER", 76, weak));
        players.add(syntheticPlayer(prefix + "-WING-R", "WINGER", 76, weak));
        players.add(syntheticPlayer(prefix + "-ATT", "ATT", 76, weak));
        return players;
    }

private SessionPlayer syntheticPlayer(String id, String position, int overall, boolean weak) {
        SessionPlayer player = SessionPlayer.custom(
            "Lab " + id,
            25,
            position,
            overall,
            overall,
            overall,
            overall,
            overall,
            overall,
            BigDecimal.ZERO);
        player.setSessionPlayerId(id);
        if (weak) {
            player.setDefense(42);
            player.setMentality(45);
            player.setStamina(55);
        }
        return player;
    }

private void weakenSyntheticWideDefender(
            List<SessionPlayer> starters,
            Map<String, LineupSlot> slots,
            boolean leftSide) {
        if (starters == null || starters.isEmpty() || slots == null || slots.isEmpty()) return;
        Optional<Map.Entry<String, LineupSlot>> target = slots.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .filter(entry -> {
                Double y = entry.getValue().customYPercent();
                return y != null && y >= 55.0;
            })
            .min((a, b) -> {
                double ax = Optional.ofNullable(a.getValue().customXPercent()).orElse(50.0);
                double bx = Optional.ofNullable(b.getValue().customXPercent()).orElse(50.0);
                return leftSide ? Double.compare(ax, bx) : Double.compare(bx, ax);
            });
        if (target.isEmpty()) return;
        String playerId = target.get().getKey();
        starters.stream()
            .filter(player -> playerId.equals(player.getSessionPlayerId()))
            .findFirst()
            .ifPresent(player -> {
                player.setDefense(42);
                player.setMentality(45);
                player.setStamina(55);
            });
    }

private ZoneCounts countZones(V24DetailedMatchResult result) {
        int homeCentral = 0, homeWide = 0, homeLong = 0;
        int awayCentral = 0, awayWide = 0, awayLong = 0;
        int homeLeftWide = 0, homeRightWide = 0;
        int awayLeftWide = 0, awayRightWide = 0;
        double homeCentralXg = 0.0, homeWideXg = 0.0, homeLongXg = 0.0;
        double awayCentralXg = 0.0, awayWideXg = 0.0, awayLongXg = 0.0;
        double homeLeftWideXg = 0.0, homeRightWideXg = 0.0;
        double awayLeftWideXg = 0.0, awayRightWideXg = 0.0;
        for (V24MatchEvent event : result.timeline().events()) {
            if (!isShotLike(event) || event.shotCoordinate() == null) {
                continue;
            }
            V24ShotLocation location = event.shotCoordinate().location();
            boolean home = result.homeTeamId().equals(event.teamId());
            if (location == V24ShotLocation.SIX_YARD_BOX || location == V24ShotLocation.PENALTY_AREA_CENTER) {
                if (home) {
                    homeCentral++;
                    homeCentralXg += event.xg();
                } else {
                    awayCentral++;
                    awayCentralXg += event.xg();
                }
            } else if (location == V24ShotLocation.PENALTY_AREA_WIDE) {
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

private boolean isLeftWide(V24MatchEvent event) {
        return event.shotCoordinate() != null && event.shotCoordinate().y() < 50.0;
    }

private boolean isShotLike(V24MatchEvent event) {
        return event.type() == V24MatchEventType.SHOT
            || event.type() == V24MatchEventType.SHOT_ON_TARGET
            || event.type() == V24MatchEventType.MISS
            || event.type() == V24MatchEventType.BLOCK
            || event.type() == V24MatchEventType.GOAL;
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

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}


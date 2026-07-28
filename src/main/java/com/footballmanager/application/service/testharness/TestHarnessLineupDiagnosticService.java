package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.domain.TeamStyle;
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
class TestHarnessLineupDiagnosticService {

    private final CareerRepository careerRepository;
    private final V24MatchContextFactory v24ContextFactory;
    private final FormationService formationService = new FormationService();
    private final TestHarnessDiagnosticAssignmentSupport assignmentSupport = new TestHarnessDiagnosticAssignmentSupport();

    TestHarnessLineupDiagnosticService(
            CareerRepository careerRepository,
            V24MatchContextFactory v24ContextFactory) {
        this.careerRepository = careerRepository;
        this.v24ContextFactory = v24ContextFactory;
    }

    public Mono<LineupDiagnostic> lineupDiagnostic(UUID userId, String matchId, Long seedOverride) {
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
                CareerSave career = optionalCareer.get();
                MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Match not found in current tournament: " + matchId));
                SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
                SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
                if (home == null || away == null) {
                    return Mono.error(new IllegalStateException(
                        "SessionTeam not found for match " + matchId
                        + " (home=" + fixture.getHomeTeamId()
                        + ", away=" + fixture.getAwayTeamId() + ")"));
                }
                V24MatchContext context = v24ContextFactory.build(career, fixture, home, away, seed);
                return Mono.just(new LineupDiagnostic(
                    matchId,
                    seed,
                    buildLineupDiagnosticTeam(
                        context.homeTeamId(),
                        context.homeTeam().getName(),
                        context.homeFormation(),
                        context.homeStyle(),
                        context.homeStartingPlayers(),
                        context.homeSlotsByPlayerId()),
                    buildLineupDiagnosticTeam(
                        context.awayTeamId(),
                        context.awayTeam().getName(),
                        context.awayFormation(),
                        context.awayStyle(),
                        context.awayStartingPlayers(),
                        context.awaySlotsByPlayerId())
                ));
            });
    }

private LineupDiagnosticTeam buildLineupDiagnosticTeam(
            String teamId,
            String teamName,
            String formation,
            TeamStyle style,
            List<SessionPlayer> starters,
            Map<String, LineupSlot> slotsByPlayerId) {
        List<LineupDiagnosticPlayer> players = starters.stream()
            .map(player -> buildLineupDiagnosticPlayer(
                player,
                resolveDiagnosticSlot(player, formation, starters, slotsByPlayerId)))
            .toList();
        double avgOverall = players.stream()
            .mapToInt(LineupDiagnosticPlayer::overall)
            .average()
            .orElse(0.0);
        double avgCollective = players.stream()
            .mapToDouble(LineupDiagnosticPlayer::collective)
            .average()
            .orElse(0.0);
        double avgEffectiveness = players.stream()
            .mapToDouble(LineupDiagnosticPlayer::effectiveness)
            .average()
            .orElse(0.0);
        return new LineupDiagnosticTeam(
            teamId,
            teamName,
            formation,
            style,
            round2(avgOverall),
            round2(avgCollective),
            round3(avgEffectiveness),
            players.size(),
            buildLineupWidthDiagnostic(players),
            players
        );
    }

private LineupWidthDiagnostic buildLineupWidthDiagnostic(List<LineupDiagnosticPlayer> players) {
        List<LineupDiagnosticPlayer> outfield = players == null
            ? List.of()
            : players.stream()
                .filter(Objects::nonNull)
                .filter(player -> !"GK".equalsIgnoreCase(player.tacticalPosition()))
                .toList();
        int leftCount = 0;
        int centerCount = 0;
        int rightCount = 0;
        double leftXSum = 0.0;
        double rightXSum = 0.0;
        for (LineupDiagnosticPlayer player : outfield) {
            String side = diagnosticPlayerLane(player);
            if ("LEFT".equals(side)) {
                leftCount++;
                leftXSum += player.xPercent() != null ? player.xPercent() : 25.0;
            } else if ("RIGHT".equals(side)) {
                rightCount++;
                rightXSum += player.xPercent() != null ? player.xPercent() : 75.0;
            } else {
                centerCount++;
            }
        }
        int wideCount = leftCount + rightCount;
        double leftAvgX = leftCount > 0 ? round2(leftXSum / leftCount) : 0.0;
        double rightAvgX = rightCount > 0 ? round2(rightXSum / rightCount) : 0.0;
        double widthScore = outfield.isEmpty() ? 0.0 : round2((wideCount * 100.0) / outfield.size());
        double sideBalance = wideCount == 0 ? 0.0 : round2(100.0 - (Math.abs(leftCount - rightCount) * 100.0 / wideCount));
        String verdict;
        if (wideCount < 2) {
            verdict = "Revisar ancho";
        } else if (sideBalance < 45.0) {
            verdict = "Revisar lado";
        } else if (widthScore < 35.0) {
            verdict = "Estrecha";
        } else if (sideBalance < 70.0) {
            verdict = "Parcial";
        } else {
            verdict = "OK";
        }
        return new LineupWidthDiagnostic(
            leftCount,
            centerCount,
            rightCount,
            wideCount,
            leftAvgX,
            rightAvgX,
            widthScore,
            sideBalance,
            verdict,
            lineupWidthRead(leftCount, centerCount, rightCount, widthScore, sideBalance, verdict)
        );
    }

private String diagnosticPlayerLane(LineupDiagnosticPlayer player) {
        String roleSide = player.slotSide();
        if ("LEFT".equals(roleSide) || "RIGHT".equals(roleSide)) {
            return roleSide;
        }
        Double x = player.xPercent();
        if (x != null && Double.isFinite(x)) {
            if (x <= 42.0) return "LEFT";
            if (x >= 58.0) return "RIGHT";
        }
        return "CENTER";
    }

private String lineupWidthRead(
            int leftCount,
            int centerCount,
            int rightCount,
            double widthScore,
            double sideBalance,
            String verdict) {
        String base = "Carriles: izquierda " + leftCount
            + ", centro " + centerCount
            + ", derecha " + rightCount
            + ". Ancho " + widthScore + "%, balance lateral " + sideBalance + "%.";
        return switch (verdict) {
            case "OK" -> base + " La estructura ofrece salida por ambos lados.";
            case "Parcial" -> base + " Hay banda, pero un lado queda mas cargado que el otro.";
            case "Estrecha" -> base + " La formacion concentra demasiados jugadores por dentro.";
            case "Revisar lado" -> base + " Un carril queda claramente mas poblado; revisar roles o movimientos.";
            default -> base + " Falta presencia real de banda; puede explicar espejos laterales pobres.";
        };
    }

private LineupDiagnosticPlayer buildLineupDiagnosticPlayer(
            SessionPlayer player,
            ResolvedDiagnosticSlot slot) {
        String natural = safePosition(player.getPosition());
        String tactical = tacticalPositionForDiagnostic(slot, natural);
        String slotRole = slot != null && slot.role() != null ? slot.role() : tactical;
        String slotSide = diagnosticSlotSide(slot);
        TestHarnessDiagnosticAssignmentSupport.CuratedMatrixRoleProfile profile = assignmentSupport.curatedMatrixRoleProfile(player);
        int roleBonus = assignmentSupport.diagnosticRoleBonus(profile, slotRole);
        int sideBonus = assignmentSupport.diagnosticSideBonus(profile, slotSide);
        int assignmentScore = assignmentSupport.formationPositionFitScore(player, diagnosticFormationPosition(slot));
        String assignmentVerdict = assignmentSupport.assignmentVerdict(natural, tactical, roleBonus, sideBonus, assignmentScore);
        String assignmentRead = assignmentSupport.assignmentRead(player, natural, slotRole, slotSide, profile, assignmentVerdict, roleBonus, sideBonus);
        double effectiveness = slot != null
            && slot.xPercent() != null && Double.isFinite(slot.xPercent())
            && slot.yPercent() != null && Double.isFinite(slot.yPercent())
            ? com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator
                .effectiveness(natural, slot.xPercent(), slot.yPercent(), tactical)
            : PositionEffectivenessCalculator.effectiveness(natural, tactical);
        int attack = intOr(player.getAttack(), 50);
        int defense = intOr(player.getDefense(), 50);
        int technique = intOr(player.getTechnique(), 50);
        int speed = intOr(player.getSpeed(), 50);
        int stamina = intOr(player.getStamina(), 50);
        int mentality = intOr(player.getMentality(), 50);
        int overall = (int) Math.round((attack + defense + technique + speed + stamina + mentality) / 6.0);
        double baseCollective = "GK".equals(natural)
            ? ((defense + mentality) / 2.0)
            : ((attack + defense + mentality) / 3.0);
        return new LineupDiagnosticPlayer(
            player.getSessionPlayerId(),
            player.getName(),
            natural,
            tactical,
            slotRole,
            slotSide,
            slot != null ? slot.subdivisionId() : null,
            slot != null ? finiteOrNull(slot.xPercent()) : null,
            slot != null ? finiteOrNull(slot.yPercent()) : null,
            slot != null ? slot.source() : "missing",
            profile != null ? String.join(" / ", profile.roles()) : "-",
            profile != null ? String.join(" / ", profile.sides()) : "-",
            roleBonus,
            sideBonus,
            assignmentScore,
            assignmentVerdict,
            assignmentRead,
            attack,
            defense,
            technique,
            speed,
            stamina,
            mentality,
            overall,
            round3(effectiveness),
            round2(baseCollective * effectiveness)
        );
    }

private ResolvedDiagnosticSlot resolveDiagnosticSlot(
            SessionPlayer player,
            String formation,
            List<SessionPlayer> starters,
            Map<String, LineupSlot> slotsByPlayerId) {
        LineupSlot manual = slotsByPlayerId != null ? slotsByPlayerId.get(player.getSessionPlayerId()) : null;
        FormationPosition canonical = null;
        if (manual != null && manual.subdivisionId() != null && !manual.subdivisionId().isBlank()) {
            canonical = findFormationPosition(formation, manual.subdivisionId());
        }
        if (canonical == null) {
            canonical = canonicalPositionByStarterIndex(formation, starters, player);
        }
        if (manual == null && canonical == null) {
            return null;
        }
        String subdivisionId = manual != null && manual.subdivisionId() != null && !manual.subdivisionId().isBlank()
            ? manual.subdivisionId()
            : canonical != null ? canonical.subdivisionId() : null;
        boolean hasCustomX = manual != null && finiteOrNull(manual.customXPercent()) != null;
        boolean hasCustomY = manual != null && finiteOrNull(manual.customYPercent()) != null;
        Double xPercent = hasCustomX
            ? manual.customXPercent()
            : canonical != null ? canonical.xPercent() : null;
        Double yPercent = hasCustomY
            ? manual.customYPercent()
            : canonical != null ? canonical.yPercent() : null;
        String source = (hasCustomX || hasCustomY)
            ? "modal-custom"
            : manual != null ? "persisted-slot" : "canonical";
        return new ResolvedDiagnosticSlot(
            subdivisionId,
            canonical != null ? canonical.role() : null,
            xPercent,
            yPercent,
            source);
    }

private FormationPosition canonicalPositionByStarterIndex(
            String formation,
            List<SessionPlayer> starters,
            SessionPlayer player) {
        if (formation == null || formation.isBlank() || starters == null || starters.isEmpty() || player == null) {
            return null;
        }
        List<FormationPosition> positions = formationPositions(formation);
        if (positions.isEmpty()) return null;
        int index = -1;
        for (int i = 0; i < starters.size(); i++) {
            SessionPlayer starter = starters.get(i);
            if (starter != null && Objects.equals(starter.getSessionPlayerId(), player.getSessionPlayerId())) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= positions.size()) return null;
        return positions.get(index);
    }

private FormationPosition findFormationPosition(String formation, String subdivisionId) {
        if (formation == null || formation.isBlank() || subdivisionId == null || subdivisionId.isBlank()) {
            return null;
        }
        return formationPositions(formation).stream()
            .filter(position -> subdivisionId.equals(position.subdivisionId()))
            .findFirst()
            .orElse(null);
    }

private List<FormationPosition> formationPositions(String formation) {
        try {
            FormationDefinition dto = formationService.getFormationByName(formation);
            if (dto == null || dto.positions() == null) return List.of();
            return dto.positions().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                    FormationPosition::index,
                    Comparator.nullsLast(Integer::compareTo)))
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

private String tacticalPositionForDiagnostic(ResolvedDiagnosticSlot slot, String naturalPosition) {
        if (slot == null) return naturalPosition;
        if ("GK-1".equals(slot.subdivisionId()) || "GK".equalsIgnoreCase(naturalPosition)) {
            return "GK";
        }
        Double customY = slot.yPercent();
        if (customY != null && Double.isFinite(customY)) {
            double y = Math.max(0.0, Math.min(100.0, customY));
            String naturalLine = tacticalLineForNaturalPosition(naturalPosition);
            if (isNear(y, 34.0, 2.0)) {
                if ("ATT".equals(naturalLine) || "MID".equals(naturalLine)) {
                    return naturalLine;
                }
            }
            if (isNear(y, 67.0, 2.0)) {
                if ("MID".equals(naturalLine) || "DEF".equals(naturalLine)) {
                    return naturalLine;
                }
            }
            if (y < 34.0) return "ATT";
            if (y < 67.0) return "MID";
            return "DEF";
        }
        String category = com.footballmanager.domain.model.valueobject.FormationInferer.categoryFor(slot.subdivisionId());
        return (category == null || category.isBlank()) ? naturalPosition : category;
    }

private boolean isNear(double value, double pivot, double radius) {
        return Math.abs(value - pivot) <= radius;
    }

private String tacticalLineForNaturalPosition(String naturalPosition) {
        if (naturalPosition == null || naturalPosition.isBlank()) {
            return "";
        }
        return switch (naturalPosition.toUpperCase(Locale.ROOT)) {
            case "GK" -> "GK";
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> "DEF";
            case "MID", "CM", "CDM", "DM", "CAM", "AM", "LM", "RM" -> "MID";
            case "ATT", "ST", "CF", "LW", "RW", "WINGER" -> "ATT";
            default -> "";
        };
    }

private String diagnosticRoleLine(String role) {
        if (role == null || role.isBlank()) return "";
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "GK" -> "GK";
            case "LB", "CB", "RB", "LWB", "RWB" -> "DEF";
            case "CDM", "CM", "CAM", "LM", "RM" -> "MID";
            case "LW", "RW", "CF", "ST" -> "ATT";
            default -> "";
        };
    }

private FormationPosition diagnosticFormationPosition(ResolvedDiagnosticSlot slot) {
        if (slot == null) {
            return new FormationPosition(null, null, null, null, null, null);
        }
        return new FormationPosition(
            null,
            slot.role(),
            slot.xPercent(),
            slot.yPercent(),
            null,
            slot.subdivisionId());
    }

private String diagnosticSlotSide(ResolvedDiagnosticSlot slot) {
        if (slot == null) return "UNKNOWN";
        return assignmentSupport.matrixSlotSide(diagnosticFormationPosition(slot));
    }

private String safePosition(String position) {
        return (position == null || position.isBlank()) ? "MID" : position;
    }

private Integer intOr(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

private Double finiteOrNull(Double value) {
        if (value != null && Double.isFinite(value)) return value;
        return null;
    }

private record ResolvedDiagnosticSlot(
        String subdivisionId,
        String role,
        Double xPercent,
        Double yPercent,
        String source
    ) {}

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}


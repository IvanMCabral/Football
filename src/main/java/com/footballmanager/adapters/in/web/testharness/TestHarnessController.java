package com.footballmanager.adapters.in.web.testharness;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.testharness.dto.CareerSnapshotResponse;
import com.footballmanager.adapters.in.web.testharness.dto.CreateCustomCareerRequest;
import com.footballmanager.adapters.in.web.testharness.dto.CustomFixtureDTO;
import com.footballmanager.adapters.in.web.testharness.dto.PlayerSwapMatrixSummaryRequest;
import com.footballmanager.adapters.in.web.testharness.dto.PositionPixelMatrixSummaryRequest;
import com.footballmanager.adapters.in.web.testharness.dto.ReplayMatchRequest;
import com.footballmanager.adapters.in.web.testharness.dto.ResetRoundRequest;
import com.footballmanager.adapters.in.web.testharness.dto.RoleSlotImpactRequest;
import com.footballmanager.adapters.in.web.testharness.dto.ScenarioMatrixSummaryRequest;
import com.footballmanager.adapters.in.web.testharness.dto.InjectPlayerStatsRequest;
import com.footballmanager.adapters.in.web.testharness.dto.SetFormationRequest;
import com.footballmanager.adapters.in.web.testharness.dto.SetStyleRequest;
import com.footballmanager.adapters.in.web.testharness.dto.SubstitutionWhatIfRequest;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import com.footballmanager.domain.port.in.testharness.*;
import com.footballmanager.domain.port.in.testharness.CustomFixture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test-harness/career")
@Profile({"dev", "local", "test"})
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
@Slf4j
public class TestHarnessController {

    private final TestHarnessUseCase testHarnessUseCase;
    private final ControllerHelper controllerHelper;

    @PostMapping("/create-custom")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Map<String, Object>>> createCustom(
            @RequestBody CreateCustomCareerRequest request,
            Authentication authentication) {
        if (request == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "request body must not be null")));
        }
        if (request.leagueId() == null || request.leagueId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "leagueId must not be blank")));
        }
        if (request.teamId() == null || request.teamId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "teamId must not be blank")));
        }
        if (request.difficulty() == null || request.difficulty().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "difficulty must not be blank")));
        }
        if (request.gameSpeed() == null || request.gameSpeed().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "gameSpeed must not be blank")));
        }
        try {
            UUID.fromString(request.leagueId());
            UUID.fromString(request.teamId());
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "leagueId/teamId must be valid UUIDs: " + ex.getMessage())));
        }

        int teamsPerDivision = request.teamsPerDivision() != null
            ? request.teamsPerDivision()
            : 5;
        if (teamsPerDivision < 2) {
            return Mono.just(ResponseEntity.badRequest().body(
                Map.of("error", "teamsPerDivision must be >= 2 (got " + teamsPerDivision + ")")));
        }

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.createCustom(
                userId, request.leagueId(), request.teamId(),
                request.difficulty(), request.gameSpeed(), teamsPerDivision)
            .<ResponseEntity<Map<String, Object>>>map(career -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("careerId", career.getCareerId());
                body.put("userSessionTeamId", career.getUserSessionTeamId());
                body.put("totalRounds", career.getTournamentState().getTotalRounds());
                body.put("currentRound", career.getTournamentState().getCurrentRound());
                body.put("teamsPerDivision", teamsPerDivision);
                body.put("message", "Custom career created - squad is healthy");
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            });
    }

    @PostMapping("/replace-fixtures")
    public Mono<ResponseEntity<Map<String, Object>>> replaceFixtures(
            @RequestBody List<CustomFixtureDTO> fixturesDto,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        List<CustomFixture> specs = fixturesDto.stream()
            .map(dto -> new CustomFixture(
                dto.homeTeamId(),
                dto.awayTeamId(),
                dto.round(),
                dto.matchId() != null ? dto.matchId().toString() : null))
            .toList();

        return testHarnessUseCase.replaceFixtures(userId, specs)
            .<ResponseEntity<Map<String, Object>>>then(Mono.fromSupplier(() -> {
                int maxRound = specs.stream().mapToInt(CustomFixture::round).max().orElse(1);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("fixtureCount", specs.size());
                body.put("maxRound", maxRound);
                body.put("currentRound", 1);
                body.put("message", "Fixtures replaced - currentRound=1, totalRounds=" + maxRound);
                return ResponseEntity.ok(body);
            }));
    }

    @PostMapping("/reset-injuries")
    public Mono<ResponseEntity<Map<String, Object>>> resetInjuries(
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.resetInjuries(userId)
            .<ResponseEntity<Map<String, Object>>>then(Mono.fromSupplier(() -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("message", "Injury/suspension/yellow/red flags cleared across squad");
                return ResponseEntity.ok(body);
            }));
    }

    @PostMapping("/set-formation")
    public Mono<ResponseEntity<Map<String, Object>>> setFormation(
            @RequestBody SetFormationRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.setFormation(userId, request.formation())
            .<ResponseEntity<Map<String, Object>>>then(Mono.fromSupplier(() -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("formation", request.formation());
                body.put("message", "Formation persisted to SessionTeam AND teamStarting11Formation map");
                return ResponseEntity.ok(body);
            }));
    }

    @PostMapping("/set-style")
    public Mono<ResponseEntity<Map<String, Object>>> setStyle(
            @RequestBody SetStyleRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.setStyle(userId, request.style())
            .<ResponseEntity<Map<String, Object>>>then(Mono.fromSupplier(() -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("style", request.style());
                body.put("message", "Style persisted to SessionTeam; V24 engine will use it on next replay");
                return ResponseEntity.ok(body);
            }));
    }

    @PostMapping("/inject-player-stats")
    public Mono<ResponseEntity<Map<String, Object>>> injectPlayerStats(
            @RequestBody InjectPlayerStatsRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.injectPlayerStats(
                userId, request.playerId(),
                request.attack(), request.defense(),
                request.technique(), request.speed(),
                request.stamina(), request.mentality(),
                request.heightCm(),
                request.skillLevels())
            .<ResponseEntity<Map<String, Object>>>then(Mono.fromSupplier(() -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("playerId", request.playerId());
                body.put("message", "Player stats mutated; V24 engine will use them via aggregateAttackerStat/aggregateDefenderStat on next replay");
                return ResponseEntity.ok(body);
            }));
    }

    @GetMapping("/snapshot")
    public Mono<ResponseEntity<CareerSnapshotResponse>> snapshot(
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.snapshot(userId)
            .<ResponseEntity<CareerSnapshotResponse>>map(
                career -> ResponseEntity.ok(CareerSnapshotResponse.from(career)));
    }

    @PostMapping("/match/{matchId}/replay")
    public Mono<ResponseEntity<MatchFixture>> replayMatch(
            @PathVariable String matchId,
            @RequestBody(required = false) ReplayMatchRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Long seedOverride = (request != null) ? request.seed() : null;

        return testHarnessUseCase.replayMatch(userId, matchId, seedOverride)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/preview-summary")
    public Mono<ResponseEntity<MatchPreviewSummary>> previewSummary(
            @PathVariable String matchId,
            @RequestBody(required = false) ScenarioMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null)
            ? request.seedStart()
            : 12345L;
        int seedCount = (request != null && request.seedCount() != null)
            ? request.seedCount()
            : 5;
        String controlledTeamSide = (request != null) ? request.controlledTeamSide() : "USER";

        return testHarnessUseCase.runMatchPreviewSummary(
                userId, matchId, seedStart, seedCount, controlledTeamSide)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/lineup-diagnostic")
    public Mono<ResponseEntity<LineupDiagnostic>> lineupDiagnostic(
            @PathVariable String matchId,
            @RequestBody(required = false) ReplayMatchRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Long seedOverride = (request != null) ? request.seed() : null;

        return testHarnessUseCase.lineupDiagnostic(userId, matchId, seedOverride)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/formation-matrix")
    public Mono<ResponseEntity<List<FormationMatrixRow>>> formationMatrix(
            @PathVariable String matchId,
            @RequestBody(required = false) ReplayMatchRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Long seedOverride = (request != null) ? request.seed() : null;
        String controlledTeamSide = (request != null) ? request.controlledTeamSide() : null;

        return testHarnessUseCase.runFormationMatrix(userId, matchId, seedOverride, controlledTeamSide)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/formation-matrix/summary")
    public Mono<ResponseEntity<List<FormationMatrixSummaryRow>>> formationMatrixSummary(
            @PathVariable String matchId,
            @RequestBody(required = false) ScenarioMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;
        String controlledTeamSide = (request != null) ? request.controlledTeamSide() : null;

        return testHarnessUseCase.runFormationMatrixSummary(userId, matchId, seedStart, seedCount, controlledTeamSide)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/side-mirror-synthetic")
    public Mono<ResponseEntity<List<SideMirrorSyntheticLabRow>>> sideMirrorSyntheticLab(
            @RequestBody(required = false) ScenarioMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;

        return testHarnessUseCase.runSideMirrorSyntheticLab(userId, seedStart, seedCount)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/scenario-matrix")
    public Mono<ResponseEntity<List<ScenarioMatrixRow>>> scenarioMatrix(
            @PathVariable String matchId,
            @RequestBody(required = false) ReplayMatchRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Long seedOverride = (request != null) ? request.seed() : null;

        return testHarnessUseCase.runScenarioMatrix(userId, matchId, seedOverride)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/scenario-matrix/summary")
    public Mono<ResponseEntity<List<ScenarioMatrixSummaryRow>>> scenarioMatrixSummary(
            @PathVariable String matchId,
            @RequestBody(required = false) ScenarioMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;
        String scenarioGroup = (request != null) ? request.scenarioGroup() : null;
        String controlledTeamSide = (request != null) ? request.controlledTeamSide() : null;

        return testHarnessUseCase.runScenarioMatrixSummary(userId, matchId, seedStart, seedCount, scenarioGroup, controlledTeamSide)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/player-swap-matrix/summary")
    public Mono<ResponseEntity<PlayerSwapMatrixSummaryRow>> playerSwapMatrixSummary(
            @PathVariable String matchId,
            @RequestBody PlayerSwapMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;

        return testHarnessUseCase.runPlayerSwapMatrixSummary(
                userId,
                matchId,
                request != null ? request.starterPlayerId() : null,
                request != null ? request.benchPlayerId() : null,
                request != null ? request.slotId() : null,
                seedStart,
                seedCount,
                request != null ? request.controlledTeamSide() : null)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/substitution-what-if/summary")
    public Mono<ResponseEntity<SubstitutionWhatIfSummaryRow>> substitutionWhatIfSummary(
            @PathVariable String matchId,
            @RequestBody SubstitutionWhatIfRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;

        return testHarnessUseCase.runSubstitutionWhatIfSummary(
                userId,
                matchId,
                request != null ? request.playerOffId() : null,
                request != null ? request.playerOnId() : null,
                request != null ? request.minute() : null,
                seedStart,
                seedCount,
                request != null ? request.controlledTeamSide() : null)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/position-pixel-matrix/summary")
    public Mono<ResponseEntity<PositionPixelMatrixSummaryRow>> positionPixelMatrixSummary(
            @PathVariable String matchId,
            @RequestBody PositionPixelMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;

        return testHarnessUseCase.runPositionPixelMatrixSummary(
                userId,
                matchId,
                request != null ? request.playerId() : null,
                request != null ? request.targetXPercent() : null,
                request != null ? request.targetYPercent() : null,
                request != null ? request.deltaXPercent() : null,
                request != null ? request.deltaYPercent() : null,
                seedStart,
                seedCount,
                request != null ? request.controlledTeamSide() : null)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/role-slot-impact/summary")
    public Mono<ResponseEntity<List<RoleSlotImpactSummaryRow>>> roleSlotImpactSummary(
            @PathVariable String matchId,
            @RequestBody(required = false) RoleSlotImpactRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;

        return testHarnessUseCase.runRoleSlotImpactSummary(
                userId,
                matchId,
                request != null ? request.slotId() : null,
                request != null ? request.naturalPositions() : null,
                seedStart,
                seedCount,
                request != null ? request.controlledTeamSide() : null)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/reset-round")
    public Mono<ResponseEntity<Map<String, Object>>> resetRound(
            @RequestBody ResetRoundRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        log.info("reset-round userId={} roundId={}",
            userId, request.roundId());

        return testHarnessUseCase.resetRound(userId, request.roundId())
            .<ResponseEntity<Map<String, Object>>>then(Mono.fromSupplier(() -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("roundId", request.roundId());
                body.put("message", "Round reset - fixtures back to PENDING, engines evicted, V24 details cleared");
                return ResponseEntity.ok(body);
            }));
    }
}

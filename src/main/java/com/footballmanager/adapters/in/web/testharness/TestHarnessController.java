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
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase.CustomFixture;
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

/**
 *
 * <p>Base path: {@code /api/v1/test-harness/career}.
 *
 * <p><b>Profile-gated</b> to {@code dev | local | test} — the bean is not
 * registered in {@code prod}, so the endpoints return 404 (Spring's default
 * for unmapped paths) without any extra guard. This guarantees no exposure
 * to production traffic.
 *
 * <p><b>Auth:</b> same JWT path as {@code CareerCommandController} —
 * {@code controllerHelper.getUserId(authentication)}. The harness is for
 * profile-gate is the primary access control.
 *
 * <p><b>Endpoints:</b>
 * <ol>
 *   <li>{@code POST /create-custom} — wipe + start fresh career</li>
 *   <li>{@code POST /replace-fixtures} — overwrite tournament fixtures</li>
 *   <li>{@code POST /reset-injuries} — clear squad injury flags</li>
 *   <li>{@code POST /set-formation} — change user formation</li>
 *   <li>{@code GET /snapshot} — dump current state for pre/post diff</li>
 *   <li>{@code POST /match/{matchId}/replay} — re-simulate a single match
 *   <li>{@code POST /reset-round} — reset every fixture of a round back
 *       to PENDING, evict cached MatchSessions, clear V24 details.
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/test-harness/career")
@Profile({"dev", "local", "test"})
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
@Slf4j
public class TestHarnessController {

    private final TestHarnessUseCase testHarnessUseCase;
    private final ControllerHelper controllerHelper;

    /**
     * POST /api/v1/test-harness/career/create-custom
     * Wipes the existing career (if any) and starts a fresh one with
     * caller-controlled {@code leagueId}, {@code teamId}, {@code difficulty},
     * {@code gameSpeed}, {@code teamsPerDivision}. After start, automatically
     * clears any injury flags the new squad might inherit (defensive —
     * new squads should be pristine anyway).
     */
    @PostMapping("/create-custom")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Map<String, Object>>> createCustom(
            @RequestBody CreateCustomCareerRequest request,
            Authentication authentication) {

        //
        // Before this fix, an empty body ({}), null leagueId/teamId, or malformed
        // UUID strings propagated to StartCareerUseCaseImpl.start() which calls
        // UUID.fromString(null) → NPE → 500 Internal Server Error with the
        // confusing message "Cannot invoke \"String.length()\" because \"name\" is null"
        // The audit found this controller was the only sibling left with the bug;
        // setFormation / setStyle / injectPlayerStats / resetRound return 422
        // (mapped by GlobalExceptionHandler from IllegalArgumentException) because
        // the underlying UseCases already null-check their fields.
        //
        // Now we return 400 Bad Request with a structured error Map per the
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
        // leagueId/teamId must parse as valid UUIDs — StartCareerUseCaseImpl.start()
        // calls UUID.fromString(worldLeagueId) which NPEs on null AND throws
        // IllegalArgumentException on malformed strings. Catch the IAE early
        // so the client gets a clear 400 instead of a 500 (NPE on null) or
        // 422 (IAE mapped by GlobalExceptionHandler — inconsistent with siblings
        // and harder to debug from the frontend).
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
                body.put("message", "Custom career created — squad is healthy");
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            });
    }

    /**
     * POST /api/v1/test-harness/career/replace-fixtures
     * Overwrites the current tournament fixtures with a caller-provided list.
     * Resets {@code currentRound=1}, {@code finished=false},
     * {@code careerPhase=PRE_MATCH}, and rebuilds empty standings.
     */
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
                body.put("message", "Fixtures replaced — currentRound=1, totalRounds=" + maxRound);
                return ResponseEntity.ok(body);
            }));
    }

    /**
     * POST /api/v1/test-harness/career/reset-injuries
     * Clears injury/suspension/yellow/red flags across the entire squad
     * (not just the starting 11). Idempotent — safe to call on a healthy
     * squad.
     */
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

    /**
     * POST /api/v1/test-harness/career/set-formation
     * Changes the user team's formation. Persists to BOTH
     * {@code SessionTeam.formation} AND {@code teamStarting11Formation} map
     * (the V24 engine reads from the latter — sprint 1.7 regression fix).
     */
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

    /**
     * Changes the user team's tactical style (BALANCED, ATTACKING, DEFENSIVE,
     * COUNTER, POSSESSION). Persists to {@code SessionTeam.style}. The V24
     * engine reads this in {@code V24MatchContextFactory.build()} when no
     * explicit style is passed (test-harness replay path).
     *
     * Axis 3 (style effect) without having to use the live-match
     * {@code /match-engine/matches/{id}/style} endpoint.
     */
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

    /**
     * Mutates one SessionPlayer's stats in the persisted career. Null fields
     *
     * {@code heightCm} (Integer, [160, 210]) and {@code skillLevels}
     * (sparse {@code Map<PlayerSkill, Integer>}, each entry [0, 99]) to
     * absent/null = leave current value unchanged. See
     * {@link InjectPlayerStatsRequest} for the full spec.
     *
     * <p>Engine reads updated stats on next replay via {@code aggregateAttackerStat}
     * (DEF + GK avg of defense+mentality). Those feed
     * {@code formationOffensiveModifier} and {@code formationDefensiveModifier}
     * respectively. Mutating stats changes the formation effect magnitude.
     *
     * Axis 2 (player stats amplify formation effect).
     */
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

    @PostMapping("/labs/offensive-upgrade/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareOffensiveUpgradeLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareOffensiveUpgradeLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/offensive-upgrade/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreOffensiveUpgradeLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreOffensiveUpgradeLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/defensive-downgrade/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareDefensiveDowngradeLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareDefensiveDowngradeLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/defensive-downgrade/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreDefensiveDowngradeLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreDefensiveDowngradeLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/objective-contrast/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareObjectiveContrastLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareObjectiveContrastLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/objective-contrast/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreObjectiveContrastLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreObjectiveContrastLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-wide-defenders/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareWeakWideDefendersLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareWeakWideDefendersLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-wide-defenders/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreWeakWideDefendersLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreWeakWideDefendersLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-wide-defenders/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareOpponentWeakWideDefendersLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareOpponentWeakWideDefendersLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-wide-defenders/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreOpponentWeakWideDefendersLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreOpponentWeakWideDefendersLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-left-defender/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareOpponentWeakLeftDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareOpponentWeakLeftDefenderLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-left-defender/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreOpponentWeakLeftDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreOpponentWeakLeftDefenderLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-right-defender/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareOpponentWeakRightDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareOpponentWeakRightDefenderLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-right-defender/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreOpponentWeakRightDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreOpponentWeakRightDefenderLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-center-backs/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareOpponentWeakCenterBacksLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareOpponentWeakCenterBacksLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-center-backs/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreOpponentWeakCenterBacksLab(
            @PathVariable String matchId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreOpponentWeakCenterBacksLab(userId, matchId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-left-defender/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareWeakLeftDefenderLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareWeakLeftDefenderLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-left-defender/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreWeakLeftDefenderLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreWeakLeftDefenderLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-right-defender/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareWeakRightDefenderLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareWeakRightDefenderLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-right-defender/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreWeakRightDefenderLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreWeakRightDefenderLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-center-backs/prepare")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> prepareWeakCenterBacksLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.prepareWeakCenterBacksLab(userId)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/labs/weak-center-backs/restore")
    public Mono<ResponseEntity<TestHarnessUseCase.LabMutationResult>> restoreWeakCenterBacksLab(
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return testHarnessUseCase.restoreWeakCenterBacksLab(userId)
            .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/test-harness/career/snapshot
     * pre/post smoke diffs. Includes computed squad health summary.
     */
    @GetMapping("/snapshot")
    public Mono<ResponseEntity<CareerSnapshotResponse>> snapshot(
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);

        return testHarnessUseCase.snapshot(userId)
            .<ResponseEntity<CareerSnapshotResponse>>map(
                career -> ResponseEntity.ok(CareerSnapshotResponse.from(career)));
    }

    /**
     * Re-simulates a single match with a new seed. The matchId must
     * exist in the current tournament fixtures; the fixture is reset
     * to PENDING, re-simulated via the V24 engine, and the new result
     * is persisted (along with cache invalidation).
     *
     * <p>Body is optional. Pass {@code {"seed": 12345}} for a
     * reproducible replay. Without a body (or with {@code seed=null}),
     * the UseCase uses {@code System.currentTimeMillis()} (NOT
     * reproducible across runs).
     *
     * <p>Returns the updated {@link MatchFixture} in the body.
     */
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
    public Mono<ResponseEntity<TestHarnessUseCase.MatchPreviewSummary>> previewSummary(
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
    public Mono<ResponseEntity<TestHarnessUseCase.LineupDiagnostic>> lineupDiagnostic(
            @PathVariable String matchId,
            @RequestBody(required = false) ReplayMatchRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Long seedOverride = (request != null) ? request.seed() : null;

        return testHarnessUseCase.lineupDiagnostic(userId, matchId, seedOverride)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/formation-matrix")
    public Mono<ResponseEntity<List<TestHarnessUseCase.FormationMatrixRow>>> formationMatrix(
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
    public Mono<ResponseEntity<List<TestHarnessUseCase.FormationMatrixSummaryRow>>> formationMatrixSummary(
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
    public Mono<ResponseEntity<List<TestHarnessUseCase.SideMirrorSyntheticLabRow>>> sideMirrorSyntheticLab(
            @RequestBody(required = false) ScenarioMatrixSummaryRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        long seedStart = (request != null && request.seedStart() != null) ? request.seedStart() : 12345L;
        int seedCount = (request != null && request.seedCount() != null) ? request.seedCount() : 20;

        return testHarnessUseCase.runSideMirrorSyntheticLab(userId, seedStart, seedCount)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/scenario-matrix")
    public Mono<ResponseEntity<List<TestHarnessUseCase.ScenarioMatrixRow>>> scenarioMatrix(
            @PathVariable String matchId,
            @RequestBody(required = false) ReplayMatchRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Long seedOverride = (request != null) ? request.seed() : null;

        return testHarnessUseCase.runScenarioMatrix(userId, matchId, seedOverride)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/match/{matchId}/scenario-matrix/summary")
    public Mono<ResponseEntity<List<TestHarnessUseCase.ScenarioMatrixSummaryRow>>> scenarioMatrixSummary(
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
    public Mono<ResponseEntity<TestHarnessUseCase.PlayerSwapMatrixSummaryRow>> playerSwapMatrixSummary(
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
    public Mono<ResponseEntity<TestHarnessUseCase.SubstitutionWhatIfSummaryRow>> substitutionWhatIfSummary(
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
    public Mono<ResponseEntity<TestHarnessUseCase.PositionPixelMatrixSummaryRow>> positionPixelMatrixSummary(
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
    public Mono<ResponseEntity<List<TestHarnessUseCase.RoleSlotImpactSummaryRow>>> roleSlotImpactSummary(
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

    /**
     * Resets every fixture of a round back to PENDING, evicts the
     * cached {@code MatchSession} for each match from
     * {@code MatchEngineRegistry}, and clears the V24 detail entries
     * from Redis. After this call, {@code /match-engine/rounds/start}
     * with the same roundId will run a fresh V24 simulation (instead
     * of returning the cached completed result from the previous run).
     *
     * <p>Body: {@code {"roundId": "<uuid>"}} — the deterministic round
     * UUID hydrated by {@code /career/fixtures/round-with-bye} and
     * carried in {@code TestHarnessMatchRow.roundId}.
     *
     * <p>The frontend calls this RIGHT BEFORE
     * {@code /match-engine/rounds/start} so the {@code "Simulate round"}
     * button is idempotent.
     *
     * <p>Response: 200 OK with a small body summarising the reset
     * (roundId, fixturesReset, enginesRemoved, detailsCleared).
     */
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
                body.put("message", "Round reset — fixtures back to PENDING, engines evicted, V24 details cleared");
                return ResponseEntity.ok(body);
            }));
    }
}

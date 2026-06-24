package com.footballmanager.adapters.in.web.testharness;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.testharness.dto.CareerSnapshotResponse;
import com.footballmanager.adapters.in.web.testharness.dto.CreateCustomCareerRequest;
import com.footballmanager.adapters.in.web.testharness.dto.CustomFixtureDTO;
import com.footballmanager.adapters.in.web.testharness.dto.ReplayMatchRequest;
import com.footballmanager.adapters.in.web.testharness.dto.ResetRoundRequest;
import com.footballmanager.adapters.in.web.testharness.dto.SetFormationRequest;
import com.footballmanager.adapters.in.web.testharness.dto.SetStyleRequest;
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
 * V24D20-TESTHARNESS — REST controller exposing the smoke-test harness.
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
 * internal smoke use only (REVISOR + local exploration), so the
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
 *       with a new (caller-provided or auto) seed. V24D20-SANDBOX-V2-MVP.</li>
 *   <li>{@code POST /reset-round} — reset every fixture of a round back
 *       to PENDING, evict cached MatchSessions, clear V24 details.
 *       V24D24.3-HOTFIX. Makes {@code "Simulate round"} idempotent.</li>
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

        UUID userId = controllerHelper.getUserId(authentication);
        int teamsPerDivision = request.teamsPerDivision() != null
            ? request.teamsPerDivision()
            : 5;

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
     * V25D28: POST /api/v1/test-harness/career/set-style
     * Changes the user team's tactical style (BALANCED, ATTACKING, DEFENSIVE,
     * COUNTER, POSSESSION). Persists to {@code SessionTeam.style}. The V24
     * engine reads this in {@code V24MatchContextFactory.build()} when no
     * explicit style is passed (test-harness replay path).
     *
     * <p>Side-quest of V25D27 REVISOR smoke: enables empirical validation of
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
     * GET /api/v1/test-harness/career/snapshot
     * Returns the current career state — REVISOR uses this to verify
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
     * V24D20-SANDBOX-V2-MVP F5: POST /api/v1/test-harness/career/match/{matchId}/replay
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

    /**
     * V24D24.3-HOTFIX: POST /api/v1/test-harness/career/reset-round
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

        log.info("[V24D24.3-HOTFIX] reset-round userId={} roundId={}",
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

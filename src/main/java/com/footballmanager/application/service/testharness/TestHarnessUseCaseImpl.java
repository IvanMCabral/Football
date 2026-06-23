package com.footballmanager.application.service.testharness;

import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * V24D20-TESTHARNESS — Impl of {@link TestHarnessUseCase}.
 *
 * <p>All methods assume the caller has already been authenticated
 * (the controller uses {@code controllerHelper.getUserId(authentication)})
 * and is operating within a {@code dev|local|test} profile context.
 *
 * <p><b>Determinism guarantee:</b> when {@code app.simulation.random-seed}
 * is set to a fixed value (e.g. 42L), match simulation becomes reproducible
 * — same squads + same fixtures + same formation + same seed ⇒ same outcome.
 * This is what enables Bloque B (same match × N runs ⇒ similar results).
 *
 * <p><b>CareerSave manipulation pattern:</b> load via repository → mutate
 * state in-memory → {@code careerRepository.save(career)}. The save
 * pipeline re-serializes via Jackson so all Redis round-trip semantics are
 * preserved (no shape drift).
 */
@Service
@Profile({"dev", "local", "test"})
@Slf4j
@RequiredArgsConstructor
public class TestHarnessUseCaseImpl implements TestHarnessUseCase {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    // V24D20-SANDBOX-V2-MVP F5: replay endpoint dependencies
    private final V24MatchContextFactory v24ContextFactory;
    private final V24DetailedMatchStoragePort v24StoragePort;
    // V24D24.3-HOTFIX: resetRound needs to evict cached MatchSessions so
    // the next /match-engine/rounds/start call rebuilds the engine from
    // scratch (see MatchEngineRegistry.startEngine line 25-30 for the
    // guard we're working around).
    private final MatchEngineRegistry matchEngineRegistry;

    // ========== replaceFixtures ==========

    @Override
    public Mono<Void> replaceFixtures(UUID userId, List<CustomFixture> fixtures) {
        // V24D24.1 — BUG_TESTHARNESS_REPLACE_FIXTURES_REJECTS_EMPTY:
        // null guard stays (genuine client-error → 400), empty list is
        // treated as a no-op so the test-harness frontend can "skip
        // replacement" when the preset builder returns [] (F2 scope
        // placeholder). Without this, the smoke REVISOR cannot drive the
        // single-match preset path.
        if (fixtures == null) {
            return Mono.error(new IllegalArgumentException("fixtures must be a non-null list"));
        }
        if (fixtures.isEmpty()) {
            log.info("[V24D24.1] replaceFixtures userId={} no-op (empty list, 0 fixtures to replace)",
                userId);
            return Mono.empty();
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeReplaceFixtures(career, fixtures);
            });
    }

    private Mono<Void> executeReplaceFixtures(CareerSave career, List<CustomFixture> fixtures) {
        int maxRound = fixtures.stream().mapToInt(CustomFixture::round).max().orElse(1);

        List<MatchFixture> newFixtures = new ArrayList<>(fixtures.size());
        for (CustomFixture spec : fixtures) {
            String matchId = (spec.matchId() != null && !spec.matchId().isBlank())
                ? spec.matchId()
                : UUID.randomUUID().toString();
            newFixtures.add(new MatchFixture(
                matchId, spec.homeTeamId(), spec.awayTeamId(), spec.round()));
        }

        career.getTournamentState().setFixtures(newFixtures);
        career.getTournamentState().setCurrentRound(1);
        career.getTournamentState().setFinished(false);
        career.getTournamentState().setCareerPhase(CareerPhase.PRE_MATCH);
        career.getTournamentState().initializeStandings(career.getAllSessionTeams());
        // V24D20-SANDBOX-V2-MVP BUG #2: setTotalRounds is the LAST write so
        // any future side-effect on setFixtures / setCurrentRound /
        // setFinished / setCareerPhase / initializeStandings cannot clobber
        // it. The invariant is totalRounds == max(fixtures.round).
        career.getTournamentState().setTotalRounds(maxRound);

        log.info("[V24D20-TESTHARNESS] replaceFixtures userId={} count={} maxRound={}",
            career.getUserId(), fixtures.size(), maxRound);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate CareerSessionService cache
        // so the next getCareerFromCache(userId) returns the updated career,
        // not the stale in-memory copy. Without this, the V24 engine sees
        // the OLD fixtures.
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== resetInjuries ==========

    @Override
    public Mono<Void> resetInjuries(UUID userId) {
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeResetInjuries(career);
            });
    }

    private Mono<Void> executeResetInjuries(CareerSave career) {
        String userSessionTeamId = career.getUserSessionTeamId();
        List<SessionPlayer> squad = career.getTeamSquad(userSessionTeamId);

        int cleared = 0;
        for (SessionPlayer p : squad) {
            if (Boolean.TRUE.equals(p.getInjured())
                || Boolean.TRUE.equals(p.getSuspended())
                || (p.getYellowCards() != null && p.getYellowCards() > 0)
                || (p.getRedCards() != null && p.getRedCards() > 0)) {
                p.setInjured(false);
                p.setInjuryType(null);
                p.setInjuryRemainingMatches(0);
                p.setSuspended(false);
                p.setSuspensionRemainingMatches(0);
                p.setYellowCards(0);
                p.setRedCards(0);
                cleared++;
            }
        }

        log.info("[V24D20-TESTHARNESS] resetInjuries userId={} squadSize={} cleared={}",
            career.getUserId(), squad.size(), cleared);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate cache after save
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== setFormation ==========

    @Override
    public Mono<Void> setFormation(UUID userId, String formation) {
        if (formation == null || formation.isBlank()) {
            return Mono.error(new IllegalArgumentException("formation must be non-blank"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeSetFormation(career, formation);
            });
    }

    /**
     * V24D20-TESTHARNESS — persist formation to BOTH the {@code SessionTeam.formation}
     * field AND the {@code teamStarting11Formation} map.
     *
     * <p>CRITICAL: the V24 engine reads formation from
     * {@code career.getTeamStarting11Formation().get(userSessionTeamId)} —
     * NOT from {@code sessionTeam.getFormation()}. The 1.7 sprint regression
     * (BUG_FORMATION_PERSIST_IGNORED) was caused by writing only to
     * {@code SessionTeam.formation} (the simulation never picked it up).
     *
     * <p>Both writes must succeed for the smoke harness to drive formation
     * changes that the engine actually respects.
     */
    private Mono<Void> executeSetFormation(CareerSave career, String formation) {
        String userSessionTeamId = career.getUserSessionTeamId();

        SessionTeam userTeam = career.getSessionTeam(userSessionTeamId);
        if (userTeam == null) {
            return Mono.error(new IllegalStateException(
                "User session team not found: " + userSessionTeamId));
        }
        userTeam.setFormation(formation);

        // CRITICAL: the V24 engine reads from this map, not from
        // SessionTeam.formation. Setting only the SessionTeam was the
        // BUG_FORMATION_PERSIST_IGNORED root cause in sprint 1.7.
        career.getTeamStarting11Formation().put(userSessionTeamId, formation);

        log.info("[V24D20-TESTHARNESS] setFormation userId={} team={} formation={}",
            career.getUserId(), userSessionTeamId, formation);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate cache after save
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== createCustom ==========

    @Override
    public Mono<CareerSave> createCustom(UUID userId, String worldLeagueId, String worldTeamId,
                                          String difficulty, String gameSpeed, int teamsPerDivision) {
        if (teamsPerDivision < 2) {
            return Mono.error(new IllegalArgumentException(
                "teamsPerDivision must be >= 2 (got " + teamsPerDivision + ")"));
        }

        log.info("[V24D20-TESTHARNESS] createCustom userId={} league={} team={} "
                + "difficulty={} gameSpeed={} teamsPerDivision={}",
            userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision);

        return careerSessionService.deleteCareer(userId)
            .then(careerSessionService.startNewCareer(
                userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision))
            .flatMap(career -> careerRepository.findById(userId.toString())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Mono.just(career);
                    }
                    return executeResetInjuries(opt.get())
                        .thenReturn(opt.get());
                }));
    }

    // ========== snapshot ==========

    @Override
    public Mono<CareerSave> snapshot(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.just(optionalCareer.get());
            });
    }

    // ========== replayMatch (V24D20-SANDBOX-V2-MVP F5) ==========

    @Override
    public Mono<MatchFixture> replayMatch(UUID userId, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : System.currentTimeMillis();

        log.info("[V24D20-SANDBOX-V2-MVP] replayMatch userId={}, matchId={}, seed={}",
            userId, matchId, seed);

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeReplayMatch(career, matchId, seed);
            });
    }

    private Mono<MatchFixture> executeReplayMatch(CareerSave career, String matchId, long seed) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        // 1. Reset the fixture to PENDING (was COMPLETED from the original
        // simulation). The new V24 simulation will set it back to COMPLETED
        // via fixture.complete() below.
        fixture.reset();

        // 2. Build the V24 context and re-simulate.
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            return Mono.error(new IllegalStateException(
                "SessionTeam not found for match " + matchId
                + " (home=" + fixture.getHomeTeamId()
                + ", away=" + fixture.getAwayTeamId() + ")"));
        }

        V24MatchContext context = v24ContextFactory.build(career, fixture, home, away, seed);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(context, new Random(seed));

        // 3. Update the fixture with the new result. possession and shots
        // default to 0 in the replay path — the V24 detail endpoint exposes
        // the rich per-minute data for full breakdowns.
        MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
            result.homeGoals(), result.awayGoals(), 0, 0, 0, 0);
        fixture.complete(resultData);

        // 4. Update standings with the new result. This will double-count if
        // the original result was already applied (the standings were
        // updated when the match first finished). Known limitation of MVP
        // replay — the manager should reset-injuries + replace-fixtures to
        // get a clean state if standings correctness is required.
        career.getTournamentState().updateStandingsWithResult(fixture);

        // 5. Best-effort: clear the old V24 detail from Redis so the next
        // GET /detail returns the new result, not the old one.
        try {
            String careerId = career.getData().getCareerId();
            v24StoragePort.deleteByMatchId(careerId, matchId);
        } catch (Exception e) {
            log.warn("[V24D20-SANDBOX-V2-MVP] replayMatch: failed to clear old V24 detail "
                + "for matchId={}, continuing (replay is best-effort): {}",
                matchId, e.getMessage());
        }

        // V24D21-SANDBOX-V2-MVP-F7 (BUG_REPLAY_NO_PERSIST): persist the NEW
        // V24 detail built from the re-simulation result. Without this, the
        // existing deleteByMatchId above leaves Redis empty and the next
        // GET /api/v1/careers/{careerId}/matches/{matchId}/detail returns
        // 404 — blocking the "what-if" smoke (replay with a changed
        // formation needs the new timeline / shot map / xG to compare
        // against the original).
        //
        // Mirrors LeagueSimulator.persistV24Detail() — same factory call
        // (V24DetailedMatchData.fromResult) and same storage port.
        // Player ratings are passed empty: the assembler lives inside
        // LeagueSimulator and replay currently has no per-player rating
        // derivation. This is a known limitation; a follow-up sprint
        // should extract V24PlayerRatingsAssembler so replay can reuse it.
        // Best-effort: a Redis failure logs a warning but does NOT fail
        // the replay — the fixture result is still saved to MongoDB and
        // the manager can re-run replay once Redis recovers.
        try {
            String careerId = career.getData().getCareerId();
            Integer seasonNumber = career.getCurrentSeason();
            Integer round = fixture.getRound();
            String homeTeamName = home.getName() != null ? home.getName() : "";
            String awayTeamName = away.getName() != null ? away.getName() : "";
            // V24D24-F1.2: capture formations from the SessionTeam at replay
            // time so the persisted detail reflects what formation was active
            // when the replay ran. Falls back to null for "—" in UI.
            String homeFormation = home.getFormation();
            String awayFormation = away.getFormation();

            V24DetailedMatchData newDetail = V24DetailedMatchData.fromResult(
                careerId,
                seasonNumber,
                round,
                homeTeamName,
                awayTeamName,
                homeFormation,
                awayFormation,
                result,
                List.<V24PlayerMatchRatingDto>of()
            );

            v24StoragePort.save(careerId, newDetail);
            log.info("[V24D21-SANDBOX-V2-MVP] replayMatch: persisted new V24 detail "
                + "for matchId={}, careerId={}, homeGoals={}, awayGoals={}",
                matchId, careerId, result.homeGoals(), result.awayGoals());
        } catch (Exception e) {
            log.warn("[V24D21-SANDBOX-V2-MVP] replayMatch: failed to persist new V24 "
                + "detail for matchId={}, continuing (replay is best-effort): {}",
                matchId, e.getMessage());
        }

        log.info("[V24D20-SANDBOX-V2-MVP] replayMatch complete: matchId={}, "
            + "newResult=({}-{}), seed={}",
            matchId, result.homeGoals(), result.awayGoals(), seed);

        // 6. Persist + invalidate cache (same pattern as the other endpoints)
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(fixture);
    }

    // ========== resetRound (V24D24.3-HOTFIX) ==========

    @Override
    public Mono<Void> resetRound(UUID userId, String roundId) {
        if (roundId == null || roundId.isBlank()) {
            return Mono.error(new IllegalArgumentException("roundId is required and must be non-blank"));
        }
        log.info("[V24D24.3-HOTFIX] resetRound userId={}, roundId={}",
            userId, roundId);

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeResetRound(career, roundId);
            });
    }

    /**
     * V24D24.3-HOTFIX core implementation. Resets every fixture of the
     * given round so the next {@code /match-engine/rounds/start} call
     * runs a fresh V24 simulation. Concretely, for every fixture in the
     * round we:
     *
     * <ol>
     *   <li>Call {@code fixture.reset()} — clears status=COMPLETED →
     *       PENDING and nulls the result. Without this step,
     *       {@code MatchFixture.startSimulation} (line 88-93) throws
     *       "Match can only start from PENDING state".</li>
     *   <li>Call {@code matchEngineRegistry.stopAndRemoveEngine} — evicts
     *       the cached {@code MatchSession} so the next
     *       {@code engineRegistry.startEngine} call rebuilds from
     *       scratch. Without this step, the registry returns the old
     *       session (which has already finished) and the new
     *       simulation never starts.</li>
     *   <li>Call {@code v24StoragePort.deleteByMatchId} — clears the V24
     *       detail (timeline / shot map / xG) from Redis so the next
     *       {@code GET /detail} returns the new simulation's events,
     *       not the old one.</li>
     * </ol>
     *
     * <p>The reverse-update is deliberately omitted: re-running the same
     * round does not change career-level standings here (the smoke
     * harness accepts that "standings drift" is the cost of true
     * determinism; REVISOR can call {@code replace-fixtures} for a
     * clean state if needed). This matches the documented limitation
     * of {@code replayMatch}.
     */
    private Mono<Void> executeResetRound(CareerSave career, String roundId) {
        String careerId = career.getCareerId();
        int totalRounds = career.getTournamentState().getTotalRounds();
        int round = deriveRoundFromUuid(roundId, careerId, totalRounds);
        if (round < 1) {
            return Mono.error(new IllegalArgumentException(
                "roundId " + roundId + " does not match any round of career " + careerId
                + " (1.." + totalRounds + ")"));
        }

        List<MatchFixture> roundFixtures = new ArrayList<>();
        for (MatchFixture f : career.getTournamentState().getFixtures()) {
            if (f.getRound() == round) {
                roundFixtures.add(f);
            }
        }

        if (roundFixtures.isEmpty()) {
            return Mono.error(new IllegalStateException(
                "No fixtures found for round " + round
                + " (career has " + career.getTournamentState().getFixtures().size()
                + " fixtures across " + totalRounds + " rounds)"));
        }

        int resetCount = 0;
        int removedEngines = 0;
        int clearedDetails = 0;
        UUID userId = career.getUserId();

        for (MatchFixture fixture : roundFixtures) {
            String matchId = fixture.getMatchId();
            boolean wasCompleted = fixture.isCompleted();
            fixture.reset();
            resetCount++;

            // Stop & remove the cached MatchSession so the next
            // /match-engine/rounds/start gets a fresh engine.
            try {
                if (matchEngineRegistry.hasEngine(userId, UUID.fromString(matchId))) {
                    matchEngineRegistry.stopAndRemoveEngine(userId, UUID.fromString(matchId));
                    removedEngines++;
                }
            } catch (Exception e) {
                log.warn("[V24D24.3-HOTFIX] resetRound: failed to remove engine for matchId={}: {}",
                    matchId, e.getMessage());
            }

            // Clear the old V24 detail from Redis (best-effort — a
            // Redis failure logs a warning but does NOT fail the reset,
            // so a stale Redis state is preferable to blocking the
            // smoke flow).
            try {
                v24StoragePort.deleteByMatchId(careerId, matchId);
                clearedDetails++;
            } catch (Exception e) {
                log.warn("[V24D24.3-HOTFIX] resetRound: failed to clear V24 detail for matchId={}: {}",
                    matchId, e.getMessage());
            }

            log.info("[V24D24.3-HOTFIX] resetRound matchId={} round={} wasCompleted={}",
                matchId, fixture.getRound(), wasCompleted);
        }

        // V24D24.3-HOTFIX (BUG_ORCHESTRATOR_SKIPS_NON_CURRENT_ROUND):
        // The MatchSimulationOrchestrator.processResultsInternal early-returns
        // when `firstFixture.getRound() != careerCurrentRound` (line 126-128 of
        // MatchSimulationOrchestrator.java). After running several rounds, the
        // career's `currentRound` may be 4 while the manager is now re-simulating
        // round 1 — the orchestrator would silently skip the result-persistence
        // and the fixtures would stay PENDING in Mongo (despite the V24 detail
        // being updated in Redis).
        //
        // Fix: rewind `currentRound` to the round being re-simulated so the
        // orchestrator's round-equality guard passes. The test-harness is the
        // ONLY caller of this method (it's @Profile-gated) and the manager
        // accepts the "rewind" cost (it just means the next natural advance
        // step will be `round + 1` instead of whatever the previous current
        // was — the smoke accepts that the tournament may finish earlier than
        // the stored totalRounds on a rewind-reset).
        int previousCurrentRound = career.getTournamentState().getCurrentRound();
        if (previousCurrentRound != round) {
            log.info("[V24D24.3-HOTFIX] resetRound rewinding currentRound: {} -> {} "
                + "(orchestrator only processes currentRound={} matchResults)",
                previousCurrentRound, round, round);
            career.getTournamentState().setCurrentRound(round);
        }
        // Force the career back into PRE_MATCH so the round engine is allowed
        // to start a new round. (The orchestrator would normally set
        // careerPhase=WAITING_USER after a round finishes, blocking a re-run.)
        career.getTournamentState().setCareerPhase(
            com.footballmanager.domain.model.entity.CareerPhase.PRE_MATCH);

        log.info("[V24D24.3-HOTFIX] resetRound complete careerId={} roundId={} round={} resetFixtures={} removedEngines={} clearedDetails={} rewoundFrom={}",
            careerId, roundId, round, resetCount, removedEngines, clearedDetails, previousCurrentRound);

        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    /**
     * V24D24.3-HOTFIX: roundId is a deterministic UUID derived from
     * (careerId, round) via {@code FixtureQueryHelper.deriveRoundId}.
     * Since we don't have a direct lookup index for roundId, we
     * recover the round number by enumerating all possible rounds and
     * matching the UUID. With {@code totalRounds <= 38} (typical
     * tournament) this is cheap.
     *
     * <p>If we ever extend the tournament beyond 38 rounds, this should
     * be replaced with a direct UUID→round index or by storing the
     * roundId on the fixture.
     *
     * @return 1-based round number, or -1 if no match found
     */
    private int deriveRoundFromUuid(String roundId, String careerId, int totalRounds) {
        try {
            UUID target = UUID.fromString(roundId);
            for (int r = 1; r <= totalRounds; r++) {
                String candidate = com.footballmanager.application.service.query.FixtureQueryHelper
                    .deriveRoundId(careerId, r);
                if (candidate != null && candidate.equals(target.toString())) {
                    return r;
                }
            }
            return -1;
        } catch (Exception e) {
            log.warn("[V24D24.3-HOTFIX] deriveRoundFromUuid failed for roundId={}, careerId={}: {}",
                roundId, careerId, e.getMessage());
            return -1;
        }
    }
}

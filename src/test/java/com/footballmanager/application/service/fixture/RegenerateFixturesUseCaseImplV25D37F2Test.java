package com.footballmanager.application.service.fixture;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.ContinueCareerUseCase;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.service.FixtureGenerator;
import com.footballmanager.domain.service.FixtureGenerator.FixtureRound;
import com.footballmanager.domain.service.FixtureGenerator.FixtureSlot;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V25D37-F2: regression test for BUG_FIXTURES_CACHE_STALE.
 *
 * <p>Before the fix, {@code RegenerateFixturesUseCaseImpl.executeRegenerate} mutated
 * the in-memory {@code CareerSave.fixtures} and persisted it via
 * {@code careerRepository.save(career)} — but it never invalidated the
 * in-memory cache held by {@link CareerSessionService}. A subsequent
 * {@code GET /api/v1/career/fixtures} call hit {@code getCareerFromCache(userId)}
 * and returned the STALE pre-regenerate {@code CareerSave} with the OLD fixtures.
 *
 * <p>This bug is structurally identical to V24D20-SANDBOX-V2-MVP BUG #1 (the
 * original report from {@code TestHarnessUseCaseImpl.executeReplaceFixtures}),
 * but the {@code regenerateFixtures} use-case was missed in that round.
 *
 * <p>The fix chains {@code careerSessionService.invalidateCache(userId)} AFTER
 * {@code careerRepository.save(career)} — same pattern as
 * {@code TestHarnessUseCaseImpl} applies for replaceFixtures / resetInjuries /
 * setFormation / etc.
 */
@ExtendWith(MockitoExtension.class)
class RegenerateFixturesUseCaseImplV25D37F2Test {

    @Mock
    private CareerRepository careerRepository;

    @Mock
    private FixtureGenerator fixtureGenerator;

    @Mock
    private StartCareerUseCase startCareerUseCase;

    @Mock
    private ContinueCareerUseCase continueCareerUseCase;

    @Mock
    private RoundEngineRegistry roundEngineRegistry;

    @Mock
    private MatchSessionRegistry matchSessionRegistry;

    @Test
    @DisplayName("V25D37-F2: regenerate invalidates CareerSessionService cache after save")
    void regenerateInvalidatesCareerSessionCache() {
        // Stub the repo FIRST so any subsequent call (including the
        // saveCareer() pre-population below) goes through a defined Mono,
        // not the Mockito default-null return.
        when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

        // GIVEN: a real CareerSessionService (uses its internal ConcurrentHashMap
        // careerCache) and a CareerSave that will be returned by the repo.
        CareerSessionService realCareerSessionService = new CareerSessionService(
                careerRepository, startCareerUseCase, continueCareerUseCase,
                roundEngineRegistry, matchSessionRegistry);

        UUID userId = UUID.randomUUID();
        CareerSave career = buildCareerWithTwoTeams(userId);

        // Pre-populate the in-memory cache with a STALE CareerSave (the one the
        // user would have read BEFORE regenerate). getCareerFromCache would
        // return this if invalidateCache weren't called.
        CareerSave stale = buildCareerWithTwoTeams(userId);
        stale.getTournamentState().setFixtures(List.of(
                new MatchFixture("STALE-MATCH-1", "team-a", "team-b", 1)));
        realCareerSessionService.saveCareer(stale).block();
        assertEquals(1, realCareerSessionService.getCacheSize(),
                "Pre-condition: cache must contain the stale CareerSave");

        // Stub the repo: findById returns the fresh career.
        when(careerRepository.findById(userId.toString()))
                .thenReturn(Mono.just(Optional.of(career)));

        // Stub the FixtureGenerator to return 1 round with 1 slot (team-a vs team-b)
        // so executeRegenerate produces a deterministic fixture set.
        TeamId teamIdA = TeamId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        TeamId teamIdB = TeamId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
        when(fixtureGenerator.generate(any(), anyBoolean()))
                .thenReturn(List.of(new FixtureRound(1,
                        List.of(new FixtureSlot(teamIdA, teamIdB)), false)));

        RegenerateFixturesUseCaseImpl useCase =
                new RegenerateFixturesUseCaseImpl(careerRepository, fixtureGenerator,
                        realCareerSessionService);

        // WHEN: regenerate runs and completes
        StepVerifier.create(useCase.regenerate(userId))
                .verifyComplete();

        // THEN 1: save was called exactly twice (once for pre-population + once for regenerate)
        //         — what we actually care about is that the regenerate path completed.
        verify(careerRepository, atLeast(1)).save(any(CareerSave.class));

        // THEN 2: cache is EMPTY after regenerate — the stale entry was evicted.
        //         Without the V25D37-F2 fix the cache would still hold the
        //         pre-regenerate CareerSave (the BUG_FIXTURES_CACHE_STALE symptom).
        assertEquals(0, realCareerSessionService.getCacheSize(),
                "V25D37-F2: CareerSessionService cache must be empty after regenerate, "
              + "otherwise GET /fixtures would return the stale pre-regenerate CareerSave");
    }

    @Test
    @DisplayName("V25D37-F2: regenerate persists BEFORE invalidating cache (fail-safe ordering)")
    void regenerateSavesBeforeInvalidating() {
        // Verifies the save → invalidate ordering documented in
        // V24D20-SANDBOX-V2-MVP BUG #1: if save fails, the cache must not be
        // invalidated (otherwise we lose both the new state AND the cached copy).

        // Stub save to fail so we can assert the cache is NOT invalidated.
        when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.error(new RuntimeException("simulated redis failure")));

        CareerSessionService realCareerSessionService = new CareerSessionService(
                careerRepository, startCareerUseCase, continueCareerUseCase,
                roundEngineRegistry, matchSessionRegistry);

        UUID userId = UUID.randomUUID();
        CareerSave career = buildCareerWithTwoTeams(userId);

        // Pre-populate cache so we can detect any premature invalidation.
        CareerSave preExisting = buildCareerWithTwoTeams(userId);
        // saveCareer will also fail here (same stub), but its internal
        // doOnSuccess is never reached because save() errored — so the cache
        // stays empty for this pre-population step. To still get a cached
        // entry we use saveCareer's thenReturn(career) but doOnSuccess is
        // not invoked on error path. So we manually invoke the public
        // getCareerFromCache after stubbing continueCareerUseCase.
        when(continueCareerUseCase.continueCareer(userId))
                .thenReturn(Mono.just(preExisting));
        // Force a cache entry via getCareerFromCache (cache miss → continueCareer → cache.put)
        realCareerSessionService.getCareerFromCache(userId).block();
        assertEquals(1, realCareerSessionService.getCacheSize(),
                "Pre-condition: cache must contain the pre-existing CareerSave");

        when(careerRepository.findById(userId.toString()))
                .thenReturn(Mono.just(Optional.of(career)));

        TeamId teamIdA = TeamId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        TeamId teamIdB = TeamId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
        when(fixtureGenerator.generate(any(), anyBoolean()))
                .thenReturn(List.of(new FixtureRound(1,
                        List.of(new FixtureSlot(teamIdA, teamIdB)), false)));

        RegenerateFixturesUseCaseImpl useCase =
                new RegenerateFixturesUseCaseImpl(careerRepository, fixtureGenerator,
                        realCareerSessionService);

        // The use-case wraps the whole chain in .onErrorResume(Mono.empty())
        // (see regenerate()), so the user-facing Mono completes even though
        // the internal save fails.
        StepVerifier.create(useCase.regenerate(userId))
                .verifyComplete();

        // Cache must STILL contain the pre-existing entry — invalidate must
        // not run when save fails.
        assertEquals(1, realCareerSessionService.getCacheSize(),
                "V25D37-F2: if save fails, cache must NOT be invalidated "
              + "(save-before-invalidate ordering)");
    }

    /**
     * Build a minimal CareerSave with 2 teams + a TournamentState, sufficient
     * for {@link RegenerateFixturesUseCaseImpl#regenerate(UUID)} to read
     * {@code career.getAllSessionTeams()} and {@code career.getTournamentState()}.
     */
    private CareerSave buildCareerWithTwoTeams(UUID userId) {
        CareerSave career = new CareerSave();
        career.setUserId(userId);

        SessionTeam teamA = SessionTeam.fromRealTeam(
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                "world_team_a", "Team Alpha", "Argentina",
                BigDecimal.ZERO, "4-3-3", null);
        teamA.setSessionTeamId("00000000-0000-0000-0000-00000000000a");

        SessionTeam teamB = SessionTeam.fromRealTeam(
                UUID.fromString("00000000-0000-0000-0000-00000000000b"),
                "world_team_b", "Team Bravo", "Argentina",
                BigDecimal.ZERO, "4-3-3", null);
        teamB.setSessionTeamId("00000000-0000-0000-0000-00000000000b");

        career.getTeamManager().addSessionTeam(teamA);
        career.getTeamManager().addSessionTeam(teamB);

        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(2);
        career.setTournamentState(ts);

        return career;
    }
}

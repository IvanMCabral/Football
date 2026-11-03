package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvanceRoundUseCaseImplTest {
    private CareerSave career;
    private CareerSessionService sessions;
    private AdvanceRoundUseCaseImpl useCase;

    @BeforeEach
    void setup() {
        career = new CareerSave();
        career.setUserId(UUID.randomUUID());
        career.getData().setCareerId(UUID.randomUUID().toString());
        career.setLifecycleGeneration("captured-generation");
        career.getTournamentState().setCurrentRound(21);
        career.getTournamentState().setTotalRounds(38);
        career.getTournamentState().setCareerPhase(CareerPhase.WAITING_USER);
        sessions = mock(CareerSessionService.class);
        when(sessions.getCareerFromCache(career.getUserId())).thenReturn(Mono.just(career));
        when(sessions.saveCareer(career)).thenReturn(Mono.just(career));
        useCase = new AdvanceRoundUseCaseImpl(sessions, new CareerLifecycleCoordinator(Duration.ofSeconds(5)));
    }

    @Test
    void preparesTheCanonicalCurrentRoundAndMarksTheSnapshotDirtyBeforeSaving() {
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> {
                assertTrue(result.success());
                assertEquals(21, result.currentRound());
                assertEquals("PRE_MATCH", result.careerPhase());
                assertEquals("captured-generation", career.getLifecycleGeneration());
            }).verifyComplete();
        var order = inOrder(sessions);
        order.verify(sessions).getCareerFromCache(career.getUserId());
        order.verify(sessions).markDirty(career.getUserId());
        order.verify(sessions).saveCareer(career);
    }

    @Test
    void alreadyPreparedDateDoesNotWriteAgain() {
        career.getTournamentState().setCareerPhase(CareerPhase.PRE_MATCH);
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertTrue(result.success())).verifyComplete();
        verify(sessions, never()).saveCareer(any());
        verify(sessions, never()).markDirty(any());
    }

    @Test
    void cachedSnapshotStillPersistsThePhaseWithItsCapturedGeneration() {
        var repository = mock(com.footballmanager.domain.model.repository.CareerRepository.class);
        when(repository.saveExistingCareer(any(), same(career))).thenReturn(Mono.empty());
        var coordinator = new CareerLifecycleCoordinator(Duration.ofSeconds(5));
        var realSessions = new CareerSessionService(repository,
            mock(com.footballmanager.domain.port.in.career.StartCareerUseCase.class),
            mock(com.footballmanager.domain.port.in.career.ContinueCareerUseCase.class),
            mock(com.footballmanager.application.engine.round.RoundEngineRegistry.class),
            mock(com.footballmanager.application.service.match.session.MatchSessionRegistry.class),
            mock(com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository.class), coordinator);
        realSessions.cacheCareer(career);
        var cachedUseCase = new AdvanceRoundUseCaseImpl(realSessions, coordinator);
        StepVerifier.create(cachedUseCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertTrue(result.success())).verifyComplete();
        verify(repository).saveExistingCareer(argThat(context ->
            context.ownerId().equals(career.getUserId()) &&
            context.careerId().equals(career.getCareerId()) &&
            context.expectedGeneration().equals("captured-generation")), same(career));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void overlappingClicksWaitForTheFirstDurableWriteAndDoNotWriteTwice() {
        Sinks.One<CareerSave> persisted = Sinks.one();
        when(sessions.saveCareer(career)).thenReturn(persisted.asMono());
        var first = useCase.advanceToNextRound(career.getUserId(), career.getCareerId());
        var second = useCase.advanceToNextRound(career.getUserId(), career.getCareerId());
        StepVerifier.create(Mono.zip(first, second))
            .then(() -> {
                verify(sessions, times(1)).getCareerFromCache(career.getUserId());
                persisted.tryEmitValue(career);
            }).assertNext(results -> {
                assertTrue(results.getT1().success());
                assertTrue(results.getT2().success());
            }).verifyComplete();
        verify(sessions, times(1)).saveCareer(career);
        assertEquals(21, career.getTournamentState().getCurrentRound());
    }

    @ParameterizedTest
    @EnumSource(value = CareerPhase.class, names = {"PRE_MATCH", "WAITING_USER"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsLiveAndFinishedPhasesWithoutMutatingThem(CareerPhase phase) {
        career.getTournamentState().setCareerPhase(phase);
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertFalse(result.success())).verifyComplete();
        assertEquals(phase, career.getTournamentState().getCareerPhase());
        verify(sessions, never()).saveCareer(any());
        verify(sessions, never()).markDirty(any());
    }

    @Test
    void rejectsAStaleCareerRouteWithoutPreparingTheReplacementCareer() {
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), UUID.randomUUID().toString()))
            .assertNext(result -> assertFalse(result.success())).verifyComplete();
        assertEquals(CareerPhase.WAITING_USER, career.getTournamentState().getCareerPhase());
        verify(sessions, never()).saveCareer(any());
    }

    @Test
    void missingCareerHasAnExplicitFailureBody() {
        when(sessions.getCareerFromCache(career.getUserId())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertFalse(result.success())).verifyComplete();
    }

    @Test
    void failedPersistenceRestoresTheWaitingPhaseSoRetryStillSaves() {
        when(sessions.saveCareer(career)).thenReturn(Mono.error(new IllegalStateException("offline")), Mono.just(career));
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertFalse(result.success())).verifyComplete();
        assertEquals(CareerPhase.WAITING_USER, career.getTournamentState().getCareerPhase());
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertTrue(result.success())).verifyComplete();
        verify(sessions, times(2)).saveCareer(career);
    }

    @Test
    void finishedFlagPreventsPreparingAnInconsistentLegacyPhase() {
        career.getTournamentState().setFinished(true);
        StepVerifier.create(useCase.advanceToNextRound(career.getUserId(), career.getCareerId()))
            .assertNext(result -> assertFalse(result.success())).verifyComplete();
        verify(sessions, never()).saveCareer(any());
    }
}

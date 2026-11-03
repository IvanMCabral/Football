package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.CareerPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CareerStatusLiveRoundTest {
    private CareerSave career;
    private RoundEngineRegistry registry;
    private GetCareerStatusUseCaseImpl status;

    @BeforeEach
    void setup() {
        career = new CareerSave();
        career.setUserId(UUID.randomUUID());
        career.getData().setCareerId(UUID.randomUUID().toString());
        career.setLifecycleGeneration("current-generation");
        career.getTournamentState().setCareerPhase(CareerPhase.PRE_MATCH);
        var sessions = mock(CareerSessionService.class);
        when(sessions.getCareerFromCache(career.getUserId())).thenReturn(Mono.just(career));
        registry = new RoundEngineRegistry();
        status = new GetCareerStatusUseCaseImpl(sessions, registry);
    }

    @Test
    void ownRunningRoundOverridesThePreparedSnapshotWithoutMutatingIt() {
        active(career.getUserId(), career.getCareerId(), "current-generation", false, false);
        StepVerifier.create(status.execute(career.getUserId())).assertNext(s -> {
            assertEquals("IN_MATCH", s.careerPhase()); assertEquals("RUNNING", s.engineStatus());
            assertFalse(s.canAdvanceRound());
        }).verifyComplete();
        assertEquals(CareerPhase.PRE_MATCH, career.getTournamentState().getCareerPhase());
    }

    @Test
    void pausedRoundStillPreventsAdvancement() {
        active(career.getUserId(), career.getCareerId(), "current-generation", true, false);
        StepVerifier.create(status.execute(career.getUserId())).assertNext(s -> {
            assertEquals("IN_MATCH", s.careerPhase()); assertEquals("PAUSED", s.engineStatus());
            assertFalse(s.canAdvanceRound());
        }).verifyComplete();
    }

    @Test
    void anotherOwnersRoundCannotChangePrivateCareerStatus() {
        active(UUID.randomUUID(), career.getCareerId(), "current-generation", false, false);
        assertPrepared();
    }

    @Test
    void anOldCareerFromTheSameOwnerIsIgnored() {
        active(career.getUserId(), UUID.randomUUID().toString(), "current-generation", false, false);
        assertPrepared();
    }

    @Test
    void anOldGenerationOfTheSameCareerIsIgnored() {
        active(career.getUserId(), career.getCareerId(), "old-generation", false, false);
        assertPrepared();
    }

    @Test
    void retainedCompletedTerminalCannotKeepTheCareerInMatch() {
        active(career.getUserId(), career.getCareerId(), "current-generation", false, true);
        assertPrepared();
    }

    @Test
    void releasingTheRoundReturnsToTheCanonicalCareerPhase() {
        UUID id = active(career.getUserId(), career.getCareerId(), "current-generation", false, false);
        registry.unregister(id);
        career.getTournamentState().setCareerPhase(CareerPhase.WAITING_USER);
        StepVerifier.create(status.execute(career.getUserId())).assertNext(s -> {
            assertEquals("WAITING_USER", s.careerPhase()); assertEquals("IDLE", s.engineStatus());
            assertTrue(s.canAdvanceRound());
        }).verifyComplete();
    }

    private void assertPrepared() {
        StepVerifier.create(status.execute(career.getUserId())).assertNext(s -> {
            assertEquals("PRE_MATCH", s.careerPhase()); assertEquals("IDLE", s.engineStatus());
            assertTrue(s.canAdvanceRound());
        }).verifyComplete();
    }

    private UUID active(UUID owner, String careerId, String generation, boolean paused, boolean completed) {
        UUID id = UUID.randomUUID();
        var round = mock(RoundEngine.class);
        when(round.belongsTo(career.getUserId(), career.getCareerId()))
            .thenReturn(owner.equals(career.getUserId()) && careerId.equals(career.getCareerId()));
        when(round.getLifecycleGeneration()).thenReturn(generation);
        when(round.isRunning()).thenReturn(true);
        when(round.isPaused()).thenReturn(paused);
        when(round.getLatestState()).thenReturn(new RoundState(id, Instant.now(), List.of(),
            completed ? RoundState.RoundStatus.COMPLETED : RoundState.RoundStatus.IN_PROGRESS));
        registry.register(id, round);
        return id;
    }
}

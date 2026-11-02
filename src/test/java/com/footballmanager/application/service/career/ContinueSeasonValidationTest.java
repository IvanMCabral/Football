package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.fixture.CareerFixtureService;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.UUID;
import static org.mockito.Mockito.*;

class ContinueSeasonValidationTest {
    @Test
    void missingCareerReturnsAnExplicitFailureInsteadOfAnEmptySuccessBody() {
        UUID owner = UUID.randomUUID();
        var sessions = mock(CareerSessionService.class);
        when(sessions.getCareerFromCache(owner)).thenReturn(Mono.empty());
        var matches = mock(MatchSessionRegistry.class);
        var rounds = mock(RoundEngineRegistry.class);
        var useCase = new ContinueSeasonUseCaseImpl(mock(CareerRepository.class), mock(CareerFixtureService.class),
                sessions, matches, rounds, new CareerLifecycleCoordinator(Duration.ofSeconds(5)));
        StepVerifier.create(useCase.continueToNewSeason(owner))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertFalse(result.success());
                    org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("No hay una carrera activa"));
                }).verifyComplete();
        verifyNoInteractions(matches, rounds);
    }

    @ParameterizedTest
    @EnumSource(value = CareerPhase.class, names = "FINISHED", mode = EnumSource.Mode.EXCLUDE)
    void rejectedContinueMustNotStopTheCurrentRound(CareerPhase phase) {
        UUID owner = UUID.randomUUID();
        CareerSave career = new CareerSave();
        career.getTournamentState().setCareerPhase(phase);
        var repository = mock(CareerRepository.class);
        var fixtures = mock(CareerFixtureService.class);
        var sessions = mock(CareerSessionService.class);
        var matches = mock(MatchSessionRegistry.class);
        var rounds = mock(RoundEngineRegistry.class);
        when(sessions.getCareerFromCache(owner)).thenReturn(Mono.just(career));
        var useCase = new ContinueSeasonUseCaseImpl(repository, fixtures, sessions, matches, rounds,
                new CareerLifecycleCoordinator(Duration.ofSeconds(5)));

        StepVerifier.create(useCase.continueToNewSeason(owner))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertFalse(result.success());
                    org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("debe estar finalizado"));
                }).verifyComplete();

        verifyNoInteractions(matches, rounds, fixtures);
        verify(sessions, never()).saveCareer(any());
        org.junit.jupiter.api.Assertions.assertEquals(phase, career.getTournamentState().getCareerPhase());
    }
}

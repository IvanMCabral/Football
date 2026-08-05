package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import com.footballmanager.domain.port.in.career.ContinueCareerUseCase;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CareerSessionServiceCleanupTest {

    @Mock CareerRepository careerRepository;
    @Mock StartCareerUseCase startCareerUseCase;
    @Mock ContinueCareerUseCase continueCareerUseCase;
    @Mock RoundEngineRegistry roundEngineRegistry;
    @Mock MatchSessionRegistry matchSessionRegistry;
    @Mock CareerDataCleanupRepository cleanupRepository;
    @Mock CareerSave career;

    @Test
    void deleteCareerCleansAllOwnedFamiliesBeforeRoot() {
        UUID userId = UUID.randomUUID();
        when(careerRepository.findById(userId.toString()))
                .thenReturn(Mono.just(Optional.of(career)));
        when(career.getCareerId()).thenReturn("career-1");
        when(cleanupRepository.deleteOwnedData(userId, "career-1")).thenReturn(Mono.empty());
        when(careerRepository.deleteById(userId.toString())).thenReturn(Mono.empty());

        StepVerifier.create(newService().deleteCareer(userId)).verifyComplete();

        verify(cleanupRepository).deleteOwnedData(userId, "career-1");
        verify(roundEngineRegistry).stopEnginesForOwner(userId, "career-1");
        verify(matchSessionRegistry).clearSessionsForOwner(userId, "career-1");
        verify(careerRepository).deleteById(userId.toString());
    }

    @Test
    void deleteCareerStillCleansUserFamiliesWhenRootIsMissing() {
        UUID userId = UUID.randomUUID();
        when(careerRepository.findById(userId.toString())).thenReturn(Mono.just(Optional.empty()));
        when(cleanupRepository.deleteOwnedData(userId, null)).thenReturn(Mono.empty());
        when(careerRepository.deleteById(userId.toString())).thenReturn(Mono.empty());

        StepVerifier.create(newService().deleteCareer(userId)).verifyComplete();

        verify(cleanupRepository).deleteOwnedData(userId, null);
    }

    @Test
    void cleanupFailurePreventsCareerRootDelete() {
        UUID userId = UUID.randomUUID();
        when(careerRepository.findById(userId.toString())).thenReturn(Mono.just(Optional.of(career)));
        when(career.getCareerId()).thenReturn("career-failing");
        when(cleanupRepository.deleteOwnedData(userId, "career-failing"))
                .thenReturn(Mono.error(new IllegalStateException("quota")));

        StepVerifier.create(newService().deleteCareer(userId))
                .expectErrorMessage("quota")
                .verify();

        verify(careerRepository, never()).deleteById(userId.toString());
    }

    private CareerSessionService newService() {
        return new CareerSessionService(
                careerRepository,
                startCareerUseCase,
                continueCareerUseCase,
                roundEngineRegistry,
                matchSessionRegistry,
                cleanupRepository);
    }
}

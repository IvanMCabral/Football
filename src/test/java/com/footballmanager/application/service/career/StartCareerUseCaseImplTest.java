package com.footballmanager.application.service.career;

import com.footballmanager.application.exception.CareerAlreadyExistsException;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.in.career.CreateCareerSnapshotUseCase;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartCareerUseCaseImplTest {

    @Mock BuildWorldViewUseCase buildWorldViewUseCase;
    @Mock CreateCareerSnapshotUseCase createCareerSnapshotUseCase;
    @Mock CareerRepository careerRepository;
    @Mock CareerLifecycleCoordinator lifecycleCoordinator;

    @Test
    void duplicateStartIsRejectedBeforeReplacingActiveCareer() {
        UUID userId = UUID.randomUUID();
        when(careerRepository.findById(userId.toString()))
                .thenReturn(Mono.just(Optional.of(new CareerSave())));
        when(lifecycleCoordinator.serializeReset(eq(userId), any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var useCase = new StartCareerUseCaseImpl(
                buildWorldViewUseCase,
                createCareerSnapshotUseCase,
                careerRepository,
                lifecycleCoordinator);

        StepVerifier.create(useCase.start(
                        userId,
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "NORMAL",
                        "NORMAL",
                        4))
                .expectError(CareerAlreadyExistsException.class)
                .verify();

        verify(buildWorldViewUseCase, never()).build(any());
        verify(careerRepository, never()).createInitialCareer(any());
    }
}

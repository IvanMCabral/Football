package com.footballmanager.application.service.usecase.query;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.footballmanager.application.service.world.WorldQueryService;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.infrastructure.observability.TeamsRequestObservability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetAllTeamsUseCaseImplObservabilityTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String REQUEST_ID = "req-service-test";

    @Mock
    private WorldQueryService queryService;

    @Mock
    private TeamsRequestObservability observability;

    @Test
    void emitsServiceStartAndSuccessForSingleSubscription() {
        when(queryService.getAllTeams(USER_ID)).thenReturn(Mono.just(List.of()));
        GetAllTeamsUseCaseImpl useCase = new GetAllTeamsUseCaseImpl(queryService, observability);

        StepVerifier.create(useCase.execute(USER_ID).contextWrite(context -> context
                .put(TeamsRequestObservability.CORRELATION_ID_CONTEXT_KEY, REQUEST_ID)
                .put(TeamsRequestObservability.START_NANOS_CONTEXT_KEY, System.nanoTime())))
                .expectNext(List.of())
                .verifyComplete();

        verify(queryService).getAllTeams(USER_ID);
        verify(observability).serviceStart(org.mockito.ArgumentMatchers.eq(REQUEST_ID), org.mockito.ArgumentMatchers.anyLong());
        verify(observability).serviceSuccess(org.mockito.ArgumentMatchers.eq(REQUEST_ID), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void emitsServiceStartAndErrorWithoutChangingFailure() {
        IllegalStateException failure = new IllegalStateException("synthetic failure");
        when(queryService.getAllTeams(USER_ID)).thenReturn(Mono.error(failure));
        GetAllTeamsUseCaseImpl useCase = new GetAllTeamsUseCaseImpl(queryService, observability);

        StepVerifier.create(useCase.execute(USER_ID).contextWrite(context -> context
                .put(TeamsRequestObservability.CORRELATION_ID_CONTEXT_KEY, REQUEST_ID)
                .put(TeamsRequestObservability.START_NANOS_CONTEXT_KEY, System.nanoTime())))
                .expectErrorSatisfies(error -> org.assertj.core.api.Assertions.assertThat(error).isSameAs(failure))
                .verify();

        verify(queryService).getAllTeams(USER_ID);
        verify(observability).serviceStart(org.mockito.ArgumentMatchers.eq(REQUEST_ID), org.mockito.ArgumentMatchers.anyLong());
        verify(observability).serviceError(org.mockito.ArgumentMatchers.eq(REQUEST_ID), org.mockito.ArgumentMatchers.anyLong());
    }
}

package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.match.MatchManagementService;
import com.footballmanager.application.service.reactive.ReactiveLifecycleExecutor;
import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.application.service.simulation.MatchResultProcessor;
import com.footballmanager.application.service.simulation.MatchSimulationOrchestrator;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.domain.ports.out.match.MatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Public controller contract: a repeated start returns the existing round. */
class RoundControllerIdempotencyTest {

    @Test
    void repeatedStartReturnsExistingRoundWithoutTouchingCareerOrCreatingScheduler() {
        RoundEngineRegistry registry = mock(RoundEngineRegistry.class);
        RoundEngine existing = mock(RoundEngine.class);
        UUID roundId = UUID.randomUUID();
        RoundState state = new RoundState(roundId, Instant.now(), List.of(), RoundState.RoundStatus.IN_PROGRESS);
        when(registry.get(roundId)).thenReturn(existing);
        when(existing.getLatestState()).thenReturn(state);

        CareerSessionService career = mock(CareerSessionService.class);
        RoundController controller = new RoundController(
            mock(MatchManagementService.class),
            mock(MatchEngineRegistry.class),
            registry,
            mock(MatchSimulationOrchestrator.class),
            career,
            mock(MatchContextFactory.class),
            mock(LeagueSimulator.class),
            mock(MatchRepository.class),
            mock(BaselineStateStoragePort.class),
            new ControllerHelper(),
            mock(ReactiveLifecycleExecutor.class));

        Authentication authentication = mock(Authentication.class);
        UUID ownerId = UUID.randomUUID();
        when(authentication.getName()).thenReturn(ownerId.toString());
        doReturn(true).when(existing).belongsTo(ownerId, null);

        var response = controller.startRound(
            new RoundController.StartRoundRequest(roundId.toString(), null, List.of()),
            authentication).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(state);
        verify(career, never()).getCareerFromCache(any());
        verify(registry, never()).register(any(), any());
    }
}

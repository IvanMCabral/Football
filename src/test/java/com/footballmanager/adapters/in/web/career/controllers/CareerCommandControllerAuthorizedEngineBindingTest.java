package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.career.SeasonAdvancementService;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.application.service.security.CareerOwnershipAuthority;
import com.footballmanager.application.service.security.RoundOwnershipAuthority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Regression proof for the authorized-instance binding contract. The registry
 * is replaced while the authority publisher is completing; a controller that
 * resolves the round ID again would mutate engine B instead of the authorized
 * engine A.
 */
@ExtendWith(MockitoExtension.class)
class CareerCommandControllerAuthorizedEngineBindingTest {

    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ROUND_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ControllerHelper controllerHelper;
    @Mock
    private CareerSessionService sessionService;
    @Mock
    private SeasonAdvancementService seasonAdvancementService;
    @Mock
    private RoundEngineRegistry registry;
    @Mock
    private GameService gameService;
    @Mock
    private RoundOwnershipAuthority ownershipAuthority;
    @Mock
    private CareerOwnershipAuthority careerOwnershipAuthority;
    @Mock
    private Authentication authentication;

    @Test
    void pauseMutatesAuthorizedEngineWhenRegistryIsReplacedBeforeMutation() {
        RoundEngine authorizedEngine = engine(false);
        RoundEngine replacementEngine = engine(false);
        AtomicBoolean replaced = new AtomicBoolean();
        CareerCommandController controller = controller();
        when(controllerHelper.getUserId(authentication)).thenReturn(OWNER_A);
        lenient().when(registry.get(ROUND_ID)).thenReturn(replacementEngine);
        when(ownershipAuthority.requireOwned(OWNER_A, "career-a", ROUND_ID))
                .thenReturn(Mono.defer(() -> {
                    replaced.set(true);
                    return Mono.just(authorizedEngine);
                }));

        ResponseEntity<Map<String, Object>> response = controller
                .pauseRound("career-a", ROUND_ID.toString(), authentication)
                .block();

        assertThat(replaced).isTrue();
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(authorizedEngine).pauseAll();
        verify(replacementEngine, never()).pauseAll();
        verify(registry, never()).get(ROUND_ID);
    }

    @Test
    void resumeMutatesAuthorizedEngineWhenRegistryIsReplacedBeforeMutation() {
        RoundEngine authorizedEngine = engine(true);
        RoundEngine replacementEngine = engine(true);
        AtomicBoolean replaced = new AtomicBoolean();
        CareerCommandController controller = controller();
        when(controllerHelper.getUserId(authentication)).thenReturn(OWNER_A);
        lenient().when(registry.get(ROUND_ID)).thenReturn(replacementEngine);
        when(ownershipAuthority.requireOwned(OWNER_A, "career-a", ROUND_ID))
                .thenReturn(Mono.defer(() -> {
                    replaced.set(true);
                    return Mono.just(authorizedEngine);
                }));

        ResponseEntity<Map<String, Object>> response = controller
                .resumeRound("career-a", ROUND_ID.toString(), authentication)
                .block();

        assertThat(replaced).isTrue();
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(authorizedEngine).resumeAll();
        verify(replacementEngine, never()).resumeAll();
        verify(registry, never()).get(ROUND_ID);
    }

    private CareerCommandController controller() {
        return new CareerCommandController(
                controllerHelper,
                sessionService,
                seasonAdvancementService,
                registry,
                gameService,
                ownershipAuthority,
                careerOwnershipAuthority);
    }

    private static RoundEngine engine(boolean paused) {
        RoundEngine engine = mock(RoundEngine.class);
        lenient().when(engine.isPaused()).thenReturn(paused);
        lenient().when(engine.isRunning()).thenReturn(true);
        lenient().when(engine.getMatchStates()).thenReturn(List.of());
        return engine;
    }
}

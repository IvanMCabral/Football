package com.footballmanager.application.service.match;

import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.security.RoundOwnershipAuthority;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.domain.port.in.match.ExecuteMatchCommandUseCase;
import com.footballmanager.domain.port.in.match.PauseMatchUseCase;
import com.footballmanager.domain.port.in.match.ResumeMatchUseCase;
import com.footballmanager.domain.port.in.match.StartMatchUseCase;
import com.footballmanager.domain.port.in.match.StopMatchUseCase;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

/** Proves the legacy match resume path keeps the authorized engine instance. */
class MatchManagementServiceAuthorizedEngineBindingTest {

    @Test
    void resumeMutatesAuthorizedEngineWithoutResolvingReplacement() {
        StartMatchUseCase start = mock(StartMatchUseCase.class);
        StartMatchUseCaseImpl detailedStart = mock(StartMatchUseCaseImpl.class);
        PauseMatchUseCase pause = mock(PauseMatchUseCase.class);
        ResumeMatchUseCase resume = mock(ResumeMatchUseCase.class);
        StopMatchUseCase stop = mock(StopMatchUseCase.class);
        ExecuteMatchCommandUseCase command = mock(ExecuteMatchCommandUseCase.class);
        MatchSessionRegistry sessions = mock(MatchSessionRegistry.class);
        RoundOwnershipAuthority authority = mock(RoundOwnershipAuthority.class);
        MatchManagementService service = new MatchManagementService(
                start, detailedStart, pause, resume, stop, command, sessions, authority);

        UUID owner = UUID.randomUUID();
        UUID match = UUID.randomUUID();
        RoundEngine authorized = mock(RoundEngine.class);
        RoundEngine replacement = mock(RoundEngine.class);
        when(authority.findOwnedEngineByMatch(owner, match)).thenReturn(Mono.just(authorized));
        when(resume.execute(owner, match)).thenReturn(Mono.empty());

        service.resumeMatch(owner, match).block();

        verify(authorized).resumeAll();
        verifyNoInteractions(replacement);
        verify(authority).findOwnedEngineByMatch(owner, match);
        verifyNoMoreInteractions(authority);
    }
}

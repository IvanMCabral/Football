package com.footballmanager.adapters.in.web.dashboard;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.career.GetCareerStatusUseCaseImpl;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import com.footballmanager.domain.port.in.career.*;
import com.footballmanager.domain.port.in.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardEntryControllerTest {
    @Test
    void coldEntryLoadsOneCareerAndDerivesStatisticsFromThatSameState() {
        UUID owner = UUID.randomUUID();
        var continuation = mock(ContinueCareerUseCase.class);
        var pending = Sinks.<CareerSave>one();
        when(continuation.continueCareer(owner)).thenReturn(pending.asMono());
        var sessions = new CareerSessionService(mock(CareerRepository.class), mock(StartCareerUseCase.class),
                continuation, mock(RoundEngineRegistry.class), mock(MatchSessionRegistry.class), mock(CareerDataCleanupRepository.class));
        var auth = mock(AuthUseCase.class);
        when(auth.getUserInfo(owner.toString())).thenReturn(Mono.just(new AuthUserInfo(owner.toString(), "qa@example.test", "QA", "QA", null, null)));
        var controller = new DashboardEntryController(new ControllerHelper(), auth, new GetCareerStatusUseCaseImpl(sessions), sessions);
        CareerSave career = new CareerSave(); career.setUserId(owner); career.getData().setCareerId("career-entry");
        career.setUserSessionTeamId("team"); career.setTournamentState(new TournamentState());
        var standing = new TeamStandings("team", "Club"); standing.setPlayed(3); standing.setWon(2); standing.setLost(1);
        career.getTournamentState().getStandings().put("team", standing);
        var hurt = new SessionPlayer(); hurt.setSessionPlayerId("hurt"); hurt.setInjured(true); hurt.setEnergy(0);
        var banned = new SessionPlayer(); banned.setSessionPlayerId("banned"); banned.setSuspensionRemainingMatches(1); banned.setEnergy(0);
        var exhausted = new SessionPlayer(); exhausted.setSessionPlayerId("exhausted"); exhausted.setEnergy(19);
        var tired = new SessionPlayer(); tired.setSessionPlayerId("tired"); tired.setEnergy(39);
        career.getPlayerManager().setSessionPlayers(java.util.Map.of("hurt", hurt, "banned", banned, "exhausted", exhausted, "tired", tired));
        career.getTeamManager().setTeamSquads(java.util.Map.of("team", java.util.List.of("hurt", "banned", "exhausted", "tired")));
        StepVerifier.create(controller.entry(new UsernamePasswordAuthenticationToken(owner.toString(), null)))
                .then(() -> pending.tryEmitValue(career))
                .assertNext(entry -> {
                    assertThat(entry.careerStatus().careerId()).isEqualTo("career-entry");
                    assertThat(entry.user().id()).isEqualTo(owner.toString());
                    assertThat(entry.userStats().matchesPlayed()).isEqualTo(3);
                    assertThat(entry.userStats().matchesWon()).isEqualTo(2);
                    assertThat(entry.userStats().winPercentage()).isEqualTo(66.67);
                    assertThat(entry.squadConditions()).isEqualTo(new DashboardEntryController.SquadConditions(1, 1, 1, 1));
                }).verifyComplete();
        verify(continuation, times(1)).continueCareer(owner);
        verify(auth, times(1)).getUserInfo(owner.toString());
    }
}

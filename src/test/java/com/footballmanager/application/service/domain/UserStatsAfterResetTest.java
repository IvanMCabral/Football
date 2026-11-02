package com.footballmanager.application.service.domain;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.ports.out.user.UserRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.mockito.Mockito.*;

class UserStatsAfterResetTest {
    @Test void absentCareerAndMissingStandingsAlwaysEmitZeroStatistics() {
        UUID owner = UUID.randomUUID();
        var users = mock(UserRepository.class);
        var sessions = mock(CareerSessionService.class);
        when(users.findById(owner)).thenReturn(Mono.just(User.create(UserId.of(owner), "qa@example.test", "Manager", "synthetic")));
        var service = new UserStatsService(users, sessions);
        CareerSave noTeam = new CareerSave(); noTeam.setUserId(owner);
        CareerSave noStanding = new CareerSave(); noStanding.setUserId(owner); noStanding.setUserSessionTeamId("team");
        noStanding.setTournamentState(new TournamentState());
        for (Mono<CareerSave> state : java.util.List.of(Mono.<CareerSave>empty(), Mono.just(noTeam), Mono.just(noStanding))) {
            when(sessions.getCareerFromCache(owner)).thenReturn(state);
            StepVerifier.create(service.getUserStats(owner))
                    .expectNext(new UserStatsSummary("Manager", 0, 0, 0, 0.0)).verifyComplete();
        }
    }
}

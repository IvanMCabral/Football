package com.footballmanager.application.service.career;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamCommandReadCacheTest {
    @Test
    void teamReadsUseTheCurrentSessionViewAndPreserveMissingCareerFailure() {
        var sessions = mock(CareerSessionService.class);
        var world = mock(WorldService.class);
        var teams = mock(TeamRepository.class);
        var service = new TeamCommandUseCaseImpl(sessions, world, teams);
        var owner = UUID.randomUUID();
        var career = mock(CareerSave.class);
        var before = mock(SessionTeam.class);
        var after = mock(SessionTeam.class);
        when(career.getSessionTeam("team")).thenReturn(before, after);
        when(sessions.getCareerFromCache(owner)).thenReturn(Mono.just(career));
        assertThat(service.getSessionTeam(owner, "team").block()).isSameAs(before);
        assertThat(service.getSessionTeam(owner, "team").block()).isSameAs(after);
        verify(sessions, never()).getCareer(any());
        verifyNoInteractions(world, teams);
        when(sessions.getCareerFromCache(owner)).thenReturn(Mono.empty());
        assertThatThrownBy(() -> service.getSessionTeam(owner, "team").block())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No existe carrera activa");
    }
}

package com.footballmanager.domain.port.in.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface TeamCommandUseCase {
    Mono<CareerSave> createRandomTeam(UUID userId);
    Mono<CareerSave> cloneTeamToSession(UUID userId, UUID realTeamId);
    Mono<CareerSave> removeSessionTeam(UUID userId, String sessionTeamId);
    Mono<List<SessionTeam>> getSessionTeams(UUID userId);
    Mono<SessionTeam> getSessionTeam(UUID userId, String sessionTeamId);
}

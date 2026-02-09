package com.footballmanager.domain.port.in.career;

import com.footballmanager.domain.model.entity.SessionPlayer;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface SquadQueryUseCase {
    Mono<List<SessionPlayer>> getTeamSquadByWorldTeamId(UUID userId, String worldTeamId);
    Mono<List<SessionPlayer>> getSessionTeamSquad(UUID userId, String sessionTeamId);
}

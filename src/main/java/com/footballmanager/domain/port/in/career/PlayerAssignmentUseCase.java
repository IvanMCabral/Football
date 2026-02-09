package com.footballmanager.domain.port.in.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface PlayerAssignmentUseCase {
    Mono<CareerSave> assignPlayerToSessionTeam(UUID userId, String playerId, String teamId);
    Mono<CareerSave> removePlayerFromSessionTeam(UUID userId, String playerId, String teamId);
    Mono<List<SessionPlayer>> getSessionTeamSquad(UUID userId, String sessionTeamId);
}

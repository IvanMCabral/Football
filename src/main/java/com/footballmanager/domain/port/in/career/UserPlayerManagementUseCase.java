package com.footballmanager.domain.port.in.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface UserPlayerManagementUseCase {
    Flux<SessionPlayer> getUserPlayers(UUID userId);
    Mono<CareerSave> assignPlayerToUserTeam(UUID userId, String sessionPlayerId);
    Mono<CareerSave> removePlayerFromUserTeam(UUID userId, String sessionPlayerId);
    Mono<List<SessionPlayer>> getUserSquad(UUID userId);
}

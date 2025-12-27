package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.Transfer;
import com.footballmanager.domain.model.PlayerId;
import com.footballmanager.domain.model.TeamId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransferRepository {
    Mono<Void> save(Transfer transfer);
    Mono<Transfer> findById(String id);
    Flux<Transfer> findByPlayerId(PlayerId playerId);
    Flux<Transfer> findByFromTeamId(TeamId teamId);
    Flux<Transfer> findPending();
    Mono<Void> delete(String id);
}

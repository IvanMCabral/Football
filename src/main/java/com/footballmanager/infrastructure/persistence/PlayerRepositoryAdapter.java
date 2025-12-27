package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.Player;
import com.footballmanager.domain.model.PlayerId;
import com.footballmanager.domain.model.TeamId;
import com.footballmanager.domain.ports.out.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PlayerRepositoryAdapter implements PlayerRepository {
    private final PlayerR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Void> save(Player player) {
        return r2dbcRepository.save(PlayerEntity.fromDomain(player)).then();
    }

    @Override
    public Mono<Player> findById(PlayerId id) {
        return r2dbcRepository.findById(id.getValue())
                .map(PlayerEntity::toDomain);
    }

    @Override
    public Flux<Player> findByTeamId(TeamId teamId) {
        return r2dbcRepository.findByTeamId(teamId.getValue())
                .map(PlayerEntity::toDomain);
    }

    @Override
    public Flux<Player> findAvailablePlayers() {
        return r2dbcRepository.findAvailable()
                .map(PlayerEntity::toDomain);
    }

    @Override
    public Flux<Player> findAll() {
        return r2dbcRepository.findAll()
                .map(PlayerEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(PlayerId id) {
        return r2dbcRepository.existsById(id.getValue());
    }

    @Override
    public Mono<Void> deleteById(PlayerId id) {
        return r2dbcRepository.deleteById(id.getValue());
    }
}

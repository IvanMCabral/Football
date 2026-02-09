package com.footballmanager.application.service.player;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.PlayerAttributes;
import com.footballmanager.domain.port.in.player.PlayerManagementUseCase;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerManagementUseCaseImpl implements PlayerManagementUseCase {

    private final PlayerRepository playerRepository;

    @Override
    public Mono<Player> createPlayer(UUID userId, Player player) {
        return playerRepository.save(userId, player);
    }

    @Override
    public Mono<Player> getPlayer(UUID userId, UUID playerId) {
        return playerRepository.findById(userId, playerId);
    }

    @Override
    public Flux<Player> getAllPlayersByUserId(UUID userId) {
        return playerRepository.findAllByUserId(userId);
    }

    @Override
    public Flux<Player> getAvailablePlayersByUserId(UUID userId) {
        return playerRepository.findAvailablePlayersByUserId(userId);
    }

    @Override
    public Mono<Player> updatePlayer(UUID userId, UUID playerId, Player player) {
        return playerRepository.save(userId, player);
    }

    @Override
    public Mono<Void> deletePlayer(UUID userId, UUID playerId) {
        return playerRepository.deleteById(userId, playerId);
    }

    @Override
    public Mono<Player> updatePlayerAttributes(UUID userId, UUID playerId, int skillChange) {
        return playerRepository.findById(userId, playerId)
            .flatMap(player -> {
                // Actualizar todos los atributos por igual
                PlayerAttributes newAttrs = player.getAttributes();
                PlayerAttributes updated = new PlayerAttributes(
                    newAttrs.getAttack() + skillChange,
                    newAttrs.getDefense() + skillChange,
                    newAttrs.getTechnique() + skillChange,
                    newAttrs.getSpeed() + skillChange,
                    newAttrs.getStamina() + skillChange,
                    newAttrs.getMentality() + skillChange
                );
                player.updateAttributes(updated);
                return playerRepository.save(userId, player);
            });
    }

    @Override
    public Flux<Player> getPlayersByTeam(UUID teamId) {
        return playerRepository.findByTeamId(teamId);
    }
}

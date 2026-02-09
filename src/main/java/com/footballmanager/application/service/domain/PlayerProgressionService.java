
package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Handles player progression after match simulation.
 * Modifica jugadores en memoria - el caller debe persistir los cambios en Redis.
 * NO escribe directamente a SQL.
 */
@Service
public class PlayerProgressionService {

    /**
     * Procesa progresión de jugadores basada en el resultado del partido.
     * @return Mono con la lista de jugadores con atributos actualizados
     */
    public Mono<List<Player>> processProgression(Match match, List<Player> homePlayers, List<Player> awayPlayers, List<Player> updatedPlayers) {
        int homeGoals = match.getHomeGoals() != null ? match.getHomeGoals() : 0;
        int awayGoals = match.getAwayGoals() != null ? match.getAwayGoals() : 0;
        boolean homeWin = homeGoals > awayGoals;
        boolean awayWin = awayGoals > homeGoals;

        Set<PlayerId> homeIds = new HashSet<>();
        homePlayers.forEach(p -> homeIds.add(p.getId()));
        Set<PlayerId> awayIds = new HashSet<>();
        awayPlayers.forEach(p -> awayIds.add(p.getId()));

        List<Player> progressedPlayers = updatedPlayers.stream()
            .map(player -> {
                int age = player.getAge();
                boolean performedWell = (homeWin && homeIds.contains(player.getId())) || (awayWin && awayIds.contains(player.getId()));
                int delta = performedWell ? 2 : -1;
                if (age < 25) delta += 1;
                if (age > 32) delta -= 1;
                // Use domain method to update attributes
                player.updateAttributes(delta, delta, delta, delta, delta, delta);
                return player;
            })
            .collect(java.util.stream.Collectors.toList());

        return Mono.just(progressedPlayers);
    }
}


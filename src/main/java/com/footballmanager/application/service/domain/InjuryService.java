
package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * <p>Originally: handled injury logic after match simulation. Modified players
 * in memory; the caller was expected to persist the changes to Redis. This
 * class does NOT write directly to SQL. As of the detailed match engine, this
 * responsibility moved into {@code InjuryMutationApplier}, which is called
 * code is preserved (deprecated) for one release to keep the public API
 *
 */
@Deprecated(forRemoval = true)
@Service
@RequiredArgsConstructor
public class InjuryService {
    private final Random random;

    /**
     * Procesa lesiones para todos los jugadores del partido.
     * @return Flux de jugadores con estados de lesión actualizados
     */
    public Flux<Player> processInjuries(Match match, List<Player> homePlayers, List<Player> awayPlayers) {
        List<Player> allPlayers = new ArrayList<>();
        allPlayers.addAll(homePlayers);
        allPlayers.addAll(awayPlayers);

        return Flux.fromIterable(allPlayers)
            .map(player -> {
                if (player.getInjuryState() == Player.InjuryState.INJURED_SERIOUS) {
                    // Lesiones serias no se sobreescriben
                    return player;
                }
                double baseRisk = 0.01;
                double ageRisk = Math.max(0, (player.getAge() - 28) * 0.005);
                double staminaRisk = Math.max(0, (70 - player.getAttributes().getStamina()) * 0.003);
                double minutesPlayed = 90;
                double totalRisk = baseRisk + ageRisk + staminaRisk + (minutesPlayed > 60 ? 0.01 : 0.0);
                double roll = random.nextDouble();

                if (player.getInjuryState() == Player.InjuryState.INJURED_LIGHT) {
                    if (roll < 0.5) {
                        player.recoverFromInjury();
                    } else {
                        player.sustainLightInjury();
                    }
                } else {
                    if (roll < totalRisk * 0.1) {
                        player.sustainSeriousInjury();
                    } else if (roll < totalRisk) {
                        player.sustainLightInjury();
                    } else {
                        player.recoverFromInjury();
                    }
                }
                return player;
            });
    }
}


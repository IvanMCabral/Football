package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WorldPlayerCommandService - Responsable de operaciones de comando sobre WorldPlayers.
 * Principio de Responsabilidad Unica: solo crea/modifica jugadores.
 */
@Service
@RequiredArgsConstructor
public class WorldPlayerCommandService {

    private final WorldSnapshotService snapshotService;

    /**
     * Crea un jugador custom en el WorldSnapshot
     */
    public Mono<WorldSnapshot> createCustomPlayer(
            UUID userId, String name, Integer age, String position,
            Integer attack, Integer defense, Integer technique,
            Integer speed, Integer stamina, Integer mentality) {
        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    BigDecimal marketValue = calculateMarketValue(
                            attack, defense, technique, speed, stamina, mentality, age);

                    WorldPlayer player = WorldPlayer.createCustom(
                            name, age, position, attack, defense, technique,
                            speed, stamina, mentality, marketValue);

                    snapshot.addWorldPlayer(player);

                    return snapshotService.saveSnapshot(snapshot);
                });
    }

    /**
     * Crea un jugador random (para world editing)
     */
    public Mono<WorldSnapshot> createRandomPlayer(UUID userId) {
        String name = "Random Player " + System.currentTimeMillis();
        int age = 18 + (int)(Math.random() * 17); // 18-34
        String position = getRandomPosition();

        // Stats aleatorios entre 50-90
        int attack = 50 + (int)(Math.random() * 41);
        int defense = 50 + (int)(Math.random() * 41);
        int technique = 50 + (int)(Math.random() * 41);
        int speed = 50 + (int)(Math.random() * 41);
        int stamina = 50 + (int)(Math.random() * 41);
        int mentality = 50 + (int)(Math.random() * 41);

        return createCustomPlayer(userId, name, age, position,
                attack, defense, technique, speed, stamina, mentality);
    }

    /**
     * Crea multiples jugadores random en batch
     */
    public Mono<WorldSnapshot> createRandomPlayers(UUID userId, int count) {
        return snapshotService.getSnapshot(userId)
                .flatMap(initialSnapshot -> {
                    Mono<WorldSnapshot> chain = Mono.just(initialSnapshot);

                    for (int i = 0; i < count; i++) {
                        chain = chain.flatMap(snapshot -> {
                            String name = "Random Player " + System.currentTimeMillis();
                            int age = 18 + (int)(Math.random() * 17);
                            String position = getRandomPosition();

                            int attack = 50 + (int)(Math.random() * 41);
                            int defense = 50 + (int)(Math.random() * 41);
                            int technique = 50 + (int)(Math.random() * 41);
                            int speed = 50 + (int)(Math.random() * 41);
                            int stamina = 50 + (int)(Math.random() * 41);
                            int mentality = 50 + (int)(Math.random() * 41);

                            BigDecimal marketValue = calculateMarketValue(
                                    attack, defense, technique, speed, stamina, mentality, age);

                            WorldPlayer player = WorldPlayer.createCustom(
                                    name, age, position,
                                    attack, defense, technique, speed, stamina, mentality, marketValue);

                            snapshot.addWorldPlayer(player);
                            return Mono.just(snapshot);
                        });
                    }

                    return chain.flatMap(snapshotService::saveSnapshot);
                });
    }

    /**
     * Helper: Retorna posicion random
     */
    private String getRandomPosition() {
        String[] positions = {"GK", "CB", "LB", "RB", "CM", "LW", "RW", "ST"};
        return positions[(int)(Math.random() * positions.length)];
    }

    /**
     * Calcula market value basado en stats
     */
    private BigDecimal calculateMarketValue(
            Integer attack, Integer defense, Integer technique,
            Integer speed, Integer stamina, Integer mentality, Integer age) {

        int totalStats = attack + defense + technique + speed + stamina + mentality;
        int avgStat = totalStats / 6;

        long baseValue = avgStat * 100_000L;

        // Ajuste por edad
        double ageFactor = 1.0;
        if (age < 23) ageFactor = 1.5;
        else if (age > 30) ageFactor = 0.7;

        return BigDecimal.valueOf((long) (baseValue * ageFactor));
    }

    /**
     * Mapea posicion de String a enum
     */
    private Player.Position mapPosition(String positionStr) {
        try {
            return Player.Position.valueOf(positionStr.toUpperCase());
        } catch (Exception e) {
            return Player.Position.CM;
        }
    }
}

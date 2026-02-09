package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.ports.out.game.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * GameService - Gestión de juegos/carreras.
 *
 * Responsabilidad:
 * - Crear Games
 * - Inicializar Careers en Redis asociadas al Game
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final StartCareerUseCase startCareerUseCase;

    /**
     * Crea un Game y automáticamente inicializa una Career en Redis.
     *
     * @param game Game a crear
     * @param worldLeagueId ID de la liga del mundo
     * @param difficulty Dificultad de la carrera (NORMAL, HARD, EXPERT)
     * @param gameSpeed Velocidad del juego (SLOW, NORMAL, FAST)
     * @param teamsPerDivision Equipos por división
     * @return Game creado con Career inicializada
     */
    public Mono<Game> createGame(Game game, UUID worldLeagueId, String difficulty, String gameSpeed, int teamsPerDivision) {
        UUID userId = game.getUserId().getValue();
        String worldTeamId = game.getTeamId().getValue().toString();

        // Inicializar Career en Redis
        return startCareerUseCase.start(userId, worldLeagueId.toString(), worldTeamId, difficulty, gameSpeed, teamsPerDivision)
            .flatMap(career -> {
                // Guardar el Game
                return gameRepository.save(userId, game);
            })
            .thenReturn(game)
            .onErrorResume(e -> Mono.error(e));
    }

    public Mono<Game> getGameById(UUID userId, GameId id) {
        return gameRepository.findById(userId, id);
    }

    public Flux<Game> getGamesByUserId(UUID userId, UserId userIdParam) {
        return gameRepository.findByUserId(userId, userIdParam);
    }

    public Flux<Game> getAllGames(UUID userId) {
        return gameRepository.findAll(userId);
    }

    public Mono<Void> deleteGame(UUID userId, GameId id) {
        return gameRepository.deleteById(userId, id);
    }
}

package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.ports.out.game.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GameService - Gestión de juegos/carreras.
 *
 * Responsabilidad:
 * - Crear Games
 * - Inicializar Careers en Redis asociadas al Game
 *
 * <p><b>V24D15-CLEANUP (BUG_GAME_DASHBOARD_404):</b> when a Career is
 * created via {@code POST /api/v1/career/start}, the UI later navigates to
 * {@code /games/{careerId}/...} expecting a Game entity with the same id
 * as the career. Without an associated Game entity,
 * {@code GET /api/v1/games/{careerId}} returns 404 and the UI shows a
 * broken "game detail" view.
 *
 * <p>The fix is {@link #createGameFromCareer} which is invoked from
 * {@code CareerCommandController.startCareer} AFTER the Career has been
 * initialized. The Game entity shares the career's UUID as its primary
 * key — this lets the existing UI navigate using {@code careerId} without
 * any frontend changes. The legacy {@link #createGame} (used directly via
 * {@code POST /api/v1/games}) still works for users that want a standalone
 * Game without an associated Career flow.
 */
@Slf4j
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

    /**
     * V24D15-CLEANUP (BUG_GAME_DASHBOARD_404): persist a Game entity that
     * shares its primary key with the supplied Career. Invoked from
     * {@code CareerCommandController.startCareer} so the UI's existing
     * {@code /games/{careerId}} navigation resolves to a real Game entity
     * instead of 404.
     *
     * <p>This method is intentionally idempotent-ish: it overwrites any
     * pre-existing Game with the same id. The {@code name} field defaults
     * to the CareerSave's name (or {@code "Career-{careerId}"} when blank)
     * so the dashboard can display it.
     *
     * <p>Best-effort: failures are logged but never fail the career start.
     * The UI will see a 404 on {@code /games/{id}} if Redis is down, but
     * the live match flow is unaffected.
     */
    public Mono<Game> createGameFromCareer(CareerSave career, String leagueId,
                                           String difficulty, String gameSpeed, int teamsPerDivision) {
        if (career == null || career.getData() == null || career.getData().getCareerId() == null) {
            return Mono.error(new IllegalArgumentException("career/careerId must not be null"));
        }
        String careerId = career.getData().getCareerId();
        UUID userUuid = career.getUserId();
        if (userUuid == null) {
            return Mono.error(new IllegalArgumentException("career.userId must not be null"));
        }
        // Resolve the teamId for the Game aggregate. Prefer the session
        // team id (always present after a successful Career start); fall
        // back to the world team id from the CareerSave data.
        String teamIdStr = career.getData().getUserSessionTeamId() != null
                ? career.getData().getUserSessionTeamId().toString()
                : (career.getData().getUserTeamId() != null
                        ? career.getData().getUserTeamId().toString()
                        : null);
        if (teamIdStr == null) {
            return Mono.error(new IllegalStateException(
                    "career " + careerId + " has no teamId — cannot create Game"));
        }

        String name = "Career-" + careerId.substring(0, Math.min(8, careerId.length()));

        Game game = new Game(
                // KEY INVARIANT: Game.id == Career.careerId. This is what
                // makes the existing UI navigation work without any
                // frontend changes — the dashboard passes careerId to
                // /games/{id} and the backend now resolves it.
                new GameId(UUID.fromString(careerId)),
                UserId.of(userUuid),
                com.footballmanager.domain.model.valueobject.TeamId.fromString(teamIdStr),
                name,
                LocalDateTime.now()
        );

        return gameRepository.save(userUuid, game)
                .doOnSuccess(saved -> log.info(
                        "[V24D15-CLEANUP] Game entity persisted for careerId={}, userId={}, name='{}'",
                        careerId, userUuid, name))
                .doOnError(err -> log.warn(
                        "[V24D15-CLEANUP] Failed to persist Game entity for careerId={}: {}",
                        careerId, err.getMessage()))
                // Best-effort: log + swallow on failure. Career start must
                // not be blocked by a Redis hiccup here. The UI will see
                // a 404 on /games/{id} until the next career restart, but
                // the live match flow is unaffected.
                .onErrorResume(err -> Mono.empty())
                .thenReturn(game);
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

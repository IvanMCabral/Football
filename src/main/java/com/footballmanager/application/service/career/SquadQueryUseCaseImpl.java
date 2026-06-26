package com.footballmanager.application.service.career;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.port.in.career.SquadQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación de UseCase para resolución de squads.
 *
 * Implementa COPY-ON-WRITE PATTERN:
 * - Base: WorldPlayers del WorldSnapshot
 * - Filtro: excluded removedPlayerIds
 * - Añade: SessionPlayers creados por usuario
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SquadQueryUseCaseImpl implements SquadQueryUseCase {

    private final CareerSessionService sessionService;
    private final WorldService worldService;

    @Override
    public Mono<List<SessionPlayer>> getTeamSquadByWorldTeamId(UUID userId, String worldTeamId) {
        log.debug("[SQUAD-DUP] getTeamSquadByWorldTeamId - userId: {}, worldTeamId: {}", userId, worldTeamId);

        // 1. Obtener jugadores BASE del WorldSnapshot
        Mono<List<WorldPlayer>> basePlayersMono = worldService.getPlayersByWorldTeam(userId, worldTeamId)
            .doOnNext(players -> {
                log.debug("[SQUAD-DUP]   Base players from World: {}", players.size());
                // DEBUG: Log de los IDs
                for (WorldPlayer wp : players) {
                    log.debug("[SQUAD-DUP]   WorldPlayer: id={}", wp.getWorldPlayerId());
                }
            });

        // 2. Obtener OVERRIDES de CareerSave
        Mono<CareerSave> careerMono = sessionService.continueCareer(userId)
            .doOnNext(career -> {
                log.debug("[SQUAD-DUP]   Career loaded from Redis - userId: {}, teams: {}, squadPlayers: {}",
                    career.getUserId(),
                    career.getAllSessionTeams().size(),
                    career.getSessionPlayers().size());
                // Log team squad sizes
                for (SessionTeam team : career.getAllSessionTeams()) {
                    List<String> squadIds = career.getSquadPlayerIds(team.getSessionTeamId());
                    log.debug("[SQUAD-DUP]   Team {} ({}) squad size: {}",
                        team.getName(), team.getSessionTeamId(), squadIds.size());
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("[SQUAD-DUP]   No career found in Redis, creating empty");
                return Mono.just(new CareerSave());
            }));

        // 3. Combinar base + overrides
        return Mono.zip(basePlayersMono, careerMono)
            .map(tuple -> {
                List<WorldPlayer> basePlayers = tuple.getT1();
                CareerSave career = tuple.getT2();

                Set<String> removedPlayerIds = career.getRemovedPlayerIds(worldTeamId);
                log.debug("[SQUAD-DUP]   Removed playerIds: {}", removedPlayerIds.size());

                String sessionTeamId = career.findSessionTeamIdByWorldTeamId(worldTeamId);
                log.debug("[SQUAD-DUP]   sessionTeamId: {}", sessionTeamId);

                if (sessionTeamId == null) {
                    // No hay SessionTeam -> retornar jugadores base filtrados
                    List<SessionPlayer> filteredPlayers = basePlayers.stream()
                        .filter(wp -> !removedPlayerIds.contains(wp.getWorldPlayerId()))
                        .map(wp -> convertWorldPlayerToSessionPlayer(wp, null))
                        .collect(Collectors.toList());

                    log.debug("[SQUAD-DUP]   No sessionTeam, returning {} filtered base players", filteredPlayers.size());
                    return filteredPlayers;
                }

                // Obtener jugadores del SessionTeam
                List<SessionPlayer> sessionPlayers = career.getTeamSquad(sessionTeamId);
                log.debug("[SQUAD-DUP]   Session players count: {}", sessionPlayers.size());

                Set<String> worldPlayerIdsInSession = sessionPlayers.stream()
                    .map(SessionPlayer::getWorldPlayerId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
                log.debug("[SQUAD-DUP]   WorldPlayerIds in session: {}", worldPlayerIdsInSession.size());

                // DEBUG: Log de los sessionPlayerIds vs worldPlayerIds
                for (SessionPlayer sp : sessionPlayers) {
                    log.debug("[SQUAD-DUP]   SessionPlayer: worldId={}, sessionId={}",
                        sp.getWorldPlayerId(), sp.getSessionPlayerId());
                }

                // Combinar: base no removidos + session players
                List<SessionPlayer> finalSquad = new ArrayList<>(sessionPlayers);

                final String finalSessionTeamId = sessionTeamId;
                int addedFromBase = 0;
                for (WorldPlayer wp : basePlayers) {
                    if (!worldPlayerIdsInSession.contains(wp.getWorldPlayerId())
                        && !removedPlayerIds.contains(wp.getWorldPlayerId())) {
                        finalSquad.add(convertWorldPlayerToSessionPlayer(wp, finalSessionTeamId));
                        addedFromBase++;
                    }
                }
                log.debug("[SQUAD-DUP]   Added from base: {}, Final squad size: {}", addedFromBase, finalSquad.size());

                return finalSquad;
            })
            .defaultIfEmpty(Collections.emptyList());
    }

    @Override
    public Mono<List<SessionPlayer>> getSessionTeamSquad(UUID userId, String sessionTeamId) {
        log.debug("[SQUAD-DUP] getSessionTeamSquad - userId: {}, sessionTeamId: {}", userId, sessionTeamId);
        return sessionService.continueCareer(userId)
            .map(career -> {
                List<String> squadPlayerIds = career.getSquadPlayerIds(sessionTeamId);
                log.debug("[SQUAD-DUP]   squadPlayerIds count: {}, values: {}", squadPlayerIds.size(), squadPlayerIds);

                List<SessionPlayer> squad = career.getTeamSquad(sessionTeamId);
                log.debug("[SQUAD-DUP]   Found {} players in sessionPlayers map", squad.size());

                // Log de los IDs para debug
                Set<String> ids = squad.stream()
                    .map(p -> p.getWorldPlayerId() + " (sessionId=" + p.getSessionPlayerId() + ")")
                    .collect(Collectors.toSet());
                log.debug("[SQUAD-DUP]   Player IDs: {}", ids);

                return squad;
            })
            .defaultIfEmpty(Collections.emptyList());
    }

    private SessionPlayer convertWorldPlayerToSessionPlayer(WorldPlayer worldPlayer, String teamId) {
        // V25D33-F0-mapping: propagate height + skills from WorldPlayer so
        // the squad view exposes them (F5 UI) and the engine can read them.
        return SessionPlayer.cloneFromWorldPlayer(
            worldPlayer.getWorldPlayerId(),
            worldPlayer.getName(),
            worldPlayer.getPosition(),
            worldPlayer.getAge(),
            worldPlayer.calculateOverall(),
            teamId,
            worldPlayer.getHeightCm(),
            worldPlayer.getSkillLevels()
        );
    }
}

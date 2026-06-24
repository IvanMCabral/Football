package com.footballmanager.application.service.career;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MVP1-lineup-cancha-1.5: tests para {@link UserPlayerManagementUseCaseImpl#getUserSquad}.
 *
 * <p>Verifica que getUserSquad usa {@code CareerSessionService.getCareerFromCache}
 * (en lugar del viejo {@code getCareer}) y que cuando la career no existe
 * retorna una lista vacía en lugar de un Mono.empty() (que Spring WebFlux
 * mapearía a 404). De este modo, la ruta que el smoke REVISOR detectó con
 * 422 al re-abrir el squad modal queda blindada.
 */
@ExtendWith(MockitoExtension.class)
class UserPlayerManagementUseCaseImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private CareerSessionService sessionService;

    @Mock
    private WorldService worldService;

    private UserPlayerManagementUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UserPlayerManagementUseCaseImpl(sessionService, worldService);
    }

    private CareerSave makeCareerWithSquad(String teamId, List<String> squadIds) {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(teamId);

        CareerPlayerManager playerManager = new CareerPlayerManager();
        java.util.Map<String, SessionPlayer> sessionPlayers = new java.util.HashMap<>();
        for (String pid : squadIds) {
            SessionPlayer p = new SessionPlayer();
            p.setSessionPlayerId(pid);
            p.setName("Player " + pid);
            p.setPosition("CM");
            sessionPlayers.put(pid, p);
        }
        playerManager.setSessionPlayers(sessionPlayers);
        CareerTeamManager teamManager = new CareerTeamManager();
        teamManager.setTeamSquads(java.util.Map.of(teamId, squadIds));

        career.setPlayerManager(playerManager);
        career.setTeamManager(teamManager);
        return career;
    }

    @Test
    @DisplayName("MVP1-lineup-cancha-1.5: getUserSquad usa getCareerFromCache (no getCareer)")
    void getUserSquad_usesCache_returnsSquadWhenCareerExists() {
        CareerSave career = makeCareerWithSquad("team-1", List.of("p1", "p2", "p3"));
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.getUserSquad(USER_ID))
            .assertNext(players -> {
                assertTrue(players != null && players.size() == 3,
                    "Debe retornar el squad del team con 3 jugadores");
            })
            .verifyComplete();

        // Verificar que NO se llamó al viejo getCareer (que es el que causaba el 422)
        verify(sessionService).getCareerFromCache(USER_ID);
        verify(sessionService, never()).getCareer(USER_ID);
    }

    @Test
    @DisplayName("MVP1-lineup-cancha-1.5: getUserSquad retorna Mono.just(List.of()) cuando no hay career")
    void getUserSquad_noCareer_returnsEmptyListNotEmptyMono() {
        // Simula race: cache vacío + Redis sin la key.
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.getUserSquad(USER_ID))
            // .defaultIfEmpty(List.of()) hace que el Mono emita una lista vacía
            // (no Mono.empty() → 404).
            .assertNext(players -> {
                assertTrue(players != null && players.isEmpty(),
                    "Debe retornar lista vacía en lugar de Mono.empty()");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("MVP1-lineup-cancha-1.5: getUserSquad con career sin userSessionTeamId retorna lista vacía")
    void getUserSquad_noUserTeam_returnsEmptyList() {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(null);  // edge case: career existe pero sin team
        when(sessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.getUserSquad(USER_ID))
            .assertNext(players -> {
                assertTrue(players != null && players.isEmpty(),
                    "Debe retornar lista vacía cuando userSessionTeamId es null");
            })
            .verifyComplete();
    }
}
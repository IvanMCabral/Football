package com.footballmanager.domain.ports.in.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.view.WorldView;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de entrada - Use Case para crear un CareerSnapshot.
 *
 * Responsabilidad única: crear CareerSave (snapshot de carrera) desde WorldView.
 * Los CareerSave son inmutables una vez creados - representan el estado inicial
 * de una carrera y se modifican posteriormente durante el gameplay.
 *
 * Flujo:
 * 1. Recibir WorldView ya construida
 * 2. Filtrar equipos de la liga seleccionada
 * 3. Clonar WorldTeams -> SessionTeams
 * 4. Clonar WorldPlayers -> SessionPlayers
 * 5. Asignar a divisiones
 * 6. Generar fixtures
 * 7. Retornar CareerSave
 *
 * No carga datos de SQL/Redis - eso es responsabilidad de BuildWorldViewUseCase.
 */
public interface CreateCareerSnapshotUseCase {

    /**
     * Crea un CareerSave desde una WorldView existente.
     *
     * @param worldView Vista del mundo ya construida
     * @param leagueId ID de la liga seleccionada
     * @param userTeamId ID del equipo del usuario
     * @param difficulty Dificultad del juego
     * @param gameSpeed Velocidad del juego
     * @param teamsPerDivision Equipos por división
     * @return Mono<CareerSave> - Snapshot de carrera listo para persistir
     */
    Mono<CareerSave> create(
            WorldView worldView,
            UUID leagueId,
            UUID userTeamId,
            String difficulty,
            String gameSpeed,
            int teamsPerDivision
    );
}

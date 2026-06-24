package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.in.career.CreateCareerSnapshotUseCase;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementación de StartCareerUseCase.
 *
 * Flujo:
 * 0. Eliminar carrera anterior (si existe) para evitar duplicación de datos
 * 1. Construir WorldView desde SQL + Redis (BuildWorldViewUseCase)
 * 2. Crear CareerSave desde WorldView (CreateCareerSnapshotUseCase)
 * 3. Persistir CareerSave (CareerRepository)
 *
 * Responsibilities:
 * - Orquestar la creación de una carrera
 * - No contiene lógica de negocio de cloning (delegado a CreateCareerSnapshotUseCase)
 * - No carga datos de SQL/Redis directamente (delegado a BuildWorldViewUseCase)
 */
@Service
@RequiredArgsConstructor
public class StartCareerUseCaseImpl implements StartCareerUseCase {

    private final BuildWorldViewUseCase buildWorldViewUseCase;
    private final CreateCareerSnapshotUseCase createCareerSnapshotUseCase;
    private final CareerRepository careerRepository;

    @Override
    public Mono<CareerSave> start(UUID userId, String worldLeagueId, String worldTeamId,
                                  String difficulty, String gameSpeed, int teamsPerDivision) {
        UUID leagueId = UUID.fromString(worldLeagueId);

        // Paso 0: Eliminar carrera anterior para evitar duplicación de datos
        return careerRepository.deleteById(userId.toString())
                // Paso 1: Construir WorldView
                .then(buildWorldViewUseCase.build(userId))
                .flatMap(worldView -> {
                    // Paso 2: Crear CareerSave desde WorldView
                    return createCareerSnapshotUseCase.create(
                            worldView,
                            leagueId,
                            UUID.fromString(worldTeamId),
                            difficulty,
                            gameSpeed,
                            teamsPerDivision
                    );
                })
                .flatMap(career -> {
                    // Paso 3: Persistir CareerSave
                    return careerRepository.save(career)
                            .thenReturn(career);
                });
    }
}

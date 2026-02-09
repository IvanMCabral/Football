package com.footballmanager.application.service.fixture;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.fixture.MigrateFixturesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Implementación de UseCase para migración de fixtures.
 *
 * Maneja la migración/regeneración de fixtures por división.
 * Usa CareerFixtureService para la generación de fixtures.
 */
@Service
@RequiredArgsConstructor
public class MigrateFixturesUseCaseImpl implements MigrateFixturesUseCase {

    private final CareerRepository careerRepository;
    private final CareerFixtureService careerFixtureService;

    @Override
    public Mono<Void> migrate(String userId) {
        return careerRepository.findById(userId)
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.empty();
                }

                CareerSave career = optionalCareer.get();
                return executeMigrate(career);
            })
            .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> executeMigrate(CareerSave career) {
        TournamentState state = career.getTournamentState();
        List<MatchFixture> existingFixtures = state.getFixtures();

        // Calcular fixtures esperados
        int expectedFixtures = career.getSeasonManager().getDivisions().stream()
            .mapToInt(d -> careerFixtureService.calculateTotalRounds(d.getTeamCount()) * (d.getTeamCount() / 2))
            .sum();

        // Si ya tiene todos los fixtures, no hacer nada
        if (existingFixtures.size() >= expectedFixtures) {
            return Mono.empty();
        }

        // Usar CareerFixtureService compartido
        careerFixtureService.setupCareerFixtures(career, false);

        // Configurar totalRounds de la división del usuario
        Division userDivision = career.getUserDivision();
        if (userDivision != null) {
            int totalRounds = careerFixtureService.calculateTotalRounds(userDivision.getTeamCount());
            state.setTotalRounds(totalRounds);
        }

        return careerRepository.save(career);
    }
}

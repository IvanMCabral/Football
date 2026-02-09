package com.footballmanager.application.service.fixture;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.port.in.fixture.RegenerateFixturesUseCase;
import com.footballmanager.domain.service.FixtureGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementación de UseCase para regeneración completa de fixtures.
 *
 * Regenera todos los fixtures de la carrera, reseteando
 * tournamentState y standings.
 */
@Service
@RequiredArgsConstructor
public class RegenerateFixturesUseCaseImpl implements RegenerateFixturesUseCase {

    private final CareerRepository careerRepository;
    private final FixtureGenerator fixtureGenerator;

    @Override
    public Mono<Void> regenerate(UUID userId) {
        return careerRepository.findById(userId.toString())
                .flatMap(optionalCareer -> {
                    if (optionalCareer.isEmpty()) {
                        return Mono.empty();
                    }

                    CareerSave career = optionalCareer.get();
                    return executeRegenerate(career);
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> executeRegenerate(CareerSave career) {
        int numTeams = career.getAllSessionTeams().size();
        int totalRounds = fixtureGenerator.calculateTotalRounds(numTeams, true);

        // Convertir SessionTeam a TeamId
        List<TeamId> teamIds = career.getAllSessionTeams().stream()
                .map(st -> TeamId.of(UUID.fromString(st.getSessionTeamId())))
                .toList();

        // Generar fixture con FixtureGenerator
        List<FixtureGenerator.FixtureRound> rounds = fixtureGenerator.generate(teamIds, true);

        // Convertir FixtureGenerator.FixtureRound a MatchFixture
        List<MatchFixture> newFixtures = new ArrayList<>();
        for (FixtureGenerator.FixtureRound round : rounds) {
            for (FixtureGenerator.FixtureSlot slot : round.matches()) {
                newFixtures.add(new MatchFixture(
                        UUID.randomUUID().toString(),
                        slot.home().getValue().toString(),
                        slot.away().getValue().toString(),
                        round.roundNumber()
                ));
            }
        }

        // Actualizar tournament state
        career.getTournamentState().setTotalRounds(totalRounds);
        career.getTournamentState().setCurrentRound(1);
        career.getTournamentState().setFinished(false);
        career.getTournamentState().setFixtures(newFixtures);

        // Resetear standings
        career.getTournamentState().initializeStandings(career.getAllSessionTeams());

        return careerRepository.save(career);
    }
}

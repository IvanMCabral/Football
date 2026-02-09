package com.footballmanager.application.service.career;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.port.in.career.TeamCommandUseCase;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementación de UseCase para operaciones sobre SessionTeams.
 *
 * Maneja creación, clonación y eliminación de equipos en Career.
 */
@Service
@RequiredArgsConstructor
public class TeamCommandUseCaseImpl implements TeamCommandUseCase {

    private final CareerSessionService sessionService;
    private final WorldService worldService;
    private final TeamRepository teamRepository;

    private static final String[] PREFIXES = {
        "FC", "SC", "Real", "Atlético", "Deportivo", "CD", "CF", "United", "City", "Sporting"
    };
    private static final String[] CITIES = {
        "Madrid", "Barcelona", "Valencia", "Sevilla", "Bilbao", "Milano", "Roma",
        "Manchester", "Liverpool", "London", "Munich", "Berlin", "Paris", "Lyon", "Amsterdam"
    };
    private static final String[] COUNTRIES = {
        "España", "Italia", "Inglaterra", "Alemania", "Francia", "Portugal", "Holanda"
    };

    @Override
    public Mono<CareerSave> createRandomTeam(UUID userId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String name = PREFIXES[random.nextInt(PREFIXES.length)] + " " + CITIES[random.nextInt(CITIES.length)];
        String country = COUNTRIES[random.nextInt(COUNTRIES.length)];
        BigDecimal budget = BigDecimal.valueOf(random.nextInt(10, 100) * 1_000_000L);

        return worldService.createCustomWorldTeam(userId, name, country, budget, "4-3-3")
            .map(snapshot -> snapshot.getAllWorldTeams().stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst()
                .orElseGet(() -> snapshot.getAllWorldTeams().get(snapshot.getAllWorldTeams().size() - 1)))
            .flatMap(worldTeam -> sessionService.continueCareer(userId)
                .flatMap(career -> {
                    SessionTeam team = SessionTeam.createRandom(
                        worldTeam.getWorldTeamId(), name, country, budget, "Iván");
                    career.addSessionTeam(team);
                    return sessionService.saveCareer(career);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CareerSave career = new CareerSave();
                    career.setUserId(userId);
                    SessionTeam team = SessionTeam.createRandom(
                        worldTeam.getWorldTeamId(), name, country, budget, "Iván");
                    career.addSessionTeam(team);
                    career.setUserSessionTeamId(team.getSessionTeamId());
                    return sessionService.saveCareer(career);
                })));
    }

    @Override
    public Mono<CareerSave> cloneTeamToSession(UUID userId, UUID realTeamId) {
        return teamRepository.findById(userId, realTeamId)
            .flatMap(realTeam ->
                worldService.getWorldSnapshot(userId)
                    .flatMap(snapshot -> {
                        WorldTeam worldTeam = snapshot.getAllWorldTeams().stream()
                            .filter(wt -> wt.getRealTeamId() != null && wt.getRealTeamId().equals(realTeamId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                "WorldTeam no encontrado para realTeamId: " + realTeamId));

                        return sessionService.continueCareer(userId)
                            .flatMap(career -> {
                                SessionTeam sessionTeam = SessionTeam.cloneFromRealTeam(
                                    realTeam.getId().getValue(),
                                    worldTeam.getWorldTeamId(),
                                    realTeam.getName(),
                                    realTeam.getCountry(),
                                    realTeam.getBudget(),
                                    realTeam.getFormation().toString(),
                                    "Iván");
                                career.addSessionTeam(sessionTeam);
                                return sessionService.saveCareer(career);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                CareerSave career = new CareerSave();
                                career.setUserId(userId);
                                SessionTeam sessionTeam = SessionTeam.cloneFromRealTeam(
                                    realTeam.getId().getValue(),
                                    worldTeam.getWorldTeamId(),
                                    realTeam.getName(),
                                    realTeam.getCountry(),
                                    realTeam.getBudget(),
                                    realTeam.getFormation().toString(),
                                    "Iván");
                                career.addSessionTeam(sessionTeam);
                                career.setUserSessionTeamId(sessionTeam.getSessionTeamId());
                                return sessionService.saveCareer(career);
                            }));
                    })
            );
    }

    @Override
    public Mono<CareerSave> removeSessionTeam(UUID userId, String sessionTeamId) {
        return sessionService.getCareer(userId)
            .flatMap(career -> {
                career.removeSessionTeam(sessionTeamId);
                return sessionService.saveCareer(career);
            });
    }

    @Override
    public Mono<List<SessionTeam>> getSessionTeams(UUID userId) {
        return sessionService.continueCareer(userId)
            .map(CareerSave::getAllSessionTeams)
            .defaultIfEmpty(Collections.emptyList());
    }

    @Override
    public Mono<SessionTeam> getSessionTeam(UUID userId, String sessionTeamId) {
        return sessionService.getCareer(userId)
            .map(career -> career.getSessionTeam(sessionTeamId));
    }
}

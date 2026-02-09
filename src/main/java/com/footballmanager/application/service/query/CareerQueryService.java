package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Servicio de consultas para informacion de divisiones.
 */
@Service
public class CareerQueryService {

    public record DivisionInfo(
            String divisionId,
            String displayName,
            Integer divisionNumber,
            Integer teamCount
    ) {}

    public record CareerStatus(
            String careerId,
            String userSessionTeamId,
            Integer currentRound,
            Integer totalRounds,
            Boolean isFinished,
            Integer squadSize,
            Integer freePlayersCount,
            String careerPhase,
            Integer season
    ) {}

    /**
     * Obtiene informacion de todas las divisiones.
     */
    public Mono<List<DivisionInfo>> getDivisions(CareerSave career) {
        return Mono.fromCallable(() -> {
            return career.getSeasonManager().getDivisions().stream()
                    .map(division -> new DivisionInfo(
                            division.getDivisionId(),
                            division.getDisplayName(),
                            division.getDivisionNumber(),
                            division.getTeamCount()
                    ))
                    .toList();
        });
    }
}

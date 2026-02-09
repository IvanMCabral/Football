package com.footballmanager.domain.port.in.career;

import com.footballmanager.domain.model.entity.CareerSave;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StartCareerUseCase {
    Mono<CareerSave> start(UUID userId, String worldLeagueId, String worldTeamId,
                           String difficulty, String gameSpeed, int teamsPerDivision);
}

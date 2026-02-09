package com.footballmanager.domain.port.in.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface LineupCommandUseCase {
    Mono<LineupDTO> autoSelectLineup(UUID userId, String formationCode);
    Mono<LineupDTO> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds);
    Mono<Void> confirmLineup(UUID userId);
}

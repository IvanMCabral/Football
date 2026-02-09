package com.footballmanager.domain.port.in.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LineupQueryUseCase {
    Mono<LineupDTO> getCurrentLineup(UUID userId);
}

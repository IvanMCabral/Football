package com.footballmanager.domain.port.in.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface LineupCommandUseCase {
    Mono<LineupDTO> autoSelectLineup(UUID userId, String formationCode);

    /**
     * Manual-select legacy: solo playerIds. La subdivision se infiere on-the-fly.
     */
    Mono<LineupDTO> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds);

    /**
     * Manual-select con slots (MVP1-lineup-cancha-1).
     * Si {@code slots} es null o vacío, equivale al overload legacy.
     */
    Mono<LineupDTO> manualSelectLineupWithSlots(UUID userId, String formationCode,
                                               List<String> playerIds,
                                               List<LineupSlotDTO> slots);

    Mono<Void> confirmLineup(UUID userId);
}

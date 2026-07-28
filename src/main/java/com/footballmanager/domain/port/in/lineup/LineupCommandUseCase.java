package com.footballmanager.domain.port.in.lineup;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface LineupCommandUseCase {
    Mono<LineupView> autoSelectLineup(UUID userId, String formationCode);

    /**
     * Manual-select legacy: solo playerIds. La subdivision se infiere on-the-fly.
     */
    Mono<LineupView> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds);

    /**
     * Manual-select con slots (MVP1-lineup-cancha-1).
     * Si {@code slots} es null o vacío, equivale al overload legacy.
     */
    Mono<LineupView> manualSelectLineupWithSlots(UUID userId, String formationCode,
                                               List<String> playerIds,
                                               List<LineupSlot> slots);

    Mono<Void> confirmLineup(UUID userId);
}

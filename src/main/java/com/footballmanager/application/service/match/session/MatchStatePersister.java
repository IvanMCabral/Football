package com.footballmanager.application.service.match.session;

import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;

import java.util.UUID;

/**
 * Persiste el estado de un partido de forma asíncrona.
 */
public class MatchStatePersister {

    private final UUID matchId;
    private final UUID userId;
    private final MatchStateRepository stateRepository;

    public MatchStatePersister(UUID matchId, UUID userId, MatchStateRepository stateRepository) {
        this.matchId = matchId;
        this.userId = userId;
        this.stateRepository = stateRepository;
    }

    public void persistAsync(MatchState state) {
        try {
            stateRepository.save(userId, state).subscribe(
                    saved -> {},
                    error -> {}
            );
        } catch (Exception e) {
        }
    }
}

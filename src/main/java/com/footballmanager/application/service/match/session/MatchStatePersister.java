package com.footballmanager.application.service.match.session;

import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persiste el estado de un partido de forma asíncrona.
 */
public class MatchStatePersister {

    private static final Logger log = LoggerFactory.getLogger(MatchStatePersister.class);

    private final UUID matchId;
    private final UUID userId;
    private final MatchStateRepository stateRepository;
    private final CareerWriteContext lifecycleContext;

    public MatchStatePersister(UUID matchId, UUID userId, MatchStateRepository stateRepository,
                               CareerWriteContext lifecycleContext) {
        this.matchId = matchId;
        this.userId = userId;
        this.stateRepository = stateRepository;
        this.lifecycleContext = lifecycleContext;
    }

    public Mono<Void> persist(MatchState state) {
        if (lifecycleContext == null) {
            return Mono.error(new IllegalStateException("match state persistence requires lifecycle context"));
        }
        return stateRepository.save(userId, state, lifecycleContext)
                .doOnError(error -> log.warn(
                        "Failed to persist match state matchId={} userId={}: {}",
                        matchId, userId, error.getMessage()))
                .then();
    }
}

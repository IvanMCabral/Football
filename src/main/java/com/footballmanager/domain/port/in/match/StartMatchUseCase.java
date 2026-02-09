package com.footballmanager.domain.port.in.match;

import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.function.Consumer;

public interface StartMatchUseCase {
    Flux<MatchStateSnapshot> execute(UUID userId, UUID matchId, Consumer<MatchStateSnapshot> onFinishCallback);
}

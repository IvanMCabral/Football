package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.port.in.match.MatchSimulationUseCase;
import com.footballmanager.domain.ports.out.match.MatchCommandRepository;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;
import com.footballmanager.domain.service.MatchCommandApplier;
import com.footballmanager.domain.service.MatchSimulator;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchSimulationUseCaseImpl implements MatchSimulationUseCase {

    private final MatchStateRepository matchStateRepository;
    private final MatchCommandRepository matchCommandRepository;
    private final MatchCommandApplier commandApplier;
    private final MatchSimulator matchSimulator;

    @Override
    public Mono<MatchState> createMatchState(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId,
                                             CareerWriteContext context) {
        if (context == null || !userId.equals(context.ownerId())) {
            return Mono.error(new IllegalArgumentException("match state lifecycle context does not match owner"));
        }
        MatchState state = new MatchState(matchId, userId, context.careerId(), context.expectedGeneration());
        state.setHomeTeamId(homeTeamId);
        state.setAwayTeamId(awayTeamId);
        return matchStateRepository.save(userId, state, context);
    }

    @Override
    public Mono<MatchState> advanceMatch(UUID userId, UUID matchId, int toMinute) {
        return matchStateRepository.findById(userId, matchId)
                .zipWith(matchCommandRepository.findPendingCommands(userId, matchId))
                .map(tuple -> {
                    MatchState state = tuple.getT1();
                    var commands = tuple.getT2();

                    return commandApplier.apply(state, commands);
                })
                .map(state -> matchSimulator.simulateReal(state, toMinute))
                .flatMap(state -> writeContext(userId, state)
                        .flatMap(context -> matchStateRepository.save(userId, state, context)
                                .then(matchCommandRepository.deleteCommandsWithContext(userId, matchId, context))
                                .thenReturn(state)));
    }

    @Override
    public Mono<MatchState> applyCommand(UUID userId, UUID matchId, MatchCommand command) {
        Mono<Void> save = matchStateRepository.findById(userId, matchId)
                .flatMap(state -> writeContext(userId, state)
                        .flatMap(context -> matchCommandRepository.saveCommandWithContext(userId, matchId, command, context)))
                .switchIfEmpty(Mono.error(new IllegalStateException("match command requires an active career state")));
        return save
                .then(matchStateRepository.findById(userId, matchId));
    }

    @Override
    public Mono<MatchState> getMatchState(UUID userId, UUID matchId) {
        return matchStateRepository.findById(userId, matchId);
    }

    private Mono<CareerWriteContext> writeContext(UUID userId, MatchState state) {
        if (state == null || state.getCareerId() == null || state.getCareerId().isBlank()) {
            return Mono.error(new IllegalStateException("career lifecycle context is required"));
        }
        if (state.getUserId() == null || !userId.toString().equals(state.getUserId())
                || state.getLifecycleGeneration() == null || state.getLifecycleGeneration().isBlank()) {
            return Mono.error(new IllegalStateException("match state requires stored lifecycle context"));
        }
        return Mono.just(new CareerWriteContext(userId, state.getCareerId(), state.getLifecycleGeneration()));
    }
}

package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.port.in.match.MatchSimulationUseCase;
import com.footballmanager.domain.ports.out.match.MatchCommandRepository;
import com.footballmanager.domain.ports.out.match.MatchStateRepository;
import com.footballmanager.domain.service.MatchCommandApplier;
import com.footballmanager.domain.service.MatchSimulator;
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
    public Mono<MatchState> createMatchState(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId) {
        MatchState state = new MatchState(matchId);
        state.setHomeTeamId(homeTeamId);
        state.setAwayTeamId(awayTeamId);

        return matchStateRepository.save(userId, state);
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
                .flatMap(state ->
                        matchStateRepository.save(userId, state)
                                .then(matchCommandRepository.deleteCommands(userId, matchId))
                                .thenReturn(state)
                );
    }

    @Override
    public Mono<MatchState> applyCommand(UUID userId, UUID matchId, MatchCommand command) {
        return matchCommandRepository.saveCommand(userId, matchId, command)
                .then(matchStateRepository.findById(userId, matchId));
    }

    @Override
    public Mono<MatchState> getMatchState(UUID userId, UUID matchId) {
        return matchStateRepository.findById(userId, matchId);
    }
}

package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.entity.Match;
import com.footballmanager.domain.model.valueobject.MatchId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.GameId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MatchRepository {
    Mono<Void> save(UUID userId, Match match);
    Mono<Match> findById(UUID userId, MatchId id);
    Flux<Match> findByTeamId(UUID userId, TeamId teamId);
    Flux<Match> findScheduledMatches(UUID userId);
    Flux<Match> findAll(UUID userId);
    Flux<Match> findByGameId(UUID userId, GameId gameId);
    Mono<Boolean> existsById(UUID userId, MatchId id);
    Mono<Void> deleteById(UUID userId, MatchId id);
}

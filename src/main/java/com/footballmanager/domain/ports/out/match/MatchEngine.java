package com.footballmanager.domain.ports.out.match;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.entity.MatchResult;
import reactor.core.publisher.Mono;

public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
}


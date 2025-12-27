package com.footballmanager.domain.ports.out;

import com.footballmanager.domain.model.Team;
import com.footballmanager.domain.model.MatchResult;
import reactor.core.publisher.Mono;

public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
}

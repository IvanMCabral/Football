package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.application.service.simulation.detailed.MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
@Slf4j
class TestHarnessReplayService {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final MatchContextFactory matchContextFactory;
    private final BaselineStateStoragePort baselineStoragePort;
    private final DetailedMatchStoragePort v24StoragePort;

    Mono<MatchFixture> replay(CareerSave career, String matchId, long seed) {
        MatchFixture fixture = findFixture(career, matchId);
        fixture.reset();

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            return Mono.error(new IllegalStateException(
                "SessionTeam not found for match " + matchId
                    + " (home=" + fixture.getHomeTeamId()
                    + ", away=" + fixture.getAwayTeamId() + ")"));
        }

        MatchContext context = matchContextFactory.build(career, fixture, home, away, seed);
        DetailedMatchResult result = new DetailedMatchEngine().simulate(context, new Random(seed));
        completeFixture(career, fixture, result);
        log.trace("replayMatch complete: matchId={}, newResult=({}-{}), seed={}",
            matchId, result.homeGoals(), result.awayGoals(), seed);
        return replaceStoredDetail(career, fixture, home, away, context, result)
            .then(persistBaseline(career, matchId, seed, context))
            .then(careerRepository.save(career))
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(fixture);
    }

    private MatchFixture findFixture(CareerSave career, String matchId) {
        return career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));
    }

    private Mono<Void> persistBaseline(CareerSave career, String matchId, long seed, MatchContext context) {
        try {
            String careerId = career.getData().getCareerId();
            BaselineState baseline = BaselineState.empty(careerId, seed, context);
            return baselineStoragePort.save(careerId, baseline)
                .doOnSuccess(ignored -> log.trace(
                    "replayMatch: persisted baseline for matchId={}, careerId={}, seed={}",
                    matchId, careerId, seed))
                .onErrorResume(e -> {
                    log.warn("replayMatch: failed to persist baseline for matchId={}, continuing: {}",
                        matchId, e.getMessage());
                    return Mono.empty();
                });
        } catch (Exception e) {
            log.warn("replayMatch: failed to prepare baseline for matchId={}, continuing: {}",
                matchId, e.getMessage());
            return Mono.empty();
        }
    }

    private void completeFixture(CareerSave career, MatchFixture fixture, DetailedMatchResult result) {
        MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
            result.homeGoals(), result.awayGoals(),
            result.homePossession(), result.awayPossession(),
            result.homeShots(), result.awayShots());
        fixture.complete(resultData);
        career.getTournamentState().updateStandingsWithResult(fixture);
    }

    private Mono<Void> replaceStoredDetail(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam home,
            SessionTeam away,
            MatchContext context,
            DetailedMatchResult result) {
        String careerId = career.getData().getCareerId();
        DetailedMatchData newDetail;
        try {
            newDetail = DetailedMatchData.fromResult(
                    careerId,
                    career.getCurrentSeason(),
                    fixture.getRound(),
                    home.getName() != null ? home.getName() : "",
                    away.getName() != null ? away.getName() : "",
                    home.getFormation(),
                    away.getFormation(),
                    result,
                    List.<PlayerMatchRatingDto>of(),
                    lineupSnapshot(context.homeStartingPlayers()),
                    lineupSnapshot(context.homeBenchPlayers()),
                    lineupSnapshot(context.awayStartingPlayers()),
                    lineupSnapshot(context.awayBenchPlayers())
            );
        } catch (Exception e) {
            log.warn("replayMatch: failed to prepare new detailed match detail for matchId={}, continuing: {}",
                fixture.getMatchId(), e.getMessage());
            return Mono.empty();
        }
        Mono<Void> deleteDetail = v24StoragePort.deleteByMatchId(careerId, fixture.getMatchId());
        if (deleteDetail == null) {
            deleteDetail = Mono.empty();
        }
        Mono<Void> saveDetail;
        try {
            saveDetail = v24StoragePort.save(careerId, newDetail);
            if (saveDetail == null) {
                saveDetail = Mono.empty();
            }
        } catch (Exception e) {
            saveDetail = Mono.error(e);
        }
        return deleteDetail
            .onErrorResume(e -> {
                log.warn("replayMatch: failed to clear old detailed match detail for matchId={}, continuing: {}",
                    fixture.getMatchId(), e.getMessage());
                return Mono.empty();
            })
            .then(saveDetail)
            .onErrorResume(e -> {
                log.warn("replayMatch: failed to persist new detailed match detail for matchId={}, continuing: {}",
                    fixture.getMatchId(), e.getMessage());
                return Mono.empty();
            });
    }

    private List<MatchLineupPlayerDto> lineupSnapshot(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        return players.stream()
            .filter(java.util.Objects::nonNull)
            .map(MatchLineupPlayerDto::fromSessionPlayer)
            .toList();
    }
}

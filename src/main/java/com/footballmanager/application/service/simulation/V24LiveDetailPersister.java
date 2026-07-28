package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerRatingsAssembler;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.util.List;

final class V24LiveDetailPersister {

    @FunctionalInterface
    interface MutationCallback {
        void apply(CareerSave career, V24DetailedMatchResult result, LiveRoundMutationTracking tracking);
    }

    private final boolean persistDetail;
    private final boolean useV24DetailedEngine;
    private final V24DetailedMatchStoragePort storagePort;
    private final V24PlayerRatingsAssembler ratingsAssembler;
    private final MutationCallback mutationCallback;
    private final Logger log;

    V24LiveDetailPersister(
            boolean persistDetail,
            boolean useV24DetailedEngine,
            V24DetailedMatchStoragePort storagePort,
            V24PlayerRatingsAssembler ratingsAssembler,
            MutationCallback mutationCallback,
            Logger log) {
        this.persistDetail = persistDetail;
        this.useV24DetailedEngine = useV24DetailedEngine;
        this.storagePort = storagePort;
        this.ratingsAssembler = ratingsAssembler;
        this.mutationCallback = mutationCallback;
        this.log = log;
    }

    Mono<Void> persist(
            CareerSave career,
            V24DetailedMatchResult result,
            String homeTeamId,
            String awayTeamId,
            LiveRoundMutationTracking tracking) {
        if (!canPersist(result)) {
            return Mono.empty();
        }
        try {
            String careerId = career.getData().getCareerId();
            String matchId = result.matchId();
            int rawSeason = career.getSeasonManager().getCurrentSeason();
            Integer seasonNumber = rawSeason > 0 ? rawSeason : 1;
            Integer round = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .map(MatchFixture::getRound)
                    .orElse(career.getTournamentState().getCurrentRound());

            SessionTeam homeTeam = career.getSessionTeam(homeTeamId);
            SessionTeam awayTeam = career.getSessionTeam(awayTeamId);
            MatchFixture playerFixture = new MatchFixture(matchId, homeTeamId, awayTeamId, round);
            List<V24PlayerMatchRatingDto> ratings =
                    ratingsAssembler.assemblePlayerRatings(career, playerFixture, result);

            V24DetailedMatchData detail = V24DetailedMatchData.fromResult(
                    careerId,
                    seasonNumber,
                    round,
                    homeTeam != null ? homeTeam.getName() : "Home",
                    awayTeam != null ? awayTeam.getName() : "Away",
                    resolveFormation(homeTeam),
                    resolveFormation(awayTeam),
                    result,
                    ratings);

            return storagePort.save(careerId, detail)
                    .doOnSuccess(ignored -> {
                        log.info("persistV24Detail careerId={}, matchId={}, season={}, round={}, timeline={}, playerRatings={}, key=career:{}:match-detail:{}",
                                careerId, matchId, seasonNumber, round,
                                result.timeline().events().size(), ratings.size(), careerId, matchId);
                        log.info("[V24-DETAIL-PERSIST] saved match detail careerId={}, matchId={}, season={}, round={}",
                                careerId, matchId, seasonNumber, round);
                        mutationCallback.apply(career, result, tracking);
                    })
                    .onErrorResume(e -> {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        log.warn("[V24-DETAIL-PERSIST] Failed to persist for match {}: {} [cause: {}], continuing",
                                result.matchId(), e.getMessage(), cause.getMessage());
                        return Mono.empty();
                    });
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[V24-DETAIL-PERSIST] Failed to persist for match {}: {} [cause: {}], continuing",
                    result != null ? result.matchId() : "unknown", e.getMessage(), cause.getMessage());
            return Mono.empty();
        }
    }

    private boolean canPersist(V24DetailedMatchResult result) {
        if (!persistDetail) {
            log.debug("[V24-DETAIL-PERSIST] Skipped for match {}: persistDetail=false",
                    result != null ? result.matchId() : "null");
            return false;
        }
        if (!useV24DetailedEngine) {
            log.debug("[V24-DETAIL-PERSIST] Skipped for match {}: useV24DetailedEngine=false",
                    result != null ? result.matchId() : "null");
            return false;
        }
        if (result == null) {
            log.warn("[V24-DETAIL-PERSIST] Skipped: v24Result is null");
            return false;
        }
        if (storagePort == null) {
            log.warn("[V24-DETAIL-PERSIST] Skipped for match {}: storagePort is null", result.matchId());
            return false;
        }
        return true;
    }

    private String resolveFormation(SessionTeam team) {
        if (team == null) {
            return null;
        }
        String formation = team.getFormation();
        return (formation != null && !formation.isBlank()) ? formation : null;
    }
}

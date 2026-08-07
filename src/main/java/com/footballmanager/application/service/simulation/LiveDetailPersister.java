package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.detailed.PlayerRatingsAssembler;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.util.List;

final class LiveDetailPersister {

    @FunctionalInterface
    interface MutationCallback {
        void apply(CareerSave career, DetailedMatchResult result, LiveRoundMutationTracking tracking);
    }

    private final boolean persistDetail;
    private final boolean useDetailedMatchEngine;
    private final DetailedMatchStoragePort storagePort;
    private final PlayerRatingsAssembler ratingsAssembler;
    private final MutationCallback mutationCallback;
    private final Logger log;

    LiveDetailPersister(
            boolean persistDetail,
            boolean useDetailedMatchEngine,
            DetailedMatchStoragePort storagePort,
            PlayerRatingsAssembler ratingsAssembler,
            MutationCallback mutationCallback,
            Logger log) {
        this.persistDetail = persistDetail;
        this.useDetailedMatchEngine = useDetailedMatchEngine;
        this.storagePort = storagePort;
        this.ratingsAssembler = ratingsAssembler;
        this.mutationCallback = mutationCallback;
        this.log = log;
    }

    Mono<Void> persist(
            CareerSave career,
            DetailedMatchResult result,
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
            List<PlayerMatchRatingDto> ratings =
                    ratingsAssembler.assemblePlayerRatings(career, playerFixture, result);

            DetailedMatchData detail = DetailedMatchData.fromResult(
                    careerId,
                    seasonNumber,
                    round,
                    homeTeam != null ? homeTeam.getName() : "Home",
                    awayTeam != null ? awayTeam.getName() : "Away",
                    resolveFormation(homeTeam),
                    resolveFormation(awayTeam),
                    result,
                    ratings);

            if (career.getLifecycleGeneration() == null || career.getLifecycleGeneration().isBlank()) {
                return Mono.error(new IllegalStateException("detail writer requires lifecycle generation"));
            }
            Mono<Void> detailWrite = storagePort.saveWithContext(new CareerWriteContext(
                    career.getUserId(), careerId, career.getLifecycleGeneration()), detail);
            return detailWrite
                    .doOnSuccess(ignored -> {
                        log.info("persistDetailedMatchDetail careerId={}, matchId={}, season={}, round={}, timeline={}, playerRatings={}, key=career:{}:match-detail:{}",
                                careerId, matchId, seasonNumber, round,
                                result.timeline().events().size(), ratings.size(), careerId, matchId);
                        log.info("[DETAIL-PERSIST] saved match detail careerId={}, matchId={}, season={}, round={}",
                                careerId, matchId, seasonNumber, round);
                        mutationCallback.apply(career, result, tracking);
                    })
                    .onErrorResume(e -> {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        log.warn("[DETAIL-PERSIST] Failed to persist for match {}: {} [cause: {}], continuing",
                                result.matchId(), e.getMessage(), cause.getMessage());
                        return Mono.empty();
                    });
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[DETAIL-PERSIST] Failed to persist for match {}: {} [cause: {}], continuing",
                    result != null ? result.matchId() : "unknown", e.getMessage(), cause.getMessage());
            return Mono.empty();
        }
    }

    private boolean canPersist(DetailedMatchResult result) {
        if (!persistDetail) {
            log.debug("[DETAIL-PERSIST] Skipped for match {}: persistDetail=false",
                    result != null ? result.matchId() : "null");
            return false;
        }
        if (!useDetailedMatchEngine) {
            log.debug("[DETAIL-PERSIST] Skipped for match {}: useDetailedMatchEngine=false",
                    result != null ? result.matchId() : "null");
            return false;
        }
        if (result == null) {
            log.warn("[DETAIL-PERSIST] Skipped: detailedResult is null");
            return false;
        }
        if (storagePort == null) {
            log.warn("[DETAIL-PERSIST] Skipped for match {}: storagePort is null", result.matchId());
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

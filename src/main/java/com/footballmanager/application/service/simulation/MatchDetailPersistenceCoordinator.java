package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.detailed.PlayerRatingsAssembler;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

final class MatchDetailPersistenceCoordinator {

    private static final Duration DETAIL_PERSIST_TIMEOUT = Duration.ofSeconds(5);

    private final boolean persistDetail;
    private final DetailedMatchStoragePort storagePort;
    private final PlayerRatingsAssembler playerRatingsAssembler;
    private final Logger log;

    MatchDetailPersistenceCoordinator(
            boolean persistDetail,
            DetailedMatchStoragePort storagePort,
            PlayerRatingsAssembler playerRatingsAssembler,
            Logger log) {
        this.persistDetail = persistDetail;
        this.storagePort = storagePort;
        this.playerRatingsAssembler = playerRatingsAssembler;
        this.log = log;
    }

    void persistDetailedMatchDetail(
            CareerSave career,
            MatchFixture fixture,
            String homeTeamName,
            String awayTeamName,
            DetailedMatchResult detailedResult,
            MatchContext context) {
        if (!persistDetail || storagePort == null) {
            return;
        }
        /*
         * simulateLeagueRound is a synchronous league/batch workflow: callers
         * expect fixture results, standings and optional detailed match detail
         * snapshots to be settled before the round returns. This bounded block
         * is intentionally kept at that batch boundary, outside WebFlux request
         * pipelines.
         */
        try {
            String careerId = career.getData().getCareerId();
            Integer seasonNumber = career.getSeasonManager().getCurrentSeason();
            Integer round = fixture.getRound();
            List<PlayerMatchRatingDto> playerRatings =
                    playerRatingsAssembler.assemblePlayerRatings(career, fixture, detailedResult);
            DetailedMatchData detail = DetailedMatchData.fromResult(
                    careerId,
                    seasonNumber,
                    round,
                    homeTeamName,
                    awayTeamName,
                    resolveFormation(career, fixture.getHomeTeamId()),
                    resolveFormation(career, fixture.getAwayTeamId()),
                    detailedResult,
                    playerRatings,
                    lineupSnapshot(context.homeStartingPlayers()),
                    lineupSnapshot(context.homeBenchPlayers()),
                    lineupSnapshot(context.awayStartingPlayers()),
                    lineupSnapshot(context.awayBenchPlayers())
            );
            Mono<Void> detailWrite = career.getLifecycleGeneration() == null
                    ? storagePort.save(careerId, detail) // pre-lifecycle batch fixture only
                    : storagePort.saveWithContext(new CareerWriteContext(
                            career.getUserId(), careerId, career.getLifecycleGeneration()), detail);
            detailWrite
                    .doOnSuccess(ignored -> log.debug(
                            "Detail saved for fixture {} in career {}",
                            fixture.getMatchId(), careerId))
                    .doOnError(e -> log.warn(
                            "Failed to persist detail for fixture {}: {}, continuing round",
                            fixture.getMatchId(), e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .block(DETAIL_PERSIST_TIMEOUT);
        } catch (Exception e) {
            log.warn("Failed to persist detail for fixture {}: {}, continuing round",
                    fixture.getMatchId(), e.getMessage());
        }
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

    private String resolveFormation(CareerSave career, String teamId) {
        if (career == null || teamId == null || teamId.isBlank()) {
            return null;
        }
        return resolveFormation(career.getSessionTeam(teamId));
    }

    private String resolveFormation(SessionTeam team) {
        if (team == null) {
            return null;
        }
        String formation = team.getFormation();
        return (formation != null && !formation.isBlank()) ? formation : null;
    }
}

package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.ShotLocation;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.MatchPreviewSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;

@Component
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessPreviewRunner {

    private final MatchContextFactory matchContextFactory;

    MatchPreviewSummary run(
            CareerSave career,
            MatchFixture fixture,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException(
                "SessionTeam not found for match " + fixture.getMatchId()
                    + " (home=" + fixture.getHomeTeamId()
                    + ", away=" + fixture.getAwayTeamId() + ")");
        }

        int safeSeedCount = Math.max(1, Math.min(50, seedCount));
        boolean userIsHome = controlledTeamIsHome(career, fixture, controlledTeamSide);
        PreviewSums sums = new PreviewSums();
        for (int i = 0; i < safeSeedCount; i++) {
            long seed = seedStart + i;
            MatchContext context = matchContextFactory.build(career, fixture, home, away, seed);
            DetailedMatchResult result = new DetailedMatchEngine()
                .simulate(context, new Random(seed));
            addSample(sums, result, userIsHome);
        }

        SessionTeam controlledTeam = userIsHome ? home : away;
        String side = userIsHome ? "HOME" : "AWAY";
        int n = safeSeedCount;
        return new MatchPreviewSummary(
            fixture.getMatchId(),
            side,
            seedStart,
            seedStart + safeSeedCount - 1L,
            safeSeedCount,
            controlledTeam.getName(),
            controlledTeam.getFormation(),
            round2(sums.goalsFor / n),
            round2(sums.goalsAgainst / n),
            round2((sums.goalsFor - sums.goalsAgainst) / n),
            round2(sums.possessionFor / n),
            round2(sums.shotsFor / n),
            round2(sums.shotsAgainst / n),
            round2((sums.shotsFor - sums.shotsAgainst) / n),
            round3(sums.xgFor / n),
            round3(sums.xgAgainst / n),
            round3((sums.xgFor - sums.xgAgainst) / n),
            round2(sums.centralShotsFor / n),
            round2(sums.wideShotsFor / n),
            round2(sums.longShotsFor / n),
            round2(sums.centralShotsAgainst / n),
            round2(sums.wideShotsAgainst / n),
            round2(sums.longShotsAgainst / n)
        );
    }

    private boolean controlledTeamIsHome(
            CareerSave career,
            MatchFixture fixture,
            String controlledTeamSide) {
        String side = controlledTeamSide == null
            ? "USER"
            : controlledTeamSide.trim().toUpperCase(Locale.ROOT);
        if ("HOME".equals(side)) return true;
        if ("AWAY".equals(side)) return false;
        return Objects.equals(fixture.getHomeTeamId(), career.getUserSessionTeamId());
    }

    private void addSample(PreviewSums sums, DetailedMatchResult result, boolean userIsHome) {
        sums.goalsFor += userIsHome ? result.homeGoals() : result.awayGoals();
        sums.goalsAgainst += userIsHome ? result.awayGoals() : result.homeGoals();
        sums.possessionFor += userIsHome ? result.homePossession() : result.awayPossession();
        sums.shotsFor += userIsHome ? result.homeShots() : result.awayShots();
        sums.shotsAgainst += userIsHome ? result.awayShots() : result.homeShots();
        sums.xgFor += userIsHome ? result.homeXg() : result.awayXg();
        sums.xgAgainst += userIsHome ? result.awayXg() : result.homeXg();

        String ownTeamId = userIsHome ? result.homeTeamId() : result.awayTeamId();
        if (result.timeline() == null || result.timeline().events() == null) {
            return;
        }
        for (DetailedMatchEvent event : result.timeline().events()) {
            if (!isShotLike(event)) continue;
            boolean ownShot = Objects.equals(event.teamId(), ownTeamId);
            ShotLocation location = event.shotCoordinate() != null
                ? event.shotCoordinate().location()
                : null;
            if (location == ShotLocation.PENALTY_AREA_WIDE) {
                if (ownShot) sums.wideShotsFor += 1.0; else sums.wideShotsAgainst += 1.0;
            } else if (location == ShotLocation.OUTSIDE_BOX || location == ShotLocation.LONG_RANGE) {
                if (ownShot) sums.longShotsFor += 1.0; else sums.longShotsAgainst += 1.0;
            } else {
                if (ownShot) sums.centralShotsFor += 1.0; else sums.centralShotsAgainst += 1.0;
            }
        }
    }

    private boolean isShotLike(DetailedMatchEvent event) {
        if (event == null || event.xg() <= 0.0) return false;
        return event.type() == DetailedMatchEventType.SHOT
            || event.type() == DetailedMatchEventType.SHOT_ON_TARGET
            || event.type() == DetailedMatchEventType.MISS
            || event.type() == DetailedMatchEventType.BLOCK
            || event.type() == DetailedMatchEventType.GOAL;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class PreviewSums {
        double goalsFor;
        double goalsAgainst;
        double possessionFor;
        double shotsFor;
        double shotsAgainst;
        double xgFor;
        double xgAgainst;
        double centralShotsFor;
        double wideShotsFor;
        double longShotsFor;
        double centralShotsAgainst;
        double wideShotsAgainst;
        double longShotsAgainst;
    }
}

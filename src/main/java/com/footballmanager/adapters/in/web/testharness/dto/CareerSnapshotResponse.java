package com.footballmanager.adapters.in.web.testharness.dto;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V24D20-TESTHARNESS — response body for {@code GET /api/v1/test-harness/career/snapshot}.
 *
 * <p>Built by the controller from the {@link CareerSave} returned by
 * {@code TestHarnessUseCase.snapshot}. Includes a computed
 * {@link SquadHealthSummary} that REVISOR can diff against pre-smoke
 * baselines.
 */
public record CareerSnapshotResponse(
    String careerId,
    String userId,
    Integer currentRound,
    Integer totalRounds,
    Boolean finished,
    CareerPhase careerPhase,
    String userSessionTeamId,
    String formation,
    Map<String, String> teamStarting11Formation,
    List<FixtureDebugDTO> fixtures,
    SquadHealthSummary squadHealthSummary
) {

    /**
     * Lightweight fixture view — only the fields REVISOR needs to verify
     * pre/post smoke state.
     */
    public record FixtureDebugDTO(
        String matchId,
        String homeTeamId,
        String awayTeamId,
        int round,
        String status,
        Integer homeGoals,
        Integer awayGoals
    ) {
        public static FixtureDebugDTO from(MatchFixture f) {
            Integer hg = null, ag = null;
            if (f.getResult() != null) {
                hg = f.getResult().getHomeGoals();
                ag = f.getResult().getAwayGoals();
            }
            return new FixtureDebugDTO(
                f.getMatchId(),
                f.getHomeTeamId(),
                f.getAwayTeamId(),
                f.getRound(),
                f.getStatus() != null ? f.getStatus().name() : null,
                hg,
                ag
            );
        }
    }

    /**
     * Squad health counters — single source of truth for "is the squad
     * pristine before smoke" verification.
     */
    public record SquadHealthSummary(
        int squadSize,
        int injuredCount,
        int suspendedCount,
        int yellowCardsCount,
        int redCardsCount
    ) {
        public static SquadHealthSummary from(List<SessionPlayer> squad) {
            int injured = 0, suspended = 0, yellows = 0, reds = 0;
            for (SessionPlayer p : squad) {
                if (Boolean.TRUE.equals(p.getInjured())) injured++;
                if (Boolean.TRUE.equals(p.getSuspended())) suspended++;
                if (p.getYellowCards() != null) yellows += p.getYellowCards();
                if (p.getRedCards() != null) reds += p.getRedCards();
            }
            return new SquadHealthSummary(squad.size(), injured, suspended, yellows, reds);
        }
    }

    public static CareerSnapshotResponse from(CareerSave career) {
        String userSessionTeamId = career.getUserSessionTeamId();
        String formation = career.getTeamStarting11Formation().get(userSessionTeamId);

        List<FixtureDebugDTO> fixturesDebug = new ArrayList<>();
        if (career.getTournamentState() != null
            && career.getTournamentState().getFixtures() != null) {
            for (MatchFixture f : career.getTournamentState().getFixtures()) {
                fixturesDebug.add(FixtureDebugDTO.from(f));
            }
        }

        SquadHealthSummary health = SquadHealthSummary.from(
            career.getTeamSquad(userSessionTeamId));

        return new CareerSnapshotResponse(
            career.getCareerId(),
            career.getUserId() != null ? career.getUserId().toString() : null,
            career.getTournamentState() != null ? career.getTournamentState().getCurrentRound() : null,
            career.getTournamentState() != null ? career.getTournamentState().getTotalRounds() : null,
            career.getTournamentState() != null ? career.getTournamentState().getFinished() : null,
            career.getTournamentState() != null ? career.getTournamentState().getCareerPhase() : null,
            userSessionTeamId,
            formation,
            career.getTeamStarting11Formation(),
            fixturesDebug,
            health
        );
    }
}

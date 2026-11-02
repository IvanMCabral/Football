package com.footballmanager.adapters.in.web.dashboard;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import java.math.BigDecimal;
import java.util.Comparator;

/** Small read model derived from the already loaded owner career. No additional transport. */
public record DashboardCareerOverview(Club club, NextMatch nextMatch) {
    public record Club(String name, String country, BigDecimal budget, String formation, Integer morale) {}
    public record NextMatch(String matchId, int round, String homeTeamName, String awayTeamName, boolean home, String status) {}

    static DashboardCareerOverview from(CareerSave career) {
        if (career == null || career.getUserSessionTeamId() == null) return null;
        String ownId = career.getUserSessionTeamId();
        SessionTeam team = career.getSessionTeam(ownId);
        Club club = team == null ? null : new Club(team.getName(), team.getCountry(), team.getBudget(), team.getFormation(), team.getMorale());
        var tournament = career.getTournamentState();
        if (tournament == null || tournament.getFixtures() == null || Boolean.TRUE.equals(tournament.getFinished()))
            return new DashboardCareerOverview(club, null);
        var next = tournament.getFixtures().stream().filter(java.util.Objects::nonNull)
                .filter(f -> f.getRound() >= tournament.getCurrentRound() && f.getResult() == null && f.getStatus() != MatchStatus.CANCELLED)
                .filter(f -> ownId.equals(f.getHomeTeamId()) || ownId.equals(f.getAwayTeamId()))
                .min(Comparator.comparingInt(MatchFixture::getRound)).orElse(null);
        return new DashboardCareerOverview(club, next == null ? null : new NextMatch(next.getMatchId(), next.getRound(),
                name(career, next.getHomeTeamId()), name(career, next.getAwayTeamId()), ownId.equals(next.getHomeTeamId()),
                next.getStatus() == null ? "PENDING" : next.getStatus().name()));
    }

    private static String name(CareerSave career, String id) {
        SessionTeam team = career.getSessionTeam(id);
        return team == null || team.getName() == null ? "Equipo por confirmar" : team.getName();
    }
}

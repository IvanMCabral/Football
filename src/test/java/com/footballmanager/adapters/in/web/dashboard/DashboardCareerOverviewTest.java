package com.footballmanager.adapters.in.web.dashboard;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DashboardCareerOverviewTest {
    @Test void overviewUsesOwnClubAndEarliestUnplayedFixtureIncludingAByeInTheCurrentRound() {
        CareerSave career = new CareerSave(); career.setUserSessionTeamId("own");
        var own = SessionTeam.custom("world-own", "Club propio", "AR", new BigDecimal("1234567.89"), "4-4-2");
        own.setSessionTeamId("own"); own.setMorale(82); career.addSessionTeam(own);
        var rival = SessionTeam.custom("world-rival", "Rival", "AR", BigDecimal.TEN, "4-3-3");
        rival.setSessionTeamId("rival"); career.addSessionTeam(rival);
        TournamentState tournament = new TournamentState(); tournament.setCurrentRound(1); tournament.setTotalRounds(38);
        tournament.setFixtures(List.of(new MatchFixture("unrelated", "rival", "other", 1),
                new MatchFixture("later", "own", "rival", 3),
                new MatchFixture("cancelled", "own", "rival", 1, MatchStatus.CANCELLED, null),
                new MatchFixture("next", "rival", "own", 2)));
        career.setTournamentState(tournament);
        var overview = DashboardCareerOverview.from(career);
        assertThat(overview.club().name()).isEqualTo("Club propio");
        assertThat(overview.club().budget()).isEqualByComparingTo("1234567.89");
        assertThat(overview.club().morale()).isEqualTo(82);
        assertThat(overview.nextMatch().matchId()).isEqualTo("next");
        assertThat(overview.nextMatch().round()).isEqualTo(2);
        assertThat(overview.nextMatch().home()).isFalse();
        assertThat(overview.nextMatch().homeTeamName()).isEqualTo("Rival");
        assertThat(overview.nextMatch().awayTeamName()).isEqualTo("Club propio");
        assertThat(tournament.getFixtures()).hasSize(4);
        tournament.setFinished(true);
        assertThat(DashboardCareerOverview.from(career).nextMatch()).isNull();
    }

    @Test void missingCareerAndLegacyClubDoNotInventValues() {
        assertThat(DashboardCareerOverview.from(null)).isNull();
        CareerSave empty = new CareerSave();
        assertThat(DashboardCareerOverview.from(empty)).isNull();
        empty.setUserSessionTeamId("missing");
        var result = DashboardCareerOverview.from(empty);
        assertThat(result.club()).isNull();
        assertThat(result.nextMatch()).isNull();
    }
}

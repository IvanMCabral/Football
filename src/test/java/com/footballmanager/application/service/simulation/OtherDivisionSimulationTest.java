package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.service.MatchSimulator;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OtherDivisionSimulationTest {
    @Test
    void catchesUpOtherDivisionsWithoutChangingLiveResultsOrFutureFixtures() {
        var career = new CareerSave(); career.setUserSessionTeamId("own");
        var own = new Division("Own", 1); own.addTeam("own"); own.addTeam("rival");
        var other = new Division("Other", 2); other.addTeam("other-a"); other.addTeam("other-b");
        career.getSeasonManager().setDivisions(List.of(own, other));
        var state = career.getTournamentState();
        for (String id : List.of("own", "rival", "other-a", "other-b")) state.getStandings().put(id, new TeamStandings(id, id));
        state.getFixtures().addAll(List.of(new MatchFixture("live", "own", "rival", 2),
                new MatchFixture("older", "other-a", "other-b", 1),
                new MatchFixture("current", "other-b", "other-a", 2),
                new MatchFixture("future", "other-a", "other-b", 3)));
        state.processMatchResult("live", 3, 0, List.of());
        var simulator = mock(MatchSimulator.class);
        when(simulator.simulateQuick(anyString(), anyString(), anyInt(), anyInt())).thenReturn(new MatchSimulator.MatchResult(2, 1));
        var league = new LeagueSimulator(simulator);
        league.simulateOtherDivisionsThroughRound(career, 2);
        league.simulateOtherDivisionsThroughRound(career, 2);
        verify(simulator, times(2)).simulateQuick(anyString(), anyString(), anyInt(), anyInt());
        assertEquals(3, state.getFixture("live").getResult().getHomeGoals());
        assertEquals(1, state.getStandings().get("own").getPlayed());
        assertEquals(2, state.getStandings().get("other-a").getPlayed());
        assertEquals(2, state.getStandings().get("other-b").getPlayed());
        assertNull(state.getFixture("future").getResult());
    }
}

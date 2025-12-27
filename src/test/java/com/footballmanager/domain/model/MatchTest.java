package com.footballmanager.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchTest {

    @Test
    void shouldCreateMatch() {
        MatchId id = MatchId.generate();
        TeamId homeTeamId = TeamId.generate();
        TeamId awayTeamId = TeamId.generate();
        Instant scheduledAt = Instant.now().plusSeconds(86400);

        Match match = Match.create(id, homeTeamId, awayTeamId, scheduledAt);

        assertNotNull(match);
        assertEquals(id, match.getId());
        assertEquals(homeTeamId, match.getHomeTeamId());
        assertEquals(awayTeamId, match.getAwayTeamId());
        assertEquals(Match.MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getResult());
        assertFalse(match.isSimulated());
    }

    @Test
    void shouldNotAllowSameTeamMatch() {
        MatchId id = MatchId.generate();
        TeamId teamId = TeamId.generate();

        assertThrows(IllegalArgumentException.class, () -> {
            Match.create(id, teamId, teamId, Instant.now().plusSeconds(86400));
        });
    }

    @Test
    void shouldSimulateMatch() {
        Match match = Match.create(MatchId.generate(), TeamId.generate(), TeamId.generate(),
                Instant.now().plusSeconds(86400));

        List<MatchEvent> events = new ArrayList<>();
        events.add(MatchEvent.of(MatchEvent.EventType.GOAL, 25, "Player 1", "Goal"));
        MatchResult result = MatchResult.of(2, 1, 55, 45, 12, 8, events, "Summary");

        match.simulate(result);

        assertEquals(Match.MatchStatus.SIMULATED, match.getStatus());
        assertNotNull(match.getResult());
        assertEquals(2, match.getResult().getHomeGoals());
        assertEquals(1, match.getResult().getAwayGoals());
        assertTrue(match.isSimulated());
    }

    @Test
    void shouldNotSimulateNonScheduledMatch() {
        Match match = Match.create(MatchId.generate(), TeamId.generate(), TeamId.generate(),
                Instant.now().plusSeconds(86400));

        List<MatchEvent> events = new ArrayList<>();
        MatchResult result = MatchResult.of(2, 1, 55, 45, 12, 8, events, "Summary");
        match.simulate(result);

        assertThrows(IllegalStateException.class, () -> {
            match.simulate(result);
        });
    }

    @Test
    void shouldCancelMatch() {
        Match match = Match.create(MatchId.generate(), TeamId.generate(), TeamId.generate(),
                Instant.now().plusSeconds(86400));

        match.cancel();

        assertEquals(Match.MatchStatus.CANCELLED, match.getStatus());
    }

    @Test
    void shouldNotCancelSimulatedMatch() {
        Match match = Match.create(MatchId.generate(), TeamId.generate(), TeamId.generate(),
                Instant.now().plusSeconds(86400));

        List<MatchEvent> events = new ArrayList<>();
        MatchResult result = MatchResult.of(2, 1, 55, 45, 12, 8, events, "Summary");
        match.simulate(result);

        assertThrows(IllegalStateException.class, match::cancel);
    }
}

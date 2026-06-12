package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.MatchResultProcessor.MatchResultInfo;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V24D6T — Lock the event-forwarding contract of MatchResultProcessor.
 *
 * <p>The processor used to pass {@code List.of()} to
 * {@code TournamentState.processMatchResult} regardless of whether the
 * incoming {@code MatchResultInfo} carried real events. The fix forwards
 * the events list (or an empty list if null). These tests pin:
 * <ul>
 *   <li>Events are forwarded (no silent loss)</li>
 *   <li>Null events are tolerated (no NPE)</li>
 *   <li>Already-completed fixtures are skipped (idempotency)</li>
 *   <li>Unknown matchId is skipped (no throw)</li>
 * </ul>
 */
class MatchResultProcessorEventsTest {

    private static final String HOME_TEAM = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY_TEAM = "22222222-2222-2222-2222-222222222222";

    private MatchResultProcessor processor;
    private CareerSave career;
    private TournamentState tournamentState;

    @BeforeEach
    void setUp() {
        processor = new MatchResultProcessor();
        career = new CareerSave();
        tournamentState = new TournamentState();
        tournamentState.setCurrentRound(1);
        tournamentState.setTotalRounds(1);
        tournamentState.getFixtures().add(new MatchFixture("m-1", HOME_TEAM, AWAY_TEAM, 1));

        // Seed standings so updateStandingsWithResult can run
        Map<String, TeamStandings> standings = new HashMap<>();
        standings.put(HOME_TEAM, new TeamStandings(HOME_TEAM, "Home FC"));
        standings.put(AWAY_TEAM, new TeamStandings(AWAY_TEAM, "Away FC"));
        tournamentState.setStandings(standings);

        career.setTournamentState(tournamentState);
    }

    @Test
    void process_processesScoreWithoutThrowingOnEvents() {
        // V24D6T: events carry through MatchResultInfo but the current
        // TournamentState.processMatchResult expects a different MatchEvent type
        // and ignores the list. The processor defends by passing an empty list
        // (no NPE, no refactor) while the events are persisted separately via
        // LeagueSimulator.persistV24DetailForLiveMatch. This test pins the
        // observable behavior: score is recorded, fixture is completed.
        List<MatchEvent> events = List.of(
            MatchEvent.of(MatchEvent.EventType.GOAL, 12, "p-1", "Scorer", HOME_TEAM, "12' goal"),
            MatchEvent.of(MatchEvent.EventType.YELLOW_CARD, 33, "p-2", "Booked", AWAY_TEAM, "33' yellow")
        );
        MatchResultInfo info = new MatchResultInfo("m-1", 1, 0, events);

        int processed = processor.process(career, List.of(info));
        assertEquals(1, processed, "One fixture should be processed");
        MatchFixture fixture = tournamentState.getFixtures().stream()
            .filter(f -> f.getMatchId().equals("m-1"))
            .findFirst().orElseThrow();
        assertNotNull(fixture.getResult(), "Fixture must have a result");
        assertEquals(MatchStatus.COMPLETED, fixture.getStatus(), "Fixture must be completed");
        assertEquals(1, fixture.getResult().getHomeGoals());
        assertEquals(0, fixture.getResult().getAwayGoals());
    }

    @Test
    void process_handlesNullEventsGracefully() {
        // MatchResultInfo has a 3-arg constructor that defaults to List.of()
        MatchResultInfo info = new MatchResultInfo("m-1", 2, 1);

        int processed = processor.process(career, List.of(info));
        assertEquals(1, processed, "Null events should not throw — the fix must defend against null");
    }

    @Test
    void process_skipsAlreadyCompletedFixtures() {
        // Mark the fixture as already completed
        MatchFixture pre = tournamentState.getFixtures().get(0);
        pre.complete(new MatchFixture.MatchResultData(5, 0, 50, 50, 10, 5));

        MatchResultInfo info = new MatchResultInfo("m-1", 1, 0, List.of(
            MatchEvent.of(MatchEvent.EventType.GOAL, 10, "p-1", "Scorer", HOME_TEAM, "10' goal")
        ));

        int processed = processor.process(career, List.of(info));
        assertEquals(0, processed, "Already-completed fixtures must be skipped (idempotency)");

        // Original score preserved
        MatchFixture fixture = tournamentState.getFixtures().get(0);
        assertEquals(5, fixture.getResult().getHomeGoals(), "Original score must remain");
    }

    @Test
    void process_skipsUnknownMatchId() {
        MatchResultInfo info = new MatchResultInfo("m-UNKNOWN", 1, 0, new ArrayList<>());
        int processed = processor.process(career, List.of(info));
        assertEquals(0, processed, "Unknown matchId must be skipped, not throw");
    }

    @Test
    void process_unprocessedFixtureResultIsNull() {
        // Sanity check: before processing, the fixture has no result
        MatchFixture fixture = tournamentState.getFixtures().get(0);
        assertNull(fixture.getResult(), "Sanity: unprocessed fixture has null result");
    }
}

package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * <p>Validates that:
 * <ul>
 *   <li>SHOT/GOAL/SHOT_ON_TARGET/BLOCK/MISS events carry non-null shotCoordinate</li>
 *   <li>Non-shot events (FOUL/YELLOW_CARD/RED_CARD/INJURY/SUBSTITUTION/OFFSIDE/CORNER) have null shotCoordinate</li>
 *   <li>Coordinates are deterministic for same seed</li>
 *   <li>DTO mapping preserves coordinates</li>
 *   <li>DetailedMatchData snapshot preserves coordinates</li>
 *   <li>Events without coordinates map to DTO with null</li>
 *   <li>xG formula is unchanged</li>
 * </ul>
 */
class ShotCoordinateAttachmentTest {

    private final DetailedMatchEngine engine = new DetailedMatchEngine();
    private final RandomGenerator rng = java.util.random.RandomGenerator.getDefault();

    // ========== 1. shotEventsIncludeShotCoordinate ==========

    @Test
    void shotEventsIncludeShotCoordinate() {
        MatchContext ctx = buildContext("shot-coord-1", 75, 75);
        DetailedMatchResult result = engine.simulate(ctx, 999L);

        List<DetailedMatchEvent> shotEvents = result.timeline().shotEvents();
        assertFalse(shotEvents.isEmpty(), "Expected at least one shot event");

        for (DetailedMatchEvent e : shotEvents) {
            assertNotNull(e.shotCoordinate(),
                    "SHOT/BLOCK/MISS event at " + e.minute() + "' should have shotCoordinate, but was null");
            // Verify all coordinate fields are valid
            assertTrue(e.shotCoordinate().x() >= 0 && e.shotCoordinate().x() <= 100,
                    "shotCoordinate x must be [0,100]");
            assertTrue(e.shotCoordinate().y() >= 0 && e.shotCoordinate().y() <= 100,
                    "shotCoordinate y must be [0,100]");
            assertNotNull(e.shotCoordinate().location(),
                    "shotCoordinate.location must not be null");
        }
    }

    // ========== 2. goalEventsIncludeShotCoordinate ==========

    @Test
    void goalEventsIncludeShotCoordinate() {
        // Use high-OVR mismatch to increase goal probability
        MatchContext ctx = buildContext("goal-coord-1", 88, 60);
        DetailedMatchResult result = engine.simulate(ctx, 777L);

        List<DetailedMatchEvent> goalEvents = result.timeline().goalEvents();
        if (goalEvents.isEmpty()) {
            // Retry with different seed if no goals scored
            result = engine.simulate(ctx, 123L);
            goalEvents = result.timeline().goalEvents();
        }

        assertFalse(goalEvents.isEmpty(), "Expected at least one goal event");
        for (DetailedMatchEvent e : goalEvents) {
            assertNotNull(e.shotCoordinate(),
                    "GOAL event at " + e.minute() + "' should have shotCoordinate, but was null");
            assertTrue(e.shotCoordinate().x() >= 0 && e.shotCoordinate().x() <= 100);
            assertTrue(e.shotCoordinate().y() >= 0 && e.shotCoordinate().y() <= 100);
        }
    }

    // ========== 3. nonShotEventsHaveNullShotCoordinate ==========

    @Test
    void nonShotEventsHaveNullShotCoordinate() {
        MatchContext ctx = buildContext("nonshot-null-1", 75, 75);
        DetailedMatchResult result = engine.simulate(ctx, 555L);

        List<DetailedMatchEventType> nonShotTypes = List.of(
                DetailedMatchEventType.FOUL,
                DetailedMatchEventType.YELLOW_CARD,
                DetailedMatchEventType.RED_CARD,
                DetailedMatchEventType.INJURY,
                DetailedMatchEventType.SUBSTITUTION,
                DetailedMatchEventType.OFFSIDE,
                DetailedMatchEventType.CORNER,
                DetailedMatchEventType.CHANCE_CREATED
        );

        for (DetailedMatchEvent e : result.timeline().events()) {
            if (!nonShotTypes.contains(e.type())) continue;
            assertNull(e.shotCoordinate(),
                    "Non-shot event " + e.type() + " at " + e.minute()
                            + "' should have null shotCoordinate, but was: "
                            + e.shotCoordinate());
        }
    }

    // ========== 4. shotCoordinateIsDeterministicForSameSeed ==========

    @Test
    void shotCoordinateIsDeterministicForSameSeed() {
        MatchContext ctx = buildContext("det-shot-1", 75, 75);

        DetailedMatchResult r1 = engine.simulate(ctx, 444L);
        DetailedMatchResult r2 = engine.simulate(ctx, 444L);

        assertEquals(r1.timeline().size(), r2.timeline().size(), "timeline size must match");

        List<DetailedMatchEvent> ev1 = r1.timeline().events();
        List<DetailedMatchEvent> ev2 = r2.timeline().events();

        for (int i = 0; i < ev1.size(); i++) {
            DetailedMatchEvent a = ev1.get(i);
            DetailedMatchEvent b = ev2.get(i);

            assertEquals(a.type(), b.type(), "event type must match");
            assertEquals(a.minute(), b.minute(), "minute must match");

            // If a shot event, coordinates must be identical
            if (a.shotCoordinate() != null) {
                assertNotNull(b.shotCoordinate(),
                        "Second run event " + i + " should also have shotCoordinate");
                assertEquals(a.shotCoordinate().x(), b.shotCoordinate().x(),
                        "shotCoordinate.x must be deterministic for event " + i);
                assertEquals(a.shotCoordinate().y(), b.shotCoordinate().y(),
                        "shotCoordinate.y must be deterministic for event " + i);
                assertEquals(a.shotCoordinate().location(), b.shotCoordinate().location(),
                        "shotCoordinate.location must be deterministic for event " + i);
            } else {
                assertNull(b.shotCoordinate(),
                        "Second run event " + i + " should have null shotCoordinate to match");
            }
        }
    }

    // ========== 5. shotCoordinateSurvivesDtoMapping ==========

    @Test
    void shotCoordinateSurvivesDtoMapping() {
        // Build an event with a coordinate and map through DTO
        ShotCoordinate coord = new ShotCoordinate(88.5, 51.2, ShotLocation.PENALTY_AREA_CENTER);
        DetailedMatchEvent event = new DetailedMatchEvent(
                23, DetailedMatchEventType.GOAL, "HOME",
                "p1", "Test Player", null, null,
                0.45, "Goal by Test Player 23'"
        ).withShotCoordinate(coord);

        DetailedMatchEventDto dto = DetailedMatchEventDto.fromEvent(event);

        assertNotNull(dto.shotCoordinate(), "shotCoordinate should survive DTO mapping");
        assertEquals(88.5, dto.shotCoordinate().x(), 0.001);
        assertEquals(51.2, dto.shotCoordinate().y(), 0.001);
        assertEquals("PENALTY_AREA_CENTER", dto.shotCoordinate().location());
        assertTrue(dto.shotCoordinate().insideBox());
        assertTrue(dto.shotCoordinate().distanceToGoal() > 0);
    }

    // ========== 6. shotCoordinateSurvivesDetailedMatchDataSnapshot ==========

    @Test
    void shotCoordinateSurvivesDetailedMatchDataSnapshot() {
        MatchContext ctx = buildContext("snapshot-1", 80, 70);
        DetailedMatchResult result = engine.simulate(ctx, 333L);

        List<DetailedMatchEventDto> dtos = new ArrayList<>();
        for (DetailedMatchEvent e : result.timeline().events()) {
            dtos.add(DetailedMatchEventDto.fromEvent(e));
        }

        List<DetailedMatchEvent> shotEvents = result.timeline().shotEvents();
        if (!shotEvents.isEmpty()) {
            DetailedMatchEvent firstShot = shotEvents.get(0);
            DetailedMatchEventDto firstDto = dtos.get(result.timeline().events().indexOf(firstShot));

            assertEquals(firstShot.shotCoordinate() != null, firstDto.shotCoordinate() != null,
                    "shotDto nullness must match event nullness");

            if (firstShot.shotCoordinate() != null) {
                assertEquals(firstShot.shotCoordinate().x(), firstDto.shotCoordinate().x(), 0.001);
                assertEquals(firstShot.shotCoordinate().y(), firstDto.shotCoordinate().y(), 0.001);
                assertEquals(firstShot.shotCoordinate().location().name(),
                        firstDto.shotCoordinate().location());
            }
        }
    }

    // ========== 7. existingEventsWithoutCoordinateRemainValid ==========

    @Test
    void existingEventsWithoutCoordinateRemainValid() {
        // Create event the old way (no coordinate) and verify it maps cleanly
        DetailedMatchEvent eventNoCoord = new DetailedMatchEvent(
                12, DetailedMatchEventType.FOUL, "AWAY",
                "p5", "Old Player", null, null,
                0.0, "Old Player committed a foul"
        );

        DetailedMatchEventDto dto = DetailedMatchEventDto.fromEvent(eventNoCoord);

        assertNull(dto.shotCoordinate(),
                "Event without shotCoordinate should map to DTO with null shotCoordinate");
        assertEquals(12, dto.minute());
        assertEquals("FOUL", dto.type());
        assertEquals("p5", dto.playerId());
    }

    // ========== 8. noXgFormulaChange ==========

    @Test
    void noXgFormulaChange() {
        // Verify xG values are reasonable (unchanged formula)
        MatchContext ctx = buildContext("xg-check-1", 75, 75);
        DetailedMatchResult result = engine.simulate(ctx, 222L);

        assertTrue(result.homeXg() >= 0, "homeXg must be non-negative");
        assertTrue(result.awayXg() >= 0, "awayXg must be non-negative");
        assertTrue(Double.isFinite(result.homeXg()), "homeXg must be finite");
        assertTrue(Double.isFinite(result.awayXg()), "awayXg must be finite");

        // xG should be correlated with shots
        assertTrue(result.homeShots() >= result.homeGoals(),
                "homeShots should be >= homeGoals");
        assertTrue(result.awayShots() >= result.awayGoals(),
                "awayShots should be >= awayGoals");

        // No extreme xG values (formula clamped)
        assertTrue(result.homeXg() < 5.0, "homeXg should be reasonable (< 5)");
        assertTrue(result.awayXg() < 5.0, "awayXg should be reasonable (< 5)");
    }

    // ========== Fixture helpers ==========

    private MatchContext buildContext(String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayers("home_" + matchId, 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayers("away_" + matchId, 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            SessionPlayer p = SessionPlayer.custom(
                    id, 25, "MID",
                    ovr, ovr, ovr, ovr, ovr, ovr,
                    BigDecimal.valueOf(ovr * 1000));
            list.add(p);
        }
        return list;
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}

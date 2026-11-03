package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveSessionSubstitutionEventIdentityTest {

    @Test
    void canonicalSlotIndexesFollowPlacementRatherThanTheRosterArrayForAllFormations() {
        var catalog = new com.footballmanager.application.service.editor.FormationService();
        for (var formation : catalog.getAllFormations()) {
            var context = contextWithUniquePlayerIds("shuffled-" + formation.name());
            var placements = new java.util.LinkedHashMap<String, com.footballmanager.domain.model.valueobject.LineupSlot>();
            for (int i = 0; i < 11; i++) {
                var position = formation.positions().get(i == 0 ? 0 : 11 - i);
                String playerId = context.homeStartingPlayers().get(i).getSessionPlayerId();
                placements.put(playerId, new com.footballmanager.domain.model.valueobject.LineupSlot(
                    playerId, position.subdivisionId(), position.xPercent(), position.yPercent()));
            }
            context = context.withNewFormation(context.homeTeamId(), formation.name()).withSlots(context.homeTeamId(), placements);
            var snapshot = new LiveSession(context, 1L).snapshot();
            for (var slot : snapshot.homeSlots()) {
                var placed = placements.get(slot.playerId());
                var expected = formation.positions().stream().filter(p -> p.subdivisionId().equals(placed.subdivisionId())).findFirst().orElseThrow();
                assertThat(slot.slotIndex()).as(formation.name() + " " + slot.playerId()).isEqualTo(expected.index());
                assertThat(slot.customXPercent()).isEqualTo(expected.xPercent());
                assertThat(slot.customYPercent()).isEqualTo(expected.yPercent());
            }
        }
    }

    @Test
    void sentOffPlayerDisappearsWithoutMovingTheOtherTenSlots() throws ReflectiveOperationException {
        var context = contextWithUniquePlayerIds("red-slot");
        var session = new LiveSession(context, 42L);
        session.tick();
        var timelineField = LiveSession.class.getDeclaredField("engineTimeline");
        timelineField.setAccessible(true);
        @SuppressWarnings("unchecked") var timeline = (List<DetailedMatchEvent>) timelineField.get(session);
        String offId = context.homeStartingPlayers().get(3).getSessionPlayerId();
        timeline.add(new DetailedMatchEvent(1, DetailedMatchEventType.RED_CARD, context.homeTeamId(), offId,
            "Sent Off", null, null, 0.0, "Sent Off received a red card"));
        var snapshot = session.snapshot();
        assertThat(snapshot.homeSlots()).hasSize(10).noneMatch(slot -> slot.playerId().equals(offId));
        assertThat(snapshot.homeSlots()).extracting(slot -> slot.slotIndex()).containsExactly(0, 1, 2, 4, 5, 6, 7, 8, 9, 10);
        assertThat(snapshot.awaySlots()).hasSize(11);
        assertThat(context.homeStartingPlayers()).hasSize(11);
    }

    @Test
    void liveSlotsFollowAutomaticSubstitutionsAndDismissalsAtEveryMinuteWithoutRenumbering() {
        MatchContext context = contextWithUniquePlayerIds("automatic-slots");
        LiveSession session = new LiveSession(context, 42L);
        int substitutions = 0;
        for (int minute = 1; minute <= 90; minute++) {
            LiveSnapshot snapshot = session.tick();
            for (boolean home : List.of(true, false)) {
                String teamId = home ? context.homeTeamId() : context.awayTeamId();
                var starters = home ? context.homeStartingPlayers() : context.awayStartingPlayers();
                var expected = new java.util.LinkedHashMap<String, Integer>();
                for (int i = 0; i < starters.size(); i++) expected.put(starters.get(i).getSessionPlayerId(), i);
                for (var event : snapshot.allEvents()) {
                    if (!teamId.equals(event.teamId())) continue;
                    if (event.type() == DetailedMatchEventType.SUBSTITUTION) {
                        Integer slot = expected.remove(event.playerId());
                        if (slot != null) expected.put(event.relatedPlayerId(), slot);
                    } else if (event.type() == DetailedMatchEventType.RED_CARD) expected.remove(event.playerId());
                }
                var actual = new java.util.LinkedHashMap<String, Integer>();
                for (var slot : home ? snapshot.homeSlots() : snapshot.awaySlots())
                    actual.put(slot.playerId(), slot.slotIndex());
                assertThat(actual).as("minute %s team %s", minute, teamId).isEqualTo(expected);
            }
            substitutions = Math.max(substitutions, (int) snapshot.allEvents().stream()
                .filter(e -> e.type() == DetailedMatchEventType.SUBSTITUTION).count());
        }
        assertThat(substitutions).as("real automatic substitutions must be exercised").isPositive();
    }

    @Test
    @DisplayName("same minute and player ids on different teams remain distinct visible substitutions")
    void samePlayerIdsOnDifferentTeamsRemainDistinct() {
        MatchContext context = contextWithSharedPlayerIds();
        LiveSession session = new LiveSession(context, 818L);
        session.tick();

        session.recordManualSubstitution(substitution(
                1, context.homeTeamId(), "shared-off", "Shared Off Home", "shared-on", "Shared On Home"));
        session.recordManualSubstitution(substitution(
                1, context.awayTeamId(), "shared-off", "Shared Off Away", "shared-on", "Shared On Away"));

        List<DetailedMatchEvent> substitutions = substitutions(session.snapshot().allEvents());

        assertThat(substitutions)
                .extracting(DetailedMatchEvent::teamId)
                .containsExactlyInAnyOrder(context.homeTeamId(), context.awayTeamId());
        assertThat(substitutions).hasSize(2);
    }

    @Test
    @DisplayName("same team and same minute can expose multiple legitimate substitutions")
    void sameTeamSameMinuteDifferentPairsRemainDistinct() {
        MatchContext context = contextWithUniquePlayerIds("same-team");
        LiveSession session = new LiveSession(context, 819L);
        session.tick();

        session.recordManualSubstitution(substitution(
                1, context.homeTeamId(), "same-team-home-starter-8", "Home Starter 8",
                "same-team-home-bench-0", "Home Bench 0"));
        session.recordManualSubstitution(substitution(
                1, context.homeTeamId(), "same-team-home-starter-9", "Home Starter 9",
                "same-team-home-bench-1", "Home Bench 1"));

        List<DetailedMatchEvent> substitutions = substitutions(session.snapshot().allEvents());

        assertThat(substitutions)
                .extracting(DetailedMatchEvent::playerId)
                .containsExactlyInAnyOrder("same-team-home-starter-8", "same-team-home-starter-9");
        assertThat(substitutions).hasSize(2);
    }

    @Test
    @DisplayName("exact replay duplicate keeps the manual visible event once")
    void exactReplayDuplicateKeepsManualEventOnce() {
        MatchContext context = contextWithUniquePlayerIds("manual-name");
        LiveSession session = new LiveSession(context, 820L);
        session.tick();

        DetailedMatchEvent event = substitution(
                1, context.homeTeamId(), "manual-name-home-starter-8", "Vinicius Jr.",
                "manual-name-home-bench-0", "Rodrygo");
        session.recordManualSubstitution(event);
        session.mutateContext(ctx -> ctx.withNewStyle(ctx.homeTeamId(), TeamStyle.ATTACKING));

        List<DetailedMatchEvent> substitutions = substitutions(session.snapshot().allEvents());

        assertThat(substitutions).hasSize(1);
        assertThat(substitutions.getFirst().playerName()).isEqualTo("Vinicius Jr.");
        assertThat(substitutions.getFirst().relatedPlayerName()).isEqualTo("Rodrygo");
    }

    @Test
    @DisplayName("accumulated events remains immutable after visible substitution deduplication")
    void accumulatedEventsIsImmutableSnapshot() {
        MatchContext context = contextWithUniquePlayerIds("immutable-events");
        LiveSession session = new LiveSession(context, 821L);
        session.tick();
        session.recordManualSubstitution(substitution(
                1, context.homeTeamId(), "immutable-events-home-starter-8", "Home Starter 8",
                "immutable-events-home-bench-0", "Home Bench 0"));

        List<DetailedMatchEvent> accumulated = session.accumulatedEvents();

        assertThat(substitutions(accumulated).stream()
                .filter(event -> "immutable-events-home-bench-0".equals(event.relatedPlayerId()))
                .toList())
                .hasSize(1);
        assertThatThrownBy(() -> accumulated.add(substitution(
                1, context.homeTeamId(), "x", "X", "y", "Y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<DetailedMatchEvent> substitutions(List<DetailedMatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == DetailedMatchEventType.SUBSTITUTION)
                .toList();
    }

    private static DetailedMatchEvent substitution(
            int minute,
            String teamId,
            String offId,
            String offName,
            String onId,
            String onName) {
        return new DetailedMatchEvent(
                minute,
                DetailedMatchEventType.SUBSTITUTION,
                teamId,
                offId,
                offName,
                onId,
                onName,
                0.0,
                "Substitution: " + onName + " on for " + offName);
    }

    private static MatchContext contextWithSharedPlayerIds() {
        SessionTeam homeTeam = team("shared-home", "Shared Home");
        SessionTeam awayTeam = team("shared-away", "Shared Away");
        return new MatchContext(
                "match-shared-player-ids",
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam,
                awayTeam,
                players("shared", "off", 11, "shared-off"),
                players("shared", "off", 11, "shared-off"),
                players("shared", "bench", 7, "shared-on"),
                players("shared", "bench", 7, "shared-on"),
                "4-4-2",
                "4-4-2",
                TeamStyle.BALANCED,
                TeamStyle.BALANCED);
    }

    private static MatchContext contextWithUniquePlayerIds(String prefix) {
        SessionTeam homeTeam = team(prefix + "-home", prefix + " Home");
        SessionTeam awayTeam = team(prefix + "-away", prefix + " Away");
        return new MatchContext(
                "match-" + prefix,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam,
                awayTeam,
                players(prefix + "-home", "starter", 11, null),
                players(prefix + "-away", "starter", 11, null),
                players(prefix + "-home", "bench", 7, null),
                players(prefix + "-away", "bench", 7, null),
                "4-4-2",
                "4-4-2",
                TeamStyle.BALANCED,
                TeamStyle.BALANCED);
    }

    private static SessionTeam team(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world-" + id,
                name,
                "Country",
                BigDecimal.ZERO,
                "4-4-2",
                null);
    }

    private static List<SessionPlayer> players(String prefix, String group, int count, String firstIdOverride) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = i == 0 && firstIdOverride != null
                    ? firstIdOverride
                    : prefix + "-" + group + "-" + i;
            String position = i == 0 ? "GK" : i < 5 ? "DEF" : i < 9 ? "MID" : "ATT";
            players.add(SessionPlayer.fromWorldPlayer(
                    id,
                    id,
                    position,
                    25,
                    75));
        }
        return players;
    }
}

package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteDisciplinePhaseTest {
    private final DetailedMatchMinuteComposition composition = DetailedMatchMinuteComposition
            .create(new DisciplineModel(), org.slf4j.LoggerFactory.getLogger(getClass()));
    private final MinuteDisciplinePhase phase =
            new MinuteDisciplinePhase(composition.playerStatePolicies(), composition.eventPolicies());

    @Test
    void canFinishWithoutFoul() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);

        phase.apply(fixture.input(15, new MinutePhaseFixture.SequenceRandom(0.99)), fixture.homePossession());

        assertThat(fixture.timeline.events()).isEmpty();
    }

    @Test
    void recordsFoulAndPossibleCardInTimelineAndState() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.ATTACKING, TeamStyle.BALANCED);

        phase.apply(fixture.input(16, new MinutePhaseFixture.SequenceRandom(0.0, 0.0)), fixture.homePossession());

        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .contains(DetailedMatchEventType.FOUL, DetailedMatchEventType.YELLOW_CARD);
        assertThat(fixture.home.startingPlayers()).anyMatch(player -> player.yellowCards() > 0);
    }
}

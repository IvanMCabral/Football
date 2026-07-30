package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteRestartEventPhaseTest {
    private final MinuteRestartEventPhase phase = new MinuteRestartEventPhase();

    @Test
    void recordsCornerThenOffsideInOrder() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.ATTACKING, TeamStyle.BALANCED);

        phase.apply(fixture.input(33, new MinutePhaseFixture.SequenceRandom(0.0, 0.0)), fixture.homePossession());

        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .containsExactly(DetailedMatchEventType.CORNER, DetailedMatchEventType.OFFSIDE);
    }

    @Test
    void recordsNoRestartEventWhenRollsMissAndSuppressesOffsideForDefensiveStyle() {
        MinutePhaseFixture noEvent = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);
        phase.apply(noEvent.input(33, new MinutePhaseFixture.SequenceRandom(0.99, 0.99)), noEvent.homePossession());
        assertThat(noEvent.timeline.events()).isEmpty();

        MinutePhaseFixture defensive = new MinutePhaseFixture(TeamStyle.DEFENSIVE, TeamStyle.BALANCED);
        phase.apply(defensive.input(33, new MinutePhaseFixture.SequenceRandom(0.99, 0.0)), defensive.homePossession());
        assertThat(defensive.timeline.events()).isEmpty();
    }
}

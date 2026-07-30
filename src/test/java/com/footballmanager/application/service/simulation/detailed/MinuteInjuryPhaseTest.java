package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteInjuryPhaseTest {
    private final MinuteInjuryPhase phase = new MinuteInjuryPhase(new InjuryModel());

    @Test
    void canFinishWithoutInjury() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);

        phase.apply(fixture.input(22, new MinutePhaseFixture.SequenceRandom(0.99)), fixture.homePossession());

        assertThat(fixture.timeline.events()).isEmpty();
    }

    @Test
    void injuryChangesPlayerStateAndTimeline() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.ATTACKING, TeamStyle.BALANCED);

        phase.apply(fixture.input(22, new MinutePhaseFixture.SequenceRandom(0.0)), fixture.homePossession());

        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .containsExactly(DetailedMatchEventType.INJURY);
        assertThat(fixture.home.startingPlayers()).anyMatch(PlayerMatchState::injured);
    }
}

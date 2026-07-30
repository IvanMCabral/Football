package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteAttackPhaseTest {
    private final DetailedMatchMinuteComposition composition = DetailedMatchMinuteComposition
            .create(new DisciplineModel(), org.slf4j.LoggerFactory.getLogger(getClass()));
    private final MinuteAttackPhase phase =
            new MinuteAttackPhase(composition.tacticalPolicies(), composition.eventPolicies(), composition.playerStatePolicies());

    @Test
    void canFinishMinuteWithoutChance() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.DEFENSIVE, TeamStyle.BALANCED);

        MinuteAttackState state = phase.apply(
                fixture.input(10, new MinutePhaseFixture.SequenceRandom(0.99, 0.99, 0.99)),
                fixture.homePossession());

        assertThat(state.possession()).isNotNull();
        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .doesNotContain(DetailedMatchEventType.GOAL);
    }

    @Test
    void highAttackingContextCreatesObservableShotOrChanceEvents() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.ATTACKING, TeamStyle.DEFENSIVE);

        phase.apply(fixture.input(10, new MinutePhaseFixture.SequenceRandom(0.0, 0.0, 0.0, 0.0, 0.0)),
                fixture.homePossession());

        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .anyMatch(type -> type == DetailedMatchEventType.CHANCE_CREATED
                        || type == DetailedMatchEventType.GOAL
                        || type == DetailedMatchEventType.MISS
                        || type == DetailedMatchEventType.SHOT_ON_TARGET
                        || type == DetailedMatchEventType.BLOCK);
    }
}

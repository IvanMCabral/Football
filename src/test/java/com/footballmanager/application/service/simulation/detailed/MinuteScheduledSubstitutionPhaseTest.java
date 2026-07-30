package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteScheduledSubstitutionPhaseTest {
    private final MinuteSubstitutionPolicies policies =
            new MinuteSubstitutionPolicies(new SubstitutionEngine(), new SubstitutionEngine());
    private final MinuteScheduledSubstitutionPhase phase =
            new MinuteScheduledSubstitutionPhase(LoggerFactory.getLogger(getClass()), policies);

    @Test
    void doesNothingWhenNoScheduledSubstitutionExists() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);
        phase.apply(fixture.input(30, new Random(1)));

        assertThat(fixture.timeline.events()).isEmpty();
        assertThat(fixture.appliedScheduledSubs).isEmpty();
    }

    @Test
    void appliesSubstitutionAtTheScheduledMinuteOnlyOnce() {
        MinutePhaseFixture fixture = fixtureWithScheduledSubAt(30);
        MinuteSimulationInput input = fixture.input(30, new Random(1));

        phase.apply(input);
        phase.apply(input);

        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .containsExactly(DetailedMatchEventType.SUBSTITUTION);
        assertThat(fixture.appliedScheduledSubs).hasSize(1);
        assertThat(fixture.home.startingPlayers().get(10).onPitch()).isFalse();
        assertThat(fixture.home.benchPlayers().get(0).onPitch()).isTrue();
    }

    @Test
    void ignoresSubstitutionAtWrongMinute() {
        MinutePhaseFixture fixture = fixtureWithScheduledSubAt(30);
        phase.apply(fixture.input(29, new Random(1)));

        assertThat(fixture.timeline.events()).isEmpty();
        assertThat(fixture.appliedScheduledSubs).isEmpty();
    }

    private static MinutePhaseFixture fixtureWithScheduledSubAt(int minute) {
        return new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED) {
            final MatchContext scheduledContext = context.withManualSubstitution(
                    context.homeTeamId(),
                    context.homeStartingPlayers().get(10).getSessionPlayerId(),
                    context.homeBenchPlayers().get(0).getSessionPlayerId(),
                    minute);

            @Override
            MinuteSimulationInput input(int inputMinute, Random random) {
                return new MinuteSimulationInput(
                        new MinuteSimulationConfig(scheduledContext, 0.5, 0.5, 1.0),
                        new MinuteMatchState(
                                random, home, away, timeline, homeSelector, awaySelector, appliedScheduledSubs),
                        inputMinute);
            }
        };
    }
}

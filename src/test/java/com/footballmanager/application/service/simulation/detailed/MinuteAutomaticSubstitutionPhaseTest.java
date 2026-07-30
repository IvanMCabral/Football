package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteAutomaticSubstitutionPhaseTest {
    private final MinuteAutomaticSubstitutionPhase phase = new MinuteAutomaticSubstitutionPhase(
            new MinuteSubstitutionPolicies(new SubstitutionEngine(), new SubstitutionEngine()));

    @Test
    void doesNotSubstituteBeforeMinuteSixty() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);

        phase.apply(fixture.input(59, new Random(1)), fixture.homePossession());

        assertThat(fixture.timeline.events()).isEmpty();
    }

    @Test
    void substitutesTeamWithoutPossessionFromMinuteSixty() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);
        for (int i = 0; i < 80; i++) {
            fixture.away.startingPlayers().get(10).drainStamina(1);
        }

        phase.apply(fixture.input(60, new Random(1)), fixture.homePossession());

        assertThat(fixture.timeline.events())
                .extracting(DetailedMatchEvent::type)
                .containsExactly(DetailedMatchEventType.SUBSTITUTION);
        assertThat(fixture.timeline.events().get(0).teamId()).isEqualTo(fixture.context.awayTeamId());
    }
}

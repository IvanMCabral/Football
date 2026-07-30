package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinutePossessionPhaseTest {
    private final MinutePossessionPhase phase =
            new MinutePossessionPhase(new MinutePlayerStatePolicies(
                    new FatigueModel(), new DisciplineModel(), new InjuryModel()));

    @Test
    void assignsPossessionToHomeAndDrainsBothTeams() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);
        int homeStamina = fixture.home.startingPlayers().get(1).currentStamina();
        int awayStamina = fixture.away.startingPlayers().get(1).currentStamina();

        MinutePossessionState state = phase.apply(
                fixture.input(1, new MinutePhaseFixture.SequenceRandom(0.1)),
                fixture.tacticalState(0.8));

        assertThat(state.homeHasPossession()).isTrue();
        assertThat(fixture.home.possessionTicks()).isEqualTo(1);
        assertThat(fixture.home.startingPlayers().get(1).currentStamina()).isLessThan(homeStamina);
        assertThat(fixture.away.startingPlayers().get(1).currentStamina()).isLessThan(awayStamina);
    }

    @Test
    void assignsPossessionToAwayDeterministically() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.BALANCED);

        MinutePossessionState state = phase.apply(
                fixture.input(1, new MinutePhaseFixture.SequenceRandom(0.9)),
                fixture.tacticalState(0.2));

        assertThat(state.homeHasPossession()).isFalse();
        assertThat(fixture.away.possessionTicks()).isEqualTo(1);
        assertThat(state.possessor()).isSameAs(fixture.away);
    }
}

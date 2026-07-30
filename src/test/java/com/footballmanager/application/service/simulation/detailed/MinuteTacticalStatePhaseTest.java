package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteTacticalStatePhaseTest {
    private final MinuteTacticalPolicies policies = DetailedMatchMinuteComposition
            .create(new DisciplineModel(), org.slf4j.LoggerFactory.getLogger(getClass()))
            .tacticalPolicies();
    private final MinuteTacticalStatePhase phase = new MinuteTacticalStatePhase(policies);

    @Test
    void resolvesShapesEffectiveSlotsAndPossessionShare() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.BALANCED, TeamStyle.COUNTER);

        MinuteTacticalState state = phase.resolve(fixture.input(12, new Random(3)));

        assertThat(state.homeShare()).isBetween(0.0, 1.0);
        assertThat(state.homeShape().attackVolumeMultiplier()).isPositive();
        assertThat(state.awayShape().defensiveResistanceMultiplier()).isPositive();
        assertThat(state.homeMaxPasser()).isGreaterThanOrEqualTo(0);
        assertThat(state.awayMaxPasser()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reactsToChangedFormationAfterSubstitution() {
        MinutePhaseFixture fixture = new MinutePhaseFixture(TeamStyle.ATTACKING, TeamStyle.DEFENSIVE);
        MinuteTacticalState before = phase.resolve(fixture.input(20, new Random(4)));

        fixture.home.setFormation("4-3-3");
        fixture.home.startingPlayers().get(8).setPosition("ATT");
        MinuteTacticalState after = phase.resolve(fixture.input(21, new Random(4)));

        assertThat(after.homeShape()).isNotEqualTo(before.homeShape());
    }
}

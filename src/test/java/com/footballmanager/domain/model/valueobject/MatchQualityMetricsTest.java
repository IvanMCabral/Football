package com.footballmanager.domain.model.valueobject;

import com.footballmanager.application.service.domain.MatchQualityComputer;
import com.footballmanager.domain.model.aggregate.Team;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MatchQualityMetricsTest {

    @Test
    void fromLambdas_matchesMatchQualityComputer_75vs75() {
        var lambdas = MatchQualityComputer.computeLambdas(75, 75);
        var metrics = MatchQualityMetrics.fromLambdas(lambdas);

        assertEquals(lambdas.homeLambda(), metrics.homeXg());
        assertEquals(lambdas.awayLambda(), metrics.awayXg());
        assertEquals(lambdas.totalLambda(), metrics.totalXg());
        assertEquals(lambdas.homeShare(), metrics.homeShare());
    }

    @Test
    void fromTeams_producesFinitePositiveXg() {
        Team home = createTeam("Home", 75);
        Team away = createTeam("Away", 75);

        var metrics = MatchQualityMetrics.fromTeams(home, away);

        assertTrue(metrics.homeXg() > 0, "homeXg should be positive");
        assertTrue(metrics.awayXg() > 0, "awayXg should be positive");
        assertTrue(metrics.totalXg() > 0, "totalXg should be positive");
        assertFalse(Double.isNaN(metrics.homeXg()));
        assertFalse(Double.isInfinite(metrics.homeXg()));
        assertFalse(Double.isNaN(metrics.awayXg()));
        assertFalse(Double.isInfinite(metrics.awayXg()));
        assertFalse(Double.isNaN(metrics.totalXg()));
        assertFalse(Double.isInfinite(metrics.totalXg()));
    }

    @Test
    void withGoals_computesGoalsToXgRatio_correctly() {
        var lambdas = MatchQualityComputer.computeLambdas(75, 75);
        var metrics = MatchQualityMetrics.fromLambdas(lambdas);

        // With 2 goals total and totalXg ~2.60, ratio should be ~0.77
        var withGoals = metrics.withGoals(1, 1);
        assertEquals(0.0, metrics.goalsToXgRatio()); // original unchanged
        double expectedRatio = 2.0 / lambdas.totalLambda();
        assertEquals(expectedRatio, withGoals.goalsToXgRatio(), 0.001);
    }

    @Test
    void withGoals_zeroGoals_returnsZeroRatio() {
        var lambdas = MatchQualityComputer.computeLambdas(75, 75);
        var metrics = MatchQualityMetrics.fromLambdas(lambdas);

        var withZeroGoals = metrics.withGoals(0, 0);
        assertEquals(0.0, withZeroGoals.goalsToXgRatio());
    }

    @Test
    void fromLambdas_rejectsNaN() {
        var validLambdas = MatchQualityComputer.computeLambdas(75, 75);
        var badLambdas = new MatchQualityComputer.MatchQualityLambdas(
            Double.NaN, validLambdas.awayLambda(), validLambdas.totalLambda(), validLambdas.homeShare()
        );
        assertThrows(IllegalArgumentException.class, () -> MatchQualityMetrics.fromLambdas(badLambdas));
    }

    @Test
    void fromLambdas_rejectsInfinity() {
        var validLambdas = MatchQualityComputer.computeLambdas(75, 75);
        var badLambdas = new MatchQualityComputer.MatchQualityLambdas(
            Double.POSITIVE_INFINITY, validLambdas.awayLambda(), validLambdas.totalLambda(), validLambdas.homeShare()
        );
        assertThrows(IllegalArgumentException.class, () -> MatchQualityMetrics.fromLambdas(badLambdas));
    }

    @Test
    void fromLambdas_rejectsNegativeXg() {
        var validLambdas = MatchQualityComputer.computeLambdas(75, 75);
        var badLambdas = new MatchQualityComputer.MatchQualityLambdas(
            -0.5, validLambdas.awayLambda(), validLambdas.totalLambda(), validLambdas.homeShare()
        );
        assertThrows(IllegalArgumentException.class, () -> MatchQualityMetrics.fromLambdas(badLambdas));
    }

    @Test
    void fromLambdas_totalXg_consistency() {
        var lambdas = MatchQualityComputer.computeLambdas(80, 70);
        var metrics = MatchQualityMetrics.fromLambdas(lambdas);

        // totalXg should equal homeXg + awayXg (by lambda definition)
        assertEquals(metrics.totalXg(), metrics.homeXg() + metrics.awayXg(), 0.0001);
    }

    private Team createTeam(String name, int ovr) {
        Team team = Team.create(
            com.footballmanager.domain.model.valueobject.TeamId.generate(),
            com.footballmanager.domain.model.valueobject.UserId.generate(),
            name, "England",
            new BigDecimal("10000000"),
            com.footballmanager.domain.model.valueobject.Formation.ofDefault()
        );
        for (int i = 0; i < 11; i++) {
            team.addPlayer(com.footballmanager.domain.model.valueobject.PlayerId.generate());
        }
        return team;
    }
}
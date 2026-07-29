package com.footballmanager.application.config;

import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.domain.service.MatchSimulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires simulation feature flags into the league simulator.
 */
@Configuration("applicationSimulationConfig")
public class SimulationConfig {

    private final Environment environment;

    public SimulationConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator,
                                           DetailedMatchStoragePort detailedMatchStoragePort) {
        boolean useClassicEngine = booleanProperty(
                "app.simulation.league.use-classic-engine",
                false);
        boolean useDetailedMatchEngine = booleanProperty(
                "app.simulation.league.detailed-enabled",
                false);
        boolean persistDetail = detailedBoolean("persist-detail", false);
        boolean mutateCareerState = detailedBoolean("mutate-career-state", false);
        boolean persistInjuries = detailedBoolean("persist-injuries", false);
        boolean persistFatigue = detailedBoolean("persist-fatigue", false);
        boolean persistDiscipline = detailedBoolean("persist-discipline", false);
        boolean persistForm = detailedBoolean("persist-form", false);

        return new LeagueSimulator(matchSimulator, null, useClassicEngine, useDetailedMatchEngine,
                persistDetail, detailedMatchStoragePort,
                mutateCareerState, persistInjuries, persistFatigue, persistDiscipline, persistForm);
    }

    private boolean detailedBoolean(String name, boolean defaultValue) {
        return booleanProperty("app.simulation.detailed." + name, defaultValue);
    }

    private boolean booleanProperty(String currentName, boolean defaultValue) {
        String current = environment.getProperty(currentName);
        if (current != null) {
            return Boolean.parseBoolean(current);
        }
        return defaultValue;
    }
}

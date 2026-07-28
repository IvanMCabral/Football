package com.footballmanager.application.config;

import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.domain.service.MatchSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires simulation feature flags into the league simulator.
 *
 * <p>Current properties use domain language. Deprecated aliases are read only at
 * this boundary so older deployments keep working without leaking versioned
 * names into the simulator.
 */
@Configuration("applicationSimulationConfig")
@Slf4j
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
                "app.simulation.league.use-v23-engine",
                false);
        boolean useDetailedMatchEngine = booleanProperty(
                "app.simulation.league.detailed-enabled",
                false,
                "app.simulation.league.use-detailed-match-engine",
                "app.simulation.league.use-v24-detailed-engine");
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
        return booleanProperty(
                "app.simulation.detailed." + name,
                defaultValue,
                "app.simulation.v24." + name);
    }

    private boolean booleanProperty(String currentName, String deprecatedName, boolean defaultValue) {
        return booleanProperty(currentName, defaultValue, deprecatedName);
    }

    private boolean booleanProperty(String currentName, boolean defaultValue, String... deprecatedNames) {
        String current = environment.getProperty(currentName);
        if (current != null) {
            return Boolean.parseBoolean(current);
        }

        for (String deprecatedName : deprecatedNames) {
            String deprecated = environment.getProperty(deprecatedName);
            if (deprecated != null) {
                log.warn("Deprecated simulation property '{}' is still in use; switch to '{}'. "
                        + "The alias is kept only for existing deployments.", deprecatedName, currentName);
                return Boolean.parseBoolean(deprecated);
            }
        }

        return defaultValue;
    }
}

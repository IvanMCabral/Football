package com.footballmanager.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Feature flags for detailed match persistence and API exposure.
 */
@Configuration
@Slf4j
public class DetailedSimulationConfig {

    private final Environment environment;

    public DetailedSimulationConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public DetailedSimulationProperties detailedSimulationProperties() {
        return new DetailedSimulationProperties(
                detailedBoolean("persist-detail", false),
                detailedBoolean("expose-detail-api", false));
    }

    @Bean("detailedMatchApiEnabled")
    public boolean detailedMatchApiEnabled(DetailedSimulationProperties properties) {
        return properties.isExposeDetailApi();
    }

    private boolean detailedBoolean(String name, boolean defaultValue) {
        String currentName = "app.simulation.detailed." + name;
        String deprecatedName = "app.simulation.v24." + name;
        String current = environment.getProperty(currentName);
        if (current != null) {
            return Boolean.parseBoolean(current);
        }

        String deprecated = environment.getProperty(deprecatedName);
        if (deprecated != null) {
            log.warn("Deprecated detailed simulation property '{}' is still in use; switch to '{}'. "
                    + "The alias is kept only for existing deployments.", deprecatedName, currentName);
            return Boolean.parseBoolean(deprecated);
        }

        return defaultValue;
    }

    public static class DetailedSimulationProperties {
        private final boolean persistDetail;
        private final boolean exposeDetailApi;

        public DetailedSimulationProperties(boolean persistDetail, boolean exposeDetailApi) {
            this.persistDetail = persistDetail;
            this.exposeDetailApi = exposeDetailApi;
        }

        public boolean isPersistDetail() {
            return persistDetail;
        }

        public boolean isExposeDetailApi() {
            return exposeDetailApi;
        }
    }
}

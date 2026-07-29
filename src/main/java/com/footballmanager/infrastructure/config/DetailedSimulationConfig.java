package com.footballmanager.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Feature flags for detailed match persistence and API exposure.
 */
@Configuration
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
        String current = environment.getProperty(currentName);
        if (current != null) {
            return Boolean.parseBoolean(current);
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

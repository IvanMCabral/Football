package com.footballmanager.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags for detailed match persistence and API exposure.
 */
@Configuration
public class DetailedSimulationConfig {

    @Bean
    public DetailedSimulationProperties detailedSimulationProperties(
            @Value("${app.simulation.detailed.persist-detail:${app.simulation.v24.persist-detail:false}}")
            boolean persistDetail,
            @Value("${app.simulation.detailed.expose-detail-api:${app.simulation.v24.expose-detail-api:false}}")
            boolean exposeDetailApi) {
        return new DetailedSimulationProperties(persistDetail, exposeDetailApi);
    }

    @Bean("detailedMatchApiEnabled")
    public boolean detailedMatchApiEnabled(DetailedSimulationProperties properties) {
        return properties.isExposeDetailApi();
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

package com.footballmanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flags for detailed match persistence and API exposure.
 */
@Configuration
public class V24SimulationConfig {

    @Bean
    public V24SimulationProperties v24SimulationProperties() {
        return new V24SimulationProperties();
    }

    @Bean("v24DetailApiEnabled")
    public boolean v24DetailApiEnabled(V24SimulationProperties properties) {
        return properties.isExposeDetailApi();
    }

    @ConfigurationProperties(prefix = "app.simulation.v24")
    public static class V24SimulationProperties {
        private boolean persistDetail = false;
        private boolean exposeDetailApi = false;

        public boolean isPersistDetail() {
            return persistDetail;
        }

        public void setPersistDetail(boolean persistDetail) {
            this.persistDetail = persistDetail;
        }

        public boolean isExposeDetailApi() {
            return exposeDetailApi;
        }

        public void setExposeDetailApi(boolean exposeDetailApi) {
            this.exposeDetailApi = exposeDetailApi;
        }
    }
}

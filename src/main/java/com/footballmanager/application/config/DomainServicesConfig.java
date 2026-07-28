package com.footballmanager.application.config;

import com.footballmanager.domain.service.DefaultMatchCommandApplier;
import com.footballmanager.domain.service.DefaultMatchSimulator;
import com.footballmanager.domain.service.DivisionScheduler;
import com.footballmanager.domain.service.FixtureGenerator;
import com.footballmanager.domain.service.FixtureValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for pure domain services.
 */
@Configuration
public class DomainServicesConfig {

    @Bean
    FixtureGenerator fixtureGenerator(FixtureValidator fixtureValidator) {
        return new FixtureGenerator(fixtureValidator);
    }

    @Bean
    FixtureValidator fixtureValidator() {
        return new FixtureValidator();
    }

    @Bean
    DivisionScheduler divisionScheduler(FixtureGenerator fixtureGenerator) {
        return new DivisionScheduler(fixtureGenerator);
    }

    @Bean
    DefaultMatchSimulator defaultMatchSimulator() {
        return new DefaultMatchSimulator();
    }

    @Bean
    DefaultMatchCommandApplier defaultMatchCommandApplier() {
        return new DefaultMatchCommandApplier();
    }
}

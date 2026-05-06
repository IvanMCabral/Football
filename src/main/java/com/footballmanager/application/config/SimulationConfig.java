package com.footballmanager.application.config;

import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.domain.service.MatchSimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 10C3: External configuration for V23 league engine flag.
 *
 * <p>Creates the LeagueSimulator bean with the useV23LeagueEngine property
 * read from application configuration. Default is false — existing
 * DefaultMatchSimulator path is used unless explicitly enabled.
 *
 * <p>LeagueSimulator is NOT annotated @Service — it is created exclusively
 * through this config to allow property injection.
 */
@Configuration
public class SimulationConfig {

    @Value("${app.simulation.league.use-v23-engine:false}")
    private boolean useV23Engine;

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator) {
        return new LeagueSimulator(matchSimulator, null, useV23Engine);
    }
}
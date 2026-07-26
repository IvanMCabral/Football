package com.footballmanager.application.config;

import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.application.service.simulation.v24.V24CareerMutationService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24InjuryMutationApplier;
import com.footballmanager.domain.service.MatchSimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires simulation feature flags into the league simulator.
 */
@Configuration("applicationSimulationConfig")
public class SimulationConfig {

    @Value("${app.simulation.league.use-v23-engine:false}")
    private boolean useV23Engine;

    @Value("${app.simulation.league.use-v24-detailed-engine:false}")
    private boolean useV24DetailedEngine;

    @Value("${app.simulation.v24.persist-detail:false}")
    private boolean persistDetail;

    @Value("${app.simulation.v24.mutate-career-state:false}")
    private boolean mutateCareerState;

    @Value("${app.simulation.v24.persist-injuries:false}")
    private boolean persistInjuries;

    @Value("${app.simulation.v24.persist-fatigue:false}")
    private boolean persistFatigue;

    @Value("${app.simulation.v24.persist-discipline:false}")
    private boolean persistDiscipline;

    @Value("${app.simulation.v24.persist-form:false}")
    private boolean persistForm;

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator,
                                           V24DetailedMatchStoragePort v24StoragePort) {
        return new LeagueSimulator(matchSimulator, null, useV23Engine, useV24DetailedEngine,
                persistDetail, v24StoragePort,
                mutateCareerState, persistInjuries, persistFatigue, persistDiscipline, persistForm);
    }
}

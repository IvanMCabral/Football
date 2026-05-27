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
 * Phase 10C3: External configuration for V23 league engine flag.
 *
 * <p>Creates the LeagueSimulator bean with the useV23LeagueEngine property
 * read from application configuration. Default is false — existing
 * DefaultMatchSimulator path is used unless explicitly enabled.
 *
 * <p>V24D5C: Also configures persistDetail flag and V24DetailedMatchStoragePort
 * for optional V24 detail persistence when app.simulation.v24.persist-detail=true.
 *
 * <p>V24D6B3: Configures career mutation flags for optional V24 injury/fatigue/
 * discipline/form persistence to CareerSave SessionPlayer state.
 * All mutation flags default to false. mutate-career-state is the master gate.
 *
 * <p>LeagueSimulator is NOT annotated @Service — it is created exclusively
 * through this config to allow property injection.
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
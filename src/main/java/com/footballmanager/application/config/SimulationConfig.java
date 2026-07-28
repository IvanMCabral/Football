package com.footballmanager.application.config;

import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.application.service.simulation.detailed.CareerMutationService;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.InjuryMutationApplier;
import com.footballmanager.domain.service.MatchSimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires simulation feature flags into the league simulator.
 */
@Configuration("applicationSimulationConfig")
public class SimulationConfig {

    @Value("${app.simulation.league.use-classic-engine:${app.simulation.league.use-v23-engine:false}}")
    private boolean useClassicEngine;

    @Value("${app.simulation.league.use-detailed-match-engine:${app.simulation.league.use-v24-detailed-engine:false}}")
    private boolean useDetailedMatchEngine;

    @Value("${app.simulation.detailed.persist-detail:${app.simulation.v24.persist-detail:false}}")
    private boolean persistDetail;

    @Value("${app.simulation.detailed.mutate-career-state:${app.simulation.v24.mutate-career-state:false}}")
    private boolean mutateCareerState;

    @Value("${app.simulation.detailed.persist-injuries:${app.simulation.v24.persist-injuries:false}}")
    private boolean persistInjuries;

    @Value("${app.simulation.detailed.persist-fatigue:${app.simulation.v24.persist-fatigue:false}}")
    private boolean persistFatigue;

    @Value("${app.simulation.detailed.persist-discipline:${app.simulation.v24.persist-discipline:false}}")
    private boolean persistDiscipline;

    @Value("${app.simulation.detailed.persist-form:${app.simulation.v24.persist-form:false}}")
    private boolean persistForm;

    @Bean
    public LeagueSimulator leagueSimulator(MatchSimulator matchSimulator,
                                           DetailedMatchStoragePort detailedMatchStoragePort) {
        return new LeagueSimulator(matchSimulator, null, useClassicEngine, useDetailedMatchEngine,
                persistDetail, detailedMatchStoragePort,
                mutateCareerState, persistInjuries, persistFatigue, persistDiscipline, persistForm);
    }
}

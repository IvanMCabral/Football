package com.footballmanager.domain.service;

import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.valueobject.Tactic;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementación del aplicador de comandos tácticos.
 * Contiene la lógica de negocio para aplicar cambios tácticos durante un partido.
 */
@Component
public class DefaultMatchCommandApplier implements MatchCommandApplier {

    @Override
    public MatchState apply(MatchState state, List<MatchCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return state;
        }

        for (MatchCommand cmd : commands) {
            applyCommand(state, cmd);
        }

        return state;
    }

    private void applyCommand(MatchState state, MatchCommand cmd) {
        if (cmd == null || cmd.getType() == null) {
            return;
        }

        switch (cmd.getType()) {
            case CHANGE_TACTIC -> applyTacticChange(state, cmd);
            case SUBSTITUTE -> applySubstitution(state, cmd);
            case CHANGE_MENTALITY -> applyMentalityChange(state, cmd);
        }
    }

    private void applyTacticChange(MatchState state, MatchCommand cmd) {
        if (cmd.getTeamId() == null || !(cmd.getPayload() instanceof Tactic tactic)) {
            return;
        }

        if (cmd.getTeamId().equals(state.getHomeTeamId())) {
            state.setHomeTactic(tactic);
        } else if (cmd.getTeamId().equals(state.getAwayTeamId())) {
            state.setAwayTactic(tactic);
        }
    }

    private void applySubstitution(MatchState state, MatchCommand cmd) {
        // TODO: Implementar lógica de sustitución cuando se definan las reglas de negocio
    }

    private void applyMentalityChange(MatchState state, MatchCommand cmd) {
        // TODO: Implementar lógica de mentalidad cuando se definan las reglas de negocio
    }
}

package com.footballmanager.application.engine.match;

import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.entity.Substitution;
import com.footballmanager.domain.model.valueobject.MatchCommandType;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import com.footballmanager.domain.model.valueobject.Tactic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Servicio que procesa comandos durante un partido en vivo.
 * Responsable de: cambios de táctica, sustituciones, cambio de mentalidad.
 *
 * Principle SRP: solo procesa comandos, no gestiona estado ni tiempo.
 *
 * Thread-safe: retorna nuevos snapshots en lugar de mutar.
 */
@Service
public class MatchCommandHandler {

    /**
     * Procesa un comando y aplica sus efectos al estado del partido.
     *
     * @param command Comando a procesar
     * @param state Estado actual del partido
     * @return Nuevo snapshot con el comando aplicado (estado inmutable)
     */
    public MatchStateSnapshot handleCommand(MatchCommand command, MatchStateSnapshot state) {
        return switch (command.getType()) {
            case CHANGE_TACTIC -> handleChangeTactic(command, state);
            case SUBSTITUTE -> handleSubstitute(command, state);
            case CHANGE_MENTALITY -> handleChangeMentality(command, state);
            default -> state;
        };
    }

    /**
     * Procesa cambio de táctica.
     */
    private MatchStateSnapshot handleChangeTactic(MatchCommand command, MatchStateSnapshot state) {
        Tactic newTactic = command.getTactic();
        if (command.isHomeTeam()) {
            return new MatchStateSnapshot(
                state.matchId(),
                state.homeTeamId(),
                state.awayTeamId(),
                state.currentMinute(),
                state.status(),
                state.score(),
                state.events(),
                state.careerId(),
                state.userId()
            );
        } else {
            return state;
        }
    }

    /**
     * Procesa sustitución de jugador.
     */
    private MatchStateSnapshot handleSubstitute(MatchCommand command, MatchStateSnapshot state) {
        if (command.getPlayerOut() == null || command.getPlayerIn() == null) {
            return state;
        }

        Substitution substitution = new Substitution(
            state.currentMinute(),
            command.getPlayerOut(),
            command.getPlayerIn()
        );

        // Crear nueva lista con la sustitución
        var substitutions = new ArrayList<>(state.events());

        return new MatchStateSnapshot(
            state.matchId(),
            state.homeTeamId(),
            state.awayTeamId(),
            state.currentMinute(),
            state.status(),
            state.score(),
            substitutions,
            state.careerId(),
            state.userId()
        );
    }

    /**
     * Procesa cambio de mentalidad.
     */
    private MatchStateSnapshot handleChangeMentality(MatchCommand command, MatchStateSnapshot state) {
        // TODO: Implementar cuando se defina la estructura de mentalidad
        return state;
    }

    /**
     * Valida si un comando es válido para el estado actual del partido.
     *
     * @param command Comando a validar
     * @param state Estado actual del partido
     * @return true si el comando es válido
     */
    public boolean isCommandValid(MatchCommand command, MatchStateSnapshot state) {
        if (state.status() != MatchStatus.RUNNING) {
            return false;
        }

        if (command.getType() == MatchCommandType.SUBSTITUTE) {
            // Validar que los jugadores no sean nulos
            if (command.getPlayerOut() == null || command.getPlayerIn() == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Versión legacy para compatibilidad con MatchState mutable.
     * @deprecated Usar la versión con MatchStateSnapshot
     */
    @Deprecated
    public void handleCommand(MatchCommand command, MatchState state) {
        switch (command.getType()) {
            case CHANGE_TACTIC -> handleChangeTacticLegacy(command, state);
            case SUBSTITUTE -> handleSubstituteLegacy(command, state);
            case CHANGE_MENTALITY -> handleChangeMentalityLegacy(command, state);
            default -> { }
        }
    }

    @Deprecated
    private void handleChangeTacticLegacy(MatchCommand command, MatchState state) {
        if (command.isHomeTeam()) {
            state.setHomeTactic(command.getTactic());
        } else {
            state.setAwayTactic(command.getTactic());
        }
    }

    @Deprecated
    private void handleSubstituteLegacy(MatchCommand command, MatchState state) {
        if (command.getPlayerOut() == null || command.getPlayerIn() == null) {
            return;
        }

        Substitution substitution = new Substitution(
            state.getCurrentMinute(),
            command.getPlayerOut(),
            command.getPlayerIn()
        );
        state.getSubstitutions().add(substitution);
    }

    @Deprecated
    private void handleChangeMentalityLegacy(MatchCommand command, MatchState state) {
        // TODO: Implementar
    }

    @Deprecated
    public boolean isCommandValid(MatchCommand command, MatchState state) {
        if (state.getStatus() != MatchStatus.RUNNING) {
            return false;
        }

        if (command.getType() == MatchCommandType.SUBSTITUTE) {
            if (command.getPlayerOut() == null || command.getPlayerIn() == null) {
                return false;
            }
        }

        return true;
    }
}

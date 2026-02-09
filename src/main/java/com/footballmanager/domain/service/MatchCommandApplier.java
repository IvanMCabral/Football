package com.footballmanager.domain.service;

import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;

import java.util.List;

/**
 * Servicio de dominio responsable de aplicar comandos tácticos sobre el estado de un partido.
 * Encapsula las reglas de negocio para cambios de táctica, sustituciones y mentalidad.
 */
public interface MatchCommandApplier {
    
    /**
     * Aplica una lista de comandos pendientes sobre el estado actual del partido.
     * @param state Estado actual del partido (será mutado)
     * @param commands Lista de comandos a aplicar
     * @return El estado actualizado con los comandos aplicados
     */
    MatchState apply(MatchState state, List<MatchCommand> commands);
}

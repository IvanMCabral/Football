package com.footballmanager.domain.service;

import com.footballmanager.domain.model.entity.MatchState;

/**
 * Servicio de dominio responsable de simular la progresión de un partido.
 * Encapsula las reglas de simulación de eventos, goles, tarjetas, etc.
 */
public interface MatchSimulator {

    /**
     * Simulación detallada (real).
     * Genera eventos por minuto: goles, tarjetas, corners, faltas, etc.
     * Usa atributos de equipos para probabilidades.
     *
     * @param state Estado actual del partido
     * @param toMinute Minuto objetivo
     * @return El estado actualizado con eventos
     */
    MatchState simulateReal(MatchState state, int toMinute);

    /**
     * Simulación rápida.
     * Calcula resultado final directamente sin generar eventos detalle.
     * Útil para simular partidos de divisiones donde no participa el usuario.
     *
     * @param homeTeamId ID del equipo local
     * @param awayTeamId ID del equipo visitante
     * @param homeOvr OVR del equipo local
     * @param awayOvr OVR del equipo visitante
     * @return Resultado del partido (goles local, goles visitante)
     */
    MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr);

    /**
     * Resultado de simulación rápida.
     */
    record MatchResult(int homeGoals, int awayGoals) {}
}

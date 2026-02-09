package com.footballmanager.domain.model.entity;

/**
 * Fases del ciclo de vida de una carrera.
 * 
 * Flujo esperado:
 * PRE_MATCH → IN_MATCH → POST_MATCH → WAITING_USER → PRE_MATCH → ... → FINISHED
 * 
 * Reglas de transición:
 * - PRE_MATCH: Listo para iniciar una fecha. Puede recibir comando de avanzar.
 * - IN_MATCH: Los partidos están en progreso. No acepta nuevos partidos.
 * - POST_MATCH: Resultados procesados. Transición automática a WAITING_USER.
 * - WAITING_USER: Pausa estratégica. Usuario debe confirmar para siguiente fecha.
 * - FINISHED: El torneo ha terminado. Solo permite ver tabla final o continuar carrera.
 */
public enum CareerPhase {
    /**
     * Preparación antes de jugar una fecha.
     * El usuario puede ajustar táctica/formación.
     */
    PRE_MATCH,
    
    /**
     * Los partidos de la fecha están en progreso.
     * Estado transitorio de corta duración.
     */
    IN_MATCH,
    
    /**
     * Todos los partidos culminaron, resultados en proceso.
     * Transición automática a WAITING_USER.
     */
    POST_MATCH,
    
    /**
     * PAUSA ESTRATÉGICA - Nuevo estado obligatorio.
     * El usuario debe confirmar explícitamente para avanzar.
     * No se puede iniciar el siguiente round automáticamente.
     * Permite: revisión de plantel, cambio de formación, ajustes tácticos.
     */
    WAITING_USER,
    
    /**
     * El torneo ha finalizado.
     * Estado terminal de una temporada.
     * Permite: ver tabla final, continuar a nuevo torneo.
     */
    FINISHED;

    /**
     * Verifica si la carrera está en una fase donde se pueden iniciar partidos.
     */
    public boolean canStartMatch() {
        return this == PRE_MATCH;
    }

    /**
     * Verifica si la carrera está en pausa estratégica.
     */
    public boolean isWaitingUser() {
        return this == WAITING_USER;
    }

    /**
     * Verifica si la carrera está en medio de partidos activos.
     */
    public boolean isInProgress() {
        return this == IN_MATCH;
    }

    /**
     * Verifica si la fase permite cambios tácticos.
     */
    public boolean allowsTacticalChanges() {
        return this == PRE_MATCH || this == WAITING_USER;
    }
    
    /**
     * Verifica si el torneo ha finalizado.
     */
    public boolean isFinished() {
        return this == FINISHED;
    }
}

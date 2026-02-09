package com.footballmanager.application.engine.model;

import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado de una jornada completa con todos sus partidos.
 * Se emite por SSE para actualizar múltiples partidos con UN solo stream.
 *
 * Thread-safe: usa MatchStateSnapshot inmutable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoundState {
    private UUID roundId;
    private Instant timestamp;
    private List<MatchStateSnapshot> matches;
    private RoundStatus status;

    public enum RoundStatus {
        NOT_STARTED,   // Jornada no ha comenzado
        IN_PROGRESS,   // Partidos en vivo
        PAUSED,        // Partidos pausados
        FINISHED,      // Todos los partidos terminaron (listo para mostrar resultados)
        COMPLETED      // Resultados procesados y persistidos
    }
}

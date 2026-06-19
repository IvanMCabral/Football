package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * DTO de posición de formación.
 *
 * <p>Replica la shape exacta que el front espera en
 * {@code SquadEditorModalComponent.FormationPositionDTO}.
 *
 * <p>Coordenadas son porcentajes relativos al field. La {@code subdivisionId}
 * apunta a la subdivisión del campo donde se renderiza el jugador.
 */
public record FormationPositionDTO(
    Integer index,
    String role,
    Double xPercent,
    Double yPercent,
    Double actionRangePercent,
    String subdivisionId
) {
}
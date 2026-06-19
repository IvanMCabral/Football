package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * DTO de subdivisión del campo de fútbol.
 *
 * <p>Replica la shape exacta que el front espera en
 * {@code SquadEditorModalComponent.FieldSubdivisionDTO}:
 * 81 subdivisiones normales (27 sectores × 3 sub-espacios) + 1 slot de GK.
 *
 * <p>Coordenadas son porcentajes relativos al field (0-100).
 * El campo está orientado vertical: top% pequeño = zona de ATAQUE
 * (arriba en pantalla = arco rival); bottom = zona DEFENSA (propio arco).
 */
public record FieldSubdivisionDTO(
    Integer sector,
    Integer subIndex,
    Boolean isGoalkeeper,
    Double left,
    Double top,
    Double width,
    Double height,
    String subdivisionId,
    String zone
) {
}
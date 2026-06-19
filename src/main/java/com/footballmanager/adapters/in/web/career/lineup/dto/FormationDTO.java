package com.footballmanager.adapters.in.web.career.lineup.dto;

import java.util.List;

/**
 * DTO de formación táctica.
 *
 * <p>Replica la shape exacta que el front espera en
 * {@code SquadEditorModalComponent.FormationDTO}.
 *
 * <p>Las posiciones ({@code positions[]}) referencian subdivisiones del campo
 * vía {@link FormationPositionDTO#subdivisionId()} y son las que el modal
 * marca como "recommended" cuando el usuario selecciona la formación.
 */
public record FormationDTO(
    String name,
    String description,
    Integer defenders,
    Integer midfielders,
    Integer attackers,
    Integer outfieldPlayers,
    List<FormationPositionDTO> positions
) {
}
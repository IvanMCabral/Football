package com.footballmanager.application.service.editor;

import java.util.List;

public record FormationDefinition(
    String name,
    String description,
    Integer defenders,
    Integer midfielders,
    Integer attackers,
    Integer outfieldPlayers,
    List<FormationPosition> positions
) {
}

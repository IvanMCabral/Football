package com.footballmanager.application.service.editor;

public record FieldSubdivision(
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

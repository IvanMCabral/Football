package com.footballmanager.application.service.editor;

public record FormationPosition(
    Integer index,
    String role,
    Double xPercent,
    Double yPercent,
    Double actionRangePercent,
    String subdivisionId
) {
}

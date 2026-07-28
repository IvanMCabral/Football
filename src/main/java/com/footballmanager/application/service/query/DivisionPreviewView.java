package com.footballmanager.application.service.query;

import java.util.List;

public record DivisionPreviewView(
    int divisionNumber,
    String divisionName,
    List<TeamOvrView> teams
) {
}

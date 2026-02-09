package com.footballmanager.adapters.in.web.world.dto;

import java.util.List;

/**
 * Preview de cómo quedarían las divisiones
 */
public record DivisionPreview(
    int divisionNumber,
    String name,
    List<TeamWithOVR> teams
) {}

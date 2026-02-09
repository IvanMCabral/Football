package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * Request para auto-seleccionar el Starting XI basado en OVR
 */
public record AutoSelectRequest(String formation) {

    public AutoSelectRequest {
        if (formation == null || formation.isBlank()) {
            throw new IllegalArgumentException("Formation is required");
        }
    }
}

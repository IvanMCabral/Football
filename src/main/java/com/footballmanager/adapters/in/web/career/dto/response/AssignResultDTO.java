package com.footballmanager.adapters.in.web.career.dto.response;

/**
 * DTO for player assignment operation result.
 */
public record AssignResultDTO(String message, Integer squadSize, Integer freePlayersCount) {

    public AssignResultDTO(String message) {
        this(message, null, null);
    }
}

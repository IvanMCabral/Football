package com.footballmanager.adapters.in.web.career.lineup.dto;

/**
 * DTO para un jugador en el lineup
 */
public record PlayerLineupDTO(
    String playerId,
    String name,
    String position,
    Integer overall,
    Integer energy,
    Boolean injured,
    Integer age
) {
}

package com.footballmanager.adapters.in.web.world.dto;

/**
 * Response de equipos random
 */
public record RandomTeamsResponse(Integer count, String message) {
    public static RandomTeamsResponse success(int count) {
        return new RandomTeamsResponse(count, count + " equipos random creados exitosamente");
    }
}

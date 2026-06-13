package com.footballmanager.application.service.world;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * V24D6U5: LaLigaSeedData — DTOs para deserializar el JSON embebido de La Liga 2024/25.
 *
 * <p>Estructura:
 * <pre>
 * {
 *   "league":  { "name": "...", "country": "...", "tier": 1 },
 *   "teams":   [ { "name": "...", "city": "...", "formation": "...", "budgetMillions": 800 }, ... ],
 *   "players": [ { "team": "...", "name": "...", "age": 25, "position": "GK",
 *                  "baseAttack": 22, "baseDefense": 90, "baseTechnique": 80,
 *                  "baseSpeed": 60, "baseStamina": 75, "baseMentality": 85 }, ... ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LaLigaSeedData(
        @JsonProperty("league") LeagueDto league,
        @JsonProperty("teams") List<TeamDto> teams,
        @JsonProperty("players") List<PlayerDto> players
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueDto(
            @JsonProperty("name") String name,
            @JsonProperty("country") String country,
            @JsonProperty("tier") Integer tier
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDto(
            @JsonProperty("name") String name,
            @JsonProperty("city") String city,
            @JsonProperty("formation") String formation,
            @JsonProperty("budgetMillions") Integer budgetMillions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerDto(
            @JsonProperty("team") String team,
            @JsonProperty("name") String name,
            @JsonProperty("age") Integer age,
            @JsonProperty("position") String position,
            @JsonProperty("baseAttack") Integer baseAttack,
            @JsonProperty("baseDefense") Integer baseDefense,
            @JsonProperty("baseTechnique") Integer baseTechnique,
            @JsonProperty("baseSpeed") Integer baseSpeed,
            @JsonProperty("baseStamina") Integer baseStamina,
            @JsonProperty("baseMentality") Integer baseMentality
    ) {}
}

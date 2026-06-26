package com.footballmanager.application.service.world;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.List;
import java.util.Map;

/**
 * V24D6U5: LaLigaSeedData — DTOs para deserializar el JSON embebido de La Liga 2024/25.
 *
 * <p>V25D32-F3: PlayerDto extendido con {@code heightCm} y {@code skillLevels} para
 * que el seed persista metadata fisica/skills en Postgres. Ambos son NULLABLE —
 * players viejos en el JSON quedan sin estos campos y LaLigaSeedService aplica
 * defaults (random height para heightCm, empty map para skillLevels).
 *
 * <p>Estructura:
 * <pre>
 * {
 *   "league":  { "name": "...", "country": "...", "tier": 1 },
 *   "teams":   [ { "name": "...", "city": "...", "formation": "...", "budgetMillions": 800 }, ... ],
 *   "players": [ { "team": "...", "name": "...", "age": 25, "position": "GK",
 *                  "baseAttack": 22, "baseDefense": 90, "baseTechnique": 80,
 *                  "baseSpeed": 60, "baseStamina": 75, "baseMentality": 85,
 *                  "heightCm": 188,                // V25D32: optional
 *                  "skillLevels": {                // V25D32: optional
 *                    "SHOOTER": 88, "DRIBBLER": 75
 *                  }
 *                }, ... ]
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

    /**
     * V25D32-F3: extendido con {@code heightCm} (Integer, nullable) y
     * {@code skillLevels} (Map&lt;PlayerSkill, Integer&gt;, nullable) para que el
     * seed pueda persistir metadata fisica/skills. Backward-compat: ambos son
     * opcionales en el JSON, players sin ellos quedan con null/empty.
     */
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
            @JsonProperty("baseMentality") Integer baseMentality,
            @JsonProperty("heightCm") Integer heightCm,
            @JsonProperty("skillLevels") Map<PlayerSkill, Integer> skillLevels
    ) {}
}

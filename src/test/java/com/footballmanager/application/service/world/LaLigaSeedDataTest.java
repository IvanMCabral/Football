package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6U5: Tests de deserialización del JSON embebido.
 */
class LaLigaSeedDataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesFullSeedJson() throws Exception {
        String json = """
                {
                  "league": { "name": "La Liga 2024/25", "country": "Spain", "tier": 1 },
                  "teams": [
                    { "name": "Real Madrid", "city": "Madrid", "formation": "4-3-3", "budgetMillions": 900 },
                    { "name": "Barcelona", "city": "Barcelona", "formation": "4-3-3", "budgetMillions": 800 }
                  ],
                  "players": [
                    { "team": "Real Madrid", "name": "Thibaut Courtois", "age": 32, "position": "GK",
                      "baseAttack": 25, "baseDefense": 92, "baseTechnique": 78,
                      "baseSpeed": 60, "baseStamina": 75, "baseMentality": 88 }
                  ]
                }
                """;

        LaLigaSeedData data = objectMapper.readValue(json, LaLigaSeedData.class);

        assertNotNull(data.league());
        assertEquals("La Liga 2024/25", data.league().name());
        assertEquals("Spain", data.league().country());
        assertEquals(1, data.league().tier());

        assertEquals(2, data.teams().size());
        assertEquals("Real Madrid", data.teams().get(0).name());
        assertEquals("Madrid", data.teams().get(0).city());
        assertEquals("4-3-3", data.teams().get(0).formation());
        assertEquals(900, data.teams().get(0).budgetMillions());

        assertEquals(1, data.players().size());
        LaLigaSeedData.PlayerDto p = data.players().get(0);
        assertEquals("Real Madrid", p.team());
        assertEquals("Thibaut Courtois", p.name());
        assertEquals(32, p.age());
        assertEquals("GK", p.position());
        assertEquals(25, p.baseAttack());
        assertEquals(92, p.baseDefense());
        assertEquals(88, p.baseMentality());
    }

    @Test
    void realSeedFileIsPresentAndParseable() throws Exception {
        // Lee el JSON real desde classpath
        var resource = new org.springframework.core.io.ClassPathResource("seed/laliga-2024-25.json");
        assertTrue(resource.exists(), "seed file must be on classpath");

        LaLigaSeedData data;
        try (var in = resource.getInputStream()) {
            data = objectMapper.readValue(in, LaLigaSeedData.class);
        }

        assertNotNull(data);
        assertEquals("La Liga 2024/25", data.league().name());
        // V25D78-C55.3 B1: LaLiga extended to 60 teams (was 20) to provide
        // real roster depth for multi-tier scheduling + auto-select flows.
        assertEquals(60, data.teams().size(), "LaLiga tiene 60 equipos (V25D78-C55.3 B1)");
        assertTrue(data.players().size() >= 900 && data.players().size() <= 1100,
                "LaLiga seed debe tener 900-1100 jugadores, fue " + data.players().size());
    }

    @Test
    void allPlayersHaveValidPosition() throws Exception {
        var resource = new org.springframework.core.io.ClassPathResource("seed/laliga-2024-25.json");
        LaLigaSeedData data;
        try (var in = resource.getInputStream()) {
            data = objectMapper.readValue(in, LaLigaSeedData.class);
        }

        for (LaLigaSeedData.PlayerDto p : data.players()) {
            // V25D78-C55.3 B1: extended LaLiga uses both 5-cat codes (GK/DEF/MID/WINGER/ATT)
            // and specific role codes (CB/LB/RB/CDM/CM/CAM/CF/ST) for synthetic teams.
            // The legacy LaLigaSeedService.mapPosition() translates 5-cat to specific
            // role codes, so both forms are valid in the JSON.
            java.util.Set<String> validPositions = java.util.Set.of(
                    "GK", "DEF", "MID", "WINGER", "ATT",
                    "CB", "LB", "RB", "CDM", "CM", "CAM", "CF", "ST");
            assertTrue(validPositions.contains(p.position()),
                    "Posición inválida para " + p.name() + ": " + p.position());
            assertTrue(p.age() != null && p.age() >= 16 && p.age() <= 45,
                    "Edad inválida para " + p.name() + ": " + p.age());
            assertTrue(p.baseAttack() != null && p.baseAttack() >= 0 && p.baseAttack() <= 100,
                    "baseAttack inválido para " + p.name());
            assertTrue(p.baseDefense() != null && p.baseDefense() >= 0 && p.baseDefense() <= 100,
                    "baseDefense inválido para " + p.name());
            assertTrue(p.baseTechnique() != null && p.baseTechnique() >= 0 && p.baseTechnique() <= 100,
                    "baseTechnique inválido para " + p.name());
            assertTrue(p.baseSpeed() != null && p.baseSpeed() >= 0 && p.baseSpeed() <= 100,
                    "baseSpeed inválido para " + p.name());
            assertTrue(p.baseStamina() != null && p.baseStamina() >= 0 && p.baseStamina() <= 100,
                    "baseStamina inválido para " + p.name());
            assertTrue(p.baseMentality() != null && p.baseMentality() >= 0 && p.baseMentality() <= 100,
                    "baseMentality inválido para " + p.name());
        }
    }

    @Test
    void allPlayerTeamReferencesExistInTeamsList() throws Exception {
        var resource = new org.springframework.core.io.ClassPathResource("seed/laliga-2024-25.json");
        LaLigaSeedData data;
        try (var in = resource.getInputStream()) {
            data = objectMapper.readValue(in, LaLigaSeedData.class);
        }

        var teamNamesLower = data.teams().stream()
                .map(t -> t.name().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        for (LaLigaSeedData.PlayerDto p : data.players()) {
            assertTrue(teamNamesLower.contains(p.team().toLowerCase()),
                    "Player " + p.name() + " referencia team " + p.team() + " que no existe en teams[]");
        }
    }
}

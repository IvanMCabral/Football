package com.footballmanager.application.service.simulation.v24.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V24D6M12: Verify PlayerSeasonStatsResponse and DTOs serialize to JSON via Jackson.
 * This is the root cause fix for the 500 "No Encoder" error in WebFlux.
 */
class PlayerSeasonStatsSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void playerSeasonStatsResponse_serializesToJson() throws Exception {
        PlayerSeasonStatsDto playerDto = new PlayerSeasonStatsDto(
                "career-123", 1, "team-A", "player-1", "Player One", "FWD",
                20, 18,  // appearances, starts
                5, 3, 10, 25,  // goals, assists, keyPasses, shots
                1, 0,  // yellowCards, redCards
                1, 3,  // injuries, fouls
                2, 0,  // matchesMissedInjuredApprox, matchesMissedSuspendedApprox
                7.5, 9.0, 5.5,  // averageRating, bestRating, worstRating
                30  // lastUpdatedRound
        );

        PlayerSeasonStatsResponse response = PlayerSeasonStatsResponse.builder()
                .careerId("career-123")
                .season(1)
                .playerStats(List.of(playerDto))
                .totalGoals(5)
                .totalAssists(3)
                .totalAppearances(20)
                .averageRating(7.5)
                .incomplete(false)
                .message("ok")
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"careerId\":\"career-123\"");
        assertThat(json).contains("\"season\":1");
        assertThat(json).contains("\"playerId\":\"player-1\"");
        assertThat(json).contains("\"playerName\":\"Player One\"");
        assertThat(json).contains("\"goals\":5");
        assertThat(json).contains("\"assists\":3");
        assertThat(json).contains("\"averageRating\":7.5");
    }

    @Test
    void playerSeasonStatsResponse_emptyList_serializesToJson() throws Exception {
        PlayerSeasonStatsResponse response = PlayerSeasonStatsResponse.builder()
                .careerId("career-123")
                .season(1)
                .playerStats(List.of())
                .totalGoals(0)
                .totalAssists(0)
                .totalAppearances(0)
                .averageRating(0.0)
                .incomplete(false)
                .message("No V24 detail data found")
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"careerId\":\"career-123\"");
        assertThat(json).contains("\"season\":1");
        assertThat(json).contains("\"playerStats\":[]");
        assertThat(json).contains("\"message\":\"No V24 detail data found\"");
    }

    @Test
    void playerSeasonStatsDto_allFields_serializeCorrectly() throws Exception {
        PlayerSeasonStatsDto dto = new PlayerSeasonStatsDto(
                "career-1", 2, "team-B", "p-99", "Test Player", "MID",
                30, 25,
                10, 8, 15, 40,
                3, 1,
                2, 5,
                4, 1,
                6.8, 8.5, 4.2,
                28
        );

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"careerId\":\"career-1\"");
        assertThat(json).contains("\"season\":2");
        assertThat(json).contains("\"teamId\":\"team-B\"");
        assertThat(json).contains("\"playerId\":\"p-99\"");
        assertThat(json).contains("\"playerName\":\"Test Player\"");
        assertThat(json).contains("\"position\":\"MID\"");
        assertThat(json).contains("\"appearances\":30");
        assertThat(json).contains("\"starts\":25");
        assertThat(json).contains("\"goals\":10");
        assertThat(json).contains("\"assists\":8");
        assertThat(json).contains("\"keyPasses\":15");
        assertThat(json).contains("\"shots\":40");
        assertThat(json).contains("\"yellowCards\":3");
        assertThat(json).contains("\"redCards\":1");
        assertThat(json).contains("\"injuries\":2");
        assertThat(json).contains("\"fouls\":5");
        assertThat(json).contains("\"matchesMissedInjuredApprox\":4");
        assertThat(json).contains("\"matchesMissedSuspendedApprox\":1");
        assertThat(json).contains("\"averageRating\":6.8");
        assertThat(json).contains("\"bestRating\":8.5");
        assertThat(json).contains("\"worstRating\":4.2");
        assertThat(json).contains("\"lastUpdatedRound\":28");
    }

    @Test
    void playerSeasonStatsWarning_serializesToJson() throws Exception {
        PlayerSeasonStatsWarning warning = new PlayerSeasonStatsWarning(
                PlayerSeasonStatsWarningCode.LARGE_LIMIT_CLAMPED,
                "limit was greater than max and was clamped to 200",
                "limit"
        );

        String json = objectMapper.writeValueAsString(warning);

        assertThat(json).contains("\"code\":\"LARGE_LIMIT_CLAMPED\"");
        assertThat(json).contains("\"message\":\"limit was greater than max and was clamped to 200\"");
        assertThat(json).contains("\"field\":\"limit\"");
    }
}
package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.entity.career.Promotion;
import com.footballmanager.infrastructure.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PromotionSerializationTest {
    @Test
    void repeatedSaveLoadsDoNotMultiplyTheRootAndNestedPromotionAliases() throws Exception {
        var mapper = new JacksonConfig().objectMapper();
        var career = new CareerSave();
        var promotion = new Promotion(); promotion.setTeamId("team"); promotion.setTeamName("Club");
        promotion.setFromDivisionId("lower"); promotion.setToDivisionId("higher"); promotion.setType(Promotion.PromotionType.PROMOTED);
        career.setPromotions(List.of(promotion, promotion));
        for (int i = 0; i < 3; i++) {
            career = mapper.readValue(mapper.writeValueAsString(career), CareerSave.class);
            assertEquals(1, career.getPromotions().size());
            assertEquals("team", career.getPromotions().get(0).getTeamId());
        }
    }
}

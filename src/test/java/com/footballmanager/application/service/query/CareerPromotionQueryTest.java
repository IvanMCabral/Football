package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.career.Promotion;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class CareerPromotionQueryTest {
    @Test
    void finishedSeasonShowsTheCalculatedMovesBeforeTheyAreExecuted() {
        var career = new CareerSave();
        career.getTournamentState().setCareerPhase(CareerPhase.FINISHED);
        var current = promotion("current-champion", Promotion.PromotionType.PROMOTED);
        var previous = promotion("previous-last", Promotion.PromotionType.RELEGATED);
        career.getPromotions().add(current);
        career.getSeasonManager().getLastExecutedPromotions().add(previous);
        StepVerifier.create(new CareerPalmaresQueryService().getPromotions(career))
                .assertNext(entries -> {
                    assertEquals(1, entries.size());
                    assertEquals("current-champion", entries.get(0).teamId());
                    assertEquals("PROMOTED", entries.get(0).type());
                }).verifyComplete();
        assertEquals(1, career.getPromotions().size());
        assertEquals(previous, career.getSeasonManager().getLastExecutedPromotions().get(0));
    }

    @Test
    void activeSeasonShowsLastExecutedMovesRatherThanOldCalculatedData() {
        var career = new CareerSave();
        career.getTournamentState().setCareerPhase(CareerPhase.PRE_MATCH);
        career.getPromotions().add(promotion("calculated", Promotion.PromotionType.PROMOTED));
        career.getSeasonManager().getLastExecutedPromotions().add(promotion("executed", Promotion.PromotionType.RELEGATED));
        StepVerifier.create(new CareerPalmaresQueryService().getPromotions(career))
                .assertNext(entries -> assertEquals("executed", entries.get(0).teamId())).verifyComplete();
    }

    private Promotion promotion(String id, Promotion.PromotionType type) {
        var p = new Promotion(); p.setTeamId(id); p.setTeamName(id); p.setType(type);
        p.setFromDivisionId("from"); p.setToDivisionId("to"); p.setFromPosition(1);
        return p;
    }
}

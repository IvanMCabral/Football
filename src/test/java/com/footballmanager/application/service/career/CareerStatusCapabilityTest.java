package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.CareerPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CareerStatusCapabilityTest {
    @ParameterizedTest
    @EnumSource(CareerPhase.class)
    void advanceCapabilityMatchesTheActualCommandPhase(CareerPhase phase) {
        var career = career(); career.getTournamentState().setCareerPhase(phase);
        var owner = career.getUserId();
        var sessions = mock(CareerSessionService.class);
        when(sessions.getCareerFromCache(owner)).thenReturn(Mono.just(career));
        StepVerifier.create(new GetCareerStatusUseCaseImpl(sessions).execute(owner))
                .assertNext(status -> assertEquals(phase == CareerPhase.PRE_MATCH || phase == CareerPhase.WAITING_USER, status.canAdvanceRound()))
                .verifyComplete();
    }

    @Test
    void missingCareerCannotAdvance() {
        var owner = UUID.randomUUID(); var sessions = mock(CareerSessionService.class);
        when(sessions.getCareerFromCache(owner)).thenReturn(Mono.empty());
        StepVerifier.create(new GetCareerStatusUseCaseImpl(sessions).execute(owner))
                .assertNext(status -> { assertNull(status.careerId()); assertFalse(status.canAdvanceRound()); }).verifyComplete();
    }

    @Test
    void finishedFlagAlsoPreventsAdvanceForAnInconsistentLegacyPhase() {
        var career = career(); career.getTournamentState().setFinished(true);
        career.getTournamentState().setCareerPhase(CareerPhase.PRE_MATCH);
        var sessions = mock(CareerSessionService.class);
        when(sessions.getCareerFromCache(career.getUserId())).thenReturn(Mono.just(career));
        StepVerifier.create(new GetCareerStatusUseCaseImpl(sessions).execute(career.getUserId()))
                .assertNext(status -> assertFalse(status.canAdvanceRound())).verifyComplete();
    }

    private CareerSave career() {
        var career = new CareerSave(); career.setUserId(UUID.randomUUID());
        career.getData().setCareerId(UUID.randomUUID().toString());
        return career;
    }
}
